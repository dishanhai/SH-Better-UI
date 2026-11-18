// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Screen-owned native Liquid Glass control suite. Register returned widgets with the screen.
 * Own one suite per layout and close it before rebuilding/removing that layout. This is not a
 * web view: optical surfaces use the loader's existing glass renderer and native input/narration.
 * Preferences update existing controls without replacing focus or confirmed application state.
 * The native host must prepare its current-frame backdrop before drawing; without a valid capture
 * the existing renderer uses its readable solid fallback. The suite does not capture the screen.
 * <pre>{@code
 * // Screen.init(): keep this owner until the next layout/rebuild.
 * glass = ShBetterUiApi.controls().glass(UiPreferences.configured());
 * addRenderableWidget(glass.button(area, Component.literal("Play"), true, pressed -> play()));
 * // Screen.removed() and before rebuilding: glass.close();
 * }</pre>
 * Button, confirmed toggle, integer slider and single-choice segmented controls are included.
 * Media cards are included; menus retain their existing APIs.
 */
public final class GlassControls implements AutoCloseable {
    private final List<UiButton> buttons = new ArrayList<>();
    private final List<UiSlider> sliders = new ArrayList<>();
    private final List<UiRubberSegment> segments = new ArrayList<>();
    private final List<UiMediaCard> mediaCards = new ArrayList<>();
    private UiPreferences preferences;
    private boolean closed;

    public GlassControls(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public UiButton button(Bounds area, Component label, boolean primary, Button.OnPress action) {
        requireOpen();
        var control = new UiButton(area.x(), area.y(), area.width(), area.height(), label,
                action, preferences, primary, UiStyle.LIQUID_GLASS);
        control.setSampleBackground(true);
        buttons.add(control);
        return control;
    }

    /** Requests do not optimistically change the value; acknowledge with toggle.setValue(). */
    public UiToggleButton toggle(Bounds area, boolean value, Function<Boolean, Component> label,
                                  Consumer<Boolean> requested) {
        requireOpen();
        var control = new UiToggleButton(area, value, preferences, UiStyle.LIQUID_GLASS, label, requested);
        control.button().setSampleBackground(true);
        control.setModeIndicatorsVisible(true);
        buttons.add(control.button());
        return control;
    }

    /** Drag preview uses onChange; setOnCommit on the returned control adds commit/cancel semantics. */
    public UiSlider slider(Bounds area, int min, int max, int step, int value,
                            IntFunction<Component> label, IntConsumer onChange) {
        requireOpen();
        var control = new UiSlider(area, min, max, step, value, preferences, label, onChange);
        control.setLiquidGlass(true);
        sliders.add(control);
        return control;
    }

    /** Native single-choice selector. Pointer release and keyboard selection invoke onChange. */
    public UiRubberSegment segmented(Bounds area, Component name, List<UiSectionBar.Section> options,
                                      int selected, IntConsumer onChange) {
        requireOpen();
        var control = new UiRubberSegment(area, name, options, selected, preferences, onChange);
        control.setLiquidGlass(true);
        segments.add(control);
        return control;
    }

    /** Requests activation only. The host confirms playing/selection on the returned card. */
    public UiMediaCard mediaCard(Bounds area, Component title, Component subtitle, Runnable onActivate) {
        requireOpen();
        var control = new UiMediaCard(area, title, subtitle, preferences, onActivate);
        control.setLiquidGlass(true);
        mediaCards.add(control);
        return control;
    }

    public void setPreferences(UiPreferences preferences) {
        requireOpen();
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        buttons.forEach(button -> button.setPreferences(preferences));
        sliders.forEach(slider -> slider.setPreferences(preferences));
        segments.forEach(segment -> segment.setPreferences(preferences));
        mediaCards.forEach(card -> card.setPreferences(preferences));
    }

    public boolean isClosed() { return closed; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        // Invalidate every old hit target before invoking consumer cancellation callbacks.
        sliders.forEach(slider -> { slider.active = false; slider.visible = false; });
        buttons.forEach(button -> { button.active = false; button.visible = false; });
        segments.forEach(segment -> { segment.active = false; segment.visible = false; });
        mediaCards.forEach(card -> { card.active = false; card.visible = false; card.cancelInteraction(); });
        segments.forEach(UiRubberSegment::cancelDrag);
        RuntimeException failure = null;
        for (var slider : sliders) {
            try { slider.cancelEdit(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
            }
        }
        for (var button : buttons) {
            try { button.unbindTheme(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
            }
        }
        sliders.clear();
        buttons.clear();
        segments.clear();
        mediaCards.clear();
        if (failure != null) throw failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Glass control suite is closed");
    }
}
