package com.dishanhai.sh_better_ui.client.gui.scaled;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class UiColorEditorTest {
    private UiColorEditor editor(ArrayList<Integer> previews, ArrayList<Integer> commits) {
        return new UiColorEditor(new Bounds(10, 20, 180, 132), Component.literal("Color"), 0xffff0000, 0xff00ff00,
                UiPreferences.DEFAULT, previews::add, commits::add);
    }
    @Test void realConstructionRejectsUndersizedGeometry() {
        assertNotNull(editor(new ArrayList<>(), new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> new UiColorEditor(new Bounds(0, 0, 179, 132),
                Component.empty(), 0, 0, UiPreferences.DEFAULT, c -> {}, c -> {}));
        assertThrows(IllegalArgumentException.class, () -> new UiColorEditor(new Bounds(0, 0, 180, 131),
                Component.empty(), 0, 0, UiPreferences.DEFAULT, c -> {}, c -> {}));
    }
    @Test void dragPreviewsClampsAndReleaseOutsideCommitsOnce() {
        var p = new ArrayList<Integer>(); var c = new ArrayList<Integer>(); var w = editor(p, c);
        assertTrue(w.mouseClicked(110, 113, 0)); // alpha ramp
        assertTrue(w.hasPendingChange()); assertTrue(c.isEmpty());
        w.mouseDragged(-500, 500, 0, 0, 0); assertEquals(0, w.state().color() >>> 24);
        assertTrue(w.mouseReleased(999, 999, 0)); assertEquals(255, w.state().color() >>> 24);
        assertEquals(1, c.size()); assertFalse(w.mouseReleased(999, 999, 0));
    }
    @Test void escapeFocusLossDisableCloseAndRetryPreserveCommittedColor() {
        var p = new ArrayList<Integer>(); var c = new ArrayList<Integer>(); var w = editor(p, c);
        w.mouseClicked(110, 113, 0); assertTrue(w.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertEquals(0xffff0000, w.state().color()); assertTrue(c.isEmpty());
        w.setFocused(true); w.mouseClicked(110, 113, 0); w.setFocused(false); w.setFocused(true); w.setFocused(false);
        assertEquals(0xffff0000, w.state().color());
        w.mouseClicked(110, 113, 0); w.active = false; w.mouseReleased(110, 113, 0);
        assertEquals(0xffff0000, w.state().color());
        w.active = true; w.mouseClicked(110, 113, 0); w.mouseReleased(110, 113, 0);
        assertEquals(1, c.size());
        w.mouseClicked(80, 113, 0); w.close(); assertEquals(c.getLast(), w.state().color());
        assertFalse(w.mouseClicked(110, 113, 0));
    }
    @Test void keyboardNavigationCoarseStepEndpointsConfirmAndReset() {
        var p = new ArrayList<Integer>(); var c = new ArrayList<Integer>(); var w = editor(p, c);
        assertFalse(w.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0)); w.setFocused(true);
        assertTrue(w.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT));
        assertEquals(10f / 360, w.state().channel(0), .0001f); assertTrue(c.isEmpty());
        w.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0); w.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        assertEquals(0, w.state().channel(1)); w.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        assertEquals(1, w.state().channel(1));
        w.commitInteraction(); w.setFocused(false); assertEquals(1, c.size());
        w.setFocused(true); for (int i = 0; i < 3; i++) w.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        assertEquals(4, w.selectedChannel()); w.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        assertEquals(0xff00ff00, w.state().color()); assertEquals(2, c.size());
    }
    @Test void resetRequiresReleaseInsideAndRightClickNeverChangesState() {
        var p = new ArrayList<Integer>(); var c = new ArrayList<Integer>(); var w = editor(p, c);
        assertFalse(w.mouseClicked(100, 137, 1)); assertTrue(w.mouseClicked(100, 137, 0));
        w.mouseReleased(900, 137, 0); assertEquals(0xffff0000, w.state().color());
        for (int i = 0; i < 10; i++) { w.mouseClicked(100, 137, 0); w.mouseReleased(100, 137, 0); }
        assertEquals(1, c.size()); assertEquals(0xff00ff00, w.state().color());
    }
    @Test void narrationIncludesChannelValueHexAndUsage() {
        var w = editor(new ArrayList<>(), new ArrayList<>()); var spoken = new StringBuilder();
        w.updateWidgetNarration(new net.minecraft.client.gui.narration.NarrationElementOutput() {
            public void add(net.minecraft.client.gui.narration.NarratedElementType type, net.minecraft.client.gui.narration.NarrationThunk<?> text) { text.getText(spoken::append); }
            public net.minecraft.client.gui.narration.NarrationElementOutput nest() { return this; }
        });
        assertTrue(spoken.toString().contains("Color")); assertTrue(spoken.toString().contains("0°"));
        assertTrue(spoken.toString().contains(Component.translatable("sh_better_ui.color_editor.hue").getString()));
        assertTrue(spoken.toString().contains(Component.translatable("sh_better_ui.color_editor.usage").getString()));
    }
    @Test void nativeFullscreenReclickFocusCyclePreservesCapturedDragAndCommitsOutside() {
        var p = new ArrayList<Integer>(); var c = new ArrayList<Integer>(); var w = editor(p, c);
        var host = new UiFullscreenPreview(null, Component.literal("Colors"), UiPreferences.DEFAULT, (bounds, add) -> null);
        host.setFocused(w);
        assertTrue(w.mouseClicked(110, 113, 0));
        host.setFocused(w); // ContainerEventHandler emits false then true for the already focused widget.
        assertTrue(w.dragging()); assertTrue(w.hasPendingChange());
        host.setDragging(true);
        assertTrue(host.mouseDragged(-500, 500, 0, 0, 0));
        assertEquals(0, w.state().color() >>> 24);
        assertTrue(host.mouseReleased(-500, 500, 0));
        assertFalse(w.dragging()); assertFalse(host.isDragging()); assertEquals(1, c.size());
        host.setFocused(null); assertEquals(c.getLast(), w.state().color());
    }
    @Test void hostEscapeCancellationOnlyConsumesAnActiveTransaction() {
        var w = editor(new ArrayList<>(), new ArrayList<>());
        UiCancelableInteraction interaction = w;
        assertFalse(interaction.cancelInteraction());
        w.mouseClicked(110, 113, 0); assertTrue(interaction.cancelInteraction());
        assertFalse(interaction.cancelInteraction()); assertFalse(interaction.releasePointer(0));
        assertEquals(0xffff0000, w.state().color());
    }
}
