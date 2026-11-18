// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicQueueCheckpointTest {
    @TempDir Path directory;
    private final OnlineTrack a = new OnlineTrack("test", "a", "mediaA", "歌曲 A", "Artist", "Album", 2000, "https://example.test/cover?secret=never-save");
    private Path file() { return directory.resolve("queue.bin"); }
    private MusicQueueStore store() { return new MusicQueueStore(file()); }
    private static class Source implements OnlineMusicSource {
        final AtomicInteger resolves = new AtomicInteger();
        @Override public String id() { return "test"; }
        @Override public List<OnlineTrack> search(String q, int page, int limit) { return List.of(); }
        @Override public Stream resolve(OnlineTrack track) { resolves.incrementAndGet(); return new Stream(URI.create("https://example.test/audio?token=never-save"), 0, 2000, false); }
        @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
        @Override public void credentials(String cookie) {}
    }
    private OnlinePlayerSession session(Source source) {
        return new OnlinePlayerSession(List.of(source), () -> { throw new AssertionError("Restore must not open audio output"); },
                uri -> { throw new AssertionError("Restore must not fetch streams"); }, null, store());
    }
    @Test void roundTripKeepsDuplicatesModeAndMetadataButNeverArtworkOrAudioUrls() throws Exception {
        store().save(new MusicQueueStore.Saved(List.of(a,a), OnlineMusicQueue.Mode.SHUFFLE));
        var saved = store().load().orElseThrow();
        assertEquals(2,saved.tracks().size()); assertEquals(saved.tracks().get(0),saved.tracks().get(1));
        assertEquals(a.title(),saved.tracks().get(0).title()); assertEquals(a.mediaId(),saved.tracks().get(0).mediaId());
        assertEquals("",saved.tracks().get(0).artworkUrl()); assertEquals(OnlineMusicQueue.Mode.SHUFFLE,saved.mode());
        var bytes = new String(Files.readAllBytes(file()),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains("https://")); assertFalse(bytes.contains("never-save"));
    }
    @Test void savingIsExplicitAndRestoringDoesNotResolvePlayOrAttachSelection() throws Exception {
        var source = new Source();
        try(var player=session(source)) {
            assertTrue(player.queue().supportsCheckpoints()); assertFalse(Files.exists(file()));
            assertFalse(player.queue().restoreCheckpoint());
            store().save(new MusicQueueStore.Saved(List.of(a,a), OnlineMusicQueue.Mode.REPEAT_ALL));
            assertTrue(player.queue().restoreCheckpoint());
            assertEquals(2,player.queue().snapshot().tracks().size()); assertEquals(-1,player.queue().snapshot().index());
            assertEquals(OnlineMusicQueue.Mode.REPEAT_ALL,player.queue().mode()); assertFalse(player.queue().snapshot().canUndo());
            assertEquals(OnlineMusicPlayer.State.IDLE,player.snapshot().state()); assertEquals(0,source.resolves.get());
            player.queue().saveCheckpoint(); player.queue().deleteCheckpoint();
            assertFalse(Files.exists(file()));assertEquals(2,player.queue().snapshot().tracks().size());
        }
        assertFalse(Files.exists(file()),"Close must not silently write a checkpoint");
    }
    @Test void restoreLeavesAudibleOutputRunningButDetachesAutomaticQueueProgress() throws Exception {
        var source=new Source(); var closed=new java.util.concurrent.atomic.AtomicBoolean();
        var outputState=new java.util.concurrent.atomic.AtomicReference<>(OnlineMusicPlayer.State.IDLE);
        var outputs=new AtomicInteger();
        try(var player=new OnlinePlayerSession(List.of(source),()-> {
            outputs.incrementAndGet();return new PlaybackOutput() {
                public void start(byte[] bytes,long position,boolean playing){outputState.set(OnlineMusicPlayer.State.PLAYING);}
                public void pause(){outputState.set(OnlineMusicPlayer.State.PAUSED);}
                public void resume(){outputState.set(OnlineMusicPlayer.State.PLAYING);}
                public OnlineMusicPlayer.State state(){return outputState.get();}
                public long positionMs(){return 0;}
                public void close(){closed.set(true);}
            };
        },uri->new byte[]{1},null,store())) {
            player.queue().replace(List.of(a,a),0);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while(outputState.get()!=OnlineMusicPlayer.State.PLAYING && System.nanoTime()<deadline)Thread.sleep(5);
            assertEquals(OnlineMusicPlayer.State.PLAYING,outputState.get());
            player.queue().saveCheckpoint();player.queue().restoreCheckpoint();
            assertEquals(OnlineMusicPlayer.State.PLAYING,player.snapshot().state());assertFalse(closed.get());
            assertEquals(-1,player.queue().snapshot().index());assertEquals(1,outputs.get());
            outputState.set(OnlineMusicPlayer.State.ENDED);Thread.sleep(180);
            assertEquals(1,outputs.get());assertEquals(1,source.resolves.get());
        }
    }
    @Test void diskLoadDoesNotHoldPlayerLockAndConcurrentChangesRejectStaleRestore() throws Exception {
        store().save(new MusicQueueStore.Saved(List.of(a),OnlineMusicQueue.Mode.SHUFFLE));
        var loaded=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var delayed=new MusicQueueStore(file()) {
            @Override Optional<Saved> load() throws IOException {
                var saved=super.load();loaded.countDown();
                try { if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("test release timeout"); }
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
                return saved;
            }
        };
        try(var player=new OnlinePlayerSession(List.of(new Source()),()->{throw new AssertionError();},uri->{throw new AssertionError();},null,delayed)) {
            var pending=java.util.concurrent.CompletableFuture.runAsync(()-> {
                try { player.queue().restoreCheckpoint(); } catch(IOException failure){throw new java.util.concurrent.CompletionException(failure);}
            });
            try {
                assertTrue(loaded.await(2,java.util.concurrent.TimeUnit.SECONDS));
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->player.queue().mode(OnlineMusicQueue.Mode.REPEAT_ONE));
            } finally { release.countDown(); }
            var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->pending.get(2,java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(IOException.class,failure.getCause());assertTrue(player.queue().snapshot().tracks().isEmpty());
            assertEquals(OnlineMusicQueue.Mode.REPEAT_ONE,player.queue().mode());
        }
    }
    @Test void corruptAndUnknownProviderCheckpointsLeaveQueueUnchanged() throws Exception {
        try(var player=session(new Source())) {
            store().save(new MusicQueueStore.Saved(List.of(a),OnlineMusicQueue.Mode.REPEAT_ONE)); player.queue().restoreCheckpoint();
            var before=player.queue().snapshot();
            Files.write(file(),new byte[]{1,2,3});assertThrows(IOException.class,()->player.queue().restoreCheckpoint());assertEquals(before,player.queue().snapshot());
            store().save(new MusicQueueStore.Saved(List.of(new OnlineTrack("other","a","a","","","",1)),OnlineMusicQueue.Mode.SEQUENTIAL));
            assertThrows(IllegalArgumentException.class,()->player.queue().restoreCheckpoint());assertEquals(before,player.queue().snapshot());
        }
    }
    @Test void failedSavePreservesExistingCheckpointAndRejectsOversizedOrInvalidMetadata() throws Exception {
        store().save(new MusicQueueStore.Saved(List.of(a),OnlineMusicQueue.Mode.SEQUENTIAL));var before=Files.readAllBytes(file());
        assertThrows(IOException.class,()->store().save(new MusicQueueStore.Saved(Collections.nCopies(1001,a),OnlineMusicQueue.Mode.SEQUENTIAL)));
        assertArrayEquals(before,Files.readAllBytes(file()));
        assertThrows(IOException.class,()->store().save(new MusicQueueStore.Saved(List.of(new OnlineTrack("test","https://secret","a","","","",0)),OnlineMusicQueue.Mode.SEQUENTIAL)));
        assertArrayEquals(before,Files.readAllBytes(file()));
        assertThrows(IOException.class,()->store().save(new MusicQueueStore.Saved(List.of(new OnlineTrack("test","a","a","x".repeat(1025),"","",0)),OnlineMusicQueue.Mode.SEQUENTIAL)));
        assertArrayEquals(before,Files.readAllBytes(file()));
    }
    @Test void unsupportedSessionAndClosedSessionRejectCheckpointOperations() throws Exception {
        try(var player=new OnlinePlayerSession(List.of(new Source()))) {
            assertFalse(player.queue().supportsCheckpoints());assertThrows(UnsupportedOperationException.class,()->player.queue().saveCheckpoint());
        }
        var player=session(new Source());player.close();
        assertThrows(IllegalStateException.class,()->player.queue().restoreCheckpoint());assertThrows(IllegalStateException.class,()->player.queue().saveCheckpoint());
        assertThrows(IllegalStateException.class,()->player.queue().deleteCheckpoint());
    }
    @Test void formatVersionAndTrailingDataAreNotSilentlyAccepted() throws Exception {
        store().save(new MusicQueueStore.Saved(List.of(a),OnlineMusicQueue.Mode.SEQUENTIAL));Files.write(file(),new byte[]{1},StandardOpenOption.APPEND);
        assertThrows(IOException.class,()->store().load());
        Files.write(file(),new byte[]{0,0,0,0});assertThrows(IOException.class,()->store().load());
    }
}
