// Author: dishanhai
package com.dishanhai.uuk.worker;

import org.mozilla.javascript.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** One framed request per process. No Minecraft/mod classes or host objects enter this JVM. */
public final class ScriptWorkerMain {
    private static final int MAGIC = 0x55554b31;
    private ScriptWorkerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) return;
        long parentPid = Long.parseLong(args[0]);
        long parentStart = Long.parseLong(args[1]);
        Thread parentWatch = new Thread(() -> {
            while (true) {
                Optional<ProcessHandle> parent = ProcessHandle.of(parentPid);
                if (parent.isEmpty() || !parent.get().isAlive()
                        || parent.get().info().startInstant().map(t -> t.toEpochMilli() != parentStart).orElse(true)) {
                    Runtime.getRuntime().halt(125);
                }
                try { Thread.sleep(100); }
                catch (InterruptedException stop) { return; }
            }
        }, "uuk-script-parent-watch");
        parentWatch.setDaemon(true); parentWatch.start();
        DataInputStream input = new DataInputStream(System.in);
        DataOutputStream output = new DataOutputStream(System.out);
        try {
            if (input.readInt() != MAGIC) throw new IOException("protocol");
            int outputLimit = bounded(input.readInt(), 128, 65_536);
            int requestLimit = bounded(input.readInt(), 1, 8_192);
            int requests = bounded(input.readInt(), 0, 32);
            int timeoutMillis = bounded(input.readInt(), 100, 30_000);
            String source = readFrame(input, 65_536);
            String data = readFrame(input, 32_768);
            if (input.read() != -1) throw new IOException("trailing input");
            Thread hardDeadline = new Thread(() -> {
                try { Thread.sleep(timeoutMillis); Runtime.getRuntime().halt(124); }
                catch (InterruptedException finished) { /* Normal completion. */ }
            }, "uuk-script-hard-deadline");
            hardDeadline.setDaemon(true); hardDeadline.start();
            try {
                String result = evaluate(source, data, outputLimit, requestLimit, requests, timeoutMillis);
                respond(output, true, result);
            } catch (BudgetExceeded failure) {
                respond(output, false, failure.code);
            } catch (RhinoException | IllegalArgumentException failure) {
                respond(output, false, "SCRIPT_ERROR");
            } finally { hardDeadline.interrupt(); }
        } catch (IOException | IllegalArgumentException failure) {
            respond(output, false, "PROTOCOL_ERROR");
        }
    }

    static String evaluate(String source, String inputJson, int outputLimit, int requestLimit,
                           int requestCount, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        return new SafeFactory(deadline).call(cx -> {
            ScriptableObject scope = cx.initSafeStandardObjects(null, true);
            for (String name : List.of("Packages", "java", "javax", "org", "com", "edu", "net",
                    "JavaAdapter", "JavaImporter", "getClass", "load", "loadClass", "readFile", "readUrl",
                    "print", "quit")) scope.delete(name);
            Object parsed = NativeJSON.parse(cx, scope, inputJson, (context, receiver, self, args) -> args[1]);
            // Validate depth/nodes/types before publishing the input in the script scope.
            new DataWriter(32_768).write(parsed);
            scope.defineProperty("input", parsed, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
            RequestBatch requests = new RequestBatch(requestCount);
            ScriptableObject api = (ScriptableObject) cx.newObject(scope);
            BaseFunction request = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
                @Override public Object call(Context context, Scriptable callScope, Scriptable self, Object[] args) {
                    checkDeadline(deadline);
                    if (args.length != 2 || !(args[0] instanceof CharSequence)) {
                        throw Context.reportRuntimeError("Invalid capability request");
                    }
                    String capability = args[0].toString();
                    if (capability.length() > 128 || !capability.matches("[a-z][a-z0-9_.:-]*")) {
                        throw Context.reportRuntimeError("Invalid capability identifier");
                    }
                    // Serialization may invoke a JavaScript getter, which can reenter this function.
                    // Reserve the slot before inspecting payload data and keep failed slots consumed.
                    int index = requests.reserve();
                    String payload = new DataWriter(requestLimit).write(args[1]);
                    String intent = "{\"capability\":" + new DataWriter(1024).write(capability) + ",\"payload\":" + payload + "}";
                    requests.intents.set(index, intent);
                    Scriptable ack = context.newObject(scope);
                    ScriptableObject.putProperty(ack, "queued", true);
                    ScriptableObject.putProperty(ack, "index", index);
                    return ack;
                }
            };
            request.sealObject();
            api.defineProperty("request", request, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
            api.sealObject();
            scope.defineProperty("uuk", api, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
            Object value = cx.evaluateString(scope, source, "uuk-script", 1, null);
            String result = new DataWriter(outputLimit).write(value == Undefined.instance ? null : value);
            requests.verifyComplete();
            String response = "{\"result\":" + result + ",\"requests\":[" + String.join(",", requests.intents) + "]}";
            if (response.getBytes(StandardCharsets.UTF_8).length > outputLimit) throw new BudgetExceeded("OUTPUT_LIMIT");
            checkDeadline(deadline);
            return response;
        });
    }

    private static final class RequestBatch {
        final List<String> intents = new ArrayList<>();
        private final int limit;
        private boolean exhausted;
        RequestBatch(int limit) { this.limit = limit; }
        int reserve() {
            if (exhausted || intents.size() >= limit) {
                exhausted = true;
                throw new BudgetExceeded("REQUEST_LIMIT");
            }
            int index = intents.size();
            intents.add(null);
            return index;
        }
        void verifyComplete() {
            // Even if script code catches an exception, a partially serialized batch is never executable.
            if (exhausted) throw new BudgetExceeded("REQUEST_LIMIT");
            if (intents.contains(null)) throw new BudgetExceeded("SCRIPT_ERROR");
        }
    }

    private static final class SafeFactory extends ContextFactory {
        private final long deadline;
        SafeFactory(long deadline) { this.deadline = deadline; }
        @Override protected Context makeContext() {
            Context context = super.makeContext();
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setInterpretedMode(true);
            context.setInstructionObserverThreshold(10_000);
            context.setMaximumInterpreterStackDepth(128);
            context.setClassShutter(name -> false);
            context.setWrapFactory(new WrapFactory() {
                @Override public Object wrap(Context cx, Scriptable scope, Object value, Class<?> staticType) {
                    throw new SecurityException("Host wrapping is disabled");
                }
                @Override public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object value, Class<?> staticType) {
                    throw new SecurityException("Host wrapping is disabled");
                }
                @Override public Scriptable wrapNewObject(Context cx, Scriptable scope, Object value) {
                    throw new SecurityException("Host wrapping is disabled");
                }
            });
            return context;
        }
        @Override protected void observeInstructionCount(Context cx, int instructions) { checkDeadline(deadline); }
    }
    private static void checkDeadline(long deadline) {
        if (System.nanoTime() - deadline >= 0) throw new BudgetExceeded("TIMEOUT");
    }
    private static final class BudgetExceeded extends RuntimeException {
        final String code;
        BudgetExceeded(String code) { super(code, null, false, false); this.code = code; }
    }

    /** Strict bounded JSON: plain records/arrays only; no toJSON, functions or Java wrappers. */
    private static final class DataWriter {
        private final int limit;
        private final StringBuilder out = new StringBuilder();
        private final Set<Object> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        private int nodes;
        DataWriter(int limit) { this.limit = limit; }
        String write(Object value) {
            append(value, 0);
            String result = out.toString();
            if (result.getBytes(StandardCharsets.UTF_8).length > limit) throw new BudgetExceeded("OUTPUT_LIMIT");
            return result;
        }
        private void add(String text) {
            if (text.length() > limit - out.length()) throw new BudgetExceeded("OUTPUT_LIMIT");
            out.append(text);
        }
        private void append(Object value, int depth) {
            if (depth > 32 || ++nodes > 4096) throw new BudgetExceeded("DATA_LIMIT");
            if (value == null) { add("null"); return; }
            if (value instanceof CharSequence text) { string(text); return; }
            if (value instanceof Boolean bool) { add(bool.toString()); return; }
            if (value instanceof Number number && !(value instanceof java.math.BigInteger)) {
                if (!Double.isFinite(number.doubleValue())) throw new BudgetExceeded("DATA_LIMIT");
                add(number.toString()); return;
            }
            if (!(value instanceof NativeObject) && !(value instanceof NativeArray)) throw new BudgetExceeded("DATA_LIMIT");
            if (!ancestors.add(value)) throw new BudgetExceeded("DATA_LIMIT");
            try {
                if (value instanceof NativeArray array) {
                    long length = array.getLength();
                    if (length > 4096) throw new BudgetExceeded("DATA_LIMIT");
                    add("[");
                    for (int i = 0; i < length; i++) {
                        if (i > 0) add(",");
                        Object item = array.get(i, array);
                        append(item == Scriptable.NOT_FOUND ? null : item, depth + 1);
                    }
                    add("]");
                } else {
                    NativeObject object = (NativeObject) value;
                    Object[] keys = object.getIds();
                    if (keys.length > 4096) throw new BudgetExceeded("DATA_LIMIT");
                    add("{");
                    for (int i = 0; i < keys.length; i++) {
                        if (i > 0) add(",");
                        Object key = keys[i];
                        if (!(key instanceof String) && !(key instanceof Integer)) throw new BudgetExceeded("DATA_LIMIT");
                        string(key.toString()); add(":");
                        append(key instanceof Integer index ? object.get(index, object) : object.get((String) key, object), depth + 1);
                    }
                    add("}");
                }
            } finally { ancestors.remove(value); }
        }
        private void string(CharSequence text) {
            if (text.length() > limit) throw new BudgetExceeded("OUTPUT_LIMIT");
            add("\"");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> add("\\\"");
                    case '\\' -> add("\\\\");
                    case '\n' -> add("\\n");
                    case '\r' -> add("\\r");
                    case '\t' -> add("\\t");
                    default -> {
                        if (c < 32 || Character.isSurrogate(c)) add(String.format(Locale.ROOT, "\\u%04x", (int)c));
                        else add(String.valueOf(c));
                    }
                }
            }
            add("\"");
        }
    }

    private static int bounded(int value, int min, int max) throws IOException {
        if (value < min || value > max) throw new IOException("limit");
        return value;
    }
    private static String readFrame(DataInputStream input, int limit) throws IOException {
        int length = bounded(input.readInt(), 0, limit);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("truncated");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static void respond(DataOutputStream output, boolean success, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        output.writeInt(MAGIC); output.writeBoolean(success); output.writeInt(bytes.length); output.write(bytes); output.flush();
    }
}
