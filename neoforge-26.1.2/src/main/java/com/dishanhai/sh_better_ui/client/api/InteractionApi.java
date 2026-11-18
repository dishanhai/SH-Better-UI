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

import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native confirmation screens and renderer-independent interaction state. Other controllers remain host-owned. */
public final class InteractionApi {
    InteractionApi() {}

    public boolean confirm(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm) {
        return UiConfirmation.open(parent, prompt, preferences, context, restoreFocus, onConfirm);
    }

    public boolean confirm(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm, GraphicIcon icon) {
        return UiConfirmation.open(parent, prompt, preferences, context, restoreFocus, onConfirm, icon);
    }

    /** Owned confirmation: abandonment is distinct from returning to edit the draft. */
    public boolean confirm(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Supplier<? extends GuiEventListener> restoreFocus, Runnable onConfirm, Runnable onAbandon) {
        return UiConfirmation.openOwned(parent,prompt,preferences,restoreFocus,onConfirm,onAbandon);
    }

    /** Native isolated confirmation with an explicit optical surface; ordinary confirm is unchanged. */
    public boolean confirmGlass(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm) {
        return UiConfirmation.openGlass(parent, prompt, preferences, context, restoreFocus, onConfirm);
    }

    public boolean confirmGlass(Screen parent, UiConfirmation.Prompt prompt, UiPreferences preferences,
                           Consumer<GuiGraphicsExtractor> context, Supplier<? extends GuiEventListener> restoreFocus,
                           Runnable onConfirm, GraphicIcon icon) {
        return UiConfirmation.openGlass(parent, prompt, preferences, context, restoreFocus, onConfirm, icon);
    }

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
