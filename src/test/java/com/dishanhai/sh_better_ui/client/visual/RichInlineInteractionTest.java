// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Button;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Icon;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Slider;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Symbol;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class RichInlineInteractionTest {
    private static final RichObjectNode BUTTON = node("button", new Button("Press"));
    private static final RichObjectNode SLIDER = node("slider", new Slider("Level", 0, 100, 40, 10));
    private static final RichObjectNode ICON = node("icon", new Icon(Symbol.CHECK));
    private static final int LEFT = GLFW_MOUSE_BUTTON_LEFT;
    private static final int RIGHT = GLFW_MOUSE_BUTTON_RIGHT;

    @Test void clippingOcclusionAndScopeChangesDoNotActivateTheUnderlyingNode() {
        var geometry = new Geometry();
        var interaction = new RichInlineInteraction(geometry, () -> 0);
        var calls = new AtomicInteger();
        interaction.onButton(BUTTON.id(), calls::incrementAndGet);

        geometry.clip = 20;
        assertFalse(interaction.press(30, 8, LEFT));
        assertTrue(interaction.press(16, 8, LEFT));
        assertTrue(interaction.release(30, 8, LEFT));
        assertEquals(0, calls.get());

        geometry.front = new Rect(10, 0, 10, 16);
        assertFalse(interaction.press(16, 8, LEFT));
        assertTrue(interaction.press(5, 8, LEFT));
        assertTrue(interaction.press(5, 8, LEFT), "repeated forwarding is still the same press");
        assertTrue(interaction.release(5, 8, LEFT));
        assertFalse(interaction.release(5, 8, LEFT));
        assertEquals(1, calls.get());

        assertTrue(interaction.press(5, 8, LEFT));
        geometry.clip = 0;
        assertTrue(interaction.release(5, 8, LEFT));
        assertEquals(1, calls.get());
        assertFalse(interaction.hasFocus());

        geometry.clip = 20;
        interaction.clear();
        assertFalse(interaction.press(5, 8, LEFT));
        assertThrows(IllegalStateException.class, () -> interaction.onButton(BUTTON.id(), calls::incrementAndGet));
    }

    @Test void sliderPreviewCommitsOnceOrReturnsToItsPreviousValueOnCancel() {
        var interaction = new RichInlineInteraction(new Geometry(), () -> 0);
        var values = new ArrayList<Integer>();
        interaction.onSlider(SLIDER.id(), values::add);
        assertEquals(40, interaction.state(SLIDER).sliderValue());

        assertTrue(interaction.press(104, 8, LEFT));
        assertTrue(interaction.drag(160, 50, LEFT));
        assertEquals(100, interaction.state(SLIDER).sliderValue());
        assertTrue(values.isEmpty());
        assertTrue(interaction.keyPressed(GLFW_KEY_ESCAPE, 0));
        assertTrue(interaction.release(160, 50, LEFT));
        assertEquals(40, interaction.state(SLIDER).sliderValue());
        assertTrue(values.isEmpty());

        assertTrue(interaction.press(104, 8, LEFT));
        assertTrue(interaction.drag(160, 50, LEFT));
        assertTrue(interaction.release(160, 50, LEFT));
        assertFalse(interaction.release(160, 50, LEFT));
        assertEquals(List.of(100), values);
        assertEquals(100, interaction.state(SLIDER).sliderValue());
    }

    @Test void visibleFocusOrderLetsTabLeaveTheViewAndIgnoresButtonKeyRepeat() {
        var interaction = new RichInlineInteraction(new Geometry(), () -> 0);
        var calls = new AtomicInteger();
        interaction.onButton(BUTTON.id(), calls::incrementAndGet);
        assertTrue(interaction.focusFirst(false));
        assertTrue(interaction.state(BUTTON).focused());
        assertTrue(interaction.keyPressed(GLFW_KEY_ENTER, 0));
        assertTrue(interaction.keyPressed(GLFW_KEY_ENTER, 0));
        assertEquals(1, calls.get());
        assertTrue(interaction.keyReleased(GLFW_KEY_ENTER));
        assertTrue(interaction.keyPressed(GLFW_KEY_ENTER, 0));
        assertEquals(2, calls.get());

        assertTrue(interaction.keyPressed(GLFW_KEY_TAB, 0));
        assertTrue(interaction.state(SLIDER).focused());
        assertTrue(interaction.keyPressed(GLFW_KEY_LEFT, 0));
        assertEquals(30, interaction.state(SLIDER).sliderValue());
        assertFalse(interaction.keyPressed(GLFW_KEY_TAB, 0));
        assertFalse(interaction.hasFocus());
        assertTrue(interaction.focusFirst(true));
        assertTrue(interaction.state(SLIDER).focused());
    }

    @Test void dropPreviewsOnlyVisibleTargetsAndCommitsOnMatchingRelease() {
        var geometry = new Geometry();
        var interaction = new RichInlineInteraction(geometry, () -> 0);
        var drops = new ArrayList<RichInlineInteraction.Drop>();
        interaction.draggable(ICON.id());
        interaction.onDrop(BUTTON.id(), drops::add);
        assertTrue(interaction.press(150, 8, LEFT));
        assertTrue(interaction.drag(10, 8, LEFT));
        assertTrue(interaction.state(BUTTON).dropTarget());
        assertTrue(drops.isEmpty());
        assertFalse(interaction.release(10, 8, RIGHT));
        assertTrue(interaction.release(10, 8, LEFT));
        assertEquals(List.of(new RichInlineInteraction.Drop(ICON.id(), BUTTON.id())), drops);
        assertFalse(interaction.state(BUTTON).dropTarget());

        assertTrue(interaction.press(150, 8, LEFT));
        assertTrue(interaction.drag(10, 8, LEFT));
        geometry.front = new Rect(0, 0, 16, 16);
        assertTrue(interaction.release(10, 8, LEFT));
        assertEquals(1, drops.size());
    }

    @Test void boundGesturesAreDispatchedOncePerRelease() {
        var time = new AtomicLong();
        var interaction = new RichInlineInteraction(new Geometry(), time::get);
        var calls = new AtomicInteger();
        var gestures = new ArrayList<RichInlineInteraction.Gesture>();
        interaction.onButton(BUTTON.id(), calls::incrementAndGet);
        interaction.onGesture(BUTTON.id(), gestures::add);

        assertTrue(interaction.press(12, 8, LEFT));
        time.set(50);
        assertTrue(interaction.release(12, 8, LEFT));
        time.set(100);
        assertTrue(interaction.press(12, 8, LEFT));
        assertTrue(interaction.release(12, 8, LEFT));
        time.set(120);
        assertTrue(interaction.press(12, 8, LEFT));
        time.set(660);
        assertTrue(interaction.release(12, 8, LEFT));
        assertTrue(interaction.press(12, 8, RIGHT));
        assertTrue(interaction.release(12, 8, RIGHT));
        assertEquals(1, calls.get());
        assertEquals(List.of(RichInlineInteraction.Gesture.DOUBLE_CLICK, RichInlineInteraction.Gesture.LONG_PRESS,
                RichInlineInteraction.Gesture.SECONDARY), gestures);
    }

    private static RichObjectNode node(String name, RichObjectNode.Content content) {
        return new RichObjectNode(new UiId("test", name), content, 1, 1, 16, name);
    }

    private static final class Geometry implements RichInlineInteraction.Geometry {
        private final List<Fragment> fragments = List.of(new Fragment(BUTTON, "", new Rect(0, 0, 40, 16)),
                new Fragment(SLIDER, "", new Rect(100, 0, 40, 16)),
                new Fragment(ICON, "", new Rect(148, 0, 16, 16)));
        private int clip = 180;
        private Rect front;

        @Override public Optional<Fragment> hit(double x, double y) {
            if (x < 0 || x >= clip || front != null && front.contains(x, y)) return Optional.empty();
            return fragments.stream().filter(fragment -> fragment.bounds().contains(x, y)).findFirst();
        }

        @Override public List<Fragment> objects() {
            return fragments.stream().filter(fragment -> fragment.bounds().x() < clip).toList();
        }

        @Override public double localX(double x, double y) { return x; }
    }
}
