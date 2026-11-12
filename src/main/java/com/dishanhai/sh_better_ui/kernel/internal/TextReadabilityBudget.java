// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Provisional per-frame reservations. Higher priorities may borrow a lower lane at its density. */
final class TextReadabilityBudget {
    private static final String PROPERTY = "shb.textReadability.";
    private static final List<String> PRIORITIES = List.of("general", "important", "hovered", "focused");
    static final List<Lane> PROVISIONAL = List.of(
            new Lane(32, 8, 196_608), new Lane(16, 16, 196_608),
            new Lane(8, 32, 327_680), new Lane(8, 32, 327_680));

    private final List<Lane> lanes;
    private final int[] runs;
    private final int[] pixels;

    TextReadabilityBudget(List<Lane> lanes) {
        this.lanes = List.copyOf(lanes);
        runs = new int[lanes.size()];
        pixels = new int[lanes.size()];
    }

    static TextReadabilityBudget fromProperties() {
        var configured = new ArrayList<Lane>(PROVISIONAL.size());
        for (int i = 0; i < PROVISIONAL.size(); i++) {
            var lane = PROVISIONAL.get(i);
            String prefix = PRIORITIES.get(i) + ".";
            configured.add(new Lane(bounded(prefix + "runs", lane.runs(), 0, lane.runs()),
                    bounded(prefix + "samples", lane.samples(), 4, lane.samples()),
                    bounded(prefix + "pixels", lane.pixels(), 0, lane.pixels())));
        }
        return new TextReadabilityBudget(configured);
    }

    static int bounded(String key, int fallback, int minimum, int maximum) {
        int value = Integer.getInteger(PROPERTY + key, fallback);
        return Math.max(minimum, Math.min(value, maximum));
    }

    void reset() {
        Arrays.fill(runs, 0);
        Arrays.fill(pixels, 0);
    }

    Grant offer(int priority, int area) {
        for (int i = priority; i >= 0; i--) {
            var lane = lanes.get(i);
            if (runs[i] < lane.runs() && area <= lane.pixels() - pixels[i]) {
                return new Grant(i, lane.samples(), lane.pixels() - pixels[i]);
            }
        }
        return null;
    }

    void spend(Grant grant, int area) {
        runs[grant.lane()]++;
        spendPixels(grant, area);
    }

    void spendPixels(Grant grant, int area) {
        pixels[grant.lane()] += area;
    }

    record Lane(int runs, int samples, int pixels) {}
    record Grant(int lane, int samples, int pixelAllowance) {}
}
