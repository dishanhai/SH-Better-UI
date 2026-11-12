// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class UiScrollableSectionBarInputTest {
    @Test
    void overflowArrowRequiresReleaseAndNeverSelectsAPage() {
        var selections = new ArrayList<Integer>();
        var bar = bar(selections);
        assertTrue(bar.mouseClicked(mouse(148, 20), false));
        bar.setFocused(true);
        assertEquals(0, bar.scrollOffset());
        assertTrue(bar.mouseReleased(mouse(148, 20)));
        assertTrue(bar.scrollOffset() > 0);
        assertEquals(0, bar.selectedIndex());
        assertTrue(selections.isEmpty());
    }

    @Test
    void cancelRestoresTheViewportAndDropsThePendingClick() {
        var selections = new ArrayList<Integer>();
        var bar = bar(selections);
        bar.scrollTo(30);
        assertTrue(bar.mouseClicked(mouse(80, 20), false));
        bar.setFocused(true);
        assertTrue(bar.mouseDragged(mouse(20, 20), -60, 0));
        assertEquals(90, bar.scrollOffset());

        assertTrue(bar.keyPressed(new KeyEvent(GLFW_KEY_ESCAPE, 0, 0)));
        assertEquals(30, bar.scrollOffset());
        assertFalse(bar.mouseReleased(mouse(20, 20)));
        assertTrue(selections.isEmpty());
    }

    @Test
    void partiallyVisibleTabKeepsViewportClipAndFullyVisibleTabHasFocusSpace() {
        var bar = bar(new ArrayList<>());
        var first = bar.buttons().getFirst();
        var visible = bar.buttonRenderClip(first);
        assertTrue(visible.x() <= first.getX() - 3);
        assertTrue(visible.y() <= first.getY() - 3);

        bar.scrollTo(20);
        var left = bar.buttonRenderClip(first);
        var right = bar.buttonRenderClip(bar.buttons().get(1));
        assertEquals(bar.viewportLeft(), left.x());
        assertEquals(bar.viewportRight(), right.right());
    }

    @Test
    void keyboardSelectionRevealsTheFocusedTabEvenWhenAutoRevealIsDisabled() {
        var selections = new ArrayList<Integer>();
        var bar = bar(selections);
        bar.setSettings(new TabScrollSettings(true, true, false, 32));
        bar.setFocused(true);

        assertTrue(bar.keyPressed(new KeyEvent(GLFW_KEY_END, 0, 0)));
        assertEquals(3, bar.selectedIndex());
        assertEquals(bar.maxScroll(), bar.scrollOffset());
        assertTrue(bar.focusedButton().isFocused());
        assertEquals(List.of(3), selections);
    }

    private static UiScrollableSectionBar bar(List<Integer> selections) {
        var entries = List.of(
                new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.PREVIOUS),
                new UiSectionBar.Section(Component.translatable("gui.stats"), GraphicIcon.NEXT),
                new UiSectionBar.Section(Component.translatable("gui.back"), GraphicIcon.PREVIOUS),
                new UiSectionBar.Section(Component.translatable("gui.done"), GraphicIcon.NEXT));
        return new UiScrollableSectionBar(new Bounds(10, 10, 148, 22), UiPreferences.DEFAULT, entries, 0,
                selections::add, TabScrollSettings.DEFAULT, 80);
    }

    private static MouseButtonEvent mouse(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW_MOUSE_BUTTON_LEFT, 0));
    }
}
