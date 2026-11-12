package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * 山海：可直接注册为单个原生控件的横向滚动标签栏。
 * 内部按钮由本控件绘制、裁切和旁白，不应再次注册到 Screen。
 * Tab 进入标签组，左右、Home、End 选择标签；鼠标在放开时选择，拖动不会误选。
 * 标签溢出时，两侧箭头可显示相邻标签，当前页保持不变。
 */
public final class UiScrollableSectionBar extends AbstractWidget {
    private static final int GAP = 4;
    private static final int ARROW_WIDTH = 20;
    private static final int ARROW_GUTTER = ARROW_WIDTH + GAP;
    // 液态玻璃原生焦点环外扩 3 单位，再为圆角抗锯齿保留 1 单位。
    private static final int DECORATION_PADDING = 4;
    public static final int DEFAULT_TAB_WIDTH = 104;
    private final List<UiButton> buttons;
    private final UiButton previous;
    private final UiButton next;
    private final TabScrollState scrolling = new TabScrollState();
    private final IntConsumer onSelect;
    private final int tabWidth;
    private TabScrollSettings settings;
    private int selected;
    private int focused;
    private int pressed = -1;
    private UiButton pressedArrow;
    private boolean captured;
    private @Nullable Tooltip tooltip;

    public UiScrollableSectionBar(Bounds area, UiPreferences preferences, List<UiSectionBar.Section> sections,
                                  int selectedIndex, IntConsumer onSelect) {
        this(area, preferences, sections, selectedIndex, onSelect,
                TabScrollSettings.DEFAULT, DEFAULT_TAB_WIDTH);
    }

    public UiScrollableSectionBar(Bounds area, UiPreferences preferences, List<UiSectionBar.Section> sections,
                                  int selectedIndex, IntConsumer onSelect, TabScrollSettings settings, int tabWidth) {
        super(area.x(), area.y(), area.width(), area.height(), CommonComponents.EMPTY);
        checkArea(area);
        if (tabWidth < GraphicIcon.PIXELS) throw new IllegalArgumentException();
        Objects.requireNonNull(preferences);
        setTooltipDelay(Duration.ofMillis(preferences.feedback().tooltipDelayMs()));
        this.settings = Objects.requireNonNull(settings);
        this.onSelect = Objects.requireNonNull(onSelect);
        this.tabWidth = tabWidth;
        var entries = List.copyOf(sections);
        Objects.checkIndex(selectedIndex, entries.size());
        var widgets = new ArrayList<UiButton>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            int position = index;
            var entry = entries.get(index);
            var button = new UiButton(0, area.y(), tabWidth, area.height(), entry.label(), ignored -> {
                select(position);
                focused = position;
                syncFocus();
                onSelect.accept(position);
            }, preferences, false, UiStyle.LIQUID_GLASS);
            button.setSampleBackground(false);
            button.setLeadingIcon(entry.icon());
            if (button.labelWidth(tabWidth, area.height()) == tabWidth - 12) button.setIcon(entry.icon());
            widgets.add(button);
        }
        buttons = List.copyOf(widgets);
        previous = arrow(preferences, "previous", GraphicIcon.PREVIOUS, false);
        next = arrow(preferences, "next", GraphicIcon.NEXT, true);
        selected = focused = selectedIndex;
        setBounds(area);
        select(selectedIndex);
    }

    private UiButton arrow(UiPreferences preferences, String key, GraphicIcon icon, boolean forward) {
        var button = new UiButton(0, getY(), ARROW_WIDTH, getHeight(),
                Component.translatable("sh_better_ui.control.tabs." + key), ignored -> browse(forward),
                preferences, false, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        button.setIcon(icon);
        return button;
    }

    /** 返回只读按钮列表，用于读取位置、提示和状态；不要再次向 Screen 注册。 */
    public List<UiButton> buttons() { return buttons; }
    public int selectedIndex() { return selected; }
    public UiButton focusedButton() { return buttons.get(focused); }
    public double scrollOffset() { return scrolling.offset(); }
    public double maxScroll() { return scrolling.maxOffset(); }

    /** 更新已确认选择，不触发回调；自动显示是否启用由设置决定。 */
    public void select(int index) {
        Objects.checkIndex(index, buttons.size());
        selected = index;
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).setSelected(i == selected);
        if (settings.autoReveal()) reveal(index);
    }

    /** 定位键盘焦点，不提交选择；键盘焦点始终可见。 */
    public void focus(int index) {
        Objects.checkIndex(index, buttons.size());
        focused = index;
        reveal(index);
        syncFocus();
    }

    public void setPreferences(UiPreferences preferences) {
        Objects.requireNonNull(preferences);
        setTooltipDelay(Duration.ofMillis(preferences.feedback().tooltipDelayMs()));
        buttons.forEach(button -> button.setPreferences(preferences));
        previous.setPreferences(preferences);
        next.setPreferences(preferences);
    }

    public void setSettings(TabScrollSettings settings) {
        cancelDrag();
        this.settings = Objects.requireNonNull(settings);
        if (settings.autoReveal()) reveal(selected);
    }

    public void setBounds(Bounds area) {
        checkArea(area);
        cancelDrag();
        setX(area.x());
        setY(area.y());
        setWidth(area.width());
        setHeight(area.height());
        scrolling.resize(viewportWidth(), contentWidth());
        layoutButtons();
        if (isFocused()) reveal(focused);
        else if (settings.autoReveal()) reveal(selected);
    }

    /** 内容坐标滚动量；可供外部滚动条或其他输入源独立控制。 */
    public void scrollTo(double offset) {
        scrolling.scrollTo(offset);
        layoutButtons();
    }

    public void reveal(int index) {
        Objects.checkIndex(index, buttons.size());
        double start = (double) index * (tabWidth + GAP);
        scrolling.reveal(start, start + tabWidth, viewportWidth());
        layoutButtons();
    }

    private double contentWidth() {
        return (double) buttons.size() * (tabWidth + GAP) - GAP;
    }

    private int gutter() {
        // 极窄的自定义栏优先给标签保留空间，仍可用键盘和已启用的手势浏览。
        return getWidth() < contentWidth() && getWidth() >= 2 * ARROW_GUTTER + GraphicIcon.PIXELS
                ? ARROW_GUTTER : 0;
    }

    int viewportLeft() { return getX() + gutter(); }
    int viewportRight() { return getX() + getWidth() - gutter(); }
    private int viewportWidth() { return getWidth() - 2 * gutter(); }

    private void browse(boolean forward) {
        int stride = tabWidth + GAP;
        double edge = scrolling.offset() + (forward ? viewportWidth() : 0);
        // 向右找第一个尾部越界的标签；向左找最近一个起点在视口之前的标签。
        int index = forward ? (int) Math.floor((edge - tabWidth) / stride) + 1
                : (int) Math.ceil(edge / stride) - 1;
        focused = UiMath.clamp(index, 0, buttons.size() - 1);
        double start = (double) focused * stride;
        scrollTo(forward ? start + tabWidth - viewportWidth() : start);
        syncFocus();
    }

    private static void checkArea(Bounds area) {
        Objects.requireNonNull(area);
        if (area.width() < GraphicIcon.PIXELS || area.height() < GraphicIcon.PIXELS) {
            throw new IllegalArgumentException();
        }
    }

    private void layoutButtons() {
        int shift = (int) Math.round(scrolling.offset());
        int left = viewportLeft();
        for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            button.setX(left + index * (tabWidth + GAP) - shift);
            button.setY(getY());
            button.setHeight(getHeight());
        }
        layoutArrow(previous, getX(), scrolling.offset() > 0);
        layoutArrow(next, getX() + getWidth() - ARROW_WIDTH, scrolling.offset() < scrolling.maxOffset());
    }

    private void layoutArrow(UiButton arrow, int x, boolean canScroll) {
        arrow.setX(x);
        arrow.setY(getY());
        arrow.setHeight(getHeight());
        arrow.visible = gutter() != 0;
        arrow.active = canScroll;
    }

    private boolean contains(double x, double y) {
        return active && visible && x >= getX() && x < getX() + getWidth()
                && y >= getY() && y < getY() + getHeight();
    }

    private int buttonAt(double x, double y) {
        if (!contains(x, y) || x < viewportLeft() || x >= viewportRight()) return -1;
        for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            if (button.active && button.visible && button.isMouseOver(x, y)) return index;
        }
        return -1;
    }

    private UiButton arrowAt(double x, double y) {
        if (!contains(x, y)) return null;
        if (previous.isMouseOver(x, y)) return previous;
        return next.isMouseOver(x, y) ? next : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        if (!isValidClickButton(event.buttonInfo()) || !contains(x, y)) return false;
        cancelDrag();
        if (x < viewportLeft() || x >= viewportRight()) {
            pressedArrow = arrowAt(x, y);
            if (pressedArrow == null) return false;
            pressedArrow.setHeld(true);
            captured = true;
            return true;
        }
        captured = true;
        pressed = buttonAt(x, y);
        if (settings.dragEnabled()) scrolling.beginDrag(x, y);
        if (pressed >= 0) {
            focused = pressed;
            buttons.get(pressed).setHeld(true);
            syncFocus();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!isValidClickButton(event.buttonInfo()) || !captured) return false;
        if (!isActive()) return cancelDrag();
        double x = event.x(), y = event.y();
        if (pressedArrow != null) {
            pressedArrow.setHeld(arrowAt(x, y) == pressedArrow);
            return true;
        }
        scrolling.drag(x, y);
        layoutButtons();
        if (pressed >= 0) buttons.get(pressed).setHeld(!scrolling.dragging() && buttonAt(x, y) == pressed);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!isValidClickButton(event.buttonInfo()) || !captured) return false;
        if (!isActive()) return cancelDrag();
        double x = event.x(), y = event.y();
        if (pressedArrow != null) {
            var arrow = pressedArrow;
            cancelDrag();
            if (arrowAt(x, y) == arrow) arrow.onPress();
            return true;
        }
        boolean dragged = scrolling.endDrag(x, y);
        int index = pressed;
        if (index >= 0) buttons.get(index).setHeld(false);
        pressed = -1;
        captured = false;
        layoutButtons();
        if (!dragged && index >= 0 && index == buttonAt(x, y)) buttons.get(index).onPress();
        return true;
    }

    /** Escape、失焦或宿主关闭时取消当前手势，并恢复按下前视口。 */
    public boolean cancelDrag() {
        boolean result = captured;
        scrolling.cancelDrag();
        if (pressedArrow != null) pressedArrow.setHeld(false);
        pressedArrow = null;
        if (buttons != null && pressed >= 0) buttons.get(pressed).setHeld(false);
        captured = false;
        pressed = -1;
        if (buttons != null) layoutButtons();
        return result;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (!contains(x, y) || !settings.wheelEnabled() || captured || !Double.isFinite(delta)
                || delta == 0 || scrolling.maxOffset() == 0) return false;
        scrolling.scrollBy(-delta * settings.scrollStep());
        layoutButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        return mouseScrolled(x, y, deltaX != 0 ? -deltaX : deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) return cancelDrag();
        if (!isActive() || !isFocused()) return false;
        int next = switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> Math.max(0, focused - 1);
            case GLFW.GLFW_KEY_RIGHT -> Math.min(buttons.size() - 1, focused + 1);
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> buttons.size() - 1;
            default -> -1;
        };
        if (next >= 0) {
            cancelDrag();
            focus(next);
            if (buttons.get(next).active && buttons.get(next).visible) buttons.get(next).onPress();
            return true;
        }
        if (event.isSelection()) {
            if (focusedButton().active && focusedButton().visible) focusedButton().onPress();
            return true;
        }
        return false;
    }

    @Override
    public void setFocused(boolean value) {
        super.setFocused(value);
        if (buttons == null) return;
        if (value) {
            if (!captured) reveal(focused);
        } else cancelDrag();
        syncFocus();
    }

    private void syncFocus() {
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).setFocused(isFocused() && focused == i);
    }

    /** 完整可见的一侧容纳外描边；越过滚动边界的一侧仍严格裁切内容。 */
    Bounds buttonRenderClip(UiButton button) {
        int viewLeft = viewportLeft(), viewRight = viewportRight();
        int left = button.getX() >= viewLeft ? Math.min(viewLeft, button.getX() - DECORATION_PADDING) : viewLeft;
        int buttonRight = button.getX() + button.getWidth();
        int right = buttonRight <= viewRight
                ? Math.max(viewRight, buttonRight + DECORATION_PADDING) : viewRight;
        return new Bounds(left, getY() - DECORATION_PADDING, right - left,
                getHeight() + 2 * DECORATION_PADDING);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!active) cancelDrag();
        layoutButtons();
        boolean hover = contains(mouseX, mouseY) && graphics.containsPointInScissor(mouseX, mouseY);
        int tooltipIndex = hover ? buttonAt(mouseX, mouseY) : isFocused() ? focused : -1;
        UiButton tooltipButton = tooltipIndex < 0 ? null : buttons.get(tooltipIndex);
        var hoveredArrow = hover ? arrowAt(mouseX, mouseY) : null;
        // 原生提示锚定聚合控件的可见视口，避免部分裁切按钮把提示定位到画面外。
        var tooltip = hoveredArrow != null ? hoveredArrow.getTooltip()
                : tooltipButton != null && intersectsViewport(tooltipButton) ? tooltipButton.getTooltip() : null;
        if (getTooltip() != tooltip) setTooltip(tooltip);
        boolean hoverTabs = hover && mouseX >= viewportLeft() && mouseX < viewportRight();
        for (UiButton button : buttons) {
            if (intersectsViewport(button)) {
                var clip = buttonRenderClip(button);
                graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
                try {
                    renderButton(button, graphics, hoverTabs ? mouseX : -1, hoverTabs ? mouseY : -1, partialTick);
                } finally {
                    graphics.disableScissor();
                }
            }
        }
        renderButton(previous, graphics, mouseX, mouseY, partialTick);
        renderButton(next, graphics, mouseX, mouseY, partialTick);
    }

    private boolean intersectsViewport(UiButton button) {
        return button.getX() < viewportRight() && button.getX() + button.getWidth() > viewportLeft();
    }

    private void renderButton(UiButton button, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!button.visible) return;
        boolean buttonActive = button.active;
        var tooltip = button.getTooltip();
        try {
            button.active &= active;
            button.setTooltip(null);
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        } finally {
            button.setTooltip(tooltip);
            button.active = buttonActive;
        }
    }

    @Override
    public void setTooltip(@Nullable Tooltip tooltip) {
        this.tooltip = tooltip;
        super.setTooltip(tooltip);
    }

    public @Nullable Tooltip getTooltip() { return tooltip; }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        focusedButton().updateWidgetNarration(output);
        output.add(NarratedElementType.USAGE, Component.translatable("sh_better_ui.control.tabs.usage"));
    }
}
