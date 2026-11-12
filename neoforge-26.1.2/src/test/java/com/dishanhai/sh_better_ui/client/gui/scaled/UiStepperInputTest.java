// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class UiStepperInputTest {
    @Test
    void keyboardSelectsOnlyReachableStepsAndHonorsFocus() {
        var selections = new ArrayList<Integer>();
        var state = new UiStepperState(3, 1);
        var stepper = new UiStepper(new Bounds(5, 10, 144, 42), List.of(
                Component.translatable("sh_better_ui.control.stepper.cart"),
                Component.translatable("sh_better_ui.control.stepper.address"),
                Component.translatable("sh_better_ui.control.stepper.review")), state, selections::add);
        var left = new KeyEvent(GLFW_KEY_LEFT, 0, 0);
        var right = new KeyEvent(GLFW_KEY_RIGHT, 0, 0);

        assertFalse(stepper.keyPressed(left));
        stepper.setFocused(true);
        assertTrue(stepper.keyPressed(right));
        assertTrue(selections.isEmpty());
        assertTrue(stepper.keyPressed(left));
        assertTrue(stepper.keyPressed(right));
        assertEquals(List.of(0, 1), selections);
        assertEquals(1, state.currentIndex());
        assertEquals(1, state.lastReachableIndex());
    }
}
