// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Selection, pointer capture and spring geometry shared by the native radio renderers. */
final class JellyRadioState {
    private static final float SWELL = .20f;
    private static final float SHRINK = .05f;
    private static final float BARGE = 6;
    private static final int STAGGER_MS = 22;
    private static final int HEIGHT_DELAY_MS = 50;

    record Chip(float x, float y, float width, float height) {
        boolean contains(double px, double py) {
            if (px < x || px >= x + width || py < y || py >= y + height) return false;
            float radius = Math.min(width, height) / 2;
            double cx = Math.max(x + radius, Math.min(x + width - radius, px));
            double cy = Math.max(y + radius, Math.min(y + height - radius, py));
            return Math.hypot(px - cx, py - cy) <= radius;
        }
    }

    private record Motion(InterruptibleAnimation offset, InterruptibleAnimation wide, InterruptibleAnimation tall) {
        private Motion() {
            this(track(.9, 580, .75), track(.8, 580 * 1.24, .45), track(.95, 580 * .86, .75));
        }

        private static InterruptibleAnimation track(double mass, double stiffness, double damping) {
            return new InterruptibleAnimation(
                    new Spring.Preset(2 * Math.PI * Math.sqrt(mass / stiffness), damping, .0015f, .02f));
        }

        void target(float direction, float growth, long now, int delay, boolean snap) {
            if (snap) {
                offset.snapTo(direction);
                wide.snapTo(growth);
                tall.snapTo(growth);
                return;
            }
            if (offset.running() || wide.running() || tall.running()) delay = 0;
            offset.animateToAfter(direction, now, delay);
            wide.animateToAfter(growth, now, delay);
            tall.animateToAfter(growth, now, delay + HEIGHT_DELAY_MS);
        }
    }

    private final boolean[] enabled;
    private final Motion[] motion;
    private int selected;
    private int pressed = -1;

    JellyRadioState(boolean[] enabled, int initial) {
        this.enabled = enabled.clone();
        motion = new Motion[enabled.length];
        for (int i = 0; i < motion.length; i++) motion[i] = new Motion();
        select(enabled(initial) ? initial : step(-1, 1), 0, true);
    }

    int selected() { return selected; }
    int pressed() { return pressed; }
    boolean enabled(int index) { return index >= 0 && index < enabled.length && enabled[index]; }

    int step(int from, int direction) {
        for (int i = 0, index = from; i < enabled.length; i++) {
            index = Math.floorMod(index + direction, enabled.length);
            if (enabled[index]) return index;
        }
        return -1;
    }

    boolean press(int index) {
        pressed = enabled(index) ? index : -1;
        return pressed >= 0;
    }

    boolean cancel() {
        boolean captured = pressed >= 0;
        pressed = -1;
        return captured;
    }

    boolean release(int index, long now, boolean snap) {
        int captured = pressed;
        return cancel() && captured == index && select(index, now, snap);
    }

    boolean select(int index, long now, boolean snap) {
        if (index != -1 && !enabled(index)) return false;
        boolean changed = selected != index;
        if (!changed && !snap) return false;
        selected = index;
        for (int i = 0; i < motion.length; i++) {
            int distance = Math.abs(i - index);
            float growth = index < 0 ? 0 : i == index ? SWELL : -SHRINK;
            motion[i].target(index < 0 ? 0 : Integer.compare(i, index), growth, now, distance * STAGGER_MS, snap);
        }
        return changed;
    }

    Chip chip(int index, int width, int height, long now, boolean reduced, boolean down) {
        float gap = Math.min(8, width / (6f * motion.length));
        float room = Math.min(BARGE, width / 15f) + 2;
        float slot = Math.max(0, (width - 2 * room - gap * (motion.length - 1)) / (motion.length + .4f));
        float body = Math.max(0, (height - 4) / 1.25f);
        var track = motion[index];
        float center = (width - (motion.length - 1) * (slot + gap)) / 2 + index * (slot + gap);
        center += track.offset.sample(now, reduced) * (slot * SWELL / 2 + room - 2);
        float pressScale = down && !reduced ? .97f : 1;
        float w = Math.min(width, slot * (1 + track.wide.sample(now, reduced)) * pressScale);
        float h = Math.min(height, body * (1 + track.tall.sample(now, reduced)) * pressScale);
        float x = Math.max(0, Math.min(width - w, center - w / 2));
        return new Chip(x, (height - h) / 2, w, h);
    }

    int hit(double x, double y, int width, int height, long now, boolean reduced) {
        if (selected >= 0 && chip(selected, width, height, now, reduced, false).contains(x, y)) return selected;
        for (int i = motion.length - 1; i >= 0; i--) {
            if (i != selected && chip(i, width, height, now, reduced, false).contains(x, y)) return i;
        }
        return -1;
    }

    /** Fits the spring into one scrollable tab slot so adjacent hit areas never overlap. */
    Chip tabChip(int index, int width, int height, long now, boolean reduced, boolean down) {
        var track = motion[index];
        float pressScale = down && !reduced ? .97f : 1;
        float w = Math.min(width, width / 1.2f * (1 + track.wide.sample(now, reduced)) * pressScale);
        float h = Math.min(height, height / 1.14f * (1 + track.tall.sample(now, reduced)) * pressScale);
        float x = (width - w) / 2;
        float offset = track.offset.sample(now, reduced) * Math.min(x, 2);
        return new Chip(Math.max(0, Math.min(width - w, x + offset)), (height - h) / 2, w, h);
    }
}
