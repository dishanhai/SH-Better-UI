package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：检查整数步进与原生滑杆事件的接合。 */
class UiSliderTest {
    @Test
    void draggingWithinOneStepDoesNotRepeatTheCallback() {
        var changed = new ArrayList<Integer>();
        var slider = slider(0, 200, 5, 0, changed);
        slider.onClick(14, 25);
        slider.mouseDragged(20, 25, GLFW.GLFW_MOUSE_BUTTON_LEFT, 6, 0);
        slider.mouseDragged(21, 25, GLFW.GLFW_MOUSE_BUTTON_LEFT, 1, 0);
        slider.mouseDragged(24, 25, GLFW.GLFW_MOUSE_BUTTON_LEFT, 3, 0);
        slider.mouseDragged(300, 25, GLFW.GLFW_MOUSE_BUTTON_LEFT, 276, 0);
        assertEquals(List.of(5, 10, 200), changed);
        assertEquals(200, slider.value());
        assertFalse(slider.isMouseOver(218, 25));
        assertTrue(slider.isMouseOver(10, 20));
    }

    @Test
    void arrowsMoveOneStepAndBothDirectionsReachAPartialEndpoint() {
        var changed = new ArrayList<Integer>();
        var slider = slider(0, 15, 4, 8, changed);
        assertFalse(slider.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertTrue(slider.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        for (int i = 0; i < 3; i++) assertTrue(slider.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        for (int i = 0; i < 5; i++) assertTrue(slider.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));
        assertEquals(List.of(12, 15, 12, 8, 4, 0), changed);
        assertEquals(0, slider.value());
    }

    @Test
    void programmaticSyncClampsAndPreservesAnExplicitTooltip() {
        var changed = new ArrayList<Integer>();
        var slider = slider(-20, 20, 5, 0, changed);
        var initialTooltip = slider.getTooltip();
        slider.setValue(-17);
        assertEquals(-15, slider.value());
        assertEquals(label(-15), slider.getMessage());
        assertNotSame(initialTooltip, slider.getTooltip());

        var tooltip = Tooltip.create(Component.translatable("options.title"));
        slider.setTooltip(tooltip);
        slider.setValue(Integer.MIN_VALUE);
        assertEquals(-20, slider.value());
        assertSame(tooltip, slider.getTooltip());
        assertTrue(changed.isEmpty());
    }

    @Test
    void dragPreviewCanBeCancelledAndKeyboardEditCommitsOnlyTheFinalValue() {
        var preview = new ArrayList<Integer>();
        var committed = new ArrayList<Integer>();
        var cancelled = new ArrayList<Integer>();
        var slider = slider(0, 100, 1, 0, preview);
        slider.setOnCommit(committed::add, cancelled::add);
        slider.onClick(114, 25);
        assertEquals(List.of(50), preview);
        assertTrue(committed.isEmpty());
        slider.cancelEdit();
        assertEquals(List.of(0), cancelled);
        assertEquals(0, slider.value());
        assertTrue(slider.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(slider.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertEquals(List.of(1), committed);
    }

    @Test
    void fullIntegerSpanDoesNotOverflow() {
        var changed = new ArrayList<Integer>();
        var slider = slider(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, changed);
        assertEquals(-1, slider.value());
        slider.setValue(Integer.MAX_VALUE);
        slider.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        slider.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        assertEquals(Integer.MAX_VALUE - 1, slider.value());
        assertEquals(List.of(Integer.MAX_VALUE - 1), changed);
    }

    @Test
    void rejectsEmptyRangesInvalidStepsAndBoundsWithoutTrackRoom() {
        var changed = new ArrayList<Integer>();
        assertThrows(IllegalArgumentException.class, () -> slider(0, 0, 1, 0, changed));
        assertThrows(IllegalArgumentException.class, () -> slider(0, 20, 0, 0, changed));
        assertThrows(IllegalArgumentException.class, () -> new UiSlider(new Bounds(0, 0, 8, 20), 0, 20, 1, 0,
                UiPreferences.DEFAULT, UiSliderTest::label, changed::add));
    }

    private static UiSlider slider(int min, int max, int step, int value, List<Integer> changed) {
        return new UiSlider(new Bounds(10, 20, 208, 22), min, max, step, value, UiPreferences.DEFAULT,
                UiSliderTest::label, changed::add);
    }

    private static Component label(int value) {
        return Component.translatable("options.percent_value", value);
    }
}
