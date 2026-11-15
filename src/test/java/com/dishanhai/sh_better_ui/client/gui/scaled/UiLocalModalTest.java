package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController.State.*;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：核对局部模态的时间、几何、冲突和按键归属。 */
class UiLocalModalTest {
    private static final Bounds SOURCE = new Bounds(10, 10, 40, 22);
    private static final Bounds TARGET = new Bounds(10, 10, 140, 22);
    private static final Bounds VIEWPORT = new Bounds(0, 0, 240, 120);
    private static final UiExpansionController.Config CONFIG = UiExpansionController.Config.DEFAULT;
    private static final UiExpansionController.Placement RIGHT = UiExpansionController.anchored(140, 22, 0, 0);
    private static final int LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private final Host host = new Host();
    private final UiButton source = host.button(SOURCE.x(), SOURCE.y());
    private long now;
    private double scale = 2;
    private final UiLocalModal modal = new UiLocalModal(host, UiPreferences.DEFAULT, () -> scale, () -> now);
    private int confirmations;

    @AfterEach
    void clearModal() {
        modal.interrupt();
        UiPointerCapture.release(host, LEFT, 0, 0);
    }

    @Test
    void advancesByElapsedTimeAndOnlyConfirmsOnce() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, false, 0);
        assertEquals(ACTIVATING, expansion.state());
        assertFalse(expansion.confirm(50));
        assertTrue(expansion.advance(100).width() > SOURCE.width());
        assertEquals(EXPANDING, expansion.state());
        assertEquals(TARGET, expansion.advance(10_000));
        assertEquals(ACTIVE, expansion.state());
        assertTrue(expansion.confirm(10_000));
        assertFalse(expansion.confirm(10_001));
        assertFalse(expansion.cancel(10_001));
        assertEquals(SOURCE, expansion.advance(11_000));
        assertEquals(IDLE, expansion.state());
    }

    @Test
    void expansionStartsWithTheLiftAndRetainsSubUnitMotionUntilTheEndpoint() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, false, 0);
        expansion.advance(8);
        assertEquals(ACTIVATING, expansion.state());
        assertTrue(expansion.outline().getW() > 40f && expansion.outline().getW() < 40.5f);
        assertTrue(UiExpansionController.contains(expansion.outline(), 11, 50.2, 21));
        assertFalse(UiExpansionController.contains(expansion.outline(), 11, 50.5, 21));
        expansion.advance(215);
        float almostOpen = expansion.outline().getW();
        assertTrue(almostOpen > 139.5f && almostOpen < 140f);
        expansion.advance(219);
        assertTrue(expansion.outline().getW() > almostOpen);
        assertEquals(EXPANDING, expansion.state());
        assertEquals(TARGET, expansion.advance(220));
        assertEquals(ACTIVE, expansion.state());
    }

    @Test
    void expansionAndCollapseCrossfadeTheSameContents() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, false, 0);
        assertEquals(0f, UiExpansionOverlay.contentOpacity(expansion.progress()));
        expansion.advance(150);
        float incoming = UiExpansionOverlay.contentOpacity(expansion.progress());
        assertTrue(incoming > 0f && incoming < 1f);
        expansion.advance(220);
        assertEquals(1f, UiExpansionOverlay.contentOpacity(expansion.progress()));
        assertTrue(expansion.confirm(220));
        expansion.advance(380);
        assertEquals(COLLAPSING, expansion.state());
        assertEquals(1f, UiExpansionOverlay.contentOpacity(expansion.progress()));
        expansion.advance(450);
        float outgoing = UiExpansionOverlay.contentOpacity(expansion.progress());
        assertTrue(outgoing > 0f && outgoing < 1f);
        expansion.advance(600);
        assertEquals(0f, UiExpansionOverlay.contentOpacity(expansion.progress()));
        assertEquals(IDLE, expansion.state());
    }

    @Test
    void cancellationReversesFromTheLastVisibleFrame() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, false, 0);
        Bounds shown = expansion.advance(170);
        var outline = expansion.outline();
        assertTrue(shown.width() > SOURCE.width() && shown.width() < TARGET.width());
        assertTrue(expansion.cancel(195));
        assertEquals(shown, expansion.advance(195));
        assertEquals(outline, expansion.outline());
        assertEquals(COLLAPSING, expansion.state());
        assertTrue(expansion.advance(260).width() < shown.width());
        assertEquals(SOURCE, expansion.advance(1_000));
    }

    @Test
    void interruptDuringAFrameDropsTheSessionAndRejectsLateConfirmation() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, true, 0);
        assertEquals(TARGET, expansion.advance(0));
        expansion.interrupt();
        assertEquals(INTERRUPTED, expansion.state());
        assertFalse(expansion.confirm(0));
        assertFalse(expansion.cancel(0));
        assertEquals(SOURCE, expansion.advance(5_000));
    }

    @Test
    void geometryUsesTheVisibleRoundedOutlineAndClampsAtViewportEdges() {
        var outline = new UiExpansionController(SOURCE, TARGET, CONFIG, true, 0);
        outline.advance(0);
        assertTrue(UiExpansionController.contains(outline.outline(), 11, 100, 20));
        assertFalse(UiExpansionController.contains(outline.outline(), 11, 10, 10));
        assertFalse(UiExpansionController.contains(outline.outline(), 11, 150, 21));
        assertFalse(UiExpansionController.contains(outline.outline(), 11, 200, 21));
        var viewport = new Bounds(5, 5, 200, 100);
        var edgeSource = new Bounds(170, 73, 30, 20);
        assertEquals(new Bounds(85, 55, 120, 50),
                UiExpansionController.anchored(120, 50, 0, 0).place(edgeSource, viewport));
        assertTrue(UiExpansionController.overlaps(TARGET, new Bounds(100, 0, 40, 24)));
        assertFalse(UiExpansionController.overlaps(TARGET, new Bounds(150, 10, 40, 22)));
    }

    @Test
    void pointerKeepsItsPressOwnerAndDrainsAfterInterruption() {
        var router = new UiLocalInputRouter<>(List.of("under", "other"), (target, x, y) -> x >= 0);
        assertNull(router.dragTarget(0));
        assertEquals("other", router.hit(0, 0));
        router.capture(0, "slide");
        assertEquals("slide", router.dragTarget(0));
        assertEquals("slide", router.forget(0));
        assertNull(router.forget(0));
        router.capture(1, "slide");
        router.capture(0, "other");
        var drained = new ArrayList<Integer>();
        router.drain("slide", drained::add);
        assertEquals(List.of(1), drained);
        assertNull(router.forget(1));
        assertEquals("other", router.forget(0));
    }

    @Test
    void actualSlideRoutesOnlyTheShownRegionAndConfirmsOnceAfterLeavingIt() {
        UiButton neighbor = host.button(60, 10);
        neighbor.setSelected(true);
        assertTrue(openSlide(source, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
        assertFalse(modal.mouseClicked(90, 21, LEFT));
        assertEquals(SOURCE.x(), source.getX());
        frame(320);

        assertTrue(modal.mouseScrolled(90, 21, 0, 1));
        assertFalse(modal.mouseScrolled(90, 40, 0, 1));
        assertTrue(modal.blocksPointer(90, 21));
        assertFalse(modal.blocksPointer(90, 40));
        assertFalse(modal.filterPointer(new UiMouseState(90, 21, 180, 42, true, false, true, 1)).available());
        assertTrue(modal.mouseClicked(90, 21, LEFT));
        assertTrue(modal.mouseReleased(170, 60, LEFT));
        assertEquals(0, confirmations);

        assertTrue(modal.mouseClicked(21, 21, LEFT));
        assertTrue(modal.mouseDragged(153, -20, LEFT));
        assertTrue(modal.mouseReleased(155, -20, LEFT));
        assertEquals(1, confirmations);
        assertTrue(modal.mouseClicked(21, 21, LEFT));
        assertTrue(modal.mouseReleased(155, -20, LEFT));
        assertEquals(1, confirmations);
        frame(2_000);
        assertFalse(modal.mouseClicked(90, 21, LEFT));
        assertTrue(neighbor.isSelected());
        assertEquals(60, neighbor.getX());
        assertEquals(SOURCE.width(), source.getWidth());
        assertSame(source, host.getFocused());
    }

    @Test
    void nativeDragReachesItsReleaseAfterEnteringTheLocalModal() {
        assertTrue(openSlide(source, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
        frame(320);
        assertFalse(modal.mouseClicked(170, 21, LEFT));
        host.setDragging(true);

        assertTrue(modal.blocksPointer(90, 21));
        assertFalse(modal.mouseDragged(90, 21, LEFT));
        assertFalse(modal.mouseReleased(90, 21, LEFT));
        assertTrue(host.isDragging());
        assertEquals(0, confirmations);
    }

    @Test
    void oneButtonsLocalPressDoesNotStealAnotherButtonsNativeDrag() {
        int right = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        assertTrue(openSlide(source, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
        frame(320);
        assertFalse(modal.mouseClicked(170, 21, LEFT));
        host.setDragging(true);
        assertTrue(modal.mouseClicked(90, 21, right));

        assertTrue(modal.mouseDragged(170, 21, right));
        assertFalse(modal.mouseDragged(90, 21, LEFT));
        assertTrue(modal.mouseReleased(170, 21, right));
        assertFalse(modal.mouseReleased(90, 21, LEFT));
        assertTrue(host.isDragging());
        assertFalse(modal.mouseReleased(90, 21, right));
    }

    @Test
    void localRightClickPreservesTheFocusedNativeDragAndItsRelease() {
        var screen = new UiScreenBuilder(Component.empty()).backButton(false).build();
        screen.width = VIEWPORT.width();
        screen.height = VIEWPORT.height();
        var trigger = screen.add(new UiButton(SOURCE.x(), SOURCE.y(), SOURCE.width(), SOURCE.height(),
                Component.empty(), ignored -> {}, UiPreferences.DEFAULT, false));
        var nativeControl = screen.add(new DragWidget());
        var local = new UiLocalModal(screen, UiPreferences.DEFAULT, () -> scale, () -> now);
        int right = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        try {
            assertTrue(local.open(trigger, VIEWPORT, RIGHT, CONFIG, session -> new UiLocalModal.Content() {
                @Override
                public void render(GuiGraphics graphics, UiLocalModal.Frame frame, int x, int y, float partialTick) {}

                @Override
                public boolean mousePressed(double x, double y, int button, long time) {
                    return button == right;
                }
            }, () -> {}));
            now = 320;
            local.prepareRender();
            assertFalse(local.mouseClicked(170, 21, LEFT));
            assertTrue(screen.mouseClicked(170, 21, LEFT));
            assertTrue(local.mouseClicked(90, 21, right));
            assertSame(nativeControl, screen.getFocused());

            assertFalse(local.mouseDragged(90, 21, LEFT));
            assertTrue(screen.mouseDragged(90, 21, LEFT, -80, 0));
            assertTrue(local.mouseReleased(170, 21, right));
            assertFalse(local.mouseReleased(90, 21, LEFT));
            assertTrue(screen.mouseReleased(90, 21, LEFT));
            assertFalse(screen.isDragging());
            assertEquals(1, nativeControl.drags);
            assertEquals(1, nativeControl.releases);
        } finally {
            local.interrupt();
            UiPointerCapture.release(screen, right, 0, 0);
        }
    }

    @Test
    void aShortReleaseCancelsAndReopeningUsesTheConfiguredKeyboardThreshold() {
        var slide = new UiSlideConfirm.LocalConfig(0.75, 400);
        assertTrue(openSlide(source, RIGHT, slide));
        frame(330);
        assertTrue(modal.mouseClicked(21, 21, LEFT));
        assertTrue(modal.mouseReleased(100, 21, LEFT));
        assertEquals(0, confirmations);
        assertFalse(openSlide(source, RIGHT, slide));
        frame(1_000);
        assertTrue(openSlide(source, RIGHT, slide));
        frame(1_330);
        assertTrue(modal.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        now += 150;
        assertTrue(modal.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        now += 150;
        assertTrue(modal.keyReleased(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertFalse(modal.keyReleased(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertEquals(1, confirmations);
    }

    @Test
    void rejectsOverlappingReservationsAndAllowsDisjointExpansions() {
        var overlapping = host.button(170, 10);
        var disjoint = host.button(10, 60);
        assertTrue(openSlide(source, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
        assertFalse(openSlide(overlapping, UiExpansionController.anchored(140, 22, 1, 0),
                UiSlideConfirm.LocalConfig.DEFAULT));
        assertTrue(openSlide(disjoint, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
        frame(350);
        assertTrue(modal.blocksPointer(130, 21));
        assertTrue(modal.blocksPointer(130, 71));
        assertFalse(modal.blocksPointer(170, 21));
    }

    @Test
    void escapeBeforeExpansionCompletesCannotPassItsPressOrReleaseToTheScreen() {
        assertTrue(openSlide(source, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
        frame(175);
        assertTrue(modal.blocksPointer(75, 21));
        assertFalse(modal.blocksPointer(149, 21));
        assertTrue(modal.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        frame(900);
        assertFalse(modal.blocksPointer(75, 21));
        assertTrue(modal.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(modal.keyReleased(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertNull(UiLocalModal.current(host));
        assertEquals(0, confirmations);
    }

    @Test
    void focusLossScaleChangeHideAndRemovalDrainAnInFlightPointer() {
        for (Runnable interrupt : List.<Runnable>of(
                () -> scale = 0,
                () -> scale = 3,
                () -> source.visible = false,
                () -> host.remove(source),
                () -> UiLocalModal.interrupt(host))) {
            assertTrue(openSlide(source, RIGHT, UiSlideConfirm.LocalConfig.DEFAULT));
            frame(now + 330);
            assertTrue(modal.mouseClicked(21, 21, LEFT));
            assertTrue(modal.mouseDragged(160, 21, LEFT));
            interrupt.run();
            assertNull(UiLocalModal.current(host));
            assertFalse(modal.blocksPointer(90, 21));
            assertTrue(UiPointerCapture.release(host, LEFT, 160, 21));
            assertFalse(UiPointerCapture.release(host, LEFT, 160, 21));
            assertEquals(0, confirmations);
            scale = 2;
            source.visible = true;
            if (!host.children().contains(source)) host.add(source);
        }
    }

    @Test
    void callbackFailureStillRestoresInputAndRejectsASecondRelease() {
        var failure = new IllegalStateException("test callback");
        assertTrue(modal.openSlide(source, VIEWPORT, RIGHT, CONFIG, UiSlideConfirm.LocalConfig.DEFAULT,
                Component.empty(), () -> {
                    confirmations++;
                    throw failure;
                }));
        frame(330);
        assertTrue(modal.mouseClicked(21, 21, LEFT));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> modal.mouseReleased(160, 21, LEFT)));
        assertNull(UiLocalModal.current(host));
        assertFalse(modal.mouseReleased(160, 21, LEFT));
        assertEquals(1, confirmations);
    }

    private void frame(long time) {
        now = time;
        modal.prepareRender();
    }

    private boolean openSlide(UiButton from, UiExpansionController.Placement placement,
                              UiSlideConfirm.LocalConfig config) {
        return modal.openSlide(from, VIEWPORT, placement, CONFIG, config, Component.empty(), () -> confirmations++);
    }

    private static final class DragWidget extends AbstractWidget {
        private int drags;
        private int releases;

        private DragWidget() {
            super(155, 10, 40, 22, Component.empty());
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return button == LEFT && isMouseOver(x, y);
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            drags++;
            return true;
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            releases++;
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    private static final class Host extends Screen {
        private Host() {
            super(Component.empty());
            width = VIEWPORT.width();
            height = VIEWPORT.height();
        }

        private UiButton button(int x, int y) {
            var button = new UiButton(x, y, SOURCE.width(), SOURCE.height(), Component.empty(), ignored -> {},
                    UiPreferences.DEFAULT, false);
            add(button);
            return button;
        }

        private void add(UiButton button) { addRenderableWidget(button); }

        private void remove(UiButton button) { removeWidget(button); }
    }
}
