// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextLayoutMotionTest {
    private static final long HALF = TextLayoutMotion.DURATION_MS / 2;

    @Test
    void movesAndScalesOneCopyAndRetargetsFromTheCurrentFrame() {
        var motion = new TextLayoutMotion();
        motion.update("label", List.of(line("Forge", 2, 1)), 0, false);
        motion.update("label", List.of(line("Forge", 10, 1.5f)), 1, false);
        var halfway = motion.update("label", List.of(line("Forge", 10, 1.5f)), HALF + 1, false);
        assertEquals(1, halfway.layers().size());
        var sample = halfway.layers().getFirst().lines().getFirst();
        assertEquals(6f, sample.x());
        assertEquals(1.25f, sample.scale());
        assertEquals(1f, halfway.layers().getFirst().opacity());

        var interrupted = motion.update("label", List.of(line("Forge", 18, 1)), HALF + 1, false);
        assertEquals(sample.x(), interrupted.layers().getFirst().lines().getFirst().x());
        assertEquals(sample.scale(), interrupted.layers().getFirst().lines().getFirst().scale());
        assertFalse(motion.update("label", List.of(line("Forge", 18, 1)), HALF + 1 + TextLayoutMotion.DURATION_MS, false).active());
    }

    @Test
    void crossfadesReflowAndPreservesTheVisibleBlendAtInterruption() {
        var motion = new TextLayoutMotion();
        var full = List.of(line("NeoForge", 0, 1));
        var shortLine = List.of(line("Neo…", 0, 1));
        motion.update("label", full, 0, false);
        motion.update("label", shortLine, 1, false);
        var halfway = motion.update("label", shortLine, HALF + 1, false);
        assertEquals(List.of(0.5f, 0.5f), halfway.layers().stream().map(TextLayoutMotion.Layer::opacity).toList());
        var reversed = motion.update("label", full, HALF + 1, false);
        assertEquals(halfway.layers(), reversed.layers());
        var later = motion.update("label", full, HALF * 2 + 1, false);
        assertEquals(2, later.layers().size());
        assertEquals(1f, (float) later.layers().stream().mapToDouble(TextLayoutMotion.Layer::opacity).sum(), 0.001f);
    }

    @Test
    void firstFrameNewSourceAndReducedMotionApplyImmediately() {
        var motion = new TextLayoutMotion();
        var first = List.of(line("One", 0, 1));
        assertFalse(motion.update("label", first, 0, false).active());
        assertFalse(motion.update("different label", List.of(line("Two", 0, 1)), 1, false).active());
        var reduced = motion.update("different label", List.of(line("T…", 0, 1)), 2, true);
        assertFalse(reduced.active());
        assertEquals(1f, reduced.layers().getFirst().opacity());
        assertTrue(motion.update("different label", List.of(), 3, true).layers().isEmpty());
    }

    @Test
    void multilineReflowsStayBoundedUnderRapidResizing() {
        var motion = new TextLayoutMotion();
        motion.update("label", List.of(line("NeoForge", 0, 1)), 0, false);
        for (int i = 1; i <= 12; i++) {
            var lines = List.of(line("Neo", i, 1), line("Forge", i, 1));
            var frame = motion.update("label", lines, i * 24L, false);
            assertTrue(frame.layers().size() <= 2);
            var rendered = motion.update("label", lines, i * 24L + 12, false);
            assertTrue(rendered.layers().size() <= 3);
        }
    }

    @Test
    void movingClipUsesTheCurrentPoseAndRoundsOutwards() {
        var pose = new Matrix4f().translation(2.5f, 3.5f, 0).scale(1.25f, 1.25f, 1);
        assertEquals(new Bounds(7, 11, 15, 10),
                TextLayoutMotion.screenClip(pose, new Bounds(4, 6, 11, 8)));
    }

    private static TextLayoutMotion.Line line(String text, float x, float scale) {
        return new TextLayoutMotion.Line(FormattedCharSequence.forward(text, Style.EMPTY), x, 0, scale);
    }
}
