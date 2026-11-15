// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.script;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Owns disposable, data-only JavaScript processes. This is not an OS security sandbox. */
public final class ScriptWorkerSupervisor implements AutoCloseable {
    private static final int MAGIC = 0x55554b31;
    public record Limits(int sourceBytes, int inputBytes, int outputBytes, int requestBytes,
                         int requests, int parallelism, int queueSize, int heapMiB, Duration timeout) {
        public Limits {
            if (sourceBytes < 1 || sourceBytes > 65_536 || inputBytes < 1 || inputBytes > 32_768
                    || outputBytes < 128 || outputBytes > 65_536 || requestBytes < 1 || requestBytes > 8_192
                    || requests < 0 || requests > 32 || parallelism < 1 || parallelism > 4
                    || queueSize < 1 || queueSize > 32 || heapMiB < 32 || heapMiB > 128
                    || timeout == null || timeout.toMillis() < 100 || timeout.toMillis() > 30_000) {
                throw new IllegalArgumentException("Invalid script worker limits");
            }
        }
        public static Limits defaults() {
            return new Limits(65_536, 32_768, 65_536, 8_192, 16, 2, 16, 64, Duration.ofSeconds(5));
        }
    }
    /** payloadJson is untrusted data, including capability intents; it never confers permission. */
    public record Result(boolean success, String payloadJson, String errorCode) {
        static Result failure(String code) { return new Result(false, "null", code); }
    }
    private final Path workerJar;
    private final Limits limits;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService timer;
    private final Set<Job> jobs = ConcurrentHashMap.newKeySet();
    private final Object lifecycle = new Object();
    private final Thread shutdownHook;
    private boolean closed;

    public ScriptWorkerSupervisor(Path workerJar, Limits limits) {
        this.workerJar = Objects.requireNonNull(workerJar).toAbsolutePath().normalize();
        if (!Files.isRegularFile(this.workerJar)) throw new IllegalArgumentException("Worker JAR missing");
        this.limits = Objects.requireNonNull(limits);
        executor = new ThreadPoolExecutor(limits.parallelism(), limits.parallelism(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.queueSize()), daemonFactory("uuk-script-io"));
        timer = Executors.newSingleThreadScheduledExecutor(daemonFactory("uuk-script-timeout"));
        shutdownHook = new Thread(this::close, "uuk-script-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public CompletableFuture<Result> submit(String source, String inputJson) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(inputJson);
        // Check UTF-16 length before allocating UTF-8 buffers for untrusted input.
        if (source.length() > limits.sourceBytes() || inputJson.length() > limits.inputBytes()) {
            return CompletableFuture.completedFuture(Result.failure("INPUT_LIMIT"));
        }
        byte[] code = source.getBytes(StandardCharsets.UTF_8);
        byte[] input = inputJson.getBytes(StandardCharsets.UTF_8);
        if (code.length > limits.sourceBytes() || input.length > limits.inputBytes()) {
            return CompletableFuture.completedFuture(Result.failure("INPUT_LIMIT"));
        }
        Job job = new Job(code, input);
        synchronized (lifecycle) {
            if (closed) return CompletableFuture.completedFuture(Result.failure("CLOSED"));
            jobs.add(job);
            try { executor.execute(job); }
            catch (RejectedExecutionException busy) {
                jobs.remove(job);
                job.result.complete(Result.failure("BUSY"));
            }
        }
        job.result.whenComplete((value, error) -> {
            if (job.result.isCancelled()) {
                job.stop("CANCELLED");
                if (executor.remove(job)) jobs.remove(job);
            }
        });
        return job.result;
    }

    public int activeProcessCount() {
        return (int) jobs.stream().filter(job -> job.process != null && job.process.isAlive()).count();
    }

    private final class Job implements Runnable {
        final byte[] source, input;
        final CompletableFuture<Result> result = new CompletableFuture<>();
        volatile Process process;
        Job(byte[] source, byte[] input) { this.source = source; this.input = input; }
        void stop(String code) {
            Process current = process;
            if (current != null) current.destroyForcibly();
            result.complete(Result.failure(code));
        }
        @Override public void run() {
            ScheduledFuture<?> deadline = null;
            try {
                synchronized (lifecycle) {
                    if (closed || result.isDone()) return;
                    boolean windows = System.getProperty("os.name", "").startsWith("Windows");
                    Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java");
                    // javaw avoids opening a console window on Windows. No shell is involved.
                    ProcessBuilder builder = new ProcessBuilder(java.toString(), "-Xms16m", "-Xmx" + limits.heapMiB() + "m",
                            "-XX:MaxMetaspaceSize=64m", "-XX:MaxDirectMemorySize=16m", "-Xss512k", "-XX:+UseSerialGC",
                            "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                            "-jar", workerJar.toString(), Long.toString(ProcessHandle.current().pid()),
                            Long.toString(ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli()));
                    for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) {
                        builder.environment().remove(name);
                    }
                    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                    process = builder.start();
                    if (result.isDone()) { process.destroyForcibly(); return; }
                    deadline = timer.schedule(() -> stop("TIMEOUT"), limits.timeout().toMillis(), TimeUnit.MILLISECONDS);
                }
                try (DataOutputStream out = new DataOutputStream(process.getOutputStream());
                     DataInputStream in = new DataInputStream(process.getInputStream())) {
                    out.writeInt(MAGIC);
                    out.writeInt(limits.outputBytes()); out.writeInt(limits.requestBytes()); out.writeInt(limits.requests());
                    out.writeInt((int) limits.timeout().toMillis());
                    writeFrame(out, source); writeFrame(out, input); out.flush();
                    process.getOutputStream().close();
                    if (in.readInt() != MAGIC) throw new IOException("Invalid worker protocol");
                    boolean success = in.readBoolean();
                    String payload = readFrame(in, limits.outputBytes());
                    // One process, one response; trailing output is a protocol failure.
                    if (in.read() != -1) throw new IOException("Trailing worker output");
                    if (!process.waitFor(1, TimeUnit.SECONDS) || process.exitValue() != 0) {
                        result.complete(Result.failure("WORKER_EXIT"));
                    } else {
                        result.complete(success ? new Result(true, payload, "") : Result.failure(knownError(payload)));
                    }
                }
            } catch (IOException | InterruptedException | RuntimeException failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                result.complete(Result.failure("WORKER_FAILURE"));
            } finally {
                if (deadline != null) deadline.cancel(false);
                if (process != null) {
                    process.destroyForcibly();
                    try { process.waitFor(2, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                if (process == null || !process.isAlive()) jobs.remove(this);
            }
        }
    }

    private static String knownError(String error) {
        return Set.of("SCRIPT_ERROR", "DATA_LIMIT", "REQUEST_LIMIT", "OUTPUT_LIMIT", "TIMEOUT", "PROTOCOL_ERROR")
                .contains(error) ? error : "WORKER_FAILURE";
    }
    private static void writeFrame(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length); out.write(data);
    }
    static String readFrame(DataInputStream input, int limit) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > limit) throw new IOException("Worker frame exceeds limit");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Truncated worker frame");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static ThreadFactory daemonFactory(String name) {
        return runnable -> { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; };
    }
    @Override public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            jobs.forEach(job -> job.stop("CLOSED"));
            executor.shutdownNow();
            timer.shutdownNow();
        }
        // Reap processes before returning from the explicit server-stop hook.
        for (Job job : jobs) {
            Process current = job.process;
            if (current != null) {
                try { current.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        jobs.removeIf(job -> job.process == null || !job.process.isAlive());
        if (Thread.currentThread() != shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException shuttingDown) { /* JVM hook already running. */ }
        }
    }
}
