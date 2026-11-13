// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Insets;
import com.dishanhai.sh_better_ui.client.visual.VisualProjection;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.ActualInput;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.ActualResult;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilityPainter.Scissor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** GL 3.2 same-frame background, mask, decision, and compositing passes for one positioned text run. */
final class TextReadabilityGpu implements AutoCloseable {
    static final int MAX_WIDTH = 2048;
    static final int MAX_HEIGHT = 256;
    static final int MAX_FRAME_COPY_PIXELS = 1_048_576;
    private static final int MAX_SAMPLES = 32;
    private static final int STATE_COLUMNS = 32;
    private static final int STATE_ROWS = 64;
    private static final float[] TRANSPARENT = {0, 0, 0, 0};

    private final TextReadabilityTelemetry telemetry = new TextReadabilityTelemetry(MAX_SAMPLES);
    private final Framebuffer backdrop = new Framebuffer(1);
    private final Framebuffer localBackdrop = new Framebuffer(1);
    private final Framebuffer mask = new Framebuffer(1);
    private final Framebuffer original = new Framebuffer(1);
    private final Framebuffer samples = new Framebuffer(1);
    private final Framebuffer decision = new Framebuffer(4);
    private final Framebuffer state = new Framebuffer(1);
    private Program sampleProgram;
    private Program decisionProgram;
    private Program compositeProgram;
    private int vertices;

    static boolean supportsSource(int mainFramebuffer) {
        return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == mainFramebuffer
                && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0) == GL30.GL_COLOR_ATTACHMENT0
                && !GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
                && !GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP)
                && !GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)
                && GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
                && attachment(GL30.GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING) == GL11.GL_LINEAR
                && attachment(GL30.GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE) == GL30.GL_UNSIGNED_NORMALIZED;
    }

    static boolean supportsProjection(VisualProjection.Target view) {
        var projection = RenderSystem.getProjectionMatrix();
        var modelView = RenderSystem.getModelViewMatrix();
        // GUI pose X/Y are expressed in native GUI coordinates. A third-party projection can
        // change their meaning, in which case its background cannot use this pixel mapping.
        return near(projection.m00(), 2 / view.guiWidth()) && near(projection.m11(), -2 / view.guiHeight())
                && near(projection.m30(), -1) && near(projection.m31(), 1) && near(projection.m33(), 1)
                && projection.m01() == 0 && projection.m10() == 0 && projection.m20() == 0 && projection.m21() == 0
                && projection.m03() == 0 && projection.m13() == 0 && projection.m23() == 0
                && modelView.m00() == 1 && modelView.m11() == 1 && modelView.m33() == 1
                && modelView.m01() == 0 && modelView.m10() == 0 && modelView.m20() == 0 && modelView.m21() == 0
                && modelView.m03() == 0 && modelView.m13() == 0 && modelView.m23() == 0
                && modelView.m30() == 0 && modelView.m31() == 0;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= Math.max(1e-8, Math.abs(b) * 1e-6);
    }

    private static int attachment(int parameter) {
        return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, parameter);
    }

    static TextReadabilityGpu create() {
        var gpu = new TextReadabilityGpu();
        int vertex = 0;
        boolean ready = false;
        try {
            if (GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS) < 4) {
                throw new IllegalStateException("Four draw buffers are required");
            }
            vertex = Program.compile(GL20.GL_VERTEX_SHADER, "text_readability.vsh");
            gpu.sampleProgram = Program.link(vertex, "text_readability_sample.fsh", "Sample");
            gpu.decisionProgram = Program.link(vertex, "text_readability_decide.fsh",
                    "NextState", "Decision", "Metrics", "NextTiming");
            gpu.compositeProgram = Program.link(vertex, "text_readability_composite.fsh", "color");
            gpu.vertices = GL30.glGenVertexArrays();
            if (gpu.vertices == 0) throw new IllegalStateException("Could not allocate a text readability VAO");
            gpu.samples.allocate(MAX_SAMPLES, 1, GL30.GL_RGBA32F);
            gpu.decision.allocate(1, 1, GL30.GL_RGBA32UI, GL30.GL_RGBA32UI, GL30.GL_RGBA32F, GL30.GL_RGBA32UI);
            gpu.state.allocate(STATE_COLUMNS * 2, STATE_ROWS, GL30.GL_RGBA32UI);
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            GL30.glClearBufferuiv(GL11.GL_COLOR, 0, new int[4]);
            ready = true;
            return gpu;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (!ready) gpu.close();
        }
    }

    ActualResult draw(int main, int[] viewport, Scissor clip, Scissor sampleBox, VisualProjection.Target projection,
                      Request request, ActualInput input, Runnable paintMask, Runnable paintOriginal) {
        int budget = Math.min(MAX_SAMPLES, Math.min(input.sampleBudget(), sampleBox.width() * sampleBox.height()));
        if (budget <= 0) return new ActualResult(Result.RENDER_UNAVAILABLE, 0, 0, false, true, false);
        if (input.stateSlot() >= STATE_COLUMNS * STATE_ROWS) throw new IllegalArgumentException("Invalid text state slot");
        var time = telemetry.beginCost(input.frame());
        boolean queued;
        try {
            backdrop.grow(clip, GL11.GL_RGBA8);
            mask.grow(clip, GL11.GL_RGBA8);
            if (paintOriginal != null) original.grow(clip, GL11.GL_RGBA8);
            copyBackdrop(main, clip, backdrop);
            paintScratch(mask, viewport, clip, paintMask);
            if (paintOriginal != null) paintScratch(original, viewport, clip, paintOriginal);

            var pixels = PixelTransform.of(request, projection, clip);
            try (var textures = new TextureUnits(4); var draw = new GlassDrawState()) {
                try {
                    RenderSystem.disableScissor();
                    sample(draw, clip, sampleBox, pixels, budget, input);
                    analyze(draw, request, budget, input);
                    if (input.stateSlot() >= 0) storeState(input.stateSlot());
                    composite(draw, main, viewport, clip, pixels, request, paintOriginal != null, input);
                } finally {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                }
            }
        } finally {
            telemetry.endCost(time);
        }
        queued = input.diagnosticId() != 0 && telemetry.enqueue(input, viewport[3], clip, budget,
                decision.framebuffer, samples.framebuffer);
        return new ActualResult(Result.DRAWN, budget, clip.width() * clip.height(), queued, false, false);
    }

    void captureLocalBackdrop(int main, Scissor clip) {
        localBackdrop.grow(clip, GL11.GL_RGBA8);
        copyBackdrop(main, clip, localBackdrop);
    }

    private static void copyBackdrop(int main, Scissor clip, Framebuffer destination) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main);
        int previous = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            destination.bind();
            GL30.glBlitFramebuffer(clip.left(), clip.bottom(), clip.right(), clip.top(),
                    0, 0, clip.width(), clip.height(), GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL11.glReadBuffer(previous);
        }
    }

    private static void paintScratch(Framebuffer target, int[] viewport, Scissor clip, Runnable paint) {
        target.bind();
        RenderSystem.viewport(-clip.left(), -clip.bottom(), viewport[2], viewport[3]);
        RenderSystem.enableScissor(0, 0, clip.width(), clip.height());
        RenderSystem.colorMask(true, true, true, true);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, TRANSPARENT);
        paint.run();
    }

    private void sample(GlassDrawState draw, Scissor clip, Scissor region, PixelTransform pixels, int budget,
                        ActualInput input) {
        samples.bind();
        RenderSystem.viewport(0, 0, budget, 1);
        var captured = input.localBackdrop();
        bind(draw, sampleProgram, captured == null ? backdrop.texture() : localBackdrop.texture());
        texture(1, mask.texture());
        sampleProgram.integer("Backdrop", 0);
        sampleProgram.integer("Mask", 1);
        sampleProgram.pair("MaskSize", clip.width(), clip.height());
        sampleProgram.pair("BackdropOffset", captured == null ? 0 : clip.left() - captured.left(),
                captured == null ? 0 : clip.bottom() - captured.bottom());
        sampleProgram.ints("SampleBox", region.left() - clip.left(), region.bottom() - clip.bottom(),
                region.width(), region.height());
        int rows = Math.min(Math.min(region.height(), budget),
                Math.max(1, (int) Math.round(Math.sqrt((double) budget * region.height() / region.width()))));
        rows = Math.max(rows, (budget + region.width() - 1) / region.width());
        sampleProgram.pair("Grid", budget, rows);
        pixels.outline(sampleProgram);
        sampleProgram.scalar("InteriorCoverage", input.sampling().minimumGlyphInteriorCoverage());
        drawTriangle();
    }

    private void analyze(GlassDrawState draw, Request request, int budget, ActualInput input) {
        decision.bind();
        RenderSystem.viewport(0, 0, 1, 1);
        bind(draw, decisionProgram, samples.texture());
        texture(1, state.texture());
        decisionProgram.integer("Samples", 0);
        decisionProgram.integer("State", 1);
        int slot = Math.max(0, input.stateSlot());
        decisionProgram.pair("Slot", slot % STATE_COLUMNS, slot / STATE_COLUMNS);
        decisionProgram.unsigned("Generation", input.stateSlot() < 0 ? 0 : input.generation());
        decisionProgram.nanos("DeltaNanos", input.deltaNanos());
        decisionProgram.nanos("StabilityNanos", Math.max(input.stabilityNanos(),
                TextReadabilityPolicy.DEFAULT_STABILITY_NANOS));
        decisionProgram.pair("TransitionTiming", TextReadabilityReference.TRANSITION_NANOS,
                TextReadabilityReference.MAX_TRANSITION_STEP_NANOS);
        var style = request.style();
        decisionProgram.unsigned("Original", style.argb());
        decisionProgram.unsigned("InitialFill", input.initialFill() == null ? style.argb() : input.initialFill());
        decisionProgram.unsigned("Declared", request.declaredBackgroundArgb() == null ? 0 : request.declaredBackgroundArgb());
        int protection = style.allowBacking() ? 2 : 0;
        if (style.allowOutline() && input.framebufferHeight() >= input.sampling().minimumOutlineFramebufferPixels()) {
            protection |= 1;
        }
        decisionProgram.ints("Rules", budget, input.sampling().minimumActualSamples(), style.lockHue() ? 1 : 0, protection);
        decisionProgram.floats("Thresholds", (float) input.threshold(), request.groupOpacity());
        drawTriangle();
    }

    Integer displayedFill(int slot, int generation) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        var pack = TextReadabilityTelemetry.Pack.capture();
        int[] values = new int[(STATE_COLUMNS + 1) * 4];
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.framebuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            TextReadabilityTelemetry.Pack.DEFAULT.apply();
            GL11.glReadPixels(slot % STATE_COLUMNS, slot / STATE_COLUMNS, STATE_COLUMNS + 1, 1,
                    GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT, values);
        } finally {
            pack.apply();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
        int timing = STATE_COLUMNS * 4;
        return values[0] != generation ? null
                : TextReadabilityReference.interpolateFill(values[1], values[timing + 2], values[timing + 3]);
    }

    private void storeState(int slot) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, decision.framebuffer);
        texture(1, state.texture());
        int x = slot % STATE_COLUMNS, y = slot / STATE_COLUMNS;
        // Copy only after the decision draw completes logically. The read-only persistent texture
        // is never attached to the active draw framebuffer during the decision.
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, 0, 0, 1, 1);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT3);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x + STATE_COLUMNS, y, 0, 0, 1, 1);
    }

    private void composite(GlassDrawState draw, int main, int[] viewport, Scissor clip, PixelTransform pixels,
                           Request request, boolean hasNativeOriginal, ActualInput input) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        clip.enable();
        bind(draw, compositeProgram, backdrop.texture());
        texture(1, mask.texture());
        texture(2, hasNativeOriginal ? original.texture() : mask.texture());
        texture(3, decision.textures[1]);
        compositeProgram.integer("Backdrop", 0);
        compositeProgram.integer("Mask", 1);
        compositeProgram.integer("NativeOriginal", 2);
        compositeProgram.integer("Analysis", 3);
        compositeProgram.unsigned("Original", request.style().argb());
        compositeProgram.pair("Origin", clip.left(), clip.bottom());
        compositeProgram.pair("MaskSize", clip.width(), clip.height());
        pixels.outline(compositeProgram);
        compositeProgram.pair("ShadowOffset", pixels.shadowX, pixels.shadowY);
        compositeProgram.integer("Shadow", request.style().shadow() ? 1 : 0);
        compositeProgram.integer("HasNativeOriginal", hasNativeOriginal ? 1 : 0);
        GL20.glUniformMatrix2fv(compositeProgram.uniform("PixelToLocal"), false, pixels.inverse);
        compositeProgram.floats("PixelOffset", pixels.offsetX, pixels.offsetY);
        var rect = input.localBacking() == null ? request.run().bounds().outset(new Insets(2, 1, 2, 1))
                : input.localBacking();
        GL20.glUniform4f(compositeProgram.uniform("BackingRect"), (float) rect.x(), (float) rect.y(),
                (float) rect.right(), (float) rect.bottom());
        compositeProgram.scalar("Opacity", request.groupOpacity());
        drawTriangle();
    }

    private void bind(GlassDrawState draw, Program program, int unitZero) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        draw.bind(program.name, vertices, unitZero);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static void drawTriangle() { GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3); }

    private static void texture(int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (GL.getCapabilities().glBindSampler != 0) GL33.glBindSampler(unit, 0);
    }

    void pollDiagnostics(Consumer<TextReadabilityPainter.ActualDiagnostic> accept) { telemetry.pollDiagnostics(accept); }
    void pollCosts(Consumer<TextReadabilityPainter.ActualCost> accept) { telemetry.pollCosts(accept); }
    void clearDiagnostics() { telemetry.clearDiagnostics(); }

    long allocatedBytes() {
        return backdrop.bytes + localBackdrop.bytes + mask.bytes + original.bytes + samples.bytes + decision.bytes + state.bytes
                + telemetry.allocatedBytes();
    }

    @Override public void close() {
        telemetry.close();
        backdrop.close();
        localBackdrop.close();
        mask.close();
        original.close();
        samples.close();
        decision.close();
        state.close();
        if (sampleProgram != null) sampleProgram.close();
        if (decisionProgram != null) decisionProgram.close();
        if (compositeProgram != null) compositeProgram.close();
        if (vertices != 0) GL30.glDeleteVertexArrays(vertices);
    }

    private record PixelTransform(float[] inverse, float offsetX, float offsetY, int xx, int yx, int xy, int yy,
                                  int shadowX, int shadowY) {
        static PixelTransform of(Request request, VisualProjection.Target view, Scissor clip) {
            var transform = request.transform();
            double sx = view.width() / view.guiWidth(), sy = view.height() / view.guiHeight();
            double a = transform.xx() * sx, b = transform.xy() * sx;
            double c = -transform.yx() * sy, d = -transform.yy() * sy;
            double tx = transform.tx() * sx - clip.left();
            double ty = view.height() - transform.ty() * sy - clip.bottom();
            double det = a * d - b * c;
            var inverse = new float[]{(float) (d / det), (float) (-c / det),
                    (float) (-b / det), (float) (a / det)};
            float offsetX = (float) ((b * ty - d * tx) / det);
            float offsetY = (float) ((c * tx - a * ty) / det);
            return new PixelTransform(inverse, offsetX, offsetY, rounded(.75 * a), rounded(.75 * c),
                    rounded(.75 * b), rounded(.75 * d), rounded(a + b), rounded(c + d));
        }

        private static int rounded(double value) {
            return (int) Math.copySign(Math.floor(Math.abs(value) + .5), value);
        }
        void outline(Program program) { program.ints("OutlineOffsets", xx, yx, xy, yy); }
    }

    /** Allocates color-only framebuffers. Sampling images never alias the active destination. */
    private static final class Framebuffer implements AutoCloseable {
        private final int[] textures;
        private int framebuffer;
        private int width, height;
        private long bytes;

        Framebuffer(int colors) { textures = new int[colors]; }

        void grow(Scissor bounds, int format) {
            if (bounds.width() <= width && bounds.height() <= height) return;
            int w = Math.max(width, (bounds.width() + 63) & ~63);
            int h = Math.max(height, (bounds.height() + 31) & ~31);
            allocate(w, h, format);
        }

        void allocate(int w, int h, int... formats) {
            close();
            framebuffer = GL30.glGenFramebuffers();
            if (framebuffer == 0) throw new IllegalStateException("Could not allocate text readability framebuffer");
            bind();
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            try {
                var attachments = new int[textures.length];
                for (int i = 0; i < textures.length; i++) {
                    int format = formats[i];
                    int texture = textures[i] = GL11.glGenTextures();
                    if (texture == 0) throw new IllegalStateException("Could not allocate text readability texture");
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                    int channels = format == GL30.GL_RGBA32UI ? GL30.GL_RGBA_INTEGER : GL11.GL_RGBA;
                    int type = switch (format) {
                        case GL30.GL_RGBA32UI -> GL11.GL_UNSIGNED_INT;
                        case GL30.GL_RGBA32F -> GL11.GL_FLOAT;
                        default -> GL11.GL_UNSIGNED_BYTE;
                    };
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, channels, type, 0L);
                    attachments[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachments[i], GL11.GL_TEXTURE_2D, texture, 0);
                    bytes += (long) w * h * (format == GL11.GL_RGBA8 ? 4 : 16);
                }
                GL20.glDrawBuffers(attachments);
                if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Incomplete text readability framebuffer");
                }
                width = w;
                height = h;
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
            }
        }

        void bind() { GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer); }
        int texture() { return textures[0]; }

        @Override public void close() {
            if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = width = height = 0;
            bytes = 0;
            for (int i = 0; i < textures.length; i++) {
                if (textures[i] != 0) GL11.glDeleteTextures(textures[i]);
                textures[i] = 0;
            }
        }
    }

    private static final class TextureUnits implements AutoCloseable {
        private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final boolean samplers = GL.getCapabilities().glBindSampler != 0;
        private final int[] textures;
        private final int[] bindings;

        TextureUnits(int count) {
            textures = new int[count];
            bindings = new int[count];
            for (int unit = 0; unit < count; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                if (samplers) bindings[unit] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
            GL13.glActiveTexture(active);
        }

        @Override public void close() {
            for (int unit = 0; unit < textures.length; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
                if (samplers) GL33.glBindSampler(unit, bindings[unit]);
            }
            GL13.glActiveTexture(active);
        }
    }

    private static final class Program implements AutoCloseable {
        private final int name;
        private final Map<String, Integer> uniforms = new HashMap<>();

        private Program(int name) { this.name = name; }

        static Program link(int vertex, String file, String... outputs) {
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, file);
            int program = GL20.glCreateProgram();
            boolean linked = false;
            try {
                if (program == 0) throw new IllegalStateException("Could not allocate " + file);
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                for (int i = 0; i < outputs.length; i++) GL30.glBindFragDataLocation(program, i, outputs[i]);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException(file + ": " + GL20.glGetProgramInfoLog(program));
                }
                GL20.glDetachShader(program, vertex);
                GL20.glDetachShader(program, fragment);
                linked = true;
                return new Program(program);
            } finally {
                GL20.glDeleteShader(fragment);
                if (!linked && program != 0) GL20.glDeleteProgram(program);
            }
        }

        static int compile(int type, String file) {
            String source;
            var resource = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "shaders/" + file);
            try (var input = Minecraft.getInstance().getResourceManager().open(resource)) {
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read " + resource, failure);
            }
            int shader = GL20.glCreateShader(type);
            if (shader == 0) throw new IllegalStateException("Could not allocate " + file);
            boolean compiled = false;
            try {
                GL20.glShaderSource(shader, source);
                GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                    throw new IllegalStateException(file + ": " + GL20.glGetShaderInfoLog(shader));
                }
                compiled = true;
                return shader;
            } finally {
                if (!compiled) GL20.glDeleteShader(shader);
            }
        }

        int uniform(String key) {
            return uniforms.computeIfAbsent(key, name -> {
                int location = GL20.glGetUniformLocation(this.name, name);
                if (location < 0) throw new IllegalStateException("Missing text readability shader uniform " + name);
                return location;
            });
        }
        void integer(String key, int value) { GL20.glUniform1i(uniform(key), value); }
        void unsigned(String key, int value) { GL30.glUniform1ui(uniform(key), value); }
        void nanos(String key, long value) { GL30.glUniform2ui(uniform(key), (int) value, (int) (value >>> 32)); }
        void scalar(String key, double value) { GL20.glUniform1f(uniform(key), (float) value); }
        void pair(String key, int x, int y) { GL20.glUniform2i(uniform(key), x, y); }
        void floats(String key, float x, float y) { GL20.glUniform2f(uniform(key), x, y); }
        void ints(String key, int x, int y, int z, int w) { GL20.glUniform4i(uniform(key), x, y, z, w); }

        @Override public void close() { GL20.glDeleteProgram(name); }
    }
}
