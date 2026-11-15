// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
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

/**
 * Reusable secondary settings page: fixed native back/title bar and scrolling cards.
 * The host owns draft values, validation and persistence. Returning never implicitly saves
 * or discards them. Create one instance per navigation entry; reuse it for resize/reopen.
 * Inspired by Card/Field composition, not a React dependency or copied shadcn implementation.
 */
public final class UiSettingsDetailPage {
    public record Field(Component label, BiFunction<Bounds, UiApiContext, AbstractWidget> control) {
        public Field { label = Objects.requireNonNull(label).copy(); Objects.requireNonNull(control); }
    }
    public record Section(Component title, List<Field> fields) {
        public Section { title = Objects.requireNonNull(title).copy(); fields = List.copyOf(fields); }
    }
    private record Placed(AbstractWidget widget, int section, int field) {}
    private final Component title;
    private final List<Section> sections;
    private final VerticalScroll scroll = new VerticalScroll();
    private final List<Placed> widgets = new ArrayList<>();
    private SettingsDetailLayout layout;
    private Bounds clip;
    private UiVerticalScrollBar scrollbar;
    private GuiEventListener lastFocus;
    private UiScreen owner;

    public UiSettingsDetailPage(Component title, List<Section> sections) {
        this.title = Objects.requireNonNull(title).copy();
        this.sections = List.copyOf(sections);
    }

    /** Pass to UUK ScreenService.create(module, builder), then open through the host. */
    public UiScreenBuilder builder() {
        return new UiScreenBuilder(title).dynamicTheme()
                .onInit(this::init).onRender(this::render).onRemoved(screen -> {
                    if (owner == screen) { widgets.clear(); scrollbar = null; owner = null; lastFocus = null; }
                });
    }

    private void init(UiScreen screen) {
        if (owner != null && owner != screen) throw new IllegalStateException("Settings page already mounted");
        owner = screen;
        widgets.clear();
        lastFocus = null;
        var viewport = screen.viewport();
        int width = Math.max(0, Math.min(560, viewport.width() - 24));
        clip = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + Math.min(8, viewport.height()), width,
                Math.max(0, viewport.height() - 16));
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
        position();
    }

    private void position() {
        int offset = (int) Math.round(scroll.offset());
        for (var placed : widgets) {
            int y = clip.y() + layout.cards().get(placed.section()).fieldTop(placed.field()) - offset;
            placed.widget().setX(clip.x() + layout.controlX());
            placed.widget().setY(y + (layout.stacked() ? 14 : 4));
            // Keep clipped controls in keyboard traversal; render() scrolls new focus into view.
            placed.widget().visible = clip.width() > 0 && clip.height() > 0;
        }
        if (scrollbar != null) scrollbar.visible = scroll.maxOffset() > 0 && clip.height() > 0;
    }

    private void render(UiScreen screen, UiFrame frame) {
        scrollbar.advanceMotion();
        var focused = screen.getFocused();
        if (focused != lastFocus) {
            lastFocus = focused;
            for (var placed : widgets) {
                if (placed.widget() != focused) continue;
                int y = placed.widget().getY();
                int adjustment = y < clip.y() ? y - clip.y()
                        : Math.max(0, y + placed.widget().getHeight() - clip.bottom());
                if (scroll.scrollBy(adjustment)) { scrollbar.synchronizeMotion(); position(); }
                break;
            }
        }
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
