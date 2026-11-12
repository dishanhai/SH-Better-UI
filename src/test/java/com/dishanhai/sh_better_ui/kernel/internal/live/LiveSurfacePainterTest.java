// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation.COMPACT;
import static com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Interruption.PRESERVE_VELOCITY;
import static com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Interruption.SNAP;
import static com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Motion.FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSurfacePainterTest {
    @Test void anOutgoingLayerWithEqualContentDoesNotCountAsPaintingTheNewPublication() {
        var viewport = new SurfacePlacement.Rect(0, 0, 320, 240);
        var geometry = new SurfaceGeometry(30, 12, 120, 24, 10, 1);
        var content = ActivityService.Content.label(ActivityService.Text.literal("Same message"));
        var first = new SurfaceTransition.Target<>(COMPACT, geometry, content, 1L);
        var second = new SurfaceTransition.Target<>(COMPACT, geometry, content, 2L);
        var transition = new SurfaceTransition<ActivityService.Content>();
        transition.update(first, viewport, 0, FULL, SNAP);

        var initial = transition.update(second, viewport, 700, FULL, PRESERVE_VELOCITY);
        assertEquals(1d, initial.animated().opacity());
        var firstPaint = new LiveSurfacePainter.Layer(initial.animated(), initial.contentMix(), second.contentKey(),
                initial.input(), null, null, null, null);
        assertFalse(firstPaint.contentMix().stream().anyMatch(firstPaint::isTargetContent));

        var next = transition.update(second, viewport, 725, FULL, PRESERVE_VELOCITY);
        var nextPaint = new LiveSurfacePainter.Layer(next.animated(), next.contentMix(), second.contentKey(),
                next.input(), null, null, null, null);
        assertTrue(nextPaint.contentMix().stream().anyMatch(nextPaint::isTargetContent));
    }

    @Test void clipsContentInsideTheAnimatedRoundedShape() {
        var geometry = new SurfaceGeometry(12.35, 3.4, 153.8, 68.15, 32.75, 1);
        var bounds = LiveSurfacePainter.innerBounds(geometry, 5);

        assertTrue(bounds.width() > 0);
        assertTrue(bounds.height() > 0);
        assertTrue(geometry.contains(bounds.x(), bounds.y()));
        assertTrue(geometry.contains(bounds.right(), bounds.y()));
        assertTrue(geometry.contains(bounds.x(), bounds.bottom()));
        assertTrue(geometry.contains(bounds.right(), bounds.bottom()));
        assertFalse(geometry.contains(geometry.x() + 5, geometry.y() + 5));
    }

    @Test void seekHitBoxUsesHalfOpenGuiBoundsAndClampsCapturedDragging() {
        var control = new LiveSurfacePainter.Control(new UiId("sh_better_ui", "live/seek"),
                new ActivityService.Text("sh_better_ui.live.seek", true), true, 20, 14, 81, 8);

        assertTrue(control.contains(20, 14));
        assertTrue(control.contains(100.9, 21.9));
        assertFalse(control.contains(101, 18));
        assertFalse(control.contains(50, 22));
        assertEquals(0, control.fraction(10));
        assertEquals(.5, control.fraction(60));
        assertEquals(1, control.fraction(100));
        assertEquals(1, control.fraction(121));
    }
}
