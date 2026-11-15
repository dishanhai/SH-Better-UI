// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.automation.rule;

import com.dishanhai.sh_better_ui.kernel.api.automation.rule.*;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleStore.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;

/**
 * 版本化二进制快照。先写临时文件并 force，再原子替换；不支持原子移动时失败关闭。
 * 独占锁保证同一文件不能同时启动两个引擎；服务器停止时必须 close。
 */
public final class AtomicFileRuleStore implements RuleStore, AutoCloseable {
    private static final int MAGIC = 0x55554B52;
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final Path file;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;

    public AtomicFileRuleStore(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        Files.createDirectories(this.file.getParent());
        lockChannel = FileChannel.open(this.file.resolveSibling(this.file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
            if (lock == null) throw new IOException("Rule store already in use");
        } catch (IOException | RuntimeException e) {
            lockChannel.close();
            throw new IOException("Cannot lock rule store", e);
        }
    }

    @Override public synchronized Snapshot load() throws IOException {
        checkOpen();
        if (!Files.exists(file)) return Snapshot.empty();
        if (Files.size(file) > MAX_BYTES) throw new IOException("Rule store too large");
        return decode(Files.readAllBytes(file));
    }

    @Override public synchronized void save(Snapshot snapshot) throws IOException {
        checkOpen();
        byte[] bytes = encode(snapshot);
        Path temp = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    public static byte[] encode(Snapshot snapshot) throws IOException {
        if (snapshot.rules().size() > MAX_ENTRIES || snapshot.tasks().size() > MAX_ENTRIES
                || snapshot.paused().size() > MAX_ENTRIES) throw new IOException("Too many records");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(snapshot.formatVersion());
            out.writeInt(snapshot.rules().size());
            for (ReplenishmentRule r : snapshot.rules()) {
                uuid(out, r.id()); out.writeLong(r.revision()); uuid(out, r.owner());
                out.writeUTF(r.targetKey()); out.writeUTF(r.item()); out.writeLong(r.lower()); out.writeLong(r.target());
                out.writeUTF(r.mode().name()); out.writeBoolean(r.enabled());
            }
            out.writeInt(snapshot.tasks().size());
            for (Task t : snapshot.tasks()) {
                uuid(out, t.intentId()); uuid(out, t.ruleId());
                AutomationAction a = t.action();
                uuid(out, a.owner()); out.writeUTF(a.targetKey()); out.writeUTF(a.kind());
                if (a.arguments().size() > 128) throw new IOException("Too many action arguments");
                out.writeInt(a.arguments().size());
                for (var entry : new TreeMap<>(a.arguments()).entrySet()) {
                    out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue());
                }
                out.writeLong(a.amount()); out.writeUTF(t.state().name());
                out.writeBoolean(t.taskId() != null); if (t.taskId() != null) out.writeUTF(t.taskId());
                out.writeInt(t.writeAttempts()); out.writeInt(t.queryFailures()); out.writeLong(t.nextAttemptAt());
            }
            out.writeInt(snapshot.paused().size());
            for (UUID id : new TreeSet<>(snapshot.paused())) uuid(out, id);
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Rule store too large");
        return bytes.toByteArray();
    }

    public static Snapshot decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Rule store too large");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid rule store header");
            int version = in.readInt();
            if (version != 1) throw new IOException("Unsupported rule store version");
            List<ReplenishmentRule> rules = new ArrayList<>();
            for (int n = count(in, MAX_ENTRIES); n > 0; n--) {
                rules.add(new ReplenishmentRule(uuid(in), in.readLong(), uuid(in), in.readUTF(), in.readUTF(),
                        in.readLong(), in.readLong(), ReplenishmentRule.Mode.valueOf(in.readUTF()), in.readBoolean()));
            }
            List<Task> tasks = new ArrayList<>();
            for (int n = count(in, MAX_ENTRIES); n > 0; n--) {
                UUID intent = uuid(in), rule = uuid(in), owner = uuid(in);
                String target = in.readUTF(), kind = in.readUTF();
                Map<String, String> args = new HashMap<>();
                for (int a = count(in, 128); a > 0; a--) {
                    if (args.put(in.readUTF(), in.readUTF()) != null) throw new IOException("Duplicate argument");
                }
                AutomationAction action = new AutomationAction(owner, target, kind, args, in.readLong());
                TaskState state = TaskState.valueOf(in.readUTF());
                String taskId = in.readBoolean() ? in.readUTF() : null;
                tasks.add(new Task(intent, rule, action, state, taskId, in.readInt(), in.readInt(), in.readLong()));
            }
            Set<UUID> paused = new HashSet<>();
            for (int n = count(in, MAX_ENTRIES); n > 0; n--) {
                if (!paused.add(uuid(in))) throw new IOException("Duplicate pause record");
            }
            if (in.available() != 0) throw new IOException("Trailing rule store data");
            return new Snapshot(version, rules, tasks, paused);
        } catch (IllegalArgumentException | NullPointerException e) { throw new IOException("Invalid rule store", e); }
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max) throw new IOException("Invalid record count");
        return count;
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private void checkOpen() throws IOException { if (closed) throw new IOException("Rule store closed"); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { lock.release(); } finally { lockChannel.close(); }
    }
}
