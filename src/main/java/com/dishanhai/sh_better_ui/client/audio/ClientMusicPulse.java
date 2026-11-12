// Author: dishanhai
package com.dishanhai.sh_better_ui.client.audio;

import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture.Status.*;

/** Client-thread session control and a single beat sample shared by native GUI passes. */
public final class ClientMusicPulse implements AutoCloseable {
    private static final ClientMusicPulse GLOBAL = new ClientMusicPulse(SystemAudioCapture::new);
    private final Supplier<SystemAudioCapture> source;
    private final MusicMotion motion = new MusicMotion(AudioBand.BEAT, 28f, 125f);
    private SystemAudioCapture capture;
    private boolean enabled;
    private float level;

    ClientMusicPulse(Supplier<SystemAudioCapture> source) { this.source = source; }

    public static ClientMusicPulse global() { return GLOBAL; }

    public boolean enabled() { return enabled; }

    public SystemAudioCapture.Status status() {
        var status = capture == null ? READY : capture.status();
        return status == CLOSED ? READY : status;
    }

    public float level() { return level; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled || status() == FAILED || status() == UNSUPPORTED) stopCapture();
    }

    public void update(long nowMs, boolean motionAllowed) {
        if (status() == FAILED || status() == UNSUPPORTED) {
            enabled = false;
            resetLevel();
            return;
        }
        if (!enabled || !motionAllowed) {
            stopCapture();
            return;
        }
        // A fast off/on or accessibility change waits for the old device worker to release its source.
        if (capture == null || capture.status() == CLOSED) {
            capture = source.get();
            capture.start();
        }
        if (capture.status() == RUNNING) level = motion.sample(capture.sample(), nowMs, false);
        else resetLevel();
    }

    private void stopCapture() {
        if (capture != null && capture.status() != CLOSED && capture.status() != STOPPING) capture.requestStop();
        resetLevel();
    }

    private void resetLevel() {
        motion.reset();
        level = 0f;
    }

    @Override public void close() {
        enabled = false;
        resetLevel();
        if (capture != null) {
            capture.close();
            capture = null;
        }
    }
}
