// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.MAX_TRANSITION_STEP_NANOS;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.TRANSITION_NANOS;

/** A rate-limited sRGB transition. The shader uses the same microsecond interpolation. */
final class TextColorTransition {
    private int from;
    private int target;
    private int elapsed;

    void start(int from, int target) {
        this.from = from;
        this.target = target;
        elapsed = 0;
    }

    void advance(long deltaNanos) {
        elapsed = Math.min(TRANSITION_NANOS, elapsed + (int) Math.min(MAX_TRANSITION_STEP_NANOS,
                Math.max(0, deltaNanos)));
    }

    int target() {
        return target;
    }

    int fill() {
        return fill(from, target, elapsed);
    }

    static int fill(int from, int target, int elapsed) {
        if (from == target || elapsed <= 0) return from;
        if (elapsed >= TRANSITION_NANOS) return target;
        int micros = elapsed / 1000;
        return (target & 0xff000000) | channel(from, target, 16, micros) << 16
                | channel(from, target, 8, micros) << 8 | channel(from, target, 0, micros);
    }

    private static int channel(int from, int target, int shift, int micros) {
        int duration = TRANSITION_NANOS / 1000;
        int first = (from >>> shift) & 255;
        int last = (target >>> shift) & 255;
        return (first * (duration - micros) + last * micros + duration / 2) / duration;
    }
}
