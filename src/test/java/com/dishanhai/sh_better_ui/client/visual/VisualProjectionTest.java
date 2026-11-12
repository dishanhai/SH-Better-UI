// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class VisualProjectionTest {
    @Test
    void coversFractionalEdgesAndClampsToActualTarget() {
        var target = new VisualProjection.Target(151, 101, 100, 50);
        assertEquals(new PixelRect(0, 2, 5, 3), target.pixels(new Rect(-2, 1.25, 5, 1)));
        assertEquals(new PixelRect(150, 100, 1, 1), target.pixels(new Rect(99.9, 49.9, 50, 50)));
        assertEquals(PixelRect.EMPTY, target.pixels(new Rect(101, 0, 1, 1)));
    }

    @Test
    void inheritsClipWithoutChangingTheOriginalEffectArea() {
        var screen = new Rect(0, 0, 200, 100);
        var layout = new AlignmentLayout(screen);
        var parent = layout.node("parent", AlignmentFrame.box(new Rect(20, 10, 50, 30))
                .withClip(new Rect(0, 0, 30, 30)));
        var node = layout.node("child", parent, AlignmentFrame.box(new Rect(20, 0, 30, 20)));
        layout.resolve();
        var view = node.current(PixelAlignment.ONE);
        var projection = new VisualProjection.Target(400, 200, 200, 100)
                .project(view, VisualProjection.BoundsMode.RENDER, screen);
        assertEquals(view.renderBounds(), projection.area());
        assertEquals(new PixelRect(80, 20, 20, 40), projection.pixels());
        assertTrue(view.hitTest(49, 11));
        assertFalse(view.hitTest(51, 11));
    }

    @Test
    void preservesPixelEdgesAtFourGuiScalesAndAfterResize() {
        var area = new Rect(1.25, 2.5, 4.75, 3.5);
        for (int scale = 1; scale <= 4; scale++) {
            var target = new VisualProjection.Target(801, 481, 801.0 / scale, 481.0 / scale);
            int left = (int) Math.floor(1.25 * scale);
            int top = (int) Math.floor(2.5 * scale);
            assertEquals(new PixelRect(left, top, 6 * scale - left, 6 * scale - top), target.pixels(area));
        }
        var resized = new VisualProjection.Target(1620, 974, 405, 243.5);
        assertEquals(new PixelRect(5, 10, 19, 14), resized.pixels(area));
        assertTrue(resized.pixels(new Rect(1.0001, 1.0001, 0.0001, 0.0001)).getW() > 0);
    }

    @Test
    void reusesTheImmutableNodePublicationUntilGeometryChanges() {
        var layout = new AlignmentLayout(new Rect(0, 0, 100, 100));
        var node = layout.node("panel", AlignmentFrame.box(new Rect(10, 0, 20, 20)));
        layout.resolve();
        var first = layout.snapshot(PixelAlignment.ONE);
        node.update(AlignmentFrame.box(new Rect(30, 0, 20, 20)));
        assertSame(first, layout.snapshot(PixelAlignment.ONE));
        layout.resolve();
        var second = layout.snapshot(PixelAlignment.ONE);
        assertNotSame(first, second);
        assertTrue(first.get(node).view().hitTest(15, 5));
        assertFalse(second.get(node).view().hitTest(15, 5));
        assertThrows(UnsupportedOperationException.class, first::clear);
    }

    @Test
    void rejectsRotatedFramebufferEffects() {
        var screen = new Rect(0, 0, 200, 100);
        var layout = new AlignmentLayout(screen);
        var node = layout.node("rotated", AlignmentFrame.box(new Rect(40, 40, 30, 20))
                .withPose(Transform.rotation(0.25)));
        layout.resolve();
        assertEquals(VisualEffectResult.UNSUPPORTED_TRANSFORM,
                new VisualProjection.Target(400, 200, 200, 100)
                        .project(node.current(PixelAlignment.ONE), VisualProjection.BoundsMode.RENDER, screen).result());
    }
}
