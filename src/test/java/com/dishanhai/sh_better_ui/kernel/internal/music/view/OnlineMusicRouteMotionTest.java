// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicRouteMotionTest {
    private enum Page { SEARCH, LIBRARY, ACCOUNTS, HISTORY, QUEUE, LYRICS }
    private enum Stage { LYRICS, QUEUE, ARTWORK }
    private final OnlineMusicRouteMotion motion = new OnlineMusicRouteMotion();

    @Test void tabsAndTheirDetailsAnimateDifferentRegionsWithoutRestartingDuringPolls() {
        assertEquals(OnlineMusicRouteMotion.Surface.SHELL, sample(Page.SEARCH, "first provider", null, 100).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.DETAIL, sample(Page.SEARCH, "second provider", null, 200).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.PAGE, sample(Page.LIBRARY, "playlists", null, 400).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.DETAIL, sample(Page.LIBRARY, "playlist #3", null, 600).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.DETAIL, sample(Page.LIBRARY, "liked", null, 800).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.PAGE, sample(Page.ACCOUNTS, null, null, 1000).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.DETAIL, sample(Page.ACCOUNTS, "provider account", null, 1200).surface());
        float inFlight = sample(Page.ACCOUNTS, "provider account", null, 1280).strength();
        assertTrue(inFlight > 0f && inFlight < 1f);
        assertEquals(0f, sample(Page.ACCOUNTS, "provider account", null, 1365).strength());
        assertEquals(OnlineMusicRouteMotion.Surface.DETAIL, sample(Page.ACCOUNTS, null, null, 1370).surface());
    }

    @Test void rapidBackRetainsVisibleStrengthAndSettlesWithoutQueuedTransitions() {
        sample(Page.SEARCH, "provider", null, 50);
        sample(Page.HISTORY, null, null, 100);
        float shown = sample(Page.HISTORY, null, null, 150).strength();
        assertEquals(shown, sample(Page.SEARCH, "provider", null, 150).strength(), 0.0001f);
        assertEquals(0f, sample(Page.SEARCH, "provider", null, 265).strength());
        assertEquals(0f, sample(Page.SEARCH, "provider", null, 1000).strength());
    }

    @Test void queueEditingAndImmersiveEntryModeSwitchAndExitStayDistinct() {
        sample(Page.QUEUE, false, null, 10);
        assertEquals(OnlineMusicRouteMotion.Surface.DETAIL, sample(Page.QUEUE, true, null, 20).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.SHELL, sample(Page.LYRICS, null, Stage.LYRICS, 220).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.STAGE_PANE, sample(Page.LYRICS, null, Stage.ARTWORK, 420).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.STAGE_PANE, sample(Page.LYRICS, null, Stage.QUEUE, 620).surface());
        assertEquals(OnlineMusicRouteMotion.Surface.SHELL, sample(Page.QUEUE, true, null, 820).surface());
    }

    @Test void coldEntranceFadesOnceButReduceMotionSkipsIt() {
        var search = new OnlineMusicRouteMotion.Route(Page.SEARCH, null, null);
        var opening = motion.sample(search, 100, false);
        assertEquals(1f, opening.strength());
        assertTrue(opening.opacity(false) >= 75 && opening.opacity(false) <= 85);
        assertTrue(opening.opacity(true) <= 45);
        assertTrue(motion.sample(search, 170, false).strength() > 0f);
        assertEquals(0f, motion.sample(search, 265, false).strength());
        var reduced = new OnlineMusicRouteMotion();
        assertEquals(0f, reduced.sample(search, 100, true).strength());
        assertEquals(0f, reduced.sample(search, 150, false).strength());
    }

    @Test void reducedMotionAndLayoutResetDiscardPendingAnimation() {
        sample(Page.SEARCH, null, null, 10);
        assertTrue(sample(Page.HISTORY, null, null, 20).strength() > 0f);
        var account = new OnlineMusicRouteMotion.Route(Page.ACCOUNTS, null, null);
        assertEquals(0f, motion.sample(account, 25, true).strength());
        assertEquals(0f, motion.sample(account, 30, false).strength());

        assertEquals(1f, sample(Page.SEARCH, null, null, 40).strength());
        motion.reset();
        assertEquals(0f, sample(Page.HISTORY, null, null, 50).strength());
    }

    @Test void pageAndCompactStageRegionsLeaveNavigationAndTransportAlone() {
        var compact = OnlineMusicLayout.of(new Bounds(0, 0, 300, 310));
        var wide = OnlineMusicLayout.of(new Bounds(0, 0, 620, 390));
        for (var layout : new OnlineMusicLayout[]{compact, wide}) {
            var page = OnlineMusicRouteMotion.bounds(OnlineMusicRouteMotion.Surface.PAGE, layout, null);
            assertTrue(page.bottom() < layout.transport().y());
            assertTrue(layout.sidebar() ? page.x() >= layout.navigation().right()
                    : page.y() >= layout.navigation().bottom());
            assertEquals(layout.content(), OnlineMusicRouteMotion.bounds(OnlineMusicRouteMotion.Surface.DETAIL, layout, null));
            var stage = FullscreenLyricLayout.of(layout.frame(),true,false,false);
            var pane = OnlineMusicRouteMotion.bounds(OnlineMusicRouteMotion.Surface.STAGE_PANE, layout, stage);
            if (stage.wide()) {
                assertEquals(stage.lyrics(),pane);
                assertTrue(pane.x() > stage.transport().right());
            } else {
                assertTrue(pane.y() >= stage.back().bottom());
                assertTrue(pane.bottom() < stage.transport().y());
                assertTrue(pane.y() <= stage.cover().y());
            }
        }
    }

    @Test void revealMovesDownwardWithoutPaintingOutsideDestination() {
        var box = new Bounds(30, 60, 155, 190);
        var opening = MusicRouteReveal.edge(box, 1f);
        var middle = MusicRouteReveal.edge(box, .5f);
        var late = MusicRouteReveal.edge(box, .05f);
        assertEquals(new MusicRouteReveal.Edge(box.y(), box.y()), opening);
        assertTrue(opening.top() < middle.top() && middle.top() < late.top());
        assertTrue(middle.top() < middle.bottom() && middle.bottom() < box.bottom());
        assertEquals(box.bottom(), late.bottom());
        assertEquals(new MusicRouteReveal.Edge(box.bottom(), box.bottom()), MusicRouteReveal.edge(box, 0f));
    }

    private OnlineMusicRouteMotion.Frame sample(Page page, Object detail, Stage stage, long now) {
        return motion.sample(new OnlineMusicRouteMotion.Route(page, detail, stage), now, false);
    }
}
