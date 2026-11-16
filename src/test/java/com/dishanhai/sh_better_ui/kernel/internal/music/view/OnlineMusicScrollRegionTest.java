// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;
class OnlineMusicScrollRegionTest {
    @Test void canvasWheelAndKeyboardScrollButNormalClicksPassThrough() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(10,20,200,120,Component.literal("Tracks"),offset::set);
        region.configure(0,20,5);
        assertFalse(region.mouseClicked(50,50,0));
        assertTrue(region.mouseScrolled(50,50,-1)); assertEquals(1,offset.get());
        assertFalse(region.mouseScrolled(0,50,-1));
        region.setFocused(true);
        assertTrue(region.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN,0,0)); assertEquals(6,offset.get());
        assertTrue(region.keyPressed(GLFW.GLFW_KEY_END,0,0)); assertEquals(15,offset.get());
        assertFalse(region.mouseScrolled(50,50,-1));
        assertTrue(region.keyPressed(GLFW.GLFW_KEY_HOME,0,0)); assertEquals(0,offset.get());
    }
    @Test void hostCancellationContractOnlyConsumesAnActiveDrag() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(0,0,200,120,Component.literal("Lyrics"),offset::set);
        region.configure(0,30,6);
        com.dishanhai.sh_better_ui.client.gui.scaled.UiCancelableInteraction interaction = region;
        assertFalse(interaction.cancelInteraction()); // ordinary Escape must still close the host
        region.setFocused(true);
        assertTrue(region.mouseClicked(196,12,0));
        region.setFocused(true);
        assertTrue(region.mouseDragged(196,90,0,0,78));
        assertTrue(offset.get() > 0);
        assertTrue(interaction.cancelInteraction());
        assertEquals(0, offset.get());
        assertFalse(region.dragging());
        assertFalse(interaction.cancelInteraction()); // second Escape may close normally
    }
    @Test void railDragReleaseAndCancelAreDistinctAndHiddenRegionsIgnoreInput() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(0,0,200,120,Component.literal("Lyrics"),offset::set);
        region.configure(0,30,6);
        region.setFocused(true);
        assertTrue(region.mouseClicked(196,12,0));
        region.setFocused(false); // native host clears old focus even when it is this same widget
        region.setFocused(true);
        assertTrue(region.dragging());
        assertTrue(region.mouseDragged(196,90,0,0,78)); assertTrue(offset.get()>0);
        assertTrue(region.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0)); assertEquals(0,offset.get());
        region.mouseClicked(196,12,0); region.setFocused(false); region.setFocused(true); region.mouseDragged(196,90,0,0,78);
        int finalOffset=offset.get(); assertTrue(region.mouseReleased(196,90,0));
        region.cancelDrag(); assertEquals(finalOffset,offset.get());
        region.visible=false;
        assertFalse(region.mouseScrolled(80,70,-1)); assertFalse(region.mouseClicked(196,12,0));
        region.visible=true; region.configure(0,3,6);
        assertFalse(region.active); assertFalse(region.mouseScrolled(80,70,-1));
    }
}
