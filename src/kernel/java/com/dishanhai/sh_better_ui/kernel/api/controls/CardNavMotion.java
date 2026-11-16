// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

/** CardNav's interruptible spring and card staggering, independent of the game renderer. */
public final class CardNavMotion {
    public enum Effect { RISE, UNFOLD, FADE }

    public record Settings(Effect effect, double stagger, double bounce, double speed) {
        public static final Settings DEFAULT = new Settings(Effect.RISE, .12, .2, 1);
        public Settings {
            if (effect == null) throw new NullPointerException("effect");
            if (!Double.isFinite(stagger) || !Double.isFinite(bounce) || !Double.isFinite(speed)) {
                throw new IllegalArgumentException("Non-finite CardNav settings");
            }
            stagger = clamp(stagger, 0, .3);
            bounce = clamp(bounce, 0, 1);
            speed = clamp(speed, .2, 3);
        }
    }

    /** Translation and blur use source CSS pixels; native adapters apply their GUI scale. */
    public record Card(double opacity, double translateY, double scale, double rotateX, double blur) {}

    private boolean open;
    private double progress, velocity;
    private long last = -1;

    public CardNavMotion(boolean defaultOpen) {
        open = defaultOpen;
        progress = open ? 1 : 0;
    }

    public boolean isOpen() { return open; }
    public double progress() { return progress; }
    public boolean shown() { return open || progress != 0 || velocity != 0; }
    public void setOpen(boolean value) { open = value; }
    public void pause() { last = -1; }

    public void advance(long now, Settings settings, boolean reducedMotion) {
        double dt = last < 0 ? 1.0 / 60 : clamp((now - last) / 1000.0, 0, .05);
        last = now;
        double target = open ? 1 : 0;
        if (reducedMotion) {
            progress = target;
            velocity = 0;
            return;
        }
        double k = 160 * settings.speed() * settings.speed();
        double zeta = open ? 1 - .68 * settings.bounce() : 1;
        int steps = Math.max(1, (int) Math.ceil(dt * 240));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            velocity += (-k * (progress - target) - 2 * zeta * Math.sqrt(k) * velocity) * h;
            progress += velocity * h;
        }
        if (Math.abs(progress - target) < .0005 && Math.abs(velocity) < .002) {
            progress = target;
            velocity = 0;
        }
    }

    public Card card(int index, int count, Settings settings) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        double lag = count > 1 ? Math.min(settings.stagger(), .7 / (count - 1)) : 0;
        double span = Math.max(.05, 1 - lag * (count - 1));
        double local = clamp((progress - index * lag) / span, 0, 1);
        double ease = 1 - Math.pow(1 - local, 3);
        return switch (settings.effect()) {
            case UNFOLD -> new Card(clamp(local * 1.8, 0, 1), (1 - ease) * -10, 1, (1 - ease) * -72, 0);
            case FADE -> new Card(ease, (1 - ease) * 6, 1, 0, (1 - ease) * 6);
            case RISE -> new Card(ease, (1 - ease) * 26, .97 + .03 * ease, 0, 0);
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
