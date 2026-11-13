// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Insets;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.client.visual.VisualProjection;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.util.List;
import java.util.function.Consumer;

/** Draws one already-laid-out native run at its absolute GUI pose. */
final class TextReadabilityPainter {
    private static final float OUTLINE_OFFSET = .75f;
    private static GroupTargets group;
    private static Composite composite;
    private static OwnershipLease lease;
    private static TextReadabilityGpu actualGpu;
    private static boolean actualFailed;
    private static long budgetFrame = -1;
    private static int frameCopyPixels;

    record ActualInput(int sampleBudget, int stateSlot, int generation, long deltaNanos, long stabilityNanos,
                       double threshold, double framebufferHeight, TextReadabilityReference.Sampling sampling,
                       long frame, long diagnosticId, int pixelAllowance, Scissor localBackdrop,
                       Rect localBacking, Integer initialFill) {}
    record ActualResult(Result result, int attemptedSamples, int copiedPixels, boolean telemetryQueued,
                        boolean regionInvalid, boolean pixelBudgetExhausted) {}
    record ActualDiagnostic(long diagnosticId, long frame, int attemptedSamples,
                            TextReadabilityReference.Decision decision, List<ActualSample> samples) {}
    /** The sample location uses top-left framebuffer coordinates. */
    record ActualSample(int x, int y, double luminance, double outlineCoverage) {}
    record ActualCost(long frame, long gpuNanos) {}

    private TextReadabilityPainter() {}

    static ActualResult drawActual(GuiGraphics graphics, Request request, ActualInput input) {
        if (request.groupOpacity() <= 0) return actualUnavailable(Result.HIDDEN, false, false);
        if (!RenderSystem.isOnRenderThread() || actualFailed || !GL.getCapabilities().OpenGL32) {
            return actualUnavailable(Result.RENDER_UNAVAILABLE, false, false);
        }
        var area = area(request, true);
        if (area == null) {
            return actualUnavailable(Result.RENDER_UNAVAILABLE, false, false);
        }
        if (area.clip.empty()) return actualUnavailable(Result.CLIPPED, true, false);
        var sampleBounds = sampleBounds(request, area);
        var samples = input.localBackdrop() == null ? sampleBounds : sampleBounds.intersect(input.localBackdrop());
        var transform = request.transform();
        double determinant = transform.xx() * transform.yy() - transform.xy() * transform.yx();
        if (samples.empty() || !Double.isFinite(determinant) || Math.abs(determinant) < 1e-12
                || area.clip.width() > TextReadabilityGpu.MAX_WIDTH
                || area.clip.height() > TextReadabilityGpu.MAX_HEIGHT) {
            return actualUnavailable(Result.RENDER_UNAVAILABLE, true, false);
        }
        int copiedPixels = area.clip.width() * area.clip.height();
        if (copiedPixels > Math.min(input.pixelAllowance(), availableFramePixels(input.frame()))) {
            return actualUnavailable(Result.RENDER_UNAVAILABLE, false, true);
        }
        var result = new ActualResult[]{actualUnavailable(Result.RENDER_UNAVAILABLE, false, false)};
        GuiStateGuard.draw(() -> {
            try (var ignored = new NativeBindings()) {
                graphics.flush();
                if (!TextReadabilityGpu.supportsSource(area.framebuffer)) return;
                var run = request.run();
                int base = request.style().argb();
                boolean[] nativeColor = {false};
                boolean[] obfuscated = {false};
                run.glyphs().accept((index, style, codePoint) -> {
                    nativeColor[0] |= style.getColor() != null && style.getColor().getValue() != (base & 0xFFFFFF);
                    obfuscated[0] |= style.isObfuscated();
                    return !nativeColor[0] || !obfuscated[0];
                });
                // Two native passes can pick different randomized glyphs for an obfuscated run.
                if (nativeColor[0] && obfuscated[0]) return;
                if (actualGpu == null && !beginActual()) return;
                frameCopyPixels += copiedPixels;
                var canvas = nativeCanvas(graphics, request);
                boolean needsOriginal = nativeColor[0] || request.style().shadow() && !obfuscated[0];
                Runnable original = !needsOriginal ? null
                        : () -> paintMask(canvas, request, run.glyphs(), base | 0xFF000000, request.style().shadow());
                result[0] = actualGpu.draw(area.framebuffer, area.viewport, area.clip, samples, area.projection,
                        request, input, () -> paintMask(canvas, request, withColor(run.glyphs(), 0xFFFFFFFF),
                                0xFFFFFFFF, false), original);
            }
        });
        return result[0];
    }

    static Scissor captureLocalBackdrop(GuiGraphics graphics, Request request, long frame, int pixelAllowance) {
        if (!RenderSystem.isOnRenderThread() || actualFailed || !GL.getCapabilities().OpenGL32) return null;
        var area = area(request, true);
        if (area == null) return null;
        var bounds = sampleBounds(request, area);
        if (bounds.empty() || bounds.width() > TextReadabilityGpu.MAX_WIDTH
                || bounds.height() > TextReadabilityGpu.MAX_HEIGHT) return null;
        int pixels = bounds.width() * bounds.height();
        if (pixels > Math.min(pixelAllowance, availableFramePixels(frame))) return null;
        boolean[] captured = {false};
        GuiStateGuard.draw(() -> {
            try (var ignored = new NativeBindings()) {
                graphics.flush();
                if (!TextReadabilityGpu.supportsSource(area.framebuffer)) return;
                if (actualGpu == null && !beginActual()) return;
                actualGpu.captureLocalBackdrop(area.framebuffer, bounds);
                captured[0] = true;
            }
        });
        if (!captured[0]) return null;
        frameCopyPixels += pixels;
        return bounds;
    }

    private static int availableFramePixels(long frame) {
        if (budgetFrame != frame) {
            budgetFrame = frame;
            frameCopyPixels = 0;
        }
        return TextReadabilityGpu.MAX_FRAME_COPY_PIXELS - frameCopyPixels;
    }

    private static Scissor sampleBounds(Request request, PaintArea area) {
        return Scissor.of(area.projection.pixels(request.transform().map(request.run().bounds())
                .intersect(request.clip())), area.viewport[3]).intersect(area.clip);
    }

    private static boolean beginActual() {
        try {
            actualGpu = TextReadabilityGpu.create();
            ownResources();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            actualFailed = true;
            if (actualGpu != null) actualGpu.close();
            actualGpu = null;
            ShBetterUi.LOGGER.warn("Actual text readability unavailable until GPU resources are reloaded", failure);
            return false;
        }
    }

    private static ActualResult actualUnavailable(Result result, boolean regionInvalid, boolean budgetExhausted) {
        return new ActualResult(result, 0, 0, false, regionInvalid, budgetExhausted);
    }

    /** Reads back the displayed fill only when a stable run leaves the GPU path. */
    static Integer displayedGpuFill(int slot, int generation) {
        return actualGpu == null ? null : actualGpu.displayedFill(slot, generation);
    }

    static void pollActualDiagnostics(Consumer<ActualDiagnostic> accept) {
        if (actualGpu != null) actualGpu.pollDiagnostics(accept);
    }

    static void pollActualCosts(Consumer<ActualCost> accept) {
        if (actualGpu != null) actualGpu.pollCosts(accept);
    }

    static long actualAllocatedBytes() { return actualGpu == null ? 0 : actualGpu.allocatedBytes(); }

    static void clearActualDiagnostics() {
        if (actualGpu != null) actualGpu.clearDiagnostics();
    }

    /** A null fill preserves each glyph's native inline color and the caller's base color. */
    static Result draw(GuiGraphics graphics, Request request, Integer fillArgb,
                        Integer outlineArgb, Integer backingArgb, Rect localBacking) {
        if (request.groupOpacity() <= 0) return Result.HIDDEN;
        if (!RenderSystem.isOnRenderThread()) return Result.RENDER_UNAVAILABLE;
        boolean fading = request.groupOpacity() < 1;
        var area = area(request, fading);
        if (area == null) return Result.RENDER_UNAVAILABLE;
        if (area.clip.empty()) return Result.CLIPPED;
        int target = area.framebuffer;
        var viewport = area.viewport;
        var clip = area.clip;
        Rect textBounds = request.run().bounds();
        Rect backing = backingArgb == null ? null
                : localBacking == null ? textBounds.outset(new Insets(2, 1, 2, 1)) : localBacking;

        boolean[] drawn = {false};
        GuiStateGuard.draw(() -> {
            try (var ignored = new NativeBindings()) {
                graphics.flush();
                if (fading) {
                    if (!beginGroup(target, viewport, clip)) return;
                } else {
                    clip.enable();
                }

                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.colorMask(true, true, true, true);
                var canvas = nativeCanvas(graphics, request);
                paint(canvas, font(request), request, fillArgb, outlineArgb, backing, backingArgb);

                if (fading) {
                    GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
                    RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                    clip.enable();
                    composite.draw(group.painted.getColorTextureId(), group.backdrop.getColorTextureId(),
                            clip, request.groupOpacity());
                }
                drawn[0] = true;
            }
        });
        return drawn[0] ? Result.DRAWN : Result.RENDER_UNAVAILABLE;
    }

    private static PaintArea area(Request request, boolean requireMain) {
        var minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        var window = minecraft.getWindow();
        if (target == null || window.getWidth() <= 0 || window.getHeight() <= 0) {
            return null;
        }
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean main = framebuffer == target.frameBufferId && viewport[0] == 0 && viewport[1] == 0
                && viewport[2] == target.viewWidth && viewport[3] == target.viewHeight;
        if (viewport[2] <= 0 || viewport[3] <= 0 || requireMain && !main) {
            return null;
        }
        double scale = window.getGuiScale();
        var projection = new VisualProjection.Target(viewport[2], viewport[3],
                window.getWidth() / scale, window.getHeight() / scale);
        if (!TextReadabilityGpu.supportsProjection(projection)) return null;

        // Advance bounds can exclude the italic overhang, decorations, and shadow.
        Rect paintBounds = request.run().bounds().outset(Insets.all(4));
        var pixels = projection.pixels(request.transform().map(paintBounds).intersect(request.clip()));
        var clip = Scissor.of(pixels, viewport[3]).offset(viewport[0], viewport[1]).intersectActive();
        return new PaintArea(framebuffer, viewport, projection, clip);
    }

    private static Font font(Request request) {
        var measured = request.run().font();
        return measured == null ? Minecraft.getInstance().font : measured;
    }

    private static GuiGraphics nativeCanvas(GuiGraphics graphics, Request request) {
        var canvas = new GuiGraphics(Minecraft.getInstance(), graphics.bufferSource());
        var transform = request.transform();
        canvas.pose().last().pose().set(
                (float) transform.xx(), (float) transform.yx(), 0, 0,
                (float) transform.xy(), (float) transform.yy(), 0, 0,
                0, 0, 1, 0,
                (float) transform.tx(), (float) transform.ty(), graphics.pose().last().pose().m32(), 1);
        return canvas;
    }

    private static void paintMask(GuiGraphics canvas, Request request, FormattedCharSequence sequence,
                                  int argb, boolean shadow) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableCull();
        try {
            glyphs(font(request), sequence, (float) request.run().x(), (float) request.run().y(),
                    argb, shadow, canvas.pose().last().pose(), canvas);
        } finally {
            canvas.bufferSource().endBatch();
        }
    }

    private record PaintArea(int framebuffer, int[] viewport, VisualProjection.Target projection, Scissor clip) {}

    private static void paint(GuiGraphics canvas, Font font, Request request, Integer fillArgb,
                              Integer outlineArgb, Rect backing, Integer backingArgb) {
        var run = request.run();
        var buffers = canvas.bufferSource();
        var pose = canvas.pose();
        RenderSystem.disableCull();
        if (backing != null) {
            pose.pushPose();
            try {
                pose.translate(backing.x(), backing.y(), 0);
                pose.scale((float) backing.width(), (float) backing.height(), 1);
                canvas.fill(RenderType.guiOverlay(), 0, 0, 1, 1, backingArgb);
            } finally {
                pose.popPose();
            }
        }

        var matrix = pose.last().pose();
        float x = (float) run.x(), y = (float) run.y();
        RenderSystem.disableDepthTest();
        try {
            if (outlineArgb != null && outlineArgb >>> 24 > 3) {
                var outline = withColor(run.glyphs(), outlineArgb);
                glyphs(font, outline, x - OUTLINE_OFFSET, y, outlineArgb, false, matrix, canvas);
                glyphs(font, outline, x + OUTLINE_OFFSET, y, outlineArgb, false, matrix, canvas);
                glyphs(font, outline, x, y - OUTLINE_OFFSET, outlineArgb, false, matrix, canvas);
                glyphs(font, outline, x, y + OUTLINE_OFFSET, outlineArgb, false, matrix, canvas);
                buffers.endBatch();
            }
            int base = fillArgb == null ? request.style().argb() : fillArgb;
            // Vanilla Font treats alpha values below four as legacy opaque RGB.
            if (base >>> 24 > 3) {
                var fill = fillArgb == null ? run.glyphs() : withColor(run.glyphs(), fillArgb);
                glyphs(font, fill, x, y, base, request.style().shadow(), matrix, canvas);
            }
        } finally {
            buffers.endBatch();
        }
    }

    private static void glyphs(Font font, FormattedCharSequence glyphs, float x, float y, int color,
                               boolean shadow, Matrix4f pose, GuiGraphics graphics) {
        font.drawInBatch(glyphs, x, y, color, shadow, pose, graphics.bufferSource(),
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private static FormattedCharSequence withColor(FormattedCharSequence sequence, int argb) {
        var color = TextColor.fromRgb(argb & 0xFFFFFF);
        return sink -> sequence.accept((index, style, codePoint) -> sink.accept(index, style.withColor(color), codePoint));
    }

    private static boolean beginGroup(int target, int[] viewport, Scissor clip) {
        RenderSystem.disableScissor();
        int width = Math.min(viewport[2], (clip.width() + 63) & ~63);
        int height = Math.min(viewport[3], (clip.height() + 63) & ~63);
        try {
            if (composite == null) composite = Composite.create();
            if (group == null) group = new GroupTargets(new ScratchTarget(), new ScratchTarget());
            group.ensureSize(width, height, viewport[2], viewport[3]);
            ownResources();
        } catch (RuntimeException failure) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
            releaseGpuResources();
            ShBetterUi.LOGGER.warn("Text readability opacity targets unavailable", failure);
            return false;
        }

        // Keep an untouched backdrop and paint over a second copy. Native glyph coverage keeps
        // the loader's atlas/blend semantics, then the two complete colors are mixed in linear light.
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
        int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            copyTo(group.backdrop, clip);
            copyTo(group.painted, clip);
        } finally {
            GL11.glReadBuffer(readBuffer);
        }
        RenderSystem.viewport(-clip.left, -clip.bottom, viewport[2], viewport[3]);
        RenderSystem.enableScissor(0, 0, clip.width(), clip.height());
        return true;
    }

    private static void copyTo(RenderTarget target, Scissor clip) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GL30.glBlitFramebuffer(clip.left, clip.bottom, clip.right, clip.top,
                0, 0, clip.width(), clip.height(), GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    static void releaseGpuResources() {
        if (lease != null) lease.close();
        else destroyGpuResources();
    }

    private static void ownResources() {
        if (lease == null) {
            lease = ClientUiKernel.ownNativeGpu(ModuleDescriptor.ResourceKind.RENDER_TARGET,
                    UiId.parse("sh_better_ui:native/text_readability/scratch"),
                    TextReadabilityPainter::destroyGpuResources);
        }
    }

    private static void destroyGpuResources() {
        lease = null;
        actualFailed = false;
        budgetFrame = -1;
        if (group == null && composite == null && actualGpu == null) return;
        GuiStateGuard.draw(() -> {
            try (var ignored = new NativeBindings()) {
                if (group != null) {
                    group.backdrop.destroyBuffers();
                    group.painted.destroyBuffers();
                }
                if (composite != null) composite.close();
                if (actualGpu != null) actualGpu.close();
                group = null;
                composite = null;
                actualGpu = null;
            }
        });
    }

    /** Allocate only after the target is reachable by the failure cleanup path. */
    private static final class ScratchTarget extends RenderTarget {
        ScratchTarget() { super(false); }
    }

    private record GroupTargets(RenderTarget backdrop, RenderTarget painted) {
        void ensureSize(int width, int height, int viewportWidth, int viewportHeight) {
            if (backdrop.width >= width && backdrop.height >= height
                    && backdrop.width <= viewportWidth && backdrop.height <= viewportHeight) return;
            int nextWidth = Math.min(viewportWidth, Math.max(backdrop.width, width));
            int nextHeight = Math.min(viewportHeight, Math.max(backdrop.height, height));
            backdrop.resize(nextWidth, nextHeight, Minecraft.ON_OSX);
            painted.resize(nextWidth, nextHeight, Minecraft.ON_OSX);
        }
    }

    record Scissor(int left, int bottom, int right, int top) {
        static Scissor of(PixelRect pixels, int framebufferHeight) {
            int top = framebufferHeight - pixels.getY();
            return new Scissor(pixels.getX(), top - pixels.getH(), pixels.getX() + pixels.getW(), top);
        }

        Scissor intersectActive() {
            if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) return this;
            var box = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            return intersect(new Scissor(box[0], box[1], box[0] + box[2], box[1] + box[3]));
        }

        Scissor intersect(Scissor other) {
            return new Scissor(Math.max(left, other.left), Math.max(bottom, other.bottom),
                    Math.min(right, other.right), Math.min(top, other.top));
        }
        Scissor offset(int x, int y) {
            return new Scissor(left + x, bottom + y, right + x, top + y);
        }

        int width() { return right - left; }
        int height() { return top - bottom; }
        boolean empty() { return width() <= 0 || height() <= 0; }
        void enable() { RenderSystem.enableScissor(left, bottom, width(), height()); }
    }

    /** Native text can bind its two samplers to physical units zero and one. */
    private static final class NativeBindings implements AutoCloseable {
        private final int activeTexture = GlStateManager._getActiveTexture();
        private final int activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private final int shaderTexture = RenderSystem.getShaderTexture(0);
        private final int overlayTexture = RenderSystem.getShaderTexture(1);
        private final int lightTexture = RenderSystem.getShaderTexture(2);
        private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final float[] clearColor = new float[4];
        private final int atlasBinding = textureBinding(GL13.GL_TEXTURE0);
        private final int secondSamplerBinding = textureBinding(GL13.GL_TEXTURE1);
        private final int lightBinding = textureBinding(GL13.GL_TEXTURE2);

        NativeBindings() {
            RenderSystem.activeTexture(activeTexture);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        }

        private static int textureBinding(int unit) {
            RenderSystem.activeTexture(unit);
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        @Override public void close() {
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.setShaderTexture(0, shaderTexture);
            RenderSystem.setShaderTexture(1, overlayTexture);
            RenderSystem.setShaderTexture(2, lightTexture);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(atlasBinding);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.bindTexture(secondSamplerBinding);
            RenderSystem.activeTexture(GL13.GL_TEXTURE2);
            RenderSystem.bindTexture(lightBinding);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.bindTexture(activeBinding);
            BufferUploader.invalidate();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        }
    }

    private record Composite(int program, int vao, int origin, int backdropSampler, int opacity) implements AutoCloseable {
        private static final String VERTEX = """
                #version 150
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        private static final String FRAGMENT = """
                #version 150
                uniform sampler2D Source;
                uniform sampler2D Backdrop;
                uniform ivec2 Origin;
                uniform float Opacity;
                out vec4 color;

                vec3 toLinear(vec3 rgb) {
                    return mix(rgb / 12.92, pow((rgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), rgb));
                }

                vec3 toSrgb(vec3 rgb) {
                    return mix(12.92 * rgb, 1.055 * pow(rgb, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), rgb));
                }

                void main() {
                    ivec2 pixel = ivec2(gl_FragCoord.xy) - Origin;
                    vec4 before = texelFetch(Backdrop, pixel, 0);
                    vec4 after = texelFetch(Source, pixel, 0);
                    vec3 mixed = toSrgb(mix(toLinear(before.rgb), toLinear(after.rgb), Opacity));
                    color = vec4(mixed, mix(before.a, after.a, Opacity));
                }
                """;

        static Composite create() {
            int vertex = 0, fragment = 0, program = 0, vao = 0;
            boolean success = false;
            try {
                vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
                fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
                }
                vao = GL30.glGenVertexArrays();
                var composite = new Composite(program, vao, GL20.glGetUniformLocation(program, "Origin"),
                        GL20.glGetUniformLocation(program, "Backdrop"), GL20.glGetUniformLocation(program, "Opacity"));
                success = true;
                return composite;
            } finally {
                if (vertex != 0) GL20.glDeleteShader(vertex);
                if (fragment != 0) GL20.glDeleteShader(fragment);
                if (!success) {
                    if (program != 0) GL20.glDeleteProgram(program);
                    if (vao != 0) GL30.glDeleteVertexArrays(vao);
                }
            }
        }

        private static int compile(int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) return shader;
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }

        void draw(int texture, int backdrop, Scissor clip, float groupOpacity) {
            boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            try (var state = new GlassDrawState()) {
                state.bind(program, vao, texture);
                if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                GL20.glUniform2i(origin, clip.left, clip.bottom);
                GL20.glUniform1f(opacity, groupOpacity);
                GL20.glUniform1i(backdropSampler, 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                int previousSampler = GL.getCapabilities().glBindSampler == 0
                        ? -1 : GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                try {
                    if (previousSampler >= 0) GL33.glBindSampler(1, 0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdrop);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                } finally {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                    if (previousSampler >= 0) GL33.glBindSampler(1, previousSampler);
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                }
            }
        }

        @Override public void close() {
            GL20.glDeleteProgram(program);
            GL30.glDeleteVertexArrays(vao);
        }
    }
}
