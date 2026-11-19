// Author: dishanhai. Original critically damped motion for the native lyric viewport.
package com.dishanhai.sh_better_ui.kernel.api.music;

final class LyricFollowMotion {
    private static final double FREQUENCY = 15.0;
    private double position, velocity;

    void reset(double value) {
        position = value;
        velocity = 0;
    }

    double step(double target, double seconds) {
        if (!(seconds > 0)) return position;
        // Exact solution for a critically damped spring. Retargets keep their
        // velocity and a slow frame advances real time, not a fixed tick.
        double x = position - target;
        double drift = velocity + FREQUENCY * x;
        double decay = Math.exp(-FREQUENCY * seconds);
        position = target + (x + drift * seconds) * decay;
        velocity = (velocity - FREQUENCY * drift * seconds) * decay;
        if (Math.abs(position - target) < .01 && Math.abs(velocity) < .1) reset(target);
        return position;
    }

    static boolean discontinuity(long previous, long current, double seconds) {
        return previous >= 0 && current >= 0
                && (current < previous - 100 || current - previous > 550 + seconds * 1000);
    }
}
