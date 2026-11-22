// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class ClientMusicDownloads implements MusicDownloads {
    @FunctionalInterface interface Transfer {
        void write(URI uri, long size, OutputStream file, LongConsumer progress) throws IOException;
    }

    private static final Set<String> AUDIO = Set.of("mp3", "flac", "wav", "m4a", "aac", "mp4");
    private static final Set<String> QQ_FILES = Set.of("mflac", "mflac0", "mgg", "mgg0", "qmc0", "qmc2", "qmc3", "qmcflac", "qmcogg");
    private static final int TASK_LIMIT = 40;
    private final List<Job> jobs = new ArrayList<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                var thread = new Thread(task, "shb-music-download");
                thread.setDaemon(true);
                return thread;
            });
    private final Path directory;
    private final Supplier<QQMusicSource> sources;
    private final Transfer transfer;
    private long sequence;

    public ClientMusicDownloads(Path directory) {
        this(directory, QQMusicSource::new, new MusicHttp()::download);
    }

    ClientMusicDownloads(Path directory, Supplier<QQMusicSource> sources, Transfer transfer) {
        this.directory = directory.toAbsolutePath().normalize();
        this.sources = sources;
        this.transfer = transfer;
        workers.allowCoreThreadTimeOut(true);
    }

    @Override public Path directory() { return directory; }

    @Override public CompletableFuture<Options> options(OnlineTrack track, String cookie) {
        return submit(() -> {
            try { return source(cookie).downloadDetail(track).options(); }
            catch (OnlineMusicSource.LoginRequired denied) { return new Options(Access.SIGN_IN, List.of()); }
        });
    }

    @Override public synchronized long start(OnlineTrack track, Quality quality, String cookie) {
        Objects.requireNonNull(track);
        Objects.requireNonNull(quality);
        if (!"qq".equals(track.source()) || !track.id().matches("[A-Za-z0-9]{1,40}"))
            throw new IllegalArgumentException("Only exact QQ track IDs can be downloaded");
        for (var job : jobs)
            if (job.active() && job.track.id().equals(track.id()) && job.quality == quality) return job.id;
        if (jobs.size() >= TASK_LIMIT) {
            for (int i = jobs.size() - 1; i >= 0; i--) {
                if (!jobs.get(i).active()) { jobs.remove(i); break; }
            }
        }
        var job = new Job(++sequence, track, quality);
        jobs.add(0, job);
        try { job.future = workers.submit(() -> run(job, cookie)); }
        catch (RejectedExecutionException busy) { job.state = State.FAILED; }
        return job.id;
    }

    @Override public synchronized List<Task> tasks() {
        return jobs.stream().map(Job::snapshot).toList();
    }

    @Override public synchronized boolean active() {
        return jobs.stream().anyMatch(Job::active);
    }

    @Override public synchronized void cancel(long id) {
        for (var job : jobs) if (job.id == id && job.active()) {
            job.state = State.CANCELLED;
            job.future.cancel(true);
            workers.purge();
            return;
        }
    }

    private void run(Job job, String cookie) {
        Path staging = null;
        try {
            var grant = QQDownload.resolve(source(cookie), job.track, job.quality);
            synchronized (this) {
                if (!job.active()) return;
                job.access = grant.access();
                if (grant.access() != Access.AVAILABLE) { job.state = State.FAILED; return; }
                job.total = grant.bytes();
                job.state = State.DOWNLOADING;
            }
            Files.createDirectories(directory);
            staging = Files.createTempFile(directory, ".song-", ".part");
            try (var sink = Files.newOutputStream(staging)) {
                transfer.write(grant.uri(), grant.bytes(), sink, n -> job.received = n);
            }
            if (Files.size(staging) != grant.bytes() || !matches(staging, job.quality))
                throw new IOException("Invalid music payload");
            synchronized (this) {
                if (!job.active()) return;
                job.file = publish(staging, job);
                job.state = State.COMPLETE;
            }
        } catch (Exception failure) {
            synchronized (this) {
                if (job.active()) {
                    if (failure instanceof OnlineMusicSource.LoginRequired) job.access = Access.SIGN_IN;
                    job.state = State.FAILED;
                }
            }
        } finally {
            if (staging != null) try { Files.deleteIfExists(staging); } catch (IOException ignored) { }
        }
    }

    private Path publish(Path staging, Job job) throws IOException {
        String stem = safeStem(job.track.artist() + " - " + job.track.title());
        String suffix = " [" + job.track.id() + "-" + job.quality.name().toLowerCase(Locale.ROOT) + "]";
        for (int n = 0; n < 100; n++) {
            Path file = directory.resolve(stem + suffix + (n == 0 ? "" : " (" + (n + 1) + ")") + "." + job.quality.extension());
            try { return Files.move(staging, file); }
            catch (FileAlreadyExistsException occupied) { /* Preserve existing user files. */ }
        }
        throw new IOException("Too many files with this song name");
    }

    private static String safeStem(String name) {
        String safe = name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").strip();
        return safe.codePointCount(0, safe.length()) > 72 ? safe.substring(0, safe.offsetByCodePoints(0, 72)) : safe;
    }

    private QQMusicSource source(String cookie) {
        var source = sources.get();
        source.credentials(cookie);
        return source;
    }

    @Override public CompletableFuture<List<LocalFile>> scan(Path folder) {
        return submit(() -> {
            if (!Files.isDirectory(folder)) return List.of();
            try (var files = Files.list(folder)) {
                var entries = files.filter(Files::isRegularFile)
                        .filter(path -> AUDIO.contains(extension(path)) || QQ_FILES.contains(extension(path)))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .iterator();
                var result = new ArrayList<LocalFile>();
                while (entries.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    try { result.add(inspect(entries.next())); }
                    catch (IOException skipped) { /* A single unreadable file does not hide the directory. */ }
                }
                return List.copyOf(result);
            }
        });
    }

    static LocalFile inspect(Path file) throws IOException {
        try (var channel = FileChannel.open(file)) {
            long size = channel.size();
            var header = read(channel, 0);
            if (header == 0x664c6143) return new LocalFile(file, LocalFormat.STANDARD_FLAC, size);
            if (QQ_FILES.contains(extension(file)) || size >= 4 && read(channel, size - 4) == 0x63657800)
                return new LocalFile(file, LocalFormat.QQ_PROTECTED, size);
            return new LocalFile(file, LocalFormat.OTHER, size);
        }
    }

    private static boolean matches(Path file, Quality quality) throws IOException {
        try (var channel = FileChannel.open(file)) {
            int header = read(channel, 0);
            if (channel.size() < 4 || read(channel, channel.size() - 4) == 0x63657800) return false;
            return quality == Quality.LOSSLESS ? header == 0x664c6143 :
                    (header >>> 8) == 0x494433 || (header & 0xffe00000) == 0xffe00000;
        }
    }

    private static int read(FileChannel channel, long offset) throws IOException {
        var buffer = ByteBuffer.allocate(4);
        channel.position(offset);
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) return 0;
        return buffer.getInt(0);
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    @Override public CompletableFuture<Boolean> openLocal(Path file) {
        return submit(() -> {
            if (!Files.isRegularFile(file) || !(AUDIO.contains(extension(file)) || QQ_FILES.contains(extension(file))))
                return false;
            if (Desktop.isDesktopSupported()) {
                var desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    try { desktop.open(file.toFile()); return true; }
                    catch (IOException unavailable) { /* Minecraft may run AWT headless. */ }
                }
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String path = file.toAbsolutePath().toString();
            List<String> command = os.startsWith("windows")
                    ? List.of("rundll32.exe", "url.dll,FileProtocolHandler", file.toUri().toASCIIString())
                    : os.startsWith("mac") ? List.of("open", path) : List.of("xdg-open", path);
            try {
                var process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                process.getOutputStream().close();
                return !process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() == 0;
            } catch (IOException unavailable) { return false; }
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> work) {
        var result = new CompletableFuture<T>();
        try {
            var future = workers.submit(() -> {
                try { result.complete(work.call()); }
                catch (Exception failure) { result.completeExceptionally(failure); }
            });
            result.whenComplete((value, failure) -> {
                if (result.isCancelled()) { future.cancel(true); workers.purge(); }
            });
        } catch (RejectedExecutionException busy) { result.completeExceptionally(busy); }
        return result;
    }

    private static final class Job {
        final long id;
        final OnlineTrack track;
        final Quality quality;
        State state = State.QUEUED;
        Access access = Access.AVAILABLE;
        Future<?> future;
        volatile long received;
        long total;
        Path file;

        Job(long id, OnlineTrack track, Quality quality) {
            this.id = id;
            this.track = track;
            this.quality = quality;
        }
        boolean active() { return state == State.QUEUED || state == State.DOWNLOADING; }
        Task snapshot() { return new Task(id, track, quality, state, access, received, total, file); }
    }
}
