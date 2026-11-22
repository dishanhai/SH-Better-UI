// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Client-side library for explicit song downloads. Playback and streaming caches are independent. */
public interface MusicDownloads {
    static MusicDownloads create(Path directory) {
        return new com.dishanhai.sh_better_ui.kernel.internal.music.ClientMusicDownloads(directory);
    }
    enum Quality {
        STANDARD("mp3"), HIGH("mp3"), LOSSLESS("flac");

        private final String extension;
        Quality(String extension) { this.extension = extension; }
        public String extension() { return extension; }
    }

    enum Access { AVAILABLE, SIGN_IN, MEMBERSHIP, PURCHASE, UNAVAILABLE }
    enum State { QUEUED, DOWNLOADING, COMPLETE, CANCELLED, FAILED }
    enum LocalFormat { STANDARD_FLAC, QQ_PROTECTED, OTHER }
    record Option(Quality quality, long bytes) {}
    record Options(Access access, List<Option> qualities) {
        public Options { qualities = List.copyOf(qualities); }
    }
    record Task(long id, OnlineTrack track, Quality quality, State state,
                Access access, long received, long total, Path file) {}
    record LocalFile(Path path, LocalFormat format, long bytes) {}

    Path directory();
    /**
     * Lists advertised qualities. AVAILABLE allows a request; the task checks authorization for the selected file.
     * Cancelling this lookup interrupts it.
     */
    CompletableFuture<Options> options(OnlineTrack track, String cookie);
    /** Reuses an existing live task for the same track and quality. */
    long start(OnlineTrack track, Quality quality, String cookie);
    List<Task> tasks();
    boolean active();
    void cancel(long id);
    default void cancelActive() {
        for (Task task : tasks()) cancel(task.id());
    }
    CompletableFuture<List<LocalFile>> scan(Path directory);
    /** Ask the operating system to open a file in its associated application. */
    CompletableFuture<Boolean> openLocal(Path file);
}
