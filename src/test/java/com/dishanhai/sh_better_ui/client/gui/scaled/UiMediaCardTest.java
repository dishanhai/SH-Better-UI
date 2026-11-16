package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UiMediaCardTest {
    private UiMediaCard card(AtomicInteger calls) {
        return new UiMediaCard(new Bounds(10, 20, 128, 48), Component.literal("Title"),
                Component.literal("Artist"), UiPreferences.DEFAULT, calls::incrementAndGet);
    }
    @Test void constructsAtMinimumAndRejectsUndersizedCards() {
        assertNotNull(card(new AtomicInteger()));
        for (var area : new Bounds[]{new Bounds(0, 0, 127, 48), new Bounds(0, 0, 128, 47)})
            assertThrows(IllegalArgumentException.class, () -> new UiMediaCard(area, Component.empty(),
                    Component.empty(), UiPreferences.DEFAULT, () -> {}));
    }
    @Test void pointerReleaseActivatesOnceButDoesNotInventPlaybackState() {
        var calls = new AtomicInteger(); var card = card(calls);
        assertTrue(card.mouseClicked(30, 40, 0)); assertEquals(0, calls.get());
        assertTrue(card.mouseReleased(30, 40, 0)); assertEquals(1, calls.get());
        assertFalse(card.mouseReleased(30, 40, 0)); assertFalse(card.isPlaying());
        card.setPlaying(true); card.setSelected(true);
        assertTrue(card.isPlaying()); assertTrue(card.isSelected());
    }
    @Test void outsideDragEscapeFocusLossAndDisableCancelActivation() {
        var calls = new AtomicInteger(); var card = card(calls);
        card.mouseClicked(30, 40, 0); card.mouseDragged(500, 500, 0, 1, 1); card.mouseReleased(30, 40, 0);
        card.mouseClicked(30, 40, 0); card.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); card.mouseReleased(30, 40, 0);
        card.mouseClicked(30, 40, 0); card.setFocused(false); card.mouseReleased(30, 40, 0);
        card.mouseClicked(30, 40, 0); card.active = false; card.mouseReleased(30, 40, 0);
        assertEquals(0, calls.get());
    }
    @Test void keyboardRequiresFocusEnabledAndVisible() {
        var calls = new AtomicInteger(); var card = card(calls);
        assertFalse(card.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        card.setFocused(true);
        assertTrue(card.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(card.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertEquals(2, calls.get());
        card.visible = false; assertFalse(card.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        card.visible = true; card.active = false; assertFalse(card.mouseClicked(30, 40, 0));
        assertFalse(card.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
    }
    @Test void canceledGestureCanBeRetriedAndRapidClicksHaveOneCallbackEach() {
        var calls = new AtomicInteger(); var card = card(calls);
        card.mouseClicked(30, 40, 0); card.cancelInteraction(); card.mouseReleased(30, 40, 0);
        for (int i = 0; i < 20; i++) {
            assertTrue(card.mouseClicked(30, 40, 0));
            assertTrue(card.mouseReleased(30, 40, 0));
        }
        assertEquals(20, calls.get());
        assertFalse(card.mouseClicked(30, 40, 1));
        assertFalse(card.mouseReleased(30, 40, 1));
    }
    @Test void narrationContainsFullLabelsActionAndConfirmedSelection() {
        var card = card(new AtomicInteger());
        var spoken = new java.util.EnumMap<net.minecraft.client.gui.narration.NarratedElementType, String>(
                net.minecraft.client.gui.narration.NarratedElementType.class);
        var output = new net.minecraft.client.gui.narration.NarrationElementOutput() {
            public void add(net.minecraft.client.gui.narration.NarratedElementType type,
                    net.minecraft.client.gui.narration.NarrationThunk<?> thunk) {
                var text = new StringBuilder(); thunk.getText(text::append); spoken.put(type, text.toString());
            }
            public net.minecraft.client.gui.narration.NarrationElementOutput nest() { return this; }
        };
        card.updateWidgetNarration(output);
        var title = spoken.get(net.minecraft.client.gui.narration.NarratedElementType.TITLE);
        assertTrue(title.contains("Title")); assertTrue(title.contains("Artist"));
        assertTrue(title.contains(Component.translatable("sh_better_ui.media_card.play").getString()));
        assertFalse(spoken.containsKey(net.minecraft.client.gui.narration.NarratedElementType.HINT));
        card.setPlaying(true); card.setSelected(true); card.setSubtitle(Component.literal("Long artist"));
        card.updateWidgetNarration(output);
        title = spoken.get(net.minecraft.client.gui.narration.NarratedElementType.TITLE);
        assertTrue(title.contains("Long artist"));
        assertTrue(title.contains(Component.translatable("sh_better_ui.media_card.pause").getString()));
        assertEquals(Component.translatable("sh_better_ui.media_card.selected").getString(),
                spoken.get(net.minecraft.client.gui.narration.NarratedElementType.HINT));
    }
}
