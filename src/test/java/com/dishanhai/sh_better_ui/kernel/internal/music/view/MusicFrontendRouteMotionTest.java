// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendRouteMotionTest {
    @Test void stableAndNestedRoutesUseTheSameBoundedEntranceOnlyOnNavigation() {
        var motion = new MusicFrontendRouteMotion();
        var settings = MusicFrontendRoutes.Route.of(SETTINGS);
        var playback = new MusicFrontendRoutes.Route(SETTINGS, "", "playback");
        assertEquals(0, motion.sample(settings, 0, 1000, false).headingOffset());
        var enter = motion.sample(playback, 0, 1100, false);
        assertEquals(4, enter.headingOffset());
        assertEquals(52, enter.contentFade());
        var sameRoute = new MusicFrontendRoutes.Route(SETTINGS, "", "playback");
        var middle = motion.sample(sameRoute, 0, 1190, false);
        assertEquals(2, middle.headingOffset());
        assertEquals(26, middle.contentFade());
        var settled = motion.sample(playback, 0, 1280, false);
        assertEquals(0, settled.headingOffset());
        assertEquals(0, settled.contentFade());
        assertEquals(4, motion.sample(settings, 0, 1290, false).headingOffset());
    }

    @Test void switchingTabsAgainRetargetsFromTheCurrentPaintedPosition() {
        var motion = new MusicFrontendRouteMotion();
        var discover = MusicFrontendRoutes.Route.of(DISCOVER);
        var search = MusicFrontendRoutes.Route.of(SEARCH);
        motion.sample(discover, 0, 1000, false);
        assertEquals(0f, motion.sample(search, 2, 1100, false).tabPosition(), 0.001f);
        assertEquals(1f, motion.sample(search, 2, 1190, false).tabPosition(), 0.001f);
        assertEquals(1f, motion.sample(discover, 0, 1190, false).tabPosition(), 0.001f);
        assertEquals(.5f, motion.sample(discover, 0, 1280, false).tabPosition(), 0.001f);
        assertEquals(0f, motion.sample(discover, 0, 1370, false).tabPosition(), 0.001f);
    }

    @Test void reducedMotionAndResetSnapWithoutReplayingPendingRoutes() {
        var motion = new MusicFrontendRouteMotion();
        var discover = MusicFrontendRoutes.Route.of(DISCOVER);
        var now = MusicFrontendRoutes.Route.of(NOW_PLAYING);
        motion.sample(discover, 0, 1000, false);
        assertEquals(4, motion.sample(now, 1, 1100, false).headingOffset());
        var accessible = motion.sample(now, 1, 1120, true);
        assertEquals(0, accessible.headingOffset());
        assertEquals(0, accessible.contentFade());
        assertEquals(1f, accessible.tabPosition(), 0.001f);
        assertEquals(accessible, motion.sample(now, 1, 1140, false));
        motion.reset();
        assertEquals(0, motion.sample(discover, 0, 1160, false).headingOffset());
        assertEquals(0f, motion.sample(discover, 0, 1160, false).tabPosition(), 0.001f);
    }

    @Test void artworkGrowsFromNearestSideAndCannotCoverNeighboringControls() {
        var motion = new MusicFrontendRouteMotion();
        var discover = MusicFrontendRoutes.Route.of(DISCOVER);
        var now = MusicFrontendRoutes.Route.of(NOW_PLAYING);
        var slot = new Bounds(60, 50, 80, 80);
        var mini = new Bounds(6, 200, 28, 28);
        motion.sample(discover, 0, 1000, false);
        var start = motion.sample(now, 0, 1100, false).artwork(slot, mini);
        assertEquals(slot.x(), start.x());
        assertEquals(slot.bottom(), start.bottom());
        var halfway = motion.sample(now, 0, 1190, false).artwork(slot, mini);
        assertTrue(start.width() < halfway.width() && halfway.width() < slot.width());
        assertTrue(halfway.x() >= slot.x() && halfway.y() >= slot.y());
        assertTrue(halfway.right() <= slot.right() && halfway.bottom() <= slot.bottom());
        assertEquals(slot, motion.sample(now, 0, 1280, false).artwork(slot, mini));
    }
}
