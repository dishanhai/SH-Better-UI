// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition;
import java.util.List;

/** One coherent geometry/content clock for the existing unified activity surface. */
final class NativeActivityMotion {
    private SurfaceTransition<ActivityService.Content> transition = new SurfaceTransition<>();
    private LiveSurfaceSettings.Motion configured;
    private SurfacePlacement.Rect viewport = new SurfacePlacement.Rect(0, 0, 0, 0);
    private long time;

    void configure(LiveSurfaceSettings.Motion settings) {
        if (configured != null && configured.response() == settings.response() && configured.damping() == settings.damping()) {
            configured = settings; return;
        }
        var prior = transition.state();
        var next = new SurfaceTransition<ActivityService.Content>(new Spring.Preset(settings.response(), settings.damping(), .05f, 1));
        var visible = prior.input().presentation().visible() ? prior.input() : prior.current();
        next.update(new SurfaceTransition.Target<>(visible.presentation(), prior.animated(), visible.content(), visible.contentKey()),
                viewport, time, SurfaceTransition.Motion.NONE, SurfaceTransition.Interruption.SNAP);
        transition = next; configured = settings;
    }

    SurfaceTransition.State<ActivityService.Content> step(SurfaceTransition.Target<ActivityService.Content> target,
            SurfacePlacement.Rect viewport, List<SurfacePlacement.Rect> occupied, long nowMs, boolean reduced) {
        this.viewport = viewport; time = nowMs;
        var mode = configured == null ? LiveSurfaceSettings.MotionMode.FULL : configured.mode();
        var motion = switch (mode) {
            case NONE -> SurfaceTransition.Motion.NONE;
            case REDUCED -> SurfaceTransition.Motion.REDUCED;
            case FULL -> reduced ? SurfaceTransition.Motion.REDUCED : SurfaceTransition.Motion.FULL;
        };
        var state = transition.update(target, viewport, nowMs, motion, SurfaceTransition.Interruption.PRESERVE_VELOCITY);
        var area = state.animated();
        var bounds = new SurfacePlacement.Rect(area.x(), area.y(), area.width(), area.height());
        if (area.visible() && occupied.stream().anyMatch(bounds::intersects))
            state = transition.update(target, viewport, nowMs, motion, SurfaceTransition.Interruption.SNAP);
        return state;
    }

    void suspend(long nowMs) {
        time = nowMs;
        transition.update(new SurfaceTransition.Target<>(SurfacePresentation.SUSPENDED, SurfaceGeometry.HIDDEN, null),
                viewport, nowMs, SurfaceTransition.Motion.NONE, SurfaceTransition.Interruption.SNAP);
    }
}
