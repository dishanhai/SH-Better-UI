// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import java.util.Objects;

/** Render-only route handoff. Never retains a route's widgets or executes navigation callbacks. */
final class OnlineMusicRouteMotion {
    enum Surface { PAGE, DETAIL, STAGE_PANE, SHELL }
    record Route(Enum<?> page, Object detail, Enum<?> stage) {
        Route {
            Objects.requireNonNull(page, "page");
        }
        Surface incomingFrom(Route previous) {
            if ((stage != null) != (previous.stage != null)) return Surface.SHELL;
            if (stage != null) return Surface.STAGE_PANE;
            return page == previous.page ? Surface.DETAIL : Surface.PAGE;
        }
    }
    record Frame(Surface surface, float strength) {
        int opacity(boolean highContrast) {
            float maxOpacity = switch (surface) {
                case SHELL -> .31f;
                case PAGE, STAGE_PANE -> .38f;
                case DETAIL -> .28f;
            };
            // Keep labels readable throughout; this is a paint layer, not a content mask.
            return Math.round(255f * strength * (highContrast ? maxOpacity * .50f : maxOpacity));
        }
    }
    private static final long DURATION_MS = 165L;
    private final InterruptibleAnimation veil = new InterruptibleAnimation(Spring.PANEL);
    private Route route;
    private boolean firstMount = true;
    private Surface surface = Surface.PAGE;

    Frame sample(Route next, long nowMs, boolean reducedMotion) {
        var previous = route;
        route = next;
        if (reducedMotion) {
            veil.snapTo(0f);
        } else if (firstMount || (previous != null && !previous.equals(next))) {
            if (veil.sample(nowMs) <= 0f) veil.snapTo(1f);
            surface = firstMount ? Surface.SHELL : next.incomingFrom(previous);
            veil.blendTo(0f, nowMs, 0, DURATION_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        firstMount = false;
        return new Frame(surface, veil.sample(nowMs));
    }

    static Bounds bounds(Surface surface, OnlineMusicLayout layout, FullscreenLyricLayout stage) {
        return switch (surface) {
            case SHELL -> layout.frame();
            case STAGE_PANE -> stageBounds(stage);
            case PAGE -> new Bounds(layout.header().x(), layout.header().y(), layout.header().width(),
                    layout.content().bottom() - layout.header().y());
            case DETAIL -> layout.content();
        };
    }

    private static Bounds stageBounds(FullscreenLyricLayout stage) {
        if (stage.wide()) return stage.lyrics();
        // The compact cover changes size in Artwork mode; keep return and transport outside.
        int top = Math.min(stage.cover().y(), stage.info().y());
        return new Bounds(stage.transport().x(), top, stage.transport().width(),
                Math.max(0, stage.transport().y() - top - 4));
    }

    void reset() {
        route = null;
        veil.snapTo(0f);
    }
}
