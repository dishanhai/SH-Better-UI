package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** 山海：可注册到宿主界面的水平编号步骤器。 */
public final class UiStepper extends AbstractWidget {
    private static final int DIAMETER = 18;
    private static final int LABEL_GAP = 5;
    private static final int LABEL_INSET = 2;
    private final List<Component> labels;
    private final UiStepperState progress;
    private final IntConsumer onSelect;

    public UiStepper(Bounds area, List<Component> labels, UiStepperState progress, IntConsumer onSelect) {
        super(area.x(), area.y(), area.width(), area.height(), CommonComponents.EMPTY);
        this.labels = List.copyOf(labels);
        this.progress = Objects.requireNonNull(progress);
        if (this.labels.size() != progress.count()) throw new IllegalArgumentException();
        this.onSelect = Objects.requireNonNull(onSelect);
    }

    /** 宿主推进或同步进度时可直接更新此状态，不触发控件回调。 */
    public UiStepperState progress() { return progress; }

    private int cellEdge(int index) {
        return getX() + (int) ((long) index * getWidth() / labels.size());
    }

    private int center(int index) {
        return cellEdge(index) + (cellEdge(index + 1) - cellEdge(index)) / 2;
    }

    private int diameter() {
        return Math.min(DIAMETER, getWidth() / labels.size() - 2 * LABEL_INSET);
    }

    private int labelWidth(int index) {
        return cellEdge(index + 1) - cellEdge(index) - 2 * LABEL_INSET;
    }

    private int reachableStepAt(double x, double y) {
        int diameter = diameter();
        if (diameter < 10 || getHeight() < diameter || x < getX() || x >= getX() + getWidth()
                || y < getY() || y >= getY() + getHeight()) return -1;
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i <= progress.lastReachableIndex(); i++) {
            if (x < cellEdge(i) || x >= cellEdge(i + 1)) continue;
            int centerX = center(i);
            double radius = diameter / 2.0;
            double dx = x - (centerX - diameter / 2 + radius);
            double dy = y - (getY() + radius);
            if (dx * dx + dy * dy < radius * radius) return i;
            int lineY = getY() + diameter + LABEL_GAP;
            if (y < lineY) return -1;
            for (var line : font.split(labels.get(i), labelWidth(i))) {
                if (lineY + font.lineHeight > getY() + getHeight()) break;
                int width = font.width(line);
                if (new Bounds(centerX - width / 2, lineY, width, font.lineHeight).contains(x, y)) return i;
                lineY += font.lineHeight;
            }
            return -1;
        }
        return -1;
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return visible && active && reachableStepAt(x, y) >= 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isValidClickButton(event.buttonInfo())) return false;
        int index = reachableStepAt(event.x(), event.y());
        if (index < 0) return false;
        select(index);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active || !visible || !isFocused()) return false;
        int direction = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
        if (direction == 0) return false;
        select(progress.currentIndex() + direction);
        return true;
    }

    private void select(int index) {
        if (progress.select(index)) onSelect.accept(index);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int diameter = diameter();
        int hovered = active && graphics.containsPointInScissor(mouseX, mouseY) ? reachableStepAt(mouseX, mouseY) : -1;
        isHovered = hovered >= 0;
        if (diameter < 10 || getHeight() < diameter) return;
        var minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        UiTheme theme = UiTheme.current();
        int muted = ARGB.srgbLerp(0.35f, theme.text(), theme.background());
        int edge = ARGB.srgbLerp(0.25f, theme.border(), theme.text());
        int accent = active ? theme.accent() : edge;
        int centerY = getY() + diameter / 2;

        for (int i = 1; i < labels.size(); i++) {
            graphics.fill(center(i - 1) - diameter / 2 + diameter, centerY,
                    center(i) - diameter / 2, centerY + 1,
                    ARGB.multiplyAlpha(i <= progress.lastReachableIndex() ? accent : edge, alpha));
        }
        for (int i = 0; i < labels.size(); i++) {
            var status = progress.status(i);
            boolean current = status == UiStepperState.Status.CURRENT;
            boolean completed = status == UiStepperState.Status.COMPLETED;
            boolean focused = current && active && isFocused() && minecraft.getLastInputType().isKeyboard();
            int ring = focused ? theme.text() : current || completed ? accent : edge;
            int fill = completed ? ARGB.srgbLerp(0.30f, theme.surface(), accent) : theme.surface();
            int text = ARGB.multiplyAlpha(current || completed || i == hovered ? theme.text() : muted, alpha);
            int centerX = center(i);
            RoundedCorners.draw(graphics, centerX - diameter / 2, getY(), diameter, diameter, diameter / 2f,
                    focused ? 3f : current ? 2f : 1f, ARGB.multiplyAlpha(ring, alpha), ARGB.multiplyAlpha(fill, alpha));
            var number = Integer.toString(i + 1);
            graphics.text(font, number, centerX - font.width(number) / 2,
                    getY() + (diameter - font.lineHeight) / 2, text, false);
            int y = getY() + diameter + LABEL_GAP;
            for (var line : font.split(labels.get(i), labelWidth(i))) {
                if (y + font.lineHeight > getY() + getHeight()) break;
                graphics.text(font, line, centerX - font.width(line) / 2, y, text, false);
                y += font.lineHeight;
            }
        }
        handleCursor(graphics);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        int current = progress.currentIndex();
        output.add(NarratedElementType.TITLE, Component.translatable("sh_better_ui.control.stepper.current",
                current + 1, labels.size(), labels.get(current)));
        if (isFocused()) output.add(NarratedElementType.USAGE,
                Component.translatable("sh_better_ui.control.stepper.usage"));
    }
}
