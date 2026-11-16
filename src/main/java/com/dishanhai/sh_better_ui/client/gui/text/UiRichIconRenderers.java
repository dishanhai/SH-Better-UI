// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconMotion;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Named icon shader/motion combinations for the rndr: modifier. Registrations may be tracked by a UUK scope. */
public final class UiRichIconRenderers {
    public enum Effect {
        NONE(0), GRADIENT_SWEEP(2400), COLOR_CYCLE(3600), ENERGY_WAVE(2000), PULSE(1800),
        HOLOGRAM(3200), GLITCH(2600), SCANLINE(2800), SPARKLE(4000), DISSOLVE(4200), OUTLINE(3000);
        private final int period;
        Effect(int period) { this.period = period; }
        public int phase(long millis, boolean reducedMotion) {
            return period == 0 ? 0 : reducedMotion ? 500 : (int) (Math.floorMod(millis, period) * 1000 / period);
        }
    }

    public record Renderer(Effect effect, GraphicIconMotion motion) {
        public static final Renderer NONE = new Renderer(Effect.NONE, null);
        public Renderer { Objects.requireNonNull(effect, "effect"); }
    }

    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+(?::[a-z0-9/._-]+)?");
    private static final Map<String, Renderer> RENDERERS = new ConcurrentHashMap<>();
    static {
        for (var effect : Effect.values()) if (effect != Effect.NONE) {
            RENDERERS.put(effect.name().toLowerCase(java.util.Locale.ROOT), new Renderer(effect, null));
        }
        for (var motion : GraphicIconMotion.values()) {
            RENDERERS.put(motion.name().toLowerCase(java.util.Locale.ROOT), new Renderer(Effect.NONE, motion));
        }
    }

    private UiRichIconRenderers() {}

    public static Renderer find(String name) { return name == null ? Renderer.NONE : RENDERERS.get(name); }

    public static AutoCloseable register(String name, Renderer renderer) {
        if (!NAME.matcher(Objects.requireNonNull(name, "name")).matches()) {
            throw new IllegalArgumentException("Invalid rich renderer name: " + name);
        }
        Objects.requireNonNull(renderer, "renderer");
        // Each registration has its own identity, even if the caller reuses the same descriptor.
        var registered = new Renderer(renderer.effect(), renderer.motion());
        if (RENDERERS.putIfAbsent(name, registered) != null) throw new IllegalArgumentException("Renderer already registered: " + name);
        return () -> RENDERERS.computeIfPresent(name, (key, current) -> current == registered ? null : current);
    }
}
