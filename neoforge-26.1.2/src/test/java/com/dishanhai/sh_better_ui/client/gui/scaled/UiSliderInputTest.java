// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class UiSliderInputTest {
    @Test
    void focusLossRestoresTheValueAndLatePointerEventsCannotEditIt() {
        var previews = new ArrayList<Integer>();
        var commits = new ArrayList<Integer>();
        var cancels = new ArrayList<Integer>();
        var slider = slider(0, 100, 5, 0, previews);
        slider.setOnCommit(commits::add, cancels::add);

        slider.onClick(mouse(114), false);
        slider.mouseDragged(mouse(116), 2, 0);
        assertEquals(List.of(50), previews);
        assertTrue(commits.isEmpty());

        slider.setFocused(false);
        slider.mouseDragged(mouse(200), 84, 0);
        slider.mouseReleased(mouse(200));
        assertEquals(0, slider.value());
        assertEquals(List.of(0), cancels);
        assertEquals(List.of(50), previews);
        assertTrue(commits.isEmpty());
    }

    @Test
    void keyboardCommitsOneStepAtATimeAcrossAPartialEndpoint() {
        var changed = new ArrayList<Integer>();
        var commits = new ArrayList<Integer>();
        var slider = slider(0, 15, 4, 8, changed);
        slider.setOnCommit(commits::add, ignored -> fail("keyboard edit cancelled"));

        assertFalse(slider.keyPressed(key(GLFW_KEY_RIGHT)));
        assertTrue(slider.keyPressed(key(GLFW_KEY_ENTER)));
        for (int i = 0; i < 3; i++) assertTrue(slider.keyPressed(key(GLFW_KEY_RIGHT)));
        for (int i = 0; i < 5; i++) assertTrue(slider.keyPressed(key(GLFW_KEY_LEFT)));

        assertEquals(List.of(12, 15, 12, 8, 4, 0), changed);
        assertEquals(changed, commits);
        assertEquals(0, slider.value());
    }

    @Test
    void programmaticSyncSupportsTheFullIntegerRangeAndPreservesCustomTooltip() {
        var changed = new ArrayList<Integer>();
        var slider = slider(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, changed);
        assertEquals(-1, slider.value());
        var original = slider.getTooltip();
        slider.setValue(Integer.MIN_VALUE);
        assertNotSame(original, slider.getTooltip());
        var tooltip = Tooltip.create(Component.translatable("options.title"));
        slider.setTooltip(tooltip);
        slider.setValue(Integer.MAX_VALUE);
        assertSame(tooltip, slider.getTooltip());
        assertEquals(label(Integer.MAX_VALUE), slider.getMessage());
        assertTrue(changed.isEmpty());

        slider.keyPressed(key(GLFW_KEY_ENTER));
        slider.keyPressed(key(GLFW_KEY_LEFT));
        assertEquals(Integer.MAX_VALUE - 1, slider.value());
        assertEquals(List.of(Integer.MAX_VALUE - 1), changed);
    }

    private static UiSlider slider(int min, int max, int step, int value, List<Integer> changed) {
        return new UiSlider(new Bounds(10, 20, 208, 22), min, max, step, value, UiPreferences.DEFAULT,
                UiSliderInputTest::label, changed::add);
    }

    private static Component label(int value) {
        return Component.translatable("options.percent_value", value);
    }

    private static KeyEvent key(int code) {
        return new KeyEvent(code, 0, 0);
    }

    private static MouseButtonEvent mouse(double x) {
        return new MouseButtonEvent(x, 25, new MouseButtonInfo(GLFW_MOUSE_BUTTON_LEFT, 0));
    }
}
