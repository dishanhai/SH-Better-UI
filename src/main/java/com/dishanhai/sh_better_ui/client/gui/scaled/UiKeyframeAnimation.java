// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.List;
import java.util.Objects;

/**
 * Millisecond, multi-stop value animation, independent of rendering FPS and Minecraft.
 * Unlike UiTimeline this interpolates continuously; unlike InterruptibleAnimation it can
 * describe several explicit stops. Use on the UI thread, with a monotonic clock. No timers,
 * callbacks or global registrations are retained. Close when the owning view is removed.
 */
public final class UiKeyframeAnimation implements AutoCloseable {
    public enum State { READY, PLAYING, PAUSED, FINISHED, CANCELED, CLOSED }

    public enum Easing {
        LINEAR, SMOOTHSTEP, EASE_OUT_CUBIC;

        double apply(double t) {
            return switch (this) {
                case LINEAR -> t;
                case SMOOTHSTEP -> t * t * (3d - 2d * t);
                case EASE_OUT_CUBIC -> 1d - (1d - t) * (1d - t) * (1d - t);
            };
        }
    }

    /** Easing describes the segment arriving at this stop; the first stop's easing is unused. */
    public record Keyframe(long atMs, float value, Easing easing) {
        public Keyframe {
            if (atMs < 0 || !Float.isFinite(value)) throw new IllegalArgumentException("Invalid keyframe");
            Objects.requireNonNull(easing, "easing");
        }
        public Keyframe(long atMs, float value) { this(atMs, value, Easing.LINEAR); }
    }

    private final List<Keyframe> frames;
    private State state = State.READY;
    private float value;
    private long positionMs;
    private long lastMs;
    private long nextRevision;
    private long revision;

    /** At least two stops, beginning at zero with strictly increasing millisecond offsets. */
    public UiKeyframeAnimation(List<Keyframe> keyframes) {
        frames = List.copyOf(keyframes);
        if (frames.size() < 2 || frames.get(0).atMs() != 0) {
            throw new IllegalArgumentException("Require at least two stops beginning at zero");
        }
        for (int i = 1; i < frames.size(); i++) {
            if (frames.get(i).atMs() <= frames.get(i - 1).atMs()) {
                throw new IllegalArgumentException("Keyframe offsets must increase");
            }
        }
        value = frames.get(0).value();
    }

    /** Explicit replay from the first stop. Invalidates any earlier completion token. */
    public long restart(long nowMs) {
        if (state == State.CLOSED) return 0;
        value = frames.get(0).value();
        positionMs = 0;
        lastMs = nowMs;
        revision = ++nextRevision;
        state = State.PLAYING;
        return revision;
    }

    /** Samples before pausing; repeated pause calls have no effect. */
    public void pause(long nowMs) {
        advance(nowMs);
        if (state == State.PLAYING) state = State.PAUSED;
    }

    /** Resumes only a paused track. To replay a finished/canceled track use restart. */
    public void resume(long nowMs) {
        if (state != State.PAUSED) return;
        lastMs = Math.max(lastMs, nowMs);
        state = State.PLAYING;
    }

    /** Freeze at the command time and invalidate completion, without restoring the first stop. */
    public void cancel(long nowMs) {
        if (state == State.CLOSED) return;
        advance(nowMs);
        revision = 0;
        state = State.CANCELED;
    }

    /** Complete a playing or paused track, retaining its completion token. */
    public void finish() {
        if (state != State.PLAYING && state != State.PAUSED) return;
        positionMs = durationMs();
        value = frames.get(frames.size() - 1).value();
        state = State.FINISHED;
    }

    public float sample(long nowMs) {
        advance(nowMs);
        return value;
    }

    /** Reduced motion skips every remaining stop and wait, including while paused. */
    public float sample(long nowMs, boolean reducedMotion) {
        if (reducedMotion) finish();
        return sample(nowMs);
    }

    public float value() { return value; }
    public long durationMs() { return frames.get(frames.size() - 1).atMs(); }
    public State state() { return state; }

    /** Completion is consumable once. Restart, cancel and close invalidate old tokens. */
    public boolean takeCompletion(long token) {
        if (token == 0 || token != revision || state != State.FINISHED) return false;
        revision = 0;
        return true;
    }

    private void advance(long nowMs) {
        if (state != State.PLAYING || nowMs <= lastMs) return;
        long elapsed;
        try {
            elapsed = Math.subtractExact(nowMs, lastMs);
        } catch (ArithmeticException overflow) {
            elapsed = Long.MAX_VALUE;
        }
        lastMs = nowMs;
        positionMs = elapsed >= durationMs() - positionMs ? durationMs() : positionMs + elapsed;
        if (positionMs >= durationMs()) {
            finish();
            return;
        }
        // Binary search avoids per-frame allocations even with long keyframe sequences.
        int low = 1;
        int high = frames.size() - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (frames.get(mid).atMs() < positionMs) low = mid + 1;
            else high = mid;
        }
        Keyframe from = frames.get(low - 1);
        Keyframe to = frames.get(low);
        double t = (double) (positionMs - from.atMs()) / (to.atMs() - from.atMs());
        value = (float) (from.value() + ((double) to.value() - from.value()) * to.easing().apply(t));
    }

    /** Keeps the last sampled value, releases playback and rejects subsequent commands. */
    @Override public void close() {
        revision = 0;
        state = State.CLOSED;
    }
}
