// Author: dishanhai
// Original native implementation inspired by MuoniumPlayer's settings hierarchy; no upstream assets/code.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.layout.SettingCardLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/**
 * Register widgets() with the host, in order, and render this decoration BEFORE those children.
 * Never register the card as an input listener. Transactions/navigation belong to the host.
 * Height is content-derived (Bounds.height is ignored). Rebuild host registrations if children change.
 */
public final class UiSettingCard implements Renderable, AutoCloseable {
    private final Font font;
    private final Component title, description;
    private final Component disclosureName;
    private final AbstractWidget primary;
    private final UiButton disclosure;
    private final UiPreferences preferences;
    private final int primaryHeight;
    private List<AbstractWidget> details = List.of();
    private final Map<AbstractWidget, Boolean> disabledState = new IdentityHashMap<>();
    private final Map<AbstractWidget, Boolean> detailVisibility = new IdentityHashMap<>();
    private List<FormattedCharSequence> titleLines, descriptionLines;
    private Bounds bounds;
    private SettingCardLayout layout;
    private boolean expanded, enabled = true, closed;

    public UiSettingCard(Font font, Bounds area, Component title, Component description,
                         AbstractWidget primary, Component detailsLabel, UiPreferences preferences, Runnable onDetails) {
        this.font = Objects.requireNonNull(font); this.title = Objects.requireNonNull(title);
        this.description = Objects.requireNonNull(description); this.primary = Objects.requireNonNull(primary);
        this.preferences = Objects.requireNonNull(preferences); primaryHeight = primary.getHeight();
        Objects.requireNonNull(onDetails);
        Component accessible = title.copy().append(". ").append(description).append(". ").append(Objects.requireNonNull(detailsLabel));
        disclosureName = accessible;
        disclosure = new UiButton(0, 0, 24, 24, accessible, ignored -> onDetails.run(), preferences, false);
        disclosure.setIcon(GraphicIcon.NEXT); disclosure.setPreview(accessible); disclosure.setSampleBackground(false);
        setBounds(area);
    }
    /** Stable native focus order: primary, details arrow, then expanded children. */
    public List<AbstractWidget> widgets() {
        var result = new ArrayList<AbstractWidget>(); result.add(primary); result.add(disclosure); result.addAll(details);
        return List.copyOf(result);
    }
    public UiButton detailsButton() { return disclosure; }
    public boolean expanded() { return expanded; }
    public int height() { return layout.height(); }
    public SettingCardLayout layout() { return layout; }
    public Bounds bounds() { return new Bounds(bounds.x(), bounds.y(), layout.width(), layout.height()); }
    public void setBounds(Bounds area) {
        Objects.requireNonNull(area);
        if (layout == null || layout.width() != area.width()) {
            int tw = SettingCardLayout.textWidth(area.width());
            titleLines = font.split(title, tw).stream().limit(2).toList();
            descriptionLines = description.getString().isEmpty() ? List.of() : font.split(description, tw).stream().limit(3).toList();
            int textHeight = titleLines.size() * font.lineHeight + (descriptionLines.isEmpty() ? 0 : 4 + descriptionLines.size() * font.lineHeight);
            layout = SettingCardLayout.measure(area.width(), textHeight, primaryHeight,
                    expanded ? details.stream().map(AbstractWidget::getHeight).toList() : List.of());
        }
        bounds = area;
        position(primary, layout.primary()); position(disclosure, layout.disclosure());
        if (expanded) for (int i = 0; i < details.size(); i++) position(details.get(i), layout.details().get(i));
    }
    private void position(AbstractWidget widget, SettingCardLayout.Rect rect) {
        widget.setX(bounds.x() + rect.x()); widget.setY(bounds.y() + rect.y()); widget.setWidth(rect.width());
    }
    /** Hidden controls remain registered but cannot receive focus/hits. Values are not discarded. */
    public void setExpanded(boolean value, List<? extends AbstractWidget> children) {
        List<AbstractWidget> next = List.copyOf(children);
        if (next.stream().anyMatch(w -> w == primary || w == disclosure) || new HashSet<>(next).size() != next.size())
            throw new IllegalArgumentException("Detail controls must be distinct from card actions");
        for (var widget : details) {
            cancel(widget);
            if (detailVisibility.containsKey(widget)) widget.visible = detailVisibility.remove(widget);
            if (disabledState.containsKey(widget)) widget.active = disabledState.remove(widget);
        }
        details = next; expanded = value;
        for (var widget : details) {
            if (!expanded) { detailVisibility.put(widget, widget.visible); widget.visible = false; cancel(widget); }
            if (!enabled) { disabledState.put(widget, widget.active); widget.active = false; cancel(widget); }
        }
        disclosure.setSelected(value);
        disclosure.setIcon(value ? GraphicIcon.PULL_DOWN : GraphicIcon.NEXT);
        disclosure.setMessage(disclosureName.copy().append(". ").append(Component.translatable(
                "sh_better_ui.panel.navigation." + (value ? "expanded" : "collapsed"))));
        disclosure.setPreview(disclosure.getMessage());
        layout = null;
        setBounds(bounds);
    }
    /** Temporarily masks child availability; re-enabling restores previous per-control disabled states. */
    public void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        for (var widget : widgets()) {
            if (!value) { disabledState.put(widget, widget.active); widget.active = false; cancel(widget); }
            else if (disabledState.containsKey(widget)) widget.active = disabledState.remove(widget);
        }
    }
    private static void cancel(AbstractWidget widget) {
        if (widget instanceof UiCancelableInteraction interaction) interaction.cancelInteraction();
        widget.setFocused(false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        if (closed) return;
        int edge = preferences.highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.borderColor();
        DarkModeTheme.rounded(g, bounds.x(), bounds.y(), layout.width(), layout.height(), 9, 1,
                edge, preferences.highContrast() ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor());
        int x = bounds.x() + layout.text().x(), y = bounds.y() + layout.text().y();
        for (var line : titleLines) { g.drawString(font, line, x, y, enabled ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor(), false); y += font.lineHeight; }
        if (!descriptionLines.isEmpty()) y += 4;
        for (var line : descriptionLines) { g.drawString(font, line, x, y, preferences.highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.secondaryTextColor(), false); y += font.lineHeight; }
    }
    /** Cancels gestures only; caller retains ownership of supplied widgets/resources. */
    @Override public void close() { if (closed) return; setEnabled(false); closed = true; }
}
