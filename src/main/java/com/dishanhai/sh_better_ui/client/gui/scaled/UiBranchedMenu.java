package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** 山海：侧栏分支导航。宿主负责注册按钮和切页，菜单只管理折叠、布局与选中提示。 */
public final class UiBranchedMenu {
    private static final int RAIL = 1;
    private static final int HEAD_INDENT = 8;
    private static final int TRUNK = 14;
    private static final int CHILD_INDENT = 27;
    private static final int CURVE = 4;
    private static final long FOLD_MS = 300;
    private static final long DRAW_MS = 400;
    private static final long MARKER_MS = 220;

    private final Bounds bounds;
    private final int rowHeight;
    private final int childCount;
    private final List<UiButton> buttons;
    private final InterruptibleAnimation fold;
    private final InterruptibleAnimation marker = new InterruptibleAnimation(Spring.INDICATOR);
    private final List<InterruptibleAnimation> paths = new ArrayList<>();
    private UiPreferences preferences;
    private boolean expanded;
    private int selected;
    private int shownHeight;

    public UiBranchedMenu(Bounds bounds, UiPreferences preferences, UiSectionBar.Section heading,
                          List<UiSectionBar.Section> children, List<UiSectionBar.Section> leaves,
                          boolean expanded, int selected, IntConsumer onSelect) {
        this.bounds = bounds;
        this.preferences = preferences;
        this.childCount = children.size();
        this.expanded = expanded;
        this.selected = selected;
        rowHeight = Math.min(22, bounds.height() / (1 + childCount + leaves.size()));
        fold = new InterruptibleAnimation(Spring.INDICATOR, expanded ? 1f : 0f);
        var widgets = new ArrayList<UiButton>();
        widgets.add(button(heading, HEAD_INDENT, this::toggle));
        for (var child : children) {
            int index = paths.size();
            paths.add(new InterruptibleAnimation(Spring.INDICATOR, index == selected ? 1f : 0f));
            widgets.add(button(child, CHILD_INDENT, () -> onSelect.accept(index)));
        }
        for (var leaf : leaves) {
            int index = widgets.size() - 1;
            widgets.add(button(leaf, HEAD_INDENT, () -> onSelect.accept(index)));
        }
        buttons = List.copyOf(widgets);
        updateHeading();
        buttons.get(selected + 1).setSelected(true);
        marker.snapTo(markerOffset());
        updateLayout(nowMs());
    }

    private UiButton button(UiSectionBar.Section entry, int indent, Runnable action) {
        var control = new UiButton(bounds.x() + indent, bounds.y(), bounds.width() - indent, rowHeight - 2,
                entry.label(), ignored -> action.run(), preferences, false);
        control.setLeadingIcon(entry.icon());
        control.setSampleBackground(false);
        return control;
    }

    public List<UiButton> buttons() { return buttons; }

    public boolean expanded() { return expanded; }

    public UiButton selectedButton() {
        return buttons.get(selected < childCount && !expanded ? 0 : selected + 1);
    }

    public void select(int index) {
        if (selected == index) return;
        long now = nowMs();
        buttons.get(selected + 1).setSelected(false);
        if (selected < childCount) retarget(paths.get(selected), 0f, now, DRAW_MS);
        selected = index;
        buttons.get(selected + 1).setSelected(true);
        if (selected < childCount) retarget(paths.get(selected), 1f, now, DRAW_MS);
        retarget(marker, markerOffset(), now, MARKER_MS);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = preferences;
        buttons.forEach(button -> button.setPreferences(preferences));
        updateLayout(nowMs());
    }

    private void toggle() {
        expanded = !expanded;
        long now = nowMs();
        retarget(fold, expanded ? 1f : 0f, now, FOLD_MS);
        updateHeading();
        updateLayout(now);
    }

    private void updateHeading() {
        var heading = buttons.get(0);
        heading.setLeadingIcon(expanded ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
        heading.setTooltip(Tooltip.create(CommonComponents.joinForNarration(heading.getMessage(),
                Component.translatable("sh_better_ui.panel.navigation." + (expanded ? "expanded" : "collapsed")))));
    }

    private void retarget(InterruptibleAnimation animation, float target, long now, long duration) {
        if (preferences.reducedMotion()) animation.snapTo(target);
        else animation.blendTo(target, now, 0, duration, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
    }

    private void updateLayout(long now) {
        shownHeight = Math.round(childCount * rowHeight * fold.sample(now, preferences));
        for (int index = 0; index < childCount; index++) {
            var control = buttons.get(index + 1);
            control.setY(bounds.y() + (index + 1) * rowHeight);
            control.visible = shownHeight >= index * rowHeight + control.getHeight();
            control.active = expanded && control.visible;
        }
        for (int index = childCount + 1; index < buttons.size(); index++) {
            buttons.get(index).setY(bounds.y() + shownHeight + (index - childCount) * rowHeight);
        }
    }

    private int markerOffset() {
        // 子项共享分组标记。独立入口的位置另外跟随折叠高度。
        return selected < childCount ? 0 : selected - childCount + 1;
    }

    @FunctionalInterface
    public interface Fill {
        void rect(int x, int y, int right, int bottom, int color);
    }

    /** 先绘制连接线，再由 Screen 绘制原生按钮。 */
    public void draw(Fill fill, int lineColor, int accentColor) {
        long now = nowMs();
        updateLayout(now);
        int x = bounds.x(), y = bounds.y();
        int half = (rowHeight - 2) / 2;
        int end = y + rowHeight + shownHeight;
        int railBottom = buttons.get(buttons.size() - 1).getY() + rowHeight;
        fill.rect(x + RAIL, y + half, x + RAIL + 1, railBottom, lineColor);
        fill.rect(x + TRUNK, y + rowHeight - 2, x + TRUNK + 1,
                Math.min(end, y + childCount * rowHeight + half - CURVE + 1), lineColor);
        for (int index = 0; index < childCount; index++) {
            int row = y + (index + 1) * rowHeight + half;
            if (row - CURVE >= end) break;
            drawBranch(fill, x, row, end, CHILD_INDENT - 3 - TRUNK, lineColor);
            int start = y + rowHeight - 2;
            int trunkLength = row - CURVE - start;
            int branchLength = CHILD_INDENT - 3 - TRUNK;
            int reach = Math.round((trunkLength + branchLength) * paths.get(index).sample(now, preferences));
            if (reach <= 0) continue;
            fill.rect(x + TRUNK, start, x + TRUNK + 1, Math.min(end, start + reach), accentColor);
            if (reach > trunkLength) drawBranch(fill, x, row, end, reach - trunkLength, accentColor);
        }
        float position = marker.sample(now, preferences);
        int markerY = y + half + Math.round(position * rowHeight + Math.min(1f, position) * shownHeight);
        fill.rect(x + RAIL, markerY - 5, x + RAIL + 2, markerY + 6, accentColor);
    }

    private static void drawBranch(Fill fill, int x, int y, int bottom, int length, int color) {
        for (int step = 0; step < Math.min(CURVE, length); step++) {
            int start = y - (CURVE - step) * (CURVE - step) / CURVE;
            int finish = y - (CURVE - step - 1) * (CURVE - step - 1) / CURVE + 1;
            if (start >= bottom) return;
            fill.rect(x + TRUNK + step, start, x + TRUNK + step + 1, Math.min(bottom, finish), color);
        }
        if (length > CURVE && y < bottom) {
            fill.rect(x + TRUNK + CURVE, y, x + TRUNK + length, y + 1, color);
        }
    }

    private static long nowMs() { return System.nanoTime() / 1_000_000L; }
}
