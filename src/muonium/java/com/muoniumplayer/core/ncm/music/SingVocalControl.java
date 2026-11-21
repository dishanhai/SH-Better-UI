package com.muoniumplayer.core.ncm.music;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Local vocal separation for the embedded player's current PCM deck. Never changes provider playback. */
public final class SingVocalControl {
    public enum State { UNAVAILABLE, IDLE, PREPARING, READY, MODEL_MISSING, UNSUPPORTED, FAILED }

    static final int RATE = FoliaStemProfile.MATERIAL_RATE;
    // 64 MB max persistent PCM per active deck; inference works against independent 26-second windows.
    static final int MAX_FRAMES = 6 * 60 * RATE;
    private static final int CHUNK_MS = 24_000, OVERLAP_MS = 2_000;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "MP Sing preparation");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });

    static { WORKER.allowCoreThreadTimeOut(true); }

    private AudioPlayer deck;
    private long session = -1;
    private int generation;
    private State state = State.UNAVAILABLE;
    private int progress;
    private Future<?> work;

    public synchronized State state(CloudMusic.PlaybackSnapshot track) {
        AudioPlayer next = track == null || track.preparing ? null : track.player;
        long nextSession = next == null ? -1 : track.sessionId;
        if (deck != next || session != nextSession) {
            cancel();
            deck = next;
            session = nextSession;
            state = next == null ? State.UNAVAILABLE : next.hasSingMaterial() ? State.READY : State.IDLE;
        }
        return state;
    }

    public synchronized int progress() { return progress; }
    public synchronized float level() { return deck == null ? 1f : deck.getSingVocalLevel(); }

    public synchronized void setLevel(float level) {
        if (state == State.READY && deck != null && deck.hasSingMaterial()) deck.setSingVocalLevel(level);
    }

    public synchronized void cancel() {
        if (work != null) {
            work.cancel(true);
            WORKER.purge();
            work = null;
            generation++;
        }
        if (state == State.PREPARING) state = State.IDLE;
        progress = 0;
    }

    public synchronized void prepare(CloudMusic.PlaybackSnapshot track) {
        State now = state(track);
        if (now == State.UNAVAILABLE || now == State.PREPARING || now == State.READY || now == State.UNSUPPORTED)
            return;
        int token = ++generation;
        state = State.PREPARING;
        progress = 0;
        try {
            work = WORKER.submit(() -> run(track, token));
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            state = State.FAILED;
        }
    }

    private void run(CloudMusic.PlaybackSnapshot track, int token) {
        State result = State.FAILED;
        try {
            AudioPlayer player = track.player;
            File source = player.getSourceFile();
            if (source == null || player.sampleRate() != RATE) {
                result = State.UNSUPPORTED;
                return;
            }
            try (PcmWindowReader audio = PcmWindowReader.open(source)) {
                if (audio == null || audio.sampleRate() != RATE || audio.channels() > 2
                        || audio.frames() < RATE / 2 || audio.frames() > MAX_FRAMES) {
                    result = State.UNSUPPORTED;
                    return;
                }
                FoliaModelManager.Kind runtime = FoliaModelManager.preferOnnxInference()
                        ? FoliaModelManager.Kind.ONNX_RUNTIME : FoliaModelManager.Kind.RUNTIME;
                if (!FoliaModelManager.isInstalled(FoliaModelManager.Kind.HTDEMUCS)
                        || !FoliaModelManager.isInstalled(runtime)) {
                    result = State.MODEL_MISSING;
                    return;
                }
                float[] voice = prepareTrack(source, audio, track, token);
                if (voice == null) return;
                synchronized (this) {
                    if (alive(track, token) && player.attachSingMaterial(voice)) result = State.READY;
                }
            }
        } catch (IOException | RuntimeException failure) {
            System.err.println("[Sing] Vocal preparation failed: " + failure.getMessage());
        } finally {
            synchronized (this) {
                if (generation == token) {
                    state = result;
                    work = null;
                }
            }
        }
    }

    private synchronized boolean alive(CloudMusic.PlaybackSnapshot track, int token) {
        return generation == token && !Thread.currentThread().isInterrupted()
                && CloudMusic.isExpectedPlayback(track);
    }

    private float[] prepareTrack(File source, PcmWindowReader audio, CloudMusic.PlaybackSnapshot track,
                                 int token) throws IOException {
        int count = audio.frames();
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(8);
        float[] voice = new float[count];
        boolean usableVocal = false;
        // Reuse one bounded temporary stem file. No full-track stem is left on disk.
        File material = File.createTempFile("mp-sing-", ".f32");
        try {
            for (int startMillis = 0; ; startMillis += CHUNK_MS) {
                if (!alive(track, token) || System.nanoTime() - deadline >= 0) return null;
                int startFrame = (int) (startMillis * (long) RATE / 1000);
                int tailFrames = count - startFrame;
                if (tailFrames <= 0) break;
                int requestedMillis = (int) Math.min(CHUNK_MS + OVERLAP_MS,
                        (tailFrames * 1000L + RATE - 1L) / RATE);
                int expected = Math.min(tailFrames, requestedMillis * RATE / 1000);
                FoliaStemProfile profile = FoliaStemSeparator.analyseForSing(
                        source, startMillis, requestedMillis, audio, material, deadline);
                if (!alive(track, token) || profile == null || profile.materialFrames != expected
                        || material.length() != expected * (long) Float.BYTES) return null;
                float[] window = FoliaVocalStem.readLittleEndianFloats(material, expected);
                if (window == null) return null;
                // Untrusted separation (e.g. instrumental intro) remains untouched. Smoothly blend across
                // independent windows; never label a center cancellation filter as separated vocals.
                float projection = profile.hasVocalMaterial() ? profile.vocalProjection : 0f;
                boolean end = expected == tailFrames;
                int overlap = startMillis == 0 ? 0 : Math.min(expected, OVERLAP_MS * RATE / 1000);
                if (!blendWindow(voice, startFrame, window, overlap, projection)) return null;
                usableVocal |= projection > 0f;
                synchronized (this) {
                    if (generation == token) progress = (int) ((startFrame + (long) expected) * 100L / count);
                }
                if (end) break;
            }
        } catch (Exception failure) {
            if (failure instanceof IOException io) throw io;
            return null;
        } finally {
            Files.deleteIfExists(material.toPath());
        }
        return usableVocal ? voice : null;
    }

    static boolean blendWindow(float[] full, int start, float[] window, int overlap, float projection) {
        for (int i = 0; i < window.length; i++) {
            float sample = window[i];
            if (!Float.isFinite(sample) || Math.abs(sample) > 4f) return false;
            float voice = sample * projection;
            int target = start + i;
            if (i < overlap) {
                float blend = (float) i / Math.max(1, overlap - 1);
                full[target] += (voice - full[target]) * blend;
            } else {
                full[target] = voice;
            }
        }
        return true;
    }
}
