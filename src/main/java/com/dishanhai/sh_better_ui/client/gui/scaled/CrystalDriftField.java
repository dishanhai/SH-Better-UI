package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Deterministic, screen-owned, original shard motion. No renderer, texture, or global mutable state. */
final class CrystalDriftField {
    float[] x = new float[0], y = new float[0], angle = new float[0], depth = new float[0],
            size = new float[0], flash = new float[0];
    private long lastTime;
    private boolean initialized, frozen, pendingPulse;
    private float pulseX, pulseY, pointerOffsetX, pointerOffsetY, gather, gatherX = .5f, gatherY = .5f;
    private double phase;
    private float heldSeconds;
    private CrystalDriftProfile previous;

    int count() { return x.length; }

    void pulse(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        pulseX = clamp(x); pulseY = clamp(y); pendingPulse = true;
    }

    void suspend() {
        initialized = false;
        previous = null; // Rebuild neutral positions even when reopened with reduced motion.
        pendingPulse = false;
        heldSeconds = 0f;
        gather = 0f;
        gatherX = gatherY = .5f;
        pointerOffsetX = pointerOffsetY = 0f;
        pulseX = pulseY = 0f;
        java.util.Arrays.fill(flash, 0f);
    }

    void update(long now, CrystalDriftProfile p, boolean reducedMotion, float pointerX, float pointerY,
                boolean interactive, boolean held) {
        java.util.Objects.requireNonNull(p, "profile");
        float dt = initialized && !frozen && !reducedMotion && now > lastTime
                ? (float) (Math.min(50d, (double) now - (double) lastTime) * .001d) : 0f;
        lastTime = now;
        initialized = true;
        frozen = reducedMotion;
        boolean changed = !p.equals(previous);
        previous = p;
        boolean resized = count() != p.count();
        if (resized) {
            x = new float[p.count()]; y = new float[p.count()]; angle = new float[p.count()];
            depth = new float[p.count()]; size = new float[p.count()]; flash = new float[p.count()];
        }
        if (reducedMotion) {
            heldSeconds = 0f;
            pendingPulse = false;
            if (!changed && !resized) return;
        }
        phase = (phase + dt * p.speed()) % (Math.PI * 2d);
        boolean active = !reducedMotion && interactive && p.mouseInteraction()
                && Float.isFinite(pointerX) && Float.isFinite(pointerY);
        float px = active ? clamp(pointerX) : .5f;
        float py = active ? clamp(pointerY) : .5f;
        float response = 1f - (float) Math.exp(-dt * 8f);
        if (!reducedMotion) {
            pointerOffsetX += ((active ? px - .5f : 0f) - pointerOffsetX) * response;
            pointerOffsetY += ((active ? py - .5f : 0f) - pointerOffsetY) * response;
            boolean pressing = active && held && p.holdToGather();
            heldSeconds = pressing ? Math.min(1f, heldSeconds + dt) : 0f;
            boolean collecting = pressing && heldSeconds >= .3f;
            if (collecting) { gatherX = px; gatherY = py; }
            gather += ((collecting ? 1f : 0f) - gather) * response;
        }
        boolean pulse = pendingPulse && active && p.pulseOnClick();
        pendingPulse = false;
        for (int i = 0; i < count(); i++) {
            // Index-derived immutable seeds: changing settings never consumes random state.
            float u = seed(i, 0), v = seed(i, 1), turn = seed(i, 2) * (float) (Math.PI * 2);
            depth[i] = .2f + .3f * Math.min(2, i * 3 / count()); // Back-to-front bands.
            float bx, by;
            switch (p.placement()) {
                case LEFT -> { bx = .06f + .35f * u; by = .06f + .88f * v; }
                case RIGHT -> { bx = .59f + .35f * u; by = .06f + .88f * v; }
                case CENTER -> {
                    double a = u * Math.PI * 2;
                    float radius = (float) Math.sqrt(v);
                    bx = .5f + .28f * radius * (float) Math.cos(a);
                    by = .5f + .40f * radius * (float) Math.sin(a);
                }
                default -> { bx = .06f + .88f * u; by = .06f + .88f * v; }
            }
            float anchor = p.placement() == CrystalDriftProfile.Placement.LEFT ? .23f
                    : p.placement() == CrystalDriftProfile.Placement.RIGHT ? .77f : .5f;
            bx = anchor + (bx - anchor) * p.spread();
            by = .5f + (by - .5f) * p.spread();
            bx += .012f * (float) Math.sin(phase + turn) + pointerOffsetX * p.parallax() * depth[i] * .10f;
            by += .018f * (float) Math.cos(phase + turn) + pointerOffsetY * p.parallax() * depth[i] * .10f;
            float attraction = gather * .55f;
            x[i] = clamp(bx + (gatherX - bx) * attraction);
            y[i] = clamp(by + (gatherY - by) * attraction);
            angle[i] = turn + .16f * (float) Math.sin(phase + turn);
            size[i] = seed(i, 3);
            flash[i] = Math.max(0f, flash[i] - dt * 1.8f);
            if (pulse) {
                float dx = x[i] - pulseX, dy = y[i] - pulseY;
                flash[i] = Math.max(flash[i], (float) Math.exp(-(dx * dx + dy * dy) * 32f));
            }
        }
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    private static float seed(int index, int channel) {
        int value = (index + 1) * 0x632BE5AB ^ (channel + 1) * 0x85157AF5;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return (value >>> 8) * (1f / 16777216f);
    }
}
