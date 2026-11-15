// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Bell animation and glyph shared by the immediate and extracted GUI renderers. */
final class BellToggleMotion {
    private static final int FADE_MS = 200;
    private static final int REVEAL_MS = 300;
    private static final int BADGE_COLOR = 0xffb52242;

    interface Canvas {
        void push();
        void pop();
        void translate(float x, float y);
        void rotate(float degrees);
        void scale(float factor);
        void box(float x, float y, float width, float height, float radius, float stroke, int edge, int fill);
        void fill(int left, int top, int right, int bottom, int color);
        void clip(int left, int top, int right, int bottom);
        void unclip();
        int width(String text);
        void text(String text, int x, int y, int color);
    }

    private boolean on;
    private long changedAt;
    private float fromFade;
    private float fromReveal;
    private int revealDuration;
    private long ringAt;
    private int ringDuration;
    private int ringPasses;
    private float ringAmplitude;
    private float ringFrom;
    private int badge;
    private int previousBadge;
    private long badgeAt;

    BellToggleMotion(boolean on, int badge) {
        this.on = on;
        this.badge = badge;
        fromFade = fromReveal = on ? 1 : 0;
    }

    boolean on() { return on; }

    void sample(boolean enabled, int count, boolean reduced, long now) {
        if (on != enabled) set(enabled, now, false);
        if (count != badge) {
            if (count > badge && on && !reduced && now - ringAt >= ringDuration) ring(now, 420, 3, 6.8f);
            previousBadge = badge;
            badge = count;
            badgeAt = now;
        }
        if (reduced) {
            fromFade = fromReveal = on ? 1 : 0;
            ringDuration = 0;
        }
    }

    void set(boolean target, long now, boolean reveal) {
        fromFade = fade(now);
        fromReveal = reveal ? reveal(now) : target ? 1 : 0;
        revealDuration = reveal ? REVEAL_MS : 0;
        changedAt = now;
        on = target;
        if (!on) ringDuration = 0;
    }

    void ring(long now, int duration, int passes, float amplitude) {
        ringFrom = angle(now);
        ringAt = now;
        ringDuration = duration;
        ringPasses = passes;
        ringAmplitude = amplitude;
    }

    private float angle(long now) {
        if (ringDuration == 0 || now - ringAt >= ringDuration) return 0;
        float t = progress(now - ringAt, ringDuration);
        float swing = (float) (-Math.sin(Math.PI * ringPasses * Math.pow(t, 0.72)) * ringAmplitude * (1 - t));
        return swing + ringFrom * (1 - t);
    }

    float fade(long now) {
        float t = progress(now - changedAt, FADE_MS);
        return GuiMotion.lerp(fromFade, on ? 1 : 0, t * t * (3 - 2 * t));
    }

    float reveal(long now) {
        float t = 1 - progress(now - changedAt, revealDuration);
        return GuiMotion.lerp(fromReveal, on ? 1 : 0, 1 - t * t * t);
    }

    void drawBell(Canvas graphics, int x, int y, int ink, long now) {
        graphics.push();
        try {
            graphics.translate(x, y - 5);
            graphics.rotate(angle(now));
            graphics.box(-4, 0, 8, 10, 4, 1, ink, 0);
            graphics.fill(-5, 8, 5, 10, ink);
            graphics.fill(-1, 11, 1, 13, ink);
        } finally {
            graphics.pop();
        }
        float time = progress(now - ringAt, ringDuration);
        if (time >= 1) return;
        float pass = time * ringPasses;
        int side = (int) pass % 2 == 0 ? -1 : 1;
        float phase = pass - (int) pass;
        int alpha = Math.round(185 * (1 - time) * (float) Math.sin(Math.PI * phase));
        int wave = alpha << 24 | (ink & 0xffffff);
        int dx = x + side * (7 + Math.round(phase * 2));
        graphics.fill(dx, y - 2, dx + 1, y + 2, wave);
        graphics.fill(dx + side * 2, y - 3, dx + side * 2 + 1, y + 3, wave);
    }

    void drawBadge(Canvas graphics, int x, int y, long now, boolean reducedMotion) {
        if (!on || badge <= 0) return;
        float roll = reducedMotion ? 1 : progress(now - badgeAt, FADE_MS);
        String text = Integer.toString(badge);
        String previous = Integer.toString(previousBadge);
        int width = 4 + (graphics.width(text) + 1) / 2;
        if (roll < 1 && previousBadge > 0) width = Math.max(width, 4 + (graphics.width(previous) + 1) / 2);
        graphics.box(x, y, width, 8, 4, 0, 0, BADGE_COLOR);
        graphics.clip(x, y, x + width, y + 8);
        graphics.push();
        try {
            graphics.translate(x + width / 2f, y + 2);
            graphics.scale(.5f);
            if (roll < 1 && previousBadge > 0) {
                graphics.text(previous, -graphics.width(previous) / 2, -Math.round(roll * 16), 0xffffffff);
            }
            graphics.text(text, -graphics.width(text) / 2, Math.round((1 - roll) * 16), 0xffffffff);
        } finally {
            graphics.pop();
            graphics.unclip();
        }
    }

    private static float progress(long elapsed, int duration) {
        return duration > 0 ? GuiMotion.clamp01(elapsed / (float) duration) : 1;
    }
}
