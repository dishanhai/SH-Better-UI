// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A screen-owned background renderer. The host sets the draw transform and clipping. */
public final class BackgroundLayer implements AutoCloseable {
    private final BackgroundRegistry registry;
    private final ResourceLocation logo;
    private BackgroundSpec active;
    private BackgroundSpec.Renderer renderer;
    private boolean clickable;

    BackgroundLayer(BackgroundRegistry registry, ResourceLocation logo) {
        this.registry = registry;
        this.logo = logo;
    }

    public void draw(String id, GuiGraphics graphics, Font font, Bounds area, UiMouseState mouse,
                     double x, double y, long now, UiPreferences preferences) {
        graphics.flush();
        GuiStateGuard.draw(() -> {
            try {
                drawIsolated(id, graphics, font, area, mouse, x, y, now, preferences);
            } finally {
                // Submit queued image/solid fallback vertices before restoring their target and textures.
                graphics.flush();
            }
        });
    }

    private void drawIsolated(String id, GuiGraphics graphics, Font font, Bounds area, UiMouseState mouse,
                              double x, double y, long now, UiPreferences preferences) {
        var spec = preferences.highContrast() || area.width() <= 0 || area.height() <= 0
                ? null : registry.find(id).orElse(null);
        if (spec != active) {
            close();
            if (spec != null) {
                renderer = Objects.requireNonNull(spec.factory().create(logo));
                active = spec;
            }
        }
        if (renderer != null) {
            UiMouseState pointer = preferences.reducedMotion() ? null : mouse;
            clickable = false;
            renderer.draw(graphics, font, area, pointer, x, y, now, preferences);
            clickable = pointer != null && pointer.available();
        }
    }

    public void click(String id, Bounds area, double x, double y, long now) {
        if (clickable && area.contains(x, y) && registry.find(id).orElse(null) == active) {
            renderer.click(area, x, y, now);
        }
    }

    /** Releases the active effect. A later draw may open a fresh instance. */
    @Override
    public void close() {
        var previous = renderer;
        renderer = null;
        active = null;
        clickable = false;
        if (previous != null) previous.close();
    }
}
