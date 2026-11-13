// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Interruptible popup geometry and capsule reveal for HUD overlays. Use one instance per owner. */
public final class PopupMotion {
    private final InterruptibleAnimation left = new InterruptibleAnimation(Spring.FLUID_PANEL);
    private final InterruptibleAnimation top = new InterruptibleAnimation(Spring.FLUID_PANEL);
    private final InterruptibleAnimation width = new InterruptibleAnimation(Spring.FLUID_PANEL);
    private final InterruptibleAnimation height = new InterruptibleAnimation(Spring.FLUID_PANEL);
    private final InterruptibleAnimation reveal = new InterruptibleAnimation(Spring.FLUID_MORPH);
    private boolean initialized;

    public record Frame(float x, float y, float width, float height, float progress) {
        public boolean acceptsInput() { return progress >= 0.75f; }
    }

    /** Retargets from the sampled geometry; reduced motion snaps all tracks. */
    public Frame sample(boolean open, float x, float y, float w, float h, long now, boolean reducedMotion) {
        if (!initialized) {
            left.snapTo(x); top.snapTo(y); width.snapTo(w); height.snapTo(h);
            initialized = true;
        }
        retarget(left, x, now); retarget(top, y, now); retarget(width, w, now); retarget(height, h, now);
        retarget(reveal, open ? 1 : 0, now);
        float px = left.sample(now, reducedMotion), py = top.sample(now, reducedMotion);
        float pw = Math.max(0, width.sample(now, reducedMotion)), ph = Math.max(0, height.sample(now, reducedMotion));
        float p = Math.max(0, Math.min(1, reveal.sample(now, reducedMotion)));
        float seedWidth = Math.min(32, pw), seedHeight = Math.min(10, ph);
        float rw = seedWidth + (pw - seedWidth) * p, rh = seedHeight + (ph - seedHeight) * p;
        return new Frame(px + pw - rw, py + ph - rh, rw, rh, p);
    }

    public void reset() { initialized = false; reveal.snapTo(0); }

    private static void retarget(InterruptibleAnimation track, float target, long now) {
        if (track.target() != target) track.animateTo(target, now);
    }
}
