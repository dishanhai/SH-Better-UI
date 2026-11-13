// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.visual.VisualProjection;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;

/** Native optical pass. The kernel owns its only capture and program for the current client lifetime. */
final class ClientGlassRender implements GlassService, AutoCloseable {
    private static final UiId HUD_TEXT = new UiId("sh_better_ui", "hud_text");
    private static final UiId PREVIEW_TEXT = new UiId("sh_better_ui", "hud_preview_text");
    private static final int MAX_HUD_PALETTES = 128;
    private static final long HUD_PALETTE_IDLE_NANOS = 5_000_000_000L;

    private final KernelHost host;
    private final GlassSceneCapture capture;
    private final ClientBackgroundSampler hudText = new ClientBackgroundSampler(8, 100_000_000L);
    private final LinkedHashMap<HudKey, HudPalette> hudPalettes = new LinkedHashMap<>(16, .75f, true);
    private long nextHudSample;
    private GlassProgram program;
    private boolean programFailed;
    private boolean passCaptured;
    private boolean hudTextClosed;
    private boolean updated;
    private long capturedPasses, opticalDraws, fallbackDraws;
    private volatile Diagnostics published = new Diagnostics(0, 0, 0, 0, false);

    ClientGlassRender(KernelHost host) {
        this.host = host;
        capture = new GlassSceneCapture(host::report);
    }

    @Override public Diagnostics diagnostics() { return published; }

    TextReadabilityPalette hudTextPalette(UiId id) {
        return hudPalette(new HudKey(0, id), host.nowNanos()).palette;
    }

    TextReadabilityPalette prepareHudText(GuiGraphics graphics, long layer, UiId id, Rect local,
                                          int darkestArgb, int lightestArgb, TextReadabilityPalette fallback) {
        long now = host.nowNanos();
        var entry = id == null ? null : hudPalette(new HudKey(layer, id), now);
        var palette = entry == null ? fallback : entry.palette;
        var sample = local == null ? null : sampleHudText(graphics, local,
                entry == null ? defaultSample() : entry.sampleId, now);
        palette.update(sample, darkestArgb, lightestArgb, now);
        return palette;
    }

    private HudPalette hudPalette(HudKey key, long now) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft == null ? null : minecraft.level;
        var entry = hudPalettes.get(key);
        if (entry == null || entry.level.get() != level || now - entry.seen >= HUD_PALETTE_IDLE_NANOS) {
            if (entry != null) hudText.forget(entry.sampleId);
            var sampleId = new UiId("sh_better_ui", "hud_sample/" + ++nextHudSample);
            entry = new HudPalette(sampleId, level);
            hudPalettes.put(key, entry);
            if (hudPalettes.size() > MAX_HUD_PALETTES) {
                var oldest = hudPalettes.keySet().iterator().next();
                hudText.forget(hudPalettes.remove(oldest).sampleId);
            }
        }
        entry.seen = now;
        return entry;
    }

    void forgetHudText(long layer) {
        hudPalettes.entrySet().removeIf(entry -> {
            if (entry.getKey().layer != layer) return false;
            if (!hudTextClosed) hudText.forget(entry.getValue().sampleId);
            return true;
        });
    }

    private static UiId defaultSample() {
        return Minecraft.getInstance().screen == null ? HUD_TEXT : PREVIEW_TEXT;
    }

    private static long hudPresentation(Minecraft minecraft) {
        return 31L * System.identityHashCode(minecraft.level) + System.identityHashCode(minecraft.screen);
    }

    BackgroundSample sampleHudText(GuiGraphics graphics, UiId id, Rect local) {
        long now = host.nowNanos();
        var sampleId = id == null ? defaultSample() : hudPalette(new HudKey(0, id), now).sampleId;
        return sampleHudText(graphics, local, sampleId, now);
    }

    private BackgroundSample sampleHudText(GuiGraphics graphics, Rect local, UiId sampleId, long now) {
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        var target = minecraft.getMainRenderTarget();
        double guiScale = window.getGuiScale();
        var pose = graphics.pose().last().pose();
        if (target == null || target.viewWidth != window.getWidth() || target.viewHeight != window.getHeight()
                || target.viewWidth <= 0 || target.viewHeight <= 0 || guiScale <= 0
                || pose.m00() <= 0 || pose.m11() <= 0 || pose.m01() != 0 || pose.m10() != 0
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != target.frameBufferId) return null;
        var projection = new VisualProjection.Target(target.viewWidth, target.viewHeight,
                target.viewWidth / guiScale, target.viewHeight / guiScale);
        var pixels = projection.pixels(new Rect(pose.m30() + local.x() * pose.m00(),
                pose.m31() + local.y() * pose.m11(), local.width() * pose.m00(), local.height() * pose.m11()));
        var clip = TextReadabilityPainter.Scissor.of(pixels, target.viewHeight).intersectActive();
        if (clip.empty()) return null;
        var roi = new PixelRegion(clip.left(), target.viewHeight - clip.top(), clip.width(), clip.height());
        long version = 31 * hudPresentation(minecraft) + target.getColorTextureId();
        hudText.beginFrame(host.frameNumber(), now, version);
        return hudText.request(sampleId, roi, Stage.POST_MATERIAL_PRE_TEXT,
                () -> GuiStateGuard.draw(graphics::flush));
    }

    void begin() {
        capture.begin();
        passCaptured = false;
    }

    void end() {
        capture.end();
        if (updated) publish();
    }

    void paint(GuiGraphics graphics, List<GlassDraw> draws) {
        RenderSystem.assertOnRenderThread();
        if (draws.isEmpty()) return;
        // Both Minecraft versions enable depth testing when GuiGraphics.flush() returns.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            paintBatch(graphics, draws);
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    private void paintBatch(GuiGraphics graphics, List<GlassDraw> draws) {
        var settings = ClientUiSettings.current();
        boolean sampling = settings.widgetBackdropBlur() && settings.backdrop().enabled()
                && draws.stream().anyMatch(draw -> draw.material().quality() != GlassQuality.FALLBACK);
        GlassProgram shader = sampling ? shader() : null;
        GlassSceneCapture.Snapshot scene = null;
        if (shader != null) {
            graphics.flush();
            scene = capture.snapshot();
        }
        updated = true;
        if (scene == null) {
            for (var draw : draws) fallback(graphics, draw);
            fallbackDraws += draws.size();
            return;
        }

        if (!passCaptured) {
            passCaptured = true;
            capturedPasses++;
        }
        var view = scene.viewport();
        var pose = graphics.pose().last().pose();
        var projection = RenderSystem.getProjectionMatrix();
        float sx = projection.m00() * pose.m00() * (view.width() * .5f);
        float sy = -projection.m11() * pose.m11() * (view.height() * .5f);
        float ox = view.x() + (projection.m00() * pose.m30() + projection.m30() + 1f) * (view.width() * .5f);
        float oy = view.y() + (projection.m11() * pose.m31() + projection.m31() + 1f) * (view.height() * .5f);
        float unit = Math.min(sx, sy);
        if (!Float.isFinite(unit) || unit <= 0) return;
        float layerDepth = clipDepth(pose, RenderSystem.getModelViewMatrix(), projection);
        if (!Float.isFinite(layerDepth) || layerDepth < -1f || layerDepth > 1f) return;

        try (var state = new GlassDrawState()) {
            shader.bind(state, scene, layerDepth);
            if (shader.supportsBlurMipmaps()) {
                float texel = Math.max(view.width() / (float) scene.size().width(),
                        view.height() / (float) scene.size().height());
                boolean wideBlur = draws.stream().anyMatch(draw -> needsBlurLevels(draw, 2f * texel / unit));
                shader.setBlurLevel(wideBlur ? capture.prepareBlur() : 0);
            }
            for (var draw : draws) {
                var region = draw.region();
                float halfWidth = region.width() * .5f;
                float halfHeight = region.height() * .5f;
                boolean drawn = shader.draw(draw, view, ox + (region.x() + halfWidth) * sx,
                        oy - (region.y() + halfHeight) * sy, halfWidth * sx, halfHeight * sy, unit, settings.highContrast());
                if (drawn) {
                    if (draw.material().quality() == GlassQuality.FALLBACK) fallbackDraws++;
                    else opticalDraws++;
                }
            }
        }
    }

    static float clipDepth(Matrix4fc pose, Matrix4fc modelView, Matrix4fc projection) {
        var position = new Vector4f(0, 0, 0, 1).mul(pose).mul(modelView).mul(projection);
        return position.z / position.w;
    }

    private static boolean needsBlurLevels(GlassDraw draw, float radiusThreshold) {
        var material = draw.material();
        return draw.profile() == GlassDraw.Profile.SNELL
                && (material.quality() == GlassQuality.BALANCED || material.quality() == GlassQuality.ULTRA)
                && material.optics().scattering() > 0 && material.optics().blurRadius() > radiusThreshold;
    }

    private GlassProgram shader() {
        if (program == null && !programFailed) {
            try {
                program = GlassProgram.load(Minecraft.getInstance().getResourceManager());
            } catch (IOException | RuntimeException | LinkageError failure) {
                programFailed = true;
                host.report("Liquid Glass program unavailable; using solid materials", failure);
            }
        }
        return program;
    }

    private static void fallback(GuiGraphics graphics, GlassDraw draw) {
        var region = draw.region();
        int fill = draw.material().foreground().fallbackArgb();
        if (region.shape() == GlassRegion.Shape.ROUNDED || region.shape() == GlassRegion.Shape.CAPSULE) {
            RoundedCorners.draw(graphics, region.x(), region.y(), region.width(), region.height(),
                    region.radius(), 0f, fill, fill);
            return;
        }
        long top = (int) Math.ceil(region.y() - .5);
        long bottom = (int) Math.ceil((double) region.y() + region.height() - .5);
        long stride = Math.max(1L, (bottom - top + 63L) / 64L);
        double center = region.x() + region.width() * .5;
        for (long row = top; row < bottom; row += stride) {
            long end = Math.min(bottom, row + stride);
            double half = region.halfWidthAt(row + (end - row) * .5);
            int left = (int) Math.ceil(center - half - .5);
            int right = (int) Math.ceil(center + half - .5);
            if (left < right) graphics.fill(left, (int) row, right, (int) end, fill);
        }
    }

    void reload() {
        capture.reset();
        hudText.reset();
        hudPalettes.clear();
        releaseProgram();
        publish();
    }

    private void releaseProgram() {
        if (program != null) {
            program.close();
            program = null;
        }
        programFailed = false;
    }

    private void publish() {
        published = new Diagnostics(capturedPasses, opticalDraws, fallbackDraws, capture.allocatedBytes(), program != null);
        updated = false;
    }

    @Override public void close() {
        try {
            capture.close();
        } finally {
            try {
                hudPalettes.clear();
                hudTextClosed = true;
                hudText.close();
            } finally {
                releaseProgram();
                publish();
            }
        }
    }

    private record HudKey(long layer, UiId panel) {}

    private static final class HudPalette {
        private final TextReadabilityPalette palette = new TextReadabilityPalette();
        private final UiId sampleId;
        private final WeakReference<ClientLevel> level;
        private long seen;

        private HudPalette(UiId sampleId, ClientLevel level) {
            this.sampleId = sampleId;
            this.level = new WeakReference<>(level);
        }
    }
}
