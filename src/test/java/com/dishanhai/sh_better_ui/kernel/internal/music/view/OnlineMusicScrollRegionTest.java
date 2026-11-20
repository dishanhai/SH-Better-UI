// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;
class OnlineMusicScrollRegionTest {
    @Test void coastingRowClickKeepsItsPaintedPositionUntilRelease() {
        for (String gesture : new String[]{"inside", "outside", "rebound"}) {
            var clock = new AtomicLong(1000);
            var activations = new AtomicInteger();
            var screen = new com.dishanhai.sh_better_ui.client.api.UiScreenBuilder(Component.empty()).backButton(false).build();
            screen.width = 320;
            screen.height = 240;
            var clip = new Bounds(20, 20, 240, 120);
            var region = new OnlineMusicScrollRegion(20, 20, 240, 120, Component.literal("Search"),
                    ignored -> {}, clock::get, () -> false);
            var row = new MusicTextControl(new Bounds(20, 68, 220, 24), Component.literal("Track"),
                    UiPreferences.DEFAULT, activations::incrementAndGet, () -> {});
            region.configure(4, 40, 5);
            region.pixelRows(28, shift -> row.setY(68 - shift));
            region.setOnBrowse(() -> screen.setFocused(region));
            screen.add(region);
            screen.addClipped(row, () -> clip);
            assertTrue(region.mouseScrolled(50, 60, -1));
            for (int i = 0; i < 20 && region.rowShift() == 0; i++) {
                clock.addAndGet(8);
                region.tick();
            }
            assertSame(region, screen.getFocused());
            assertTrue(region.rowShift() > 0, "wheel motion must reach a partial row");
            int paintedY = row.getY();
            double sampled = region.viewportOffset();
            double y = paintedY + 12;
            assertTrue(screen.mouseClicked(50, y, 0));
            assertSame(row, screen.getFocused());
            assertTrue(row.isArmed());
            assertEquals(paintedY, row.getY());
            clock.addAndGet(200);
            region.tick();
            assertEquals(sampled, region.viewportOffset());
            assertEquals(0, activations.get(), "mouse-down must not play a track");
            boolean outside = gesture.equals("outside");
            if (gesture.equals("rebound")) row.rebind(Component.literal("Replacement track"));
            screen.mouseReleased(outside ? 280 : 50, y, 0);
            assertEquals(gesture.equals("inside") ? 1 : 0, activations.get(), gesture);
        }
    }

    @Test void realFullscreenHostCommitsReleaseOutsideChildInsteadOfLaterRollingBack() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(0,0,200,120,Component.literal("Lyrics"),offset::set);
        region.configure(0,30,6);
        var host = new com.dishanhai.sh_better_ui.client.gui.scaled.UiFullscreenPreview(null, Component.literal("Music"),
                com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT, (bounds, add) -> null);
        host.setFocused(region);
        assertTrue(region.mouseClicked(196,12,0));
        host.setFocused(region); // exercise the actual native false -> true focus dispatch
        host.setDragging(true);
        assertTrue(host.mouseDragged(400,400,0,204,388));
        int end = offset.get(); assertTrue(end > 0);
        assertTrue(host.mouseReleased(400,400,0)); // outside the widget and host children
        assertFalse(region.dragging()); assertFalse(host.isDragging());
        host.setFocused(null);
        assertEquals(end, offset.get()); // completed drag must not roll back on focus loss
    }
    @Test void canvasWheelAndKeyboardScrollButNormalClicksPassThrough() {
        var offset = new AtomicInteger();
        var region = new OnlineMusicScrollRegion(10,20,200,120,Component.literal("Tracks"),offset::set, () -> 0L, () -> true);
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
    @Test void bottomLoadsOnceAndCannotLoadAfterAReverseOrRouteChange() {
        var offset = new AtomicInteger();
        var requests = new AtomicInteger();
        var ms = new java.util.concurrent.atomic.AtomicLong(1000);
        var region = new OnlineMusicScrollRegion(0,0,200,120,Component.literal("Tracks"),
                offset::set, ms::get, () -> false);
        region.onEnd(() -> requests.get() == 0, requests::incrementAndGet);
        region.configure(0,20,5);
        assertTrue(region.mouseScrolled(50,50,-40));
        assertEquals(0, requests.get());
        region.mouseScrolled(50,50,40);
        for (int i=0;i<60;i++) { ms.addAndGet(8); region.tick(); }
        assertEquals(0, requests.get());
        region.mouseScrolled(50,50,-40);
        for (int i=0;i<60;i++) { ms.addAndGet(8); region.tick(); }
        assertEquals(15, offset.get());
        assertEquals(1, requests.get());
        assertFalse(region.mouseScrolled(50,50,-3));
        assertEquals(1, requests.get());
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
