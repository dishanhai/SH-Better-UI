// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextDataSource;
import java.util.Optional;

/**
 * Owner-thread metric, changed only by an explicit trusted host call. It never collects data.
 * Values and revisions are immutable at each sample boundary; invalid updates are atomic.
 */
public final class BoundedMetricSource implements RichTextDataSource {
    private static final String PREFIX = "metric-v1";
    private static final int MAX_ENCODED_LENGTH = 128;
    private State state;
    private Snapshot sampled;

    public BoundedMetricSource(double value, double min, double max) {
        state = new State(value, min, max, 0);
        sampled = new Snapshot(0, encode(state));
    }

    public State state() { return state; }

    /** Unchanged values keep their revision. Call on the same owner thread as sample(). */
    public void set(double value, double min, double max) {
        var previous = state;
        // Validate first, before any mutation or revision increment.
        var candidate = new State(value, min, max, previous.revision());
        if (candidate.equals(previous)) return;
        if (previous.revision() == Long.MAX_VALUE) throw new IllegalStateException("Metric revision exhausted");
        state = new State(value, min, max, previous.revision() + 1);
        sampled = new Snapshot(state.revision(), encode(state));
    }

    @Override public Snapshot sample() {
        return sampled;
    }

    /** Only our canonical finite numeric format is accepted. No resources or code are evaluated. */
    public static Optional<State> decode(Snapshot snapshot) {
        if (snapshot == null || snapshot.value() == null || snapshot.value().length() > MAX_ENCODED_LENGTH) {
            return Optional.empty();
        }
        String[] parts = snapshot.value().split("\\|", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return Optional.empty();
        try {
            var decoded = new State(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), snapshot.revision());
            return encode(decoded).equals(snapshot.value()) ? Optional.of(decoded) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String encode(State state) {
        return PREFIX + "|" + Double.toString(state.value()) + "|" + Double.toString(state.min())
                + "|" + Double.toString(state.max());
    }

    public record State(double value, double min, double max, long revision) {
        public State {
            if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)
                    || !(min < max) || value < min || value > max || revision < 0) {
                throw new IllegalArgumentException("Metric requires finite min < max and a value within the range");
            }
        }

        /** Supports the full finite-double range even when max - min would overflow. */
        public double ratio() {
            double span = max - min;
            double result = Double.isFinite(span) ? (value - min) / span
                    : (value / 2 - min / 2) / (max / 2 - min / 2);
            return Math.max(0, Math.min(1, result));
        }
    }
}
