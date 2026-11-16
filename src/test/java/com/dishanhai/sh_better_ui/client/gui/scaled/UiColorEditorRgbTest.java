package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class UiColorEditorRgbTest {
    private static UiColorEditor rgb(ArrayList<Integer> previews, ArrayList<Integer> commits) {
        return new UiColorEditor(new Bounds(0, 0, 180, 112), Component.literal("Theme color"),
                0x1200ff00, 0x340000ff, false, UiPreferences.DEFAULT, previews::add, commits::add);
    }
    @Test void modelEnforcesOpaqueValuesThroughPreviewCancelExternalUpdateAndReset() {
        var previews = new ArrayList<Integer>(); var commits = new ArrayList<Integer>();
        var state = new ColorEditorState(0x1200ff00, 0x340000ff, false, previews::add, commits::add);
        assertEquals(0xff00ff00, state.color()); assertEquals(0xff0000ff, state.defaultColor());
        assertThrows(IllegalArgumentException.class, () -> state.preview(3, .2f));
        assertThrows(IllegalArgumentException.class, () -> state.rampColor(3, .2f));
        assertFalse(state.editing());
        state.preview(0, .6f); state.cancel(); assertEquals(0xff00ff00, state.color());
        state.preview(1, .4f); state.commit();
        state.setValue(0x00123456); assertEquals(0xff123456, state.color()); assertEquals(state.color(), state.committedColor());
        state.reset(); assertEquals(0xff0000ff, state.color());
        assertTrue(previews.stream().allMatch(color -> color >>> 24 == 255));
        assertTrue(commits.stream().allMatch(color -> color >>> 24 == 255));
    }
    @Test void rgbMinimumFactoryAndLegacyArgbContractAreDistinct() {
        var widget = rgb(new ArrayList<>(), new ArrayList<>());
        assertFalse(widget.alphaEditable()); assertEquals(3, widget.channelCount());
        assertEquals("#00FF00", widget.formatColor(widget.state().color()));
        assertNotNull(ShBetterUiApi.controls().colorEditor(new Bounds(0, 0, 180, 112), Component.empty(),
                0, 0, false, UiPreferences.DEFAULT, c -> {}, c -> {}));
        assertThrows(IllegalArgumentException.class, () -> new UiColorEditor(new Bounds(0, 0, 180, 111),
                Component.empty(), 0, 0, false, UiPreferences.DEFAULT, c -> {}, c -> {}));
        assertThrows(IllegalArgumentException.class, () -> new UiColorEditor(new Bounds(0, 0, 180, 112),
                Component.empty(), 0, 0, UiPreferences.DEFAULT, c -> {}, c -> {}));
        var argb = new UiColorEditor(new Bounds(0, 0, 180, 132), Component.empty(), 0x1200ff00, 0,
                UiPreferences.DEFAULT, c -> {}, c -> {});
        assertTrue(argb.alphaEditable()); assertEquals(4, argb.channelCount());
        assertEquals(0x1200ff00, argb.state().color()); assertEquals("#1200FF00", argb.formatColor(argb.state().color()));
    }
    @Test void keyboardCyclesOnlyThreeChannelsThenResetAndCannotEditOpacity() {
        var previews = new ArrayList<Integer>(); var commits = new ArrayList<Integer>(); var widget = rgb(previews, commits);
        widget.setFocused(true);
        for (int i = 0; i < 3; i++) widget.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        assertEquals(3, widget.selectedChannel());
        assertFalse(widget.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));
        widget.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); assertEquals(0xff0000ff, widget.state().color());
        widget.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0); assertEquals(0, widget.selectedChannel());
        widget.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT);
        assertTrue(widget.hasPendingChange()); widget.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        assertEquals(0xff0000ff, widget.state().color()); assertEquals(1, commits.size());
    }
    @Test void pointerUsesRgbResetPositionAndKeepsCancelAndCommitOpaque() {
        var previews = new ArrayList<Integer>(); var commits = new ArrayList<Integer>(); var widget = rgb(previews, commits);
        assertFalse(widget.mouseClicked(110, 90, 0)); // removed alpha ramp, before reset
        assertTrue(widget.mouseClicked(110, 73, 0)); // brightness
        widget.setFocused(false); widget.setFocused(true); // native click focus transfer
        widget.setFocused(false); assertEquals(0xff00ff00, widget.state().color());
        widget.mouseClicked(110, 73, 0); widget.mouseReleased(110, 73, 0); assertEquals(1, commits.size());
        assertTrue(widget.mouseClicked(110, 98, 0)); assertEquals(3, widget.selectedChannel());
        widget.mouseReleased(110, 98, 0); assertEquals(0xff0000ff, widget.state().color());
        assertEquals(2, commits.size()); assertTrue(previews.stream().allMatch(c -> c >>> 24 == 255));
        widget.setValue(0x00334455); assertEquals(0xff334455, widget.state().color());
    }
    @Test void resetNarrationUsesRgbHexWithoutAnAlphaChannel() {
        var widget = rgb(new ArrayList<>(), new ArrayList<>()); widget.setFocused(true);
        for (int i = 0; i < 3; i++) widget.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        var spoken = new StringBuilder();
        widget.updateWidgetNarration(new net.minecraft.client.gui.narration.NarrationElementOutput() {
            public void add(net.minecraft.client.gui.narration.NarratedElementType type, net.minecraft.client.gui.narration.NarrationThunk<?> thunk) { thunk.getText(spoken::append); }
            public net.minecraft.client.gui.narration.NarrationElementOutput nest() { return this; }
        });
        assertTrue(spoken.toString().contains("#0000FF")); assertTrue(spoken.toString().contains("#00FF00"));
        assertFalse(spoken.toString().contains("#FF0000FF"));
        assertFalse(spoken.toString().contains(Component.translatable("sh_better_ui.color_editor.alpha").getString()));
    }
}
