// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.SettingsDetailLayout;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Reusable secondary settings page: fixed native back/title bar and scrolling cards.
 * The host owns draft values, validation and persistence. Optional Actions provide a
 * scoped edit transaction: Apply merges, Cancel/Back confirm discard, Reset stays local. Create one instance per navigation entry; reuse it for resize/reopen.
 * Inspired by Card/Field composition, not a React dependency or copied shadcn implementation.
 */
public final class UiSettingsDetailPage {
    public record Field(Component label, BiFunction<Bounds, UiApiContext, AbstractWidget> control) {
        public Field { label = Objects.requireNonNull(label).copy(); Objects.requireNonNull(control); }
    }
    public record Section(Component title, List<Field> fields) {
        public Section { title = Objects.requireNonNull(title).copy(); fields = List.copyOf(fields); }
    }
    /** apply returns null on success or a visible error; it must atomically merge only this scope. */
    public record Actions(Supplier<Component> resetLabel, Component cancelLabel, Component applyLabel,
                          Runnable reset, Runnable cancel, Supplier<Component> apply, BooleanSupplier dirty) {
        public Actions {
            Objects.requireNonNull(resetLabel);
            cancelLabel = Objects.requireNonNull(cancelLabel).copy();
            applyLabel = Objects.requireNonNull(applyLabel).copy();
            Objects.requireNonNull(reset); Objects.requireNonNull(cancel);
            Objects.requireNonNull(apply); Objects.requireNonNull(dirty);
        }
    }
    private record Placed(AbstractWidget widget, int section, int field) {}
    private final Component title;
    private final List<Section> sections;
    private final VerticalScroll scroll = new VerticalScroll();
    private final List<Placed> widgets = new ArrayList<>();
    private SettingsDetailLayout layout;
    private Bounds clip;
    private UiVerticalScrollBar scrollbar;
    private final Actions actions;
    private boolean applied;
    private boolean discardConfirmed;
    private UiOverlayHost confirmation;
    private Component status;
    private Component footerHint;
    private UiButton applyButton;
    private UiButton resetButton;
    private int footerY;
    private UiScreen owner;

    public UiSettingsDetailPage(Component title, List<Section> sections) {
        this(title, sections, null);
    }

    public UiSettingsDetailPage(Component title, List<Section> sections, Actions actions) {
        this.title = Objects.requireNonNull(title).copy();
        this.sections = List.copyOf(sections);
        this.actions = actions;
    }

    /** Optional host explanation (for example, Apply updates the parent draft, not disk). */
    public UiSettingsDetailPage footerHint(Component hint) {
        if (owner != null) throw new IllegalStateException("Set footer hint before mounting");
        footerHint = Objects.requireNonNull(hint).copy();
        return this;
    }

    public double scrollOffset() { return scroll.offset(); }

    /** Pass to UUK ScreenService.create(module, builder), then open through the host. */
    public UiScreenBuilder builder() {
        return new UiScreenBuilder(title).dynamicTheme().themeBackground()
                .onInit(this::init).onTick(screen -> syncActionState()).onRender(this::render).onRemoved(screen -> {
                    if (owner == screen) {
                        try { if (actions != null && !applied) actions.cancel().run(); }
                        finally {
                            if (confirmation != null) confirmation.ownerClosed();
                            confirmation = null;
                            widgets.clear(); scrollbar = null; owner = null; applyButton = null; resetButton = null;
                        }
                    }
                });
    }

    void init(UiScreen screen) {
        if (owner != null && owner != screen) throw new IllegalStateException("Settings page already mounted");
        if (owner == null) { applied = false; discardConfirmed = false; }
        owner = screen;
        if (confirmation != null) confirmation.ownerClosed();
        confirmation = null;
        widgets.clear();
        var viewport = screen.viewport();
        int width = Math.max(0, Math.min(560, viewport.width() - 24));
        clip = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + Math.min(8, viewport.height()), width,
                Math.max(0, viewport.height() - 16 - (actions == null ? 0 : 54)));
        layout = SettingsDetailLayout.of(Math.max(0, width - 12), sections.stream().map(s -> s.fields().size()).toList());
        scroll.resize(clip.height(), layout.contentHeight());
        for (int s = 0; s < sections.size(); s++) {
            var fields = sections.get(s).fields();
            for (int f = 0; f < fields.size(); f++) {
                var field = fields.get(f);
                var bounds = new Bounds(clip.x() + layout.controlX(), 0, layout.controlWidth(), layout.stacked() ? 16 : 22);
                var widget = Objects.requireNonNull(field.control().apply(bounds, screen.context()), "Field widget");
                // The factory owns the control's dynamic message/narration; do not replace it.
                widget.setTooltip(Tooltip.create(field.label()));
                widget.setTabOrderGroup(1);
                screen.addClipped(widget, () -> clip);
                widgets.add(new Placed(widget, s, f));
            }
        }
        scrollbar = screen.addClipped(new UiVerticalScrollBar(
                new Bounds(clip.right() - 8, clip.y(), 8, clip.height()), title, scroll,
                SettingsDetailLayout.ROW, this::position), () -> clip);
        scrollbar.setTabOrderGroup(1);
        scrollbar.setSmoothScrolling(() -> screen.context().preferences().reducedMotion());
        screen.widgetViewport(screen.viewport(), (x, y, horizontal, vertical) -> clip.contains(x, y) && scrollbar.scrollByWheel(vertical));
        if (actions != null) {
            footerY = Math.max(viewport.y(), viewport.bottom() - 30);
            int buttonWidth = Math.max(0, (width - 12) / 3);
            resetButton = addAction(screen, clip.x(), buttonWidth, actions.resetLabel().get(), () -> {
                actions.reset().run();
                status = null;
                screen.resize(Minecraft.getInstance(), screen.width, screen.height);
            });
            addAction(screen, clip.x() + buttonWidth + 6, buttonWidth, actions.cancelLabel(), screen::onClose);
            applyButton = addAction(screen, clip.x() + 2 * (buttonWidth + 6), buttonWidth,
                    actions.applyLabel(), () -> {
                        if (applied || !actions.dirty().getAsBoolean()) return;
                        status = actions.apply().get();
                        if (status == null) { applied = true; screen.onClose(); }
                        else Minecraft.getInstance().getNarrator().sayNow(status);
                    });
        }
        screen.onCloseRequest(() -> {
            if (actions == null || applied || discardConfirmed || !actions.dirty().getAsBoolean()) return true;
            if (confirmation == null || !confirmation.isModalOpen()) {
                confirmation = new UiOverlayHost(screen, screen.context().preferences());
                confirmation.showDialog(new UiConfirmation.Prompt(
                        Component.translatable("sh_better_ui.settings_detail.discard_title"),
                        Component.translatable("sh_better_ui.settings_detail.discard_details"),
                        Component.translatable("sh_better_ui.settings_detail.discard"),
                        Component.translatable("sh_better_ui.settings_detail.keep_editing")), () -> {
                    discardConfirmed = true;
                    screen.onClose();
                });
            }
            return false;
        });
        screen.onFocusChanged(this::revealFocus);
        syncActionState();
        position();
    }

    private UiButton addAction(UiScreen screen, int x, int width, Component label, Runnable action) {
        var button = new UiButton(x, footerY, width, 22, label, ignored -> action.run(),
                screen.context().preferences(), false, UiStyle.LIQUID_GLASS);
        button.setTabOrderGroup(2);
        return screen.addClipped(button, screen::viewport);
    }

    private void position() {
        int offset = (int) Math.round(scroll.offset());
        for (var placed : widgets) {
            int y = clip.y() + layout.cards().get(placed.section()).fieldTop(placed.field()) - offset;
            placed.widget().setX(clip.x() + layout.controlX());
            placed.widget().setY(y + (layout.stacked() ? 14 : 4));
            // Native focus hooks reveal keyboard targets synchronously, not on the next frame.
            placed.widget().visible = clip.width() > 0 && clip.height() > 0;
            if (owner.getFocused() == placed.widget()
                    && (placed.widget().getY() >= clip.bottom()
                    || placed.widget().getY() + placed.widget().getHeight() <= clip.y())) {
                owner.setFocused(null); // Wheel/drag must not leave an invisible keyboard action armed.
            }
        }
        if (scrollbar != null) scrollbar.visible = scroll.maxOffset() > 0 && clip.height() > 0;
    }

    private void revealFocus(GuiEventListener focused) {
        if (scrollbar == null || focused == null) return;
        for (var placed : widgets) {
            if (placed.widget() != focused) continue;
            int y = placed.widget().getY();
            int adjustment = y < clip.y() ? y - clip.y()
                    : Math.max(0, y + placed.widget().getHeight() - clip.bottom());
            if (scroll.scrollBy(adjustment)) { scrollbar.synchronizeMotion(); position(); }
            break;
        }
    }

    // Keep labels, narration and click semantics aligned after host-owned draft edits.
    void syncActionState() {
        if (resetButton != null) {
            var label = actions.resetLabel().get();
            if (!resetButton.getMessage().equals(label)) resetButton.setMessage(label);
        }
        if (applyButton != null) applyButton.active = !applied && actions.dirty().getAsBoolean();
    }

    private void render(UiScreen screen, UiFrame frame) {
        scrollbar.advanceMotion();
        syncActionState();
        var notice = status == null ? footerHint : status;
        if (notice != null && actions != null) frame.graphics().drawString(Minecraft.getInstance().font,
                Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(notice, clip.width())),
                clip.x(), footerY - 14, DarkModeTheme.textColor(), false);
        var graphics = frame.graphics();
        var font = Minecraft.getInstance().font;
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            int offset = (int) Math.round(scroll.offset());
            for (int s = 0; s < sections.size(); s++) {
                var card = layout.cards().get(s);
                int top = clip.y() + card.top() - offset;
                if (top >= clip.bottom() || top + card.height() <= clip.y()) continue;
                ShBetterUiApi.materials().roundedRect(graphics, clip.x(), top, layout.width(), card.height(), 8, 1,
                        frame.preferences().highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.borderColor(),
                        DarkModeTheme.surfaceColor());
                int textWidth = Math.max(0, layout.width() - 2 * SettingsDetailLayout.PADDING);
                graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(sections.get(s).title(), textWidth)),
                        clip.x() + SettingsDetailLayout.PADDING, top + 9, DarkModeTheme.accentColor(), false);
                for (int f = 0; f < sections.get(s).fields().size(); f++) {
                    int y = clip.y() + card.fieldTop(f) - offset;
                    int labelWidth = layout.stacked() ? textWidth : Math.max(0, layout.controlX() - 2 * SettingsDetailLayout.PADDING);
                    graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(sections.get(s).fields().get(f).label(), labelWidth)),
                            clip.x() + SettingsDetailLayout.PADDING, y + (layout.stacked() ? 0 : 9), DarkModeTheme.textColor(), false);
                }
            }
        } finally { graphics.disableScissor(); }
    }
}
