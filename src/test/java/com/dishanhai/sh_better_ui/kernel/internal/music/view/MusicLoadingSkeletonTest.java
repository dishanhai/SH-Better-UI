// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class MusicLoadingSkeletonTest {
    @Test void quickAndFailedReadsDoNotLeaveAPlaceholder() {
        var motion = new MusicLoadingSkeleton();
        var prompt = new CompletableFuture<String>();
        assertEquals(0f, motion.sample(prompt, false, 0, false));
        assertEquals(0f, motion.sample(prompt, false, 149, false));
        prompt.complete("results");
        assertEquals(0f, motion.sample(null, true, 149, false));
        var slow = new CompletableFuture<String>();
        assertEquals(0f, motion.sample(slow, false, 200, false));
        assertEquals(0f, motion.sample(slow, false, 350, false));
        assertTrue(motion.sample(slow, false, 410, false) > 0f);
        slow.completeExceptionally(new IllegalStateException());
        assertEquals(0f, motion.sample(null, false, 411, false));
        assertEquals(0f, motion.sample(null, false, 600, false));
    }

    @Test void successOnlyFadesPaintAndDoesNotKeepOwningRequest() {
        var motion = new MusicLoadingSkeleton();
        var request = new CompletableFuture<String>();
        motion.sample(request, false, 10, false);
        motion.sample(request, false, 160, false);
        assertEquals(1f, motion.sample(request, false, 270, false));
        request.complete("results");
        assertEquals(1f, motion.sample(null, true, 271, false));
        assertTrue(motion.sample(null, true, 315, false) < 1f);
        assertTrue(motion.sample(null, true, 316, false) > 0f);
        assertEquals(0f, motion.sample(null, true, 361, false));
        assertEquals("results", request.join());
    }

    @Test void rapidReplacementResetsDelayAndDetachDoesNotCancelRequest() {
        var motion = new MusicLoadingSkeleton();
        var first = new CompletableFuture<String>();
        var replacement = new CompletableFuture<String>();
        motion.sample(first, false, 0, false);
        motion.sample(first, false, 150, false);
        assertEquals(1f, motion.sample(first, false, 260, false));
        assertEquals(0f, motion.sample(replacement, false, 261, false));
        assertEquals(0f, motion.sample(replacement, false, 410, false));
        motion.reset();
        assertFalse(first.isDone());
        assertFalse(replacement.isDone());
        assertEquals(0f, motion.sample(null, true, 600, false), "An old result must not revive detached paint");
        assertEquals(0f, motion.sample(replacement, false, 601, false));
        replacement.cancel(false);
        assertEquals(0f, motion.sample(replacement, false, 901, false));
    }

    @Test void reducedMotionIsStaticAndDoesNotRetainACompletionFade() {
        var motion = new MusicLoadingSkeleton();
        var pending = new CompletableFuture<String>();
        assertEquals(0f, motion.sample(pending, false, 100, true));
        assertEquals(1f, motion.sample(pending, false, 250, true));
        assertEquals(1f, motion.sample(pending, false, 550, true));
        pending.complete("results");
        assertEquals(0f, motion.sample(null, true, 551, true));
    }

    @Test void compactAndExpandedBlocksFitTheRealTitleSlotsAndClip() {
        for (var frame : List.of(new Bounds(6, 9, 208, 240), new Bounds(13, 24, 800, 600))) {
            var layout = OnlineMusicLayout.of(frame);
            var content = layout.content();
            var clip = new Bounds(content.x(), content.y() + layout.listHeadingHeight(), content.width(),
                    layout.pager().y() - content.y() - layout.listHeadingHeight() - 4);
            var rows = layout.rows().stream().map(row -> new Bounds(row.x(), row.y(), row.width() - 10 - 52, row.height())).toList();
            for (boolean withIcon : new boolean[]{false, true}) {
                var blocks = MusicLoadingSkeleton.blocks(rows, clip, withIcon);
                assertFalse(blocks.isEmpty());
                assertTrue(blocks.size() <= rows.size() * (withIcon ? 3 : 2));
                for (var block : blocks) {
                    var bounds = block.bounds();
                    assertTrue(bounds.bottom() <= clip.bottom());
                    assertTrue(rows.stream().anyMatch(row -> bounds.x() >= row.x() + 10 && bounds.y() >= row.y()
                            && bounds.right() <= row.right() - 10 && bounds.bottom() <= row.bottom()));
                }
            }
        }
    }
}
