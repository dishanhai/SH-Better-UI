// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.client.visual.RichTextEffects;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;
import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Native readback and submission-time comparison for the rich-text backdrop and foreground policy. */
final class RichTextEffectsSmoke {
    private static final int PANEL_X = 20;
    private static final int PANEL_Y = 22;
    private static final int PANEL_WIDTH = 104;
    private static final int PANEL_HEIGHT = 42;
    private static final int CLIP_WIDTH = 96;
    private static final int WARMUP = 30;
    private static final int SAMPLES = 100;
    private static final int TEXT_LEFT = 4;
    private static final int TEXT_TOP = 5;

    private enum Mode {
        BASELINE(new RichTextEffects(GlassStyle.NONE, false, new RevealProfile(0, 0.15f, 0.85f),
                0, 0, 0, true)),
        REVEAL(RichTextEffects.REVEAL),
        HIGH_CONTRAST(RichTextEffects.REVEAL);

        final RichTextEffects effects;

        Mode(RichTextEffects effects) { this.effects = effects; }
    }

    private final Screen parent;
    private final Probe probe;
    private final Path output;
    private String baseline;
    private String effects;
    private long pixelPasses;

    RichTextEffectsSmoke(Minecraft client, ModuleContext module, Path output) {
        parent = client.screen;
        probe = new Probe(module);
        this.output = output;
        if (client.getWindow().getGuiScale() != 2) {
            client.options.guiScale().set(2);
            client.resizeDisplay();
        }
        client.setScreen(probe);
    }

    boolean advance(Minecraft client) throws IOException {
        require(client.screen == probe, "Rich-text effects probe closed before completion");
        if (probe.failure != null) throw new AssertionError("Rich-text effects callback failed", probe.failure);
        if (probe.last == null) return false;
        require(client.getWindow().getGuiScale() == 2, "Effects probe requires GUI Scale 2");
        require(probe.presented == probe.binding.currentSnapshot().orElseThrow(),
                "Rich-text foreground did not present the same layout snapshot as effects");
        switch (probe.mode) {
            case BASELINE -> {
                if (probe.timings.size() < SAMPLES) return false;
                baseline = probe.stats();
                probe.switchTo(Mode.REVEAL);
            }
            case REVEAL -> {
                if (probe.timings.size() < SAMPLES) return false;
                var sample = probe.last;
                require(sample.glass() == DRAWN && sample.gradient() == DRAWN && sample.reveal() == DRAWN
                                && sample.lateBlur() == ACCEPTED && sample.deferred(),
                        "Rich text effects did not execute on the native backend: "
                                + List.of(sample.glass(), sample.gradient(), sample.reveal(), sample.lateBlur()));
                pixelPasses = sample.plannedPixelPasses();
                require(pixelPasses == (long) CLIP_WIDTH * PANEL_HEIGHT * 4 * 7,
                        "Effects budget ignored physical pixels or the active viewport clip: " + pixelPasses);
                require(probe.immediateState && probe.foregroundState, "Effects state restoration was not checked");
                effects = probe.stats();
                capture(client, false, "rich-text-effects.png");
                probe.switchTo(Mode.HIGH_CONTRAST);
            }
            case HIGH_CONTRAST -> {
                var sample = probe.last;
                require(sample.glass() == DISABLED && sample.gradient() == DISABLED && sample.reveal() == DISABLED
                                && sample.lateBlur() == DISABLED && sample.plannedPixelPasses() == 0,
                        "High contrast did not skip optional framebuffer passes");
                capture(client, true, "rich-text-effects-high-contrast.png");
                var frame = probe.presented;
                client.setScreen(parent);
                require(!frame.isValid(), "Rich-text effects retained the closed Screen presentation");
                return true;
            }
        }
        return false;
    }

    String result() {
        return "rich text effects native smoke passed\n"
                + "Liquid Glass, gradient, VisualReveal, late blur and crisp foreground used one layout snapshot\n"
                + "clip, GL state restoration, high-contrast fallback and scope closure passed\n"
                + "GUI Scale 2, " + WARMUP + " warmup frames and " + SAMPLES + " timed frames per mode, "
                + "render-thread elapsed from binding entry to foreground flush (includes driver waits)\n"
                + "GPU timestamp window is binding entry through foreground flush, asynchronous readback; "
                + "frame interval is between starts of consecutive probe Screen.render calls\n"
                + "flat baseline: " + baseline + "\n"
                + "full effects: " + effects + ", planned_pixel_passes=" + pixelPasses + "\n";
    }

    private void capture(Minecraft client, boolean highContrast, String file) throws IOException {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int background = color(image, client, PANEL_X + CLIP_WIDTH - 4, PANEL_Y + 17);
            double floor = highContrast ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO;
            int readable = 0;
            for (int y = PANEL_Y + TEXT_TOP; y < PANEL_Y + TEXT_TOP + probe.glyphHeight; y++) {
                for (int x = PANEL_X + TEXT_LEFT; x < PANEL_X + TEXT_LEFT + probe.glyphWidth; x++) {
                    int text = color(image, client, x, y);
                    if ((text & 0xffffff) > 0xdedede && ContrastPolicy.ratio(text, background, 0xff000000) >= floor) {
                        readable++;
                    }
                }
            }
            require(readable > 5, "Framebuffer did not contain the protected foreground glyphs");
            int outside = PANEL_X + CLIP_WIDTH + 2;
            require(color(image, client, outside, PANEL_Y + 12) == stripe(outside),
                    "Effects crossed the visual viewport clip");
            require((background & 0xffffff) < 0x646464, "Contrast backing was missing");
            if (highContrast) require(background == 0xff090c12, "High-contrast backing was not opaque");
            image.writeToFile(output.resolve(file).toFile());
        }
    }

    private static int color(NativeImage image, Minecraft client, int x, int y) {
        int abgr = KernelClientSmoke.pixel(image, client, x, y);
        return (abgr & 0xff00ff00) | ((abgr & 0x00ff0000) >>> 16) | ((abgr & 0x000000ff) << 16);
    }

    private static String latency(List<Long> samples) {
        var sorted = samples.stream().sorted().toList();
        return String.format(Locale.ROOT, "n=%d, mean_us=%.2f, p95_us=%.2f", sorted.size(),
                samples.stream().mapToLong(Long::longValue).average().orElseThrow() / 1000,
                sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1) / 1000.0);
    }

    private static int stripe(int x) { return x % 2 == 0 ? 0xffffffff : 0xff000000; }

    private static final class Probe extends Screen {
        static { ShBetterUiApi.blur().allowScreen(Probe.class::isInstance); }

        private final ModuleContext module;
        private final List<Long> timings = new ArrayList<>();
        private final List<Long> intervals = new ArrayList<>();
        private GpuTimers gpu;
        private ScopeHandle scope;
        private AlignmentLayout.Node panel;
        private VisualLayoutBinding binding;
        private Mode mode = Mode.BASELINE;
        private FrameViewSnapshot presented;
        private RichTextEffects.Sample last;
        private Throwable failure;
        private int frames;
        private int glyphWidth;
        private int glyphHeight;
        private boolean immediateState;
        private boolean foregroundState;
        private long previousFrame;

        Probe(ModuleContext module) {
            super(Component.translatable("gui.done").withStyle(style -> style.withColor(0x091019)));
            this.module = module;
        }

        @Override protected void init() {
            if (scope != null && scope.isOpen()) return;
            var engine = module.engine();
            scope = engine.require(UIApis.SCREENS).mount(module, this, UiId.parse("sh_better_ui:rich_effects_smoke"));
            var layout = ShBetterUiApi.layout().create(viewport());
            panel = layout.node("panel", AlignmentFrame.box(new Rect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT))
                    .withClip(new Rect(0, 0, CLIP_WIDTH, PANEL_HEIGHT)));
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, scope, layout, time -> {});
            glyphWidth = font.width(title);
            glyphHeight = font.lineHeight;
            gpu = GpuTimers.open();
        }

        @Override public void removed() {
            if (gpu != null) gpu.close();
            if (scope != null) scope.close();
            super.removed();
        }

        private Bounds viewport() { return new Bounds(0, 0, width, height); }

        void switchTo(Mode next) {
            mode = next;
            frames = 0;
            last = null;
            presented = null;
            timings.clear();
            intervals.clear();
            previousFrame = 0;
            if (gpu != null) gpu.reset();
        }

        String stats() {
            return "CPU[" + latency(timings) + "], GPU[" + (gpu == null ? "unavailable" : gpu.stats())
                    + "], frame interval[" + latency(intervals) + "]";
        }

        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            long frameStart = System.nanoTime();
            long interval = frameStart - previousFrame;
            previousFrame = frameStart;
            for (int x = 0; x < width; x++) graphics.fill(x, 0, x + 1, height, stripe(x));
            if (minecraft.getOverlay() != null) return;
            graphics.flush();
            var preferences = mode == Mode.HIGH_CONTRAST ? new UiPreferences(true, true, true) : UiPreferences.DEFAULT;
            var frame = new UiFrame(graphics, viewport(), UiMouseState.read(), preferences,
                    UiFrameScheduler.nowMs(), partialTick);
            frames++;
            boolean verifyState = mode == Mode.REVEAL && frames == 1;
            boolean measure = mode != Mode.HIGH_CONTRAST && frames > WARMUP && timings.size() < SAMPLES;
            if (measure) intervals.add(interval);
            GpuTimers.Pair timer = measure && gpu != null ? gpu.begin() : null;
            long start = System.nanoTime();
            binding.renderScreen(frame, pass -> pass.at(panel, visual -> verify(() -> {
                var snapshot = pass.snapshot();
                int[] before = verifyState ? state() : null;
                var sample = mode.effects.drawBackdrop(visual, snapshot, preferences);
                if (verifyState) {
                    require(Arrays.equals(before, state()), "Immediate effects changed the enclosing GUI GL state");
                    immediateState = true;
                }
                if (sample.deferred()) {
                    // The two callbacks share a snapshot and clip but each has its own guarded graphics context.
                    int[][] foregroundBefore = verifyState ? new int[1][] : null;
                    visual.foreground(0, front -> verify(() -> {
                        require(front.snapshot() == snapshot, "Deferred foreground acquired another snapshot");
                        if (verifyState) foregroundBefore[0] = state();
                        paint(front.graphics(), snapshot, sample, start, measure, timer);
                    }));
                    if (verifyState) visual.foreground(1, front -> verify(() -> {
                        require(Arrays.equals(foregroundBefore[0], state()), "Foreground failed to restore GL state");
                        foregroundState = true;
                    }));
                } else {
                    visual.draw(local -> paint(local, snapshot, sample, start, measure, timer));
                }
            })));
        }

        private void paint(GuiGraphics graphics, FrameViewSnapshot snapshot, RichTextEffects.Sample sample, long start,
                           boolean measure, GpuTimers.Pair timer) {
            sample.drawReadabilityPlate(graphics);
            sample.drawText(graphics, font, title.getVisualOrderText(),
                    new RichLayoutSnapshot.Rect(TEXT_LEFT, TEXT_TOP, glyphWidth, glyphHeight), 0xff091019);
            graphics.flush();
            if (measure) timings.add(System.nanoTime() - start);
            if (timer != null) timer.end();
            presented = snapshot;
            last = sample;
        }

        private void verify(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | AssertionError error) {
                failure = error;
                throw error;
            }
        }

        private static int[] state() {
            var viewport = new int[4];
            var scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (clipped) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            var color = RenderSystem.getShaderColor();
            return new int[]{
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), viewport[0], viewport[1], viewport[2], viewport[3],
                    clipped ? 1 : 0, scissor[0], scissor[1], scissor[2], scissor[3],
                    GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0, GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0,
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0, GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0,
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    Float.floatToIntBits(color[0]), Float.floatToIntBits(color[1]),
                    Float.floatToIntBits(color[2]), Float.floatToIntBits(color[3])
            };
        }
    }

    private static final class GpuTimers {
        private final Pair[] ring = new Pair[8];
        private final List<Long> samples = new ArrayList<>();

        static GpuTimers open() {
            var caps = GL.getCapabilities();
            return caps.OpenGL33 || caps.GL_ARB_timer_query ? new GpuTimers() : null;
        }

        private GpuTimers() {
            for (int i = 0; i < ring.length; i++) ring[i] = new Pair();
        }

        Pair begin() {
            collect();
            for (var pair : ring) {
                if (!pair.pending) {
                    GL33.glQueryCounter(pair.start, GL33.GL_TIMESTAMP);
                    return pair;
                }
            }
            return null;
        }

        private void collect() {
            for (var pair : ring) {
                if (pair.pending && GL15.glGetQueryObjecti(pair.stop, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                    long start = GL33.glGetQueryObjectui64(pair.start, GL15.GL_QUERY_RESULT);
                    long stop = GL33.glGetQueryObjectui64(pair.stop, GL15.GL_QUERY_RESULT);
                    if (stop > start) samples.add(stop - start);
                    pair.pending = false;
                }
            }
        }

        String stats() {
            collect();
            return samples.isEmpty() ? "no completed timestamps" : latency(samples);
        }

        void reset() {
            samples.clear();
            for (var pair : ring) pair.pending = false;
        }

        void close() {
            for (var pair : ring) {
                GL15.glDeleteQueries(pair.start);
                GL15.glDeleteQueries(pair.stop);
            }
        }

        private static final class Pair {
            private final int start = GL15.glGenQueries();
            private final int stop = GL15.glGenQueries();
            private boolean pending;

            void end() {
                GL33.glQueryCounter(stop, GL33.GL_TIMESTAMP);
                pending = true;
            }
        }
    }
}
