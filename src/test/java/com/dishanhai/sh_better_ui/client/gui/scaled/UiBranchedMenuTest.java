package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：检查折叠后的键鼠可用性、侧栏边界和选中轨迹。 */
class UiBranchedMenuTest {
    private static final int LINE = 0xFF808080;
    private static final int ACCENT = 0xFFFFFFFF;

    @Test
    void collapsedChildrenStayOutOfInputAndRetainTheSelection() {
        var selected = new AtomicInteger(-1);
        var menu = menu(selected);
        var heading = menu.buttons().get(0);
        var child = menu.buttons().get(2);
        var tools = menu.buttons().get(3);
        int openY = tools.getY();
        child.onPress();
        assertEquals(1, selected.get());
        menu.select(1);

        heading.onPress();
        assertFalse(menu.expanded());
        assertFalse(child.visible);
        assertFalse(child.active);
        assertTrue(child.isSelected());
        assertSame(heading, menu.selectedButton());
        assertTrue(tools.getY() < openY);

        heading.onPress();
        assertTrue(child.isActive());
        assertEquals(openY, tools.getY());
        assertSame(child, menu.selectedButton());
        tools.onPress();
        assertEquals(2, selected.get());
        menu.select(2);
        assertFalse(child.isSelected());
        assertTrue(tools.isSelected());
    }

    @Test
    void activeBranchTerminatesAtTheSelectedChildAndStaysInsideTheMenu() {
        var menu = menu(new AtomicInteger());
        var active = new ArrayList<Bounds>();
        menu.select(1);
        menu.draw((x, y, right, bottom, color) -> {
            assertTrue(x >= 10 && right <= 142 && right > x);
            assertTrue(y >= 20 && bottom <= 108 && bottom > y);
            if (color == ACCENT) active.add(new Bounds(x, y, right - x, bottom - y));
        }, LINE, ACCENT);
        var child = menu.selectedButton();
        int childY = child.getY() + child.getHeight() / 2;
        int branchEnd = child.getX() - 3;
        assertTrue(active.stream().anyMatch(rect -> rect.y() == childY && rect.right() == branchEnd));
    }

    private static UiBranchedMenu menu(AtomicInteger selection) {
        var heading = new UiSectionBar.Section(Component.literal("Settings"), GraphicIcon.SETTINGS);
        var glass = new UiSectionBar.Section(Component.literal("Glass"), GraphicIcon.BLUR);
        var display = new UiSectionBar.Section(Component.literal("Display"), GraphicIcon.MONITOR);
        var tools = new UiSectionBar.Section(Component.literal("Tools"), GraphicIcon.INFO);
        return new UiBranchedMenu(new Bounds(10, 20, 132, 88), new UiPreferences(false, true, false),
                heading, List.of(glass, display), List.of(tools), true, 0, selection::set);
    }
}
