// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit local metadata checkpoint. Never receives credentials, streams or audio bytes. */
class MusicQueueStore {
    private static final int MAGIC = 0x53485131, MAX_TRACKS = 1000, MAX_BYTES = 8_000_000;
    record Saved(List<OnlineTrack> tracks, OnlineMusicQueue.Mode mode) {
        Saved { tracks = List.copyOf(tracks); Objects.requireNonNull(mode); }
    }
    private final Path file;
    MusicQueueStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    void save(Saved saved) throws IOException {
        if (saved.tracks().size() > MAX_TRACKS) throw new IOException("Queue checkpoint exceeds 1000 tracks");
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeUTF(saved.mode().name()); out.writeInt(saved.tracks().size());
            for (var track : saved.tracks()) {
                validate(track);
                out.writeUTF(track.source()); out.writeUTF(track.id()); out.writeUTF(track.mediaId());
                out.writeUTF(track.title()); out.writeUTF(track.artist()); out.writeUTF(track.album());
                out.writeLong(track.durationMs());
            }
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Queue checkpoint is too large");
        Files.createDirectories(file.getParent());
        var temporary = Files.createTempFile(file.getParent(), "queue-", ".tmp");
        try {
            Files.write(temporary, bytes.toByteArray());
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    Optional<Saved> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > MAX_BYTES) throw new IOException("Queue checkpoint is too large");
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC) throw new IOException("Unsupported queue checkpoint format");
            OnlineMusicQueue.Mode mode;
            try { mode = OnlineMusicQueue.Mode.valueOf(in.readUTF()); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid queue mode", invalid); }
            int count = in.readInt();
            if (count < 0 || count > MAX_TRACKS) throw new IOException("Invalid queue checkpoint size");
            var tracks = new ArrayList<OnlineTrack>();
            for (int i = 0; i < count; i++) {
                var track = new OnlineTrack(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readLong());
                validate(track); tracks.add(track);
            }
            if (in.read() != -1) throw new IOException("Unexpected queue checkpoint data");
            return Optional.of(new Saved(tracks, mode));
        }
    }
    void delete() throws IOException { Files.deleteIfExists(file); }
    private static void validate(OnlineTrack track) throws IOException {
        if (!track.source().matches("[A-Za-z0-9_-]{1,40}") || !track.id().matches("[A-Za-z0-9]{1,40}")
                || !track.mediaId().matches("[A-Za-z0-9]{1,40}")) throw new IOException("Invalid platform track identifier");
        if (track.title().length() > 1024 || track.artist().length() > 1024 || track.album().length() > 1024)
            throw new IOException("Queue track metadata is too long");
    }
}
