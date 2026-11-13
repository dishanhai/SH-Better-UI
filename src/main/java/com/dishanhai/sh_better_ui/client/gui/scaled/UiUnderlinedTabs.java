package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** 山海：单个原生控件，包含底线标签栏和选中项的短文字面板。 */
public final class UiUnderlinedTabs extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private static final int GAP = 8;
    private static final int PADDING = 4;
    private static final int PANEL_GAP = 5;
    private final List<Tab> tabs;
    private final UiTabSelection selection;
    private final IntConsumer onSelect;
    private NativeTextReadability.Label readableLabel;

    public record Tab(Component label, Component panel, boolean enabled) {
        public Tab {
            Objects.requireNonNull(label);
            Objects.requireNonNull(panel);
        }

        public Tab(Component label, Component panel) { this(label, panel, true); }
    }

    public UiUnderlinedTabs(Bounds area, List<Tab> tabs, int selectedIndex, IntConsumer onSelect) {
        super(area.x(), area.y(), area.width(), area.height(), CommonComponents.EMPTY);
        this.tabs = List.copyOf(tabs);
        selection = new UiTabSelection(this.tabs.size(), selectedIndex, index -> this.tabs.get(index).enabled());
        this.onSelect = Objects.requireNonNull(onSelect);
    }

    /** 宿主可直接同步选中项，此途径不触发控件回调。 */
    public UiTabSelection selection() { return selection; }

    public void bindTextReadability(NativeTextReadability.Label label) {
        readableLabel = label;
    }

    private Bounds[] tabBounds(Font font) {
        var bounds = new Bounds[tabs.size()];
        int rowHeight = Math.min(getHeight(), ROW_HEIGHT);
        int x = getX();
        for (int i = 0; i < tabs.size(); i++) {
            int width = font.width(tabs.get(i).label()) + 2 * PADDING;
            bounds[i] = new Bounds(x, getY(), width, rowHeight);
            x += width + GAP;
        }
        if (x - GAP > getX() + getWidth()) {
            for (int i = 0; i < tabs.size(); i++) {
                int start = (int) ((long) i * getWidth() / tabs.size());
                int end = (int) ((long) (i + 1) * getWidth() / tabs.size());
                bounds[i] = new Bounds(getX() + start, getY(), end - start, rowHeight);
            }
        }
        return bounds;
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return visible && active && enabledTabAt(x, y) >= 0;
    }

    private int enabledTabAt(double x, double y) {
        if (getHeight() < ROW_HEIGHT || y < getY() || y >= getY() + ROW_HEIGHT
                || x < getX() || x >= getX() + getWidth()) return -1;
        var bounds = tabBounds(Minecraft.getInstance().font);
        for (int i = 0; i < bounds.length; i++) {
            if (selection.isEnabled(i) && bounds[i].width() > 2 * PADDING && bounds[i].contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!active || !visible || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        int index = enabledTabAt(x, y);
        if (index < 0) return false;
        if (selection.select(index)) onSelect.accept(index);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        int direction = switch (key) {
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            default -> 0;
        };
        if (direction == 0) return false;
        if (selection.move(direction)) onSelect.accept(selection.selectedIndex());
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        UiTheme theme = UiTheme.current();
        int muted = GuiMotion.lerpColor(theme.text(), theme.background(), 0.32f);
        int disabled = GuiMotion.lerpColor(theme.text(), theme.background(), 0.54f);
        int hover = GuiMotion.lerpColor(muted, theme.text(), 0.45f);
        var bounds = tabBounds(font);
        for (int i = 0; i < tabs.size(); i++) {
            var tab = bounds[i];
            if (tab.width() <= 2 * PADDING || tab.height() < ROW_HEIGHT) continue;
            var label = tabs.get(i).label();
            int available = Math.max(0, tab.width() - 2 * PADDING);
            var line = Language.getInstance().getVisualOrder(font.substrByWidth(label, available));
            int width = font.width(line);
            int x = tab.x() + (tab.width() - width) / 2;
            boolean selected = i == selection.selectedIndex();
            int color = !active || !selection.isEnabled(i) ? disabled
                    : selected ? theme.text() : tab.contains(mouseX, mouseY) ? hover : muted;
            if (readableLabel == null || !readableLabel.draw(graphics, font, line, x, tab.y() + 4,
                    color, false, i, tab.contains(mouseX, mouseY), selected && isFocused())) {
                graphics.drawString(font, line, x, tab.y() + 4, color, false);
            }
            if (selected) {
                graphics.fill(x, tab.bottom() - 4, x + width, tab.bottom() - 2, active ? theme.accent() : disabled);
                if (active && isFocused() && minecraft.getLastInputType().isKeyboard()) {
                    graphics.renderOutline(tab.x(), tab.y(), tab.width(), tab.height(), theme.text());
                }
            }
        }

        int panelY = getY() + ROW_HEIGHT + PANEL_GAP;
        int panelWidth = getWidth() - 2 * PADDING;
        if (panelWidth <= 0 || panelY + font.lineHeight > getY() + getHeight()) return;
        var panel = tabs.get(selection.selectedIndex()).panel();
        for (var line : font.split(panel, panelWidth)) {
            if (panelY + font.lineHeight > getY() + getHeight()) break;
            graphics.drawString(font, line, getX() + PADDING, panelY, theme.text(), false);
            panelY += font.lineHeight;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        var tab = tabs.get(selection.selectedIndex());
        output.add(NarratedElementType.TITLE, Component.translatable("sh_better_ui.control.underlined_tabs.current",
                tab.label(), selection.selectedIndex() + 1, tabs.size(), tab.panel()));
        if (isFocused()) output.add(NarratedElementType.USAGE,
                Component.translatable("sh_better_ui.control.underlined_tabs.usage"));
    }
}
