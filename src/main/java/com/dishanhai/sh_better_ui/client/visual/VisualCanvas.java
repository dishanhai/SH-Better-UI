// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import net.minecraft.client.renderer.MultiBufferSource;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

final class VisualCanvas {
    private VisualCanvas() {}

    static VisualProjection.Target target() {
        var client = Minecraft.getInstance();
        var target = client.getMainRenderTarget();
        var window = client.getWindow();
        if (target == null || target.width <= 0 || target.height <= 0
                || window.getWidth() <= 0 || window.getHeight() <= 0
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != target.frameBufferId) return null;
        double scale = window.getGuiScale();
        return new VisualProjection.Target(target.width, target.height,
                window.getWidth() / scale, window.getHeight() / scale);
    }

    static Rect screenClip(VisualProjection.Target target) {
        if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) return target.viewport();
        var scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        return target.guiRect(new PixelRect(scissor[0], target.height() - scissor[1] - scissor[3],
                scissor[2], scissor[3])).intersect(target.viewport());
    }

    static Rect clip(Rect viewport, View view) {
        return view.clip() == null ? viewport : viewport.intersect(view.clip());
    }

    static void draw(GuiGraphics graphics, VisualProjection.Target target, Rect viewport, View view,
                     Consumer<GuiGraphics> draw) {
        Rect clip = clip(viewport, view).intersect(screenClip(target));
        var pixels = target.pixels(clip);
        if (pixels.isEmpty()) return;
        GuiStateGuard.draw(() -> {
            graphics.flush();
            // The consumer's native scissor stack can only narrow the inherited layout clip.
            var local = new ClippedGraphics(graphics.bufferSource(), pixels, target.height());
            ShBetterUiApi.layout().applyLocalPose(local, view);
            try {
                draw.accept(local);
            } finally {
                local.flush();
            }
        });
    }

    /** Fork the current content pose while intersecting the inherited scissor with the inline box. */
    static void drawInline(GuiGraphics graphics, RichLayoutSnapshot.Rect box, Consumer<GuiGraphics> draw) {
        var target = target();
        if (target == null) return;
        var pose = graphics.pose().last().pose();
        var transform = new Transform(pose.m00(), pose.m10(), pose.m01(), pose.m11(), pose.m30(), pose.m31());
        var area = transform.map(new Rect(box.x(), box.y(), box.width(), box.height()));
        var pixels = target.pixels(area.intersect(screenClip(target)));
        if (pixels.isEmpty()) return;
        GuiStateGuard.draw(() -> {
            graphics.flush();
            var local = new ClippedGraphics(graphics.bufferSource(), pixels, target.height());
            local.pose().last().pose().set(pose);
            local.pose().last().normal().set(graphics.pose().last().normal());
            try {
                draw.accept(local);
            } finally {
                local.flush();
            }
        });
    }

    private static final class ClippedGraphics extends GuiGraphics {
        private final int left, bottom, right, top;
        private final int[] scissor = new int[4];

        ClippedGraphics(MultiBufferSource.BufferSource buffers, PixelRect pixels, int height) {
            super(Minecraft.getInstance(), buffers);
            left = pixels.getX();
            right = left + pixels.getW();
            top = height - pixels.getY();
            bottom = top - pixels.getH();
            constrain();
        }

        @Override public void enableScissor(int x0, int y0, int x1, int y1) {
            super.enableScissor(x0, y0, x1, y1);
            constrain();
        }

        @Override public void disableScissor() {
            super.disableScissor();
            constrain();
        }

        private void constrain() {
            int x = left, y = bottom, x1 = right, y1 = top;
            if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
                x = Math.max(x, scissor[0]);
                y = Math.max(y, scissor[1]);
                x1 = Math.min(x1, scissor[0] + scissor[2]);
                y1 = Math.min(y1, scissor[1] + scissor[3]);
            }
            RenderSystem.enableScissor(x, y, Math.max(0, x1 - x), Math.max(0, y1 - y));
        }
    }
}
