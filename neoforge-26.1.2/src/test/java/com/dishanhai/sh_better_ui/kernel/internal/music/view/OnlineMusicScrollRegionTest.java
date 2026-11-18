// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;
class OnlineMusicScrollRegionTest {
    private com.dishanhai.sh_better_ui.client.api.NativeTestClient clientFixture;
    @org.junit.jupiter.api.BeforeEach void installNativeClient() throws Exception {
        clientFixture = com.dishanhai.sh_better_ui.client.api.NativeTestClient.install();
    }
    @org.junit.jupiter.api.AfterEach void restoreNativeClient() throws Exception {
        if (clientFixture != null) clientFixture.close();
    }

    private static net.minecraft.client.input.MouseButtonEvent mouse(double x, double y, int button) {
        return new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
    }
    @Test void realFullscreenHostCommitsReleaseOutsideChildInsteadOfLaterRollingBack() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(0,0,200,120,Component.literal("Lyrics"),offset::set);
        region.configure(0,30,6);
        var host = com.dishanhai.sh_better_ui.client.api.ShBetterUiApi.screen(Component.literal("Music")).build();
        host.setFocused(region);
        assertTrue(region.mouseClicked(mouse(196,12,0), false));
        host.setFocused(region); // exercise the actual native false -> true focus dispatch
        host.setDragging(true);
        assertTrue(host.mouseDragged(mouse(400,400,0),204,388));
        int end = offset.get(); assertTrue(end > 0);
        assertTrue(host.mouseReleased(mouse(400,400,0))); // outside the widget and host children
        assertFalse(region.dragging()); assertFalse(host.isDragging());
        host.setFocused(null);
        assertEquals(end, offset.get()); // completed drag must not roll back on focus loss
    }
    @Test void canvasWheelAndKeyboardScrollButNormalClicksPassThrough() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(10,20,200,120,Component.literal("Tracks"),offset::set);
        region.configure(0,20,5);
        assertFalse(region.mouseClicked(mouse(50,50,0), false));
        assertTrue(region.mouseScrolled(50,50,-1)); assertEquals(1,offset.get());
        assertFalse(region.mouseScrolled(0,50,-1));
        region.setFocused(true);
        assertTrue(region.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_PAGE_DOWN,0,0))); assertEquals(6,offset.get());
        assertTrue(region.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_END,0,0))); assertEquals(15,offset.get());
        assertFalse(region.mouseScrolled(50,50,-1));
        assertTrue(region.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_HOME,0,0))); assertEquals(0,offset.get());
    }
    @Test void hostCancellationContractOnlyConsumesAnActiveDrag() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(0,0,200,120,Component.literal("Lyrics"),offset::set);
        region.configure(0,30,6);
        com.dishanhai.sh_better_ui.client.gui.scaled.UiCancelableInteraction interaction = region;
        assertFalse(interaction.cancelInteraction()); // ordinary Escape must still close the host
        region.setFocused(true);
        assertTrue(region.mouseClicked(mouse(196,12,0), false));
        region.setFocused(true);
        assertTrue(region.mouseDragged(mouse(196,90,0),0,78));
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
        assertTrue(region.mouseClicked(mouse(196,12,0), false));
        region.setFocused(false); // native host clears old focus even when it is this same widget
        region.setFocused(true);
        assertTrue(region.dragging());
        assertTrue(region.mouseDragged(mouse(196,90,0),0,78)); assertTrue(offset.get()>0);
        assertTrue(region.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0))); assertEquals(0,offset.get());
        region.mouseClicked(mouse(196,12,0), false); region.setFocused(false); region.setFocused(true); region.mouseDragged(mouse(196,90,0),0,78);
        int finalOffset=offset.get(); assertTrue(region.mouseReleased(mouse(196,90,0)));
        region.cancelDrag(); assertEquals(finalOffset,offset.get());
        region.visible=false;
        assertFalse(region.mouseScrolled(80,70,-1)); assertFalse(region.mouseClicked(mouse(196,12,0), false));
        region.visible=true; region.configure(0,3,6);
        assertFalse(region.active); assertFalse(region.mouseScrolled(80,70,-1));
    }
}
