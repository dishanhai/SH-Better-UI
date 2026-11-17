// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback;
import com.dishanhai.sh_better_ui.client.gui.scaled.PausableCountdown;
import com.dishanhai.sh_better_ui.client.gui.scaled.PointerDrag;
import com.dishanhai.sh_better_ui.client.gui.scaled.ScrollPageTurn;
import com.dishanhai.sh_better_ui.client.gui.scaled.SpringTable;
import com.dishanhai.sh_better_ui.client.gui.scaled.SwipeGesture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHistory;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerTracker;
import com.dishanhai.sh_better_ui.client.gui.scaled.ViewportTransform;

/** Renderer-independent interaction state. Hosts route native input and own cancellation and drawing. This is the supported 26.x subset of the shared API. */
public final class InteractionApi {
    InteractionApi() {}

    public UiPointerTracker pointerTracker() { return new UiPointerTracker(); }

    public PointerDrag pointerDrag() {
        return new PointerDrag();
    }

    public SwipeGesture swipeGesture() {
        return new SwipeGesture();
    }

    public PausableCountdown countdown(long durationMs, long nowMs) {
        return new PausableCountdown(durationMs, nowMs);
    }

    public ScrollPageTurn scrollPageTurn() {
        return new ScrollPageTurn();
    }

    public com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState tabScrollState() {
        return new com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState();
    }

    public double dragScrollOffset(double scrollOffset, double pointerDeltaY, double maxScroll) {
        return ScrollPageTurn.dragOffset(scrollOffset, pointerDeltaY, maxScroll);
    }

    public ViewportTransform viewport(double x, double y, double scale) {
        return new ViewportTransform(x, y, scale);
    }

    public <S> UiHistory<S> history(int capacity) {
        return new UiHistory<>(capacity);
    }

    public ButtonFeedback buttonFeedback() {
        return new ButtonFeedback();
    }

    public ButtonFeedback buttonFeedback(SpringTable<Long> hoverTable) {
        return new ButtonFeedback(hoverTable);
    }
}
