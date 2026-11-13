// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Anchor;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Decision;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Protection;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Run;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.LinkedHashSet;
import java.util.List;

final class ClientTextPaint {
    private ClientTextPaint() {}

    static Transform transform(GuiGraphicsExtractor graphics) {
        var pose = graphics.pose();
        return new Transform(pose.m00(), pose.m10(), pose.m01(), pose.m11(), pose.m20(), pose.m21());
    }

    static Matrix3x2f matrix(Transform transform) {
        return new Matrix3x2f((float) transform.xx(), (float) transform.yx(), (float) transform.xy(),
                (float) transform.yy(), (float) transform.tx(), (float) transform.ty());
    }

    static Rect clip(GuiGraphicsExtractor graphics, Rect requested) {
        var viewport = new Rect(0, 0, graphics.guiWidth(), graphics.guiHeight());
        var scissor = graphics.peekScissorStack();
        if (scissor != null) viewport = viewport.intersect(new Rect(scissor.left(), scissor.top(), scissor.width(), scissor.height()));
        return viewport.intersect(requested);
    }

    static Font font(Run run) {
        return run.font() == null ? Minecraft.getInstance().font : run.font();
    }

    /** Samples interior points of the measured glyph bounds, without claiming texture-mask coverage. */
    static List<SamplePoint> points(Run run, int argb, Transform transform, Rect clip, int scale, PixelRegion region) {
        if (region == null) return List.of();
        var result = new LinkedHashSet<SamplePoint>();
        font(run).prepareText(run.glyphs(), (float) run.x(), (float) run.y(), argb, false, false, 0)
                .visit(new Font.GlyphVisitor() {
                    @Override public void acceptGlyph(TextRenderable.Styled glyph) {
                        if (result.size() >= 256) return;
                        for (double fy : new double[]{0.3, 0.7}) {
                            for (double fx : new double[]{0.25, 0.5, 0.75}) {
                                var at = transform.map(new Anchor(glyph.left() + (glyph.right() - glyph.left()) * fx,
                                        glyph.top() + (glyph.bottom() - glyph.top()) * fy));
                                int x = (int) Math.floor(at.x() * scale);
                                int y = (int) Math.floor(at.y() * scale);
                                if (clip.contains((x + 0.5) / scale, (y + 0.5) / scale)
                                        && x >= region.x() && x < region.x() + region.width()
                                        && y >= region.y() && y < region.y() + region.height()) {
                                    result.add(new SamplePoint(x, y, 0, 0));
                                }
                            }
                        }
                    }
                });
        return List.copyOf(result);
    }

    static void paint(GuiGraphicsExtractor graphics, Run run, Transform transform, Rect clip, Rect backingBounds,
                      int argb, boolean shadow, float opacity, Decision decision) {
        var pose = matrix(transform);
        int left = (int) Math.ceil(clip.x()), top = (int) Math.ceil(clip.y());
        int right = (int) Math.floor(clip.right()), bottom = (int) Math.floor(clip.bottom());
        if (left >= right || top >= bottom) return;
        var screenClip = new ScreenRectangle(left, top, right - left, bottom - top);
        int fill = decision == null ? argb : decision.fillArgb();
        var glyphs = decision == null ? run.glyphs() : recolor(run.glyphs(), fill);
        if (decision != null && decision.protection() == Protection.BACKING) {
            var matrix = graphics.pose();
            matrix.pushMatrix();
            try {
                matrix.identity();
                graphics.enableScissor(left, top, right, bottom);
                try {
                    matrix.set(pose);
                    graphics.fill((int) Math.floor(backingBounds.x()), (int) Math.floor(backingBounds.y()),
                            (int) Math.ceil(backingBounds.right()), (int) Math.ceil(backingBounds.bottom()),
                            fade(decision.protectionArgb(), opacity));
                } finally {
                    graphics.disableScissor();
                }
            } finally {
                matrix.popMatrix();
            }
        }
        if (decision != null && decision.protection() == Protection.OUTLINE) {
            int outline = decision.protectionArgb();
            var edge = recolor(run.glyphs(), outline);
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) submit(graphics, run, edge, dx * 0.5f, dy * 0.5f,
                        fade(outline, opacity), false, pose, screenClip);
            }
        }
        submit(graphics, run, glyphs, 0, 0, fade(fill, opacity), shadow, pose, screenClip);
    }

    private static void submit(GuiGraphicsExtractor graphics, Run run, FormattedCharSequence sequence,
                               float dx, float dy, int color, boolean shadow, Matrix3x2f pose, ScreenRectangle clip) {
        if (color >>> 24 == 0) return;
        var stack = graphics.pose();
        stack.pushMatrix();
        try {
            stack.identity();
            graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
            try {
                stack.set(pose).translate((float) run.x() + dx, (float) run.y() + dy);
                graphics.text(font(run), sequence, 0, 0, color, shadow);
            } finally {
                graphics.disableScissor();
            }
        } finally {
            stack.popMatrix();
        }
    }

    private static FormattedCharSequence recolor(FormattedCharSequence sequence, int color) {
        return sink -> sequence.accept((index, style, glyph) -> sink.accept(index, style.withColor(color & 0xFFFFFF), glyph));
    }

    private static int fade(int argb, float opacity) {
        return Math.round((argb >>> 24) * opacity) << 24 | argb & 0xFFFFFF;
    }
}
