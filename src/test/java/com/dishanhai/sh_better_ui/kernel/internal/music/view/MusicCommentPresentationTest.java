// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MusicCommentPresentationTest {
    @Test void skeletonFitsBothFullAndShortViewports() {
        var viewport = new Bounds(50, 80, 160, 101);
        var blocks = MusicCommentPresentation.Companion.skeleton(viewport);
        assertEquals(7, blocks.size());
        assertEquals(new Bounds(54, 86, 24, 24), blocks.getFirst().bounds());
        assertTrue(blocks.stream().allMatch(block -> {
            var bounds = block.bounds();
            return bounds.x() >= viewport.x() && bounds.right() <= viewport.right()
                    && bounds.y() >= viewport.y() && bounds.bottom() <= viewport.bottom();
        }));
        assertTrue(MusicCommentPresentation.Companion.skeleton(new Bounds(50, 80, 160, 20)).isEmpty());
    }

    @Test void populatedResultEntersOnceAndReducedMotionSkipsTheOffset() {
        var presentation = new MusicCommentPresentation();
        var empty = new OnlineMusicComments(List.of(), 0L, false);
        var entry = new OnlineMusicComments.Entry("1", "Listener", "Comment", null, null, null);
        var result = new OnlineMusicComments(List.of(entry), 1L, false);
        presentation.update(empty, 10);
        assertEquals(0, presentation.offset(0, 10, false));

        presentation.update(result, 100);
        assertEquals(5, presentation.offset(0, 100, false));
        assertEquals(0, presentation.offset(0, 100, true));
        assertTrue(presentation.offset(1, 150, false) > presentation.offset(0, 150, false));
        presentation.update(result, 300);
        assertEquals(0, presentation.offset(0, 300, false));

        presentation.reset();
        assertEquals(0, presentation.offset(0, 310, false));
        presentation.update(result, 320);
        assertEquals(5, presentation.offset(0, 320, false));
    }
}
