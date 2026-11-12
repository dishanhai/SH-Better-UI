// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.RichTextEffects;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Background;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Decision;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Sample;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.DetailStatus;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Diagnostic;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingHint;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingStatus;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.pixel;
import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Exercises the public API from an ordinary Screen and reads pixels only for the smoke assertions. */
final class TextReadabilityGpuSmoke extends Screen {
    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private static final int AUTHORED_GREEN = 0xff33dd55;
    private static final int X = 18;
    private static final int MOTION_Y = 25;
    private static final int SPLIT_Y = 53;
    private static final int RICH_Y = 81;
    private static final int LOCAL_Y = 99;
    private static final int GLASS_Y = 119;
    private static final int BUDGET_RUNS = 70;
    private static final long STABILITY = 400_000_000;
    private static final double METRIC_TOLERANCE = 0.00002;
    private static final FormattedCharSequence GLYPHS = FormattedCharSequence.forward("MMMMMMMM", Style.EMPTY);
    private static final FormattedCharSequence SMALL_RUN = FormattedCharSequence.forward("MM", Style.EMPTY);
    private static final FormattedCharSequence LOCAL_BOLD = FormattedCharSequence.forward("MM", Style.EMPTY
            .withBold(true).withColor(AUTHORED_GREEN & 0xFFFFFF));
    private static final FormattedCharSequence EMPTY_INK = FormattedCharSequence.forward("        ", Style.EMPTY);
    private static final FormattedCharSequence GREEN_GLYPHS = FormattedCharSequence.forward("MMMMMMMM",
            Style.EMPTY.withColor(AUTHORED_GREEN & 0xffffff));
    private static final FormattedCharSequence MIXED_GLYPHS = FormattedCharSequence.composite(GLYPHS, GREEN_GLYPHS);
    private static final TextId MOTION = text("motion");
    private static final TextId SPLIT = text("split");
    private static final TextId DECLARED = text("declared");
    private static final TextId IMMEDIATE = text("immediate");
    private static final TextId EXPIRED = text("expired");
    private static final TextId EXPLICIT = text("explicit");
    private static final TextId GLASS = text("glass");
    private static final TextId INSUFFICIENT = text("insufficient");
    private static final TextId INVALID_REGION = text("invalid_region");
    private static final TextId UNAVAILABLE = text("unavailable");
    private static final TextId FOREIGN_FRAMEBUFFER = text("foreign_framebuffer");
    private static final TextId AUTHORED = text("authored");
    private static final TextId MIXED = text("mixed");
    private static final TextId LOCAL = text("local_glyphs");
    private static final TextId LOCAL_MIXED = text("local_mixed");
    private static final UiId RICH_DARK = id("rich_dark");
    private static final UiId RICH_LIGHT = id("rich_light");
    private static final UiPreferences PREFERENCES = new UiPreferences(false, false, true);
    private static final TextReadabilityPolicy ADAPTIVE = new TextReadabilityPolicy(true, true,
            null, null, null, STABILITY);
    private static final TextReadabilityPolicy LOCAL_ADAPTIVE = new TextReadabilityPolicy(true, true,
            null, null, null, 0L).withLocalColor(true);
    private static final TextReadabilityService.Style WHITE_TEXT = new TextReadabilityService.Style(WHITE,
            false, TextReadabilityPolicy.Usage.BODY, false, false, true);

    private final ModuleContext module;
    private final TextReadabilityService service;
    private final StringBuilder statistics = new StringBuilder(
            "stage\tframe\truns\tsamples\tcopied_pixels\tbudget_fallbacks\tunavailable\tinvalid_region"
                    + "\tcpu_ns\tgpu_frame\ttimed_runs\tgpu_ns\tallocated_bytes\tpeak_bytes\tlive_slots\n");
    private ScopeHandle page;
    private ScopeHandle richScope;
    private ScopeHandle explicitScope;
    private RichTextView rich;
    private VisualLayoutBinding binding;
    private RenderService.Context expired;
    private Stage stage = Stage.DARK;
    private Stage rendered;
    private int framesInStage;
    private long firstBrightNanos;
    private Throwable failure;
    private String result;

    private enum Stage { DARK, FLASH, STABLE, GLASS, DEGRADATION, INTEROP, OVERLAY, BUDGET }

    TextReadabilityGpuSmoke(ModuleContext module) {
        super(Component.translatable("gui.done"));
        this.module = module;
        service = module.engine().require(UIApis.TEXT_READABILITY);
        GaussBlur.allowScreen(screen -> screen instanceof TextReadabilityGpuSmoke);
    }

    @Override protected void init() {
        if (page != null && page.isOpen()) return;
        var engine = module.engine();
        page = engine.require(UIApis.SCREENS).mount(module, this, id("page"));
        richScope = page.openChild(ScopeKind.COMPONENT, id("rich"));
        explicitScope = page.openChild(ScopeKind.COMPONENT, id("explicit"));
        service.configure(module, page, ADAPTIVE);
        service.configure(module, explicitScope, TextReadabilityPolicy.inherit()
                .withSamplingMode(TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY));
        service.setGpuDiagnostics(module, page, true);

        var layout = new AlignmentLayout(new Rect(0, 0, width, height));
        var node = layout.node("rich", AlignmentFrame.box(new Rect(X, RICH_Y, 200, 14))
                .withClip(new Rect(0, 0, 200, 14)));
        rich = RichTextView.attach(module, richScope, node).readability(module,
                new TextReadabilityPolicy(true, true, null, null, null, 0L).withLocalColor(true));
        rich.prepare(new RichDocument(List.of(new TextRun(RICH_DARK, "MMMMMMMM"),
                new TextRun(RICH_LIGHT, "MMMMMMMM", new RichStyle(null, true, false, false, false, false, null)))),
                font, 200);
        binding = engine.require(UIApis.LAYOUT).bindVisual(module, richScope, layout, time -> {});
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        guard(() -> renderProbe(graphics, mouseX, mouseY, partialTick));
    }

    private void renderProbe(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (rendered != stage) framesInStage = 0;
        rendered = stage;
        framesInStage++;
        require(width >= 350 && height >= 210, "GPU smoke requires a GUI area of at least 350×210");
        graphics.fill(0, 0, width, height, BLACK);
        if (stage == Stage.BUDGET) {
            budgetProbe();
            return;
        }
        if (stage == Stage.DEGRADATION) {
            degradationProbe();
            return;
        }
        if (stage == Stage.INTEROP) {
            interoperabilityProbe(graphics);
            return;
        }
        if (stage == Stage.FLASH || stage == Stage.STABLE) {
            if (firstBrightNanos == 0) firstBrightNanos = System.nanoTime();
            int left = X - 4 - framesInStage % 5;
            graphics.fill(left, MOTION_Y - 5, left + font.width(GLYPHS) + 20, MOTION_Y + 14, WHITE);
        } else {
            graphics.fill(0, MOTION_Y - 5, X - 4, MOTION_Y + 14, WHITE);
        }
        graphics.fill(X + font.width(GLYPHS) / 2, SPLIT_Y - 4, X + font.width(GLYPHS) + 8, SPLIT_Y + 12, WHITE);
        graphics.fill(X + font.width(GLYPHS), RICH_Y - 5, X + 190, RICH_Y + 13, WHITE);
        int localSplit = font.width(SMALL_RUN);
        graphics.fill(X + localSplit, LOCAL_Y - 3, X + localSplit * 3, LOCAL_Y + 12, WHITE);
        graphics.fill(118 + localSplit, LOCAL_Y - 3, 118 + localSplit * 3, LOCAL_Y + 12, WHITE);

        submit(request(page, MOTION, GLYPHS, X, MOTION_Y, BLACK, SamplingHint.FOCUSED));
        submit(request(page, SPLIT, GLYPHS, X, SPLIT_Y, BLACK, SamplingHint.IMPORTANT));
        submit(request(page, DECLARED, GLYPHS, 118, MOTION_Y, BLACK, SamplingHint.DECLARED_ONLY));
        submit(request(explicitScope, EXPLICIT, GLYPHS, 118, SPLIT_Y, BLACK, SamplingHint.DEFAULT));
        submit(request(page, LOCAL, FormattedCharSequence.forward("MMMM", Style.EMPTY), X, LOCAL_Y, BLACK,
                SamplingHint.FOCUSED).withPolicy(LOCAL_ADAPTIVE));
        expect(Result.INVALID_STAGE, service.drawNow(module, expired,
                request(page, EXPIRED, GLYPHS, 210, MOTION_Y, BLACK, SamplingHint.DECLARED_ONLY)));
        expect(Result.SUBMITTED, service.foreground(module, page, 1, context -> guard(() -> {
            expect(Result.DRAWN, service.drawNow(module, context,
                    request(page, IMMEDIATE, GLYPHS, 210, SPLIT_Y, BLACK, SamplingHint.DECLARED_ONLY)));
            var mixed = FormattedCharSequence.composite(SMALL_RUN, LOCAL_BOLD);
            expect(Result.DRAWN, service.drawNow(module, context,
                    request(page, LOCAL_MIXED, mixed, 118, LOCAL_Y, BLACK, SamplingHint.FOCUSED)
                            .withPolicy(LOCAL_ADAPTIVE)));
            expired = context;
        })));

        if (stage == Stage.GLASS) {
            for (int x = 0; x < width; x += 4) {
                int stripe = (x / 4 + framesInStage / 4) % 2 == 0 ? BLACK : WHITE;
                graphics.fill(x, GLASS_Y - 20, x + 4, GLASS_Y + 20, stripe);
            }
            double scale = minecraft.getWindow().getGuiScale();
            GaussBlur.request(0, px(GLASS_Y - 16, scale), px(width, scale), px(33, scale), 1,
                    px(9, scale), 0, 0, 0, 0, GlassStyle.LIQUID_CLEAR);
            submit(request(page, GLASS, GLYPHS, X, GLASS_Y, null, SamplingHint.HOVERED));
        }

        var frame = new UiFrame(graphics, new Bounds(0, 0, width, height), UiMouseState.read(),
                PREFERENCES, UiFrameScheduler.nowMs(), partialTick);
        binding.renderScreen(frame, pass -> rich.draw(pass, WHITE, PREFERENCES, RichTextEffects.NONE, 0, mouseX, mouseY));
    }

    private void budgetProbe() {
        for (int i = 0; i < BUDGET_RUNS; i++) {
            submit(request(page, budgetId(i), SMALL_RUN, 12 + i % 10 * 31, 25 + i / 10 * 17,
                    BLACK, SamplingHint.IMPORTANT));
        }
        // Deliberately submitted after the low-priority crowd.
        submit(request(page, MOTION, GLYPHS, X, GLASS_Y + 30, BLACK, SamplingHint.FOCUSED));
    }

    private void degradationProbe() {
        submit(request(page, INSUFFICIENT, EMPTY_INK, X, MOTION_Y, BLACK, SamplingHint.FOCUSED));
        var tall = request(page, INVALID_REGION, SMALL_RUN, width - 24, 45, BLACK, SamplingHint.IMPORTANT);
        submit(new Request(tall.scope(), tall.text(), tall.run(), tall.style(),
                tall.transform().compose(Transform.scale(1, 16)), tall.clip(), 1, BLACK)
                .withSampling(tall.sampling()));
        expect(Result.SUBMITTED, service.foreground(module, page, 1, context -> guard(() -> {
            boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            try {
                expect(Result.DRAWN, service.drawNow(module, context,
                        request(page, UNAVAILABLE, GLYPHS, X, SPLIT_Y, BLACK, SamplingHint.IMPORTANT)));
            } finally {
                if (!stencil) GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        })));
    }

    private void interoperabilityProbe(GuiGraphics graphics) {
        submit(request(page, AUTHORED, GREEN_GLYPHS, X, MOTION_Y, BLACK, SamplingHint.FOCUSED));
        expect(Result.INVALID_REQUEST, service.submit(module,
                request(page, MIXED, MIXED_GLYPHS, X, SPLIT_Y, BLACK, SamplingHint.IMPORTANT)));
        if (framesInStage == 1) {
            expect(Result.SUBMITTED, service.foreground(module, page, 1, context -> guard(() ->
                    drawForeignFramebuffer(graphics, context))));
        }
    }

    private void drawForeignFramebuffer(GuiGraphics graphics, RenderService.Context context) {
        graphics.flush();
        var nativeState = FramebufferState.snapshot();
        GuiStateGuard.draw(() -> {
            int offset = 8;
            int textureUnit = GlStateManager._getActiveTexture();
            int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            float[] clearColor = new float[4];
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            TextureTarget target = null;
            try {
                target = new TextureTarget(nativeState.width() + 2 * offset, nativeState.height() + 2 * offset,
                        false, Minecraft.ON_OSX);
                target.bindWrite(false);
                RenderSystem.viewport(offset, offset, nativeState.width(), nativeState.height());
                GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[] {0, 0, 0, 1});
                var before = FramebufferState.snapshot();
                expect(Result.DRAWN, service.drawNow(module, context,
                        request(page, FOREIGN_FRAMEBUFFER, GLYPHS, X, MOTION_Y, BLACK, SamplingHint.IMPORTANT)));
                require(before.equals(FramebufferState.snapshot()),
                        "Declared fallback changed the third-party framebuffer bindings or viewport");
                double scale = minecraft.getWindow().getGuiScale();
                try (var image = Screenshot.takeScreenshot(target)) {
                    int white = 0;
                    for (int y = px(MOTION_Y, scale); y < px(MOTION_Y + font.lineHeight, scale); y++) {
                        for (int x = px(X, scale); x < px(X + font.width(GLYPHS), scale); x++) {
                            if (image.getPixelRGBA(offset + x, offset + y) == WHITE) white++;
                        }
                    }
                    require(white > 0, "Declared fallback did not produce glyph pixels in the third-party framebuffer");
                }
            } finally {
                if (target != null) target.destroyBuffers();
                RenderSystem.activeTexture(textureUnit);
                RenderSystem.bindTexture(textureBinding);
                RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            }
        });
        require(nativeState.equals(FramebufferState.snapshot()), "The fixture did not restore the main framebuffer");
    }

    private record FramebufferState(int read, int write, int x, int y, int width, int height) {
        private static FramebufferState snapshot() {
            var viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return new FramebufferState(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    boolean verify(Minecraft client, Path output) throws Exception {
        if (failure != null) throw new AssertionError("Native GPU readability Screen failed", failure);
        if (rendered != stage) return false;
        var stats = service.samplingStatistics(module);
        statistics.append(stage).append('\t').append(stats.sourceFrame()).append('\t')
                .append(stats.analyzedRuns()).append('\t').append(stats.attemptedSamples()).append('\t')
                .append(stats.copiedPixels()).append('\t').append(stats.budgetFallbacks()).append('\t')
                .append(stats.unavailableFallbacks()).append('\t').append(stats.invalidRegions()).append('\t')
                .append(stats.cpuNanos()).append('\t').append(stats.gpuSourceFrame()).append('\t')
                .append(stats.timedGpuRuns()).append('\t').append(stats.gpuNanos()).append('\t')
                .append(stats.allocatedGpuBytes()).append('\t').append(stats.peakGpuBytes()).append('\t')
                .append(stats.liveStateSlots()).append('\n');
        switch (stage) {
            case DARK -> {
                var dark = diagnostic(page, MOTION);
                var split = diagnostic(page, SPLIT);
                require(dark.sampling() == SamplingStatus.GPU && split.sampling() == SamplingStatus.GPU,
                        "Actual background analysis did not run: " + dark.sampling() + ", " + split.sampling());
                require(diagnostic(page, IMMEDIATE).result() == Result.DRAWN
                                && diagnostic(page, EXPIRED).result() == Result.INVALID_STAGE
                                && diagnostic(explicitScope, EXPLICIT).sampling() == SamplingStatus.EXPLICIT_ONLY,
                        "Immediate foreground validity or explicit-only sampling changed");
                require(diagnostic(page, LOCAL).sampling() == SamplingStatus.LOCAL_GPU
                                && diagnostic(page, LOCAL_MIXED).sampling() == SamplingStatus.LOCAL_GPU,
                        "Native single-sequence localized sampling did not run");
                if (dark.gpu().status() != DetailStatus.READY || split.gpu().status() != DetailStatus.READY) return false;
                verifyParity(client, dark, split, diagnostic(page, DECLARED));
                capture(client, output, "dark", image -> {
                    int white = ink(image, client, X, MOTION_Y, font.width(GLYPHS), WHITE);
                    require(white > 0 && white == ink(image, client, 118, MOTION_Y, font.width(GLYPHS), WHITE),
                            "GPU changed white on black or diverged from the declared uniform control");
                    int localSplit = font.width(SMALL_RUN);
                    int localWhite = ink(image, client, X, LOCAL_Y, localSplit, WHITE);
                    int localBlack = ink(image, client, X + localSplit, LOCAL_Y, localSplit, BLACK);
                    int mixedWhite = ink(image, client, 118, LOCAL_Y, localSplit, WHITE);
                    int expectedGreen = TextReadabilityReference.evaluate(new TextReadabilityReference.Input(
                            AUTHORED_GREEN, false, false, true, 4.5, 1,
                            font.lineHeight * client.getWindow().getGuiScale(), 0, Background.declared(WHITE)),
                            null, 0).fillArgb();
                    int imageGreen = (expectedGreen & 0xFF00FF00) | ((expectedGreen & 0xFF) << 16)
                            | ((expectedGreen >>> 16) & 0xFF);
                    int mixedGreen = ink(image, client, 118 + localSplit, LOCAL_Y, font.width(LOCAL_BOLD), imageGreen);
                    require(localWhite > 0 && localWhite == localBlack && localWhite == mixedWhite
                                    && expectedGreen != AUTHORED_GREEN && mixedGreen > localBlack,
                            "Localized sequences did not preserve the native glyph footprint and inline bold style: "
                                    + localWhite + "/" + localBlack + "/" + mixedWhite + "/" + mixedGreen);
                });
                if (Boolean.getBoolean("shb.kernel.smoke.textReadabilityLocal")) {
                    Files.writeString(output.resolve("text-readability-gpu.tsv"), statistics);
                    result = "Native text readability local-color GPU smoke passed on Java " + Runtime.version().feature()
                            + "\nUniform and mixed styled native sequences through submit and drawNow\n";
                    return true;
                }
                stage = Stage.FLASH;
            }
            case FLASH -> {
                require(framesInStage == 1, "Missed the first bright frame");
                capture(client, output, "flash", image -> require(pixel(image, client, X - 1, MOTION_Y - 1) == BLACK
                                && ink(image, client, X, MOTION_Y, font.width(GLYPHS), WHITE) > 0,
                        "The first bright frame did not retain the white fill with immediate black protection"));
                stage = Stage.STABLE;
            }
            case STABLE -> {
                if (System.nanoTime() - firstBrightNanos < STABILITY + 100_000_000) return false;
                var bright = diagnostic(page, MOTION);
                if (bright.gpu().status() != DetailStatus.READY || bright.gpu().decision().fillArgb() != BLACK) return false;
                require(bright.gpu().decision().protection() == TextReadabilityReference.Protection.NONE,
                        "Settled black fill retained temporary protection");
                capture(client, output, "stable", image -> require(pixel(image, client, X - 1, MOTION_Y - 1) == WHITE
                                && ink(image, client, X, MOTION_Y, font.width(GLYPHS), BLACK) > 0,
                        "Sustained bright backdrop did not switch to black glyphs without backing"));
                stage = Stage.GLASS;
            }
            case GLASS -> {
                var glass = diagnostic(page, GLASS);
                if (glass.gpu().status() != DetailStatus.READY || framesInStage < 5) return false;
                require(GaussBlur.isReady() && glass.gpu().decision().source() == TextReadabilityReference.Source.ACTUAL
                                && glass.gpu().samples().stream().anyMatch(point -> point.luminance() > .01
                                && point.luminance() < .95),
                        "The GPU sampled source stripes instead of their composited glass backdrop");
                var layout = rich.currentSnapshot().orElseThrow().layout();
                var dark = layout.lines().get(0).fragments().get(0).bounds();
                var bright = layout.lines().get(0).fragments().get(1).bounds();
                capture(client, output, "glass", image -> require(
                        ink(image, client, X + dark.x(), RICH_Y + dark.y(), dark.width(), WHITE) > 0
                                && ink(image, client, X + bright.x(), RICH_Y + bright.y(), bright.width(), BLACK) > 0,
                        "Rich text did not adapt across its bright and dark spans"));
                stage = Stage.DEGRADATION;
            }
            case DEGRADATION -> {
                var sparse = diagnostic(page, INSUFFICIENT);
                var invalid = diagnostic(page, INVALID_REGION);
                var unsupported = diagnostic(page, UNAVAILABLE);
                require(invalid.sampling() == SamplingStatus.INVALID_REGION && declaredFallback(invalid)
                                && unsupported.sampling() == SamplingStatus.UNAVAILABLE && declaredFallback(unsupported),
                        "Oversized analysis region or unsupported stencil path did not fall back: "
                                + invalid.sampling() + ", " + unsupported.sampling());
                if (sparse.gpu().status() != DetailStatus.READY) return false;
                var decision = sparse.gpu().decision();
                require(sparse.sampling() == SamplingStatus.GPU && sparse.result() == Result.DRAWN
                                && decision.source() == TextReadabilityReference.Source.DECLARED
                                && decision.diagnostics().contains(TextReadabilityReference.Diagnostic.INSUFFICIENT_ACTUAL_SAMPLES)
                                && stats.invalidRegions() == 1 && stats.unavailableFallbacks() == 1,
                        "A measured whitespace run did not report insufficient glyph samples");
                stage = Stage.INTEROP;
            }
            case INTEROP -> {
                var foreign = diagnostic(page, FOREIGN_FRAMEBUFFER);
                require(foreign.sampling() == SamplingStatus.UNAVAILABLE && declaredFallback(foreign)
                                && diagnostic(page, MIXED).result() == Result.INVALID_REQUEST,
                        "Foreign framebuffer fallback or the mixed-style request rule changed");
                var authored = diagnostic(page, AUTHORED);
                if (authored.gpu().status() != DetailStatus.READY) return false;
                require(authored.gpu().decision().source() == TextReadabilityReference.Source.ACTUAL
                                && authored.gpu().decision().fillArgb() == AUTHORED_GREEN,
                        "Actual selection ignored the uniform native inline color");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    require(ink(image, client, X, MOTION_Y, font.width(GLYPHS), 0xff55dd33) > 0
                                    && ink(image, client, X, SPLIT_Y, font.width(MIXED_GLYPHS), BLACK)
                                    == font.width(MIXED_GLYPHS) * font.lineHeight,
                            "The authored color did not paint or the rejected mixed run changed pixels");
                }
                service.setGpuDiagnostics(module, page, false);
                ClientUiKernel.toggleTextReadabilityOverlay(client);
                pointAtSplit(client);
                stage = Stage.OVERLAY;
            }
            case OVERLAY -> {
                var mouse = UiMouseState.read();
                var split = diagnostic(page, SPLIT);
                boolean hovering = mouse.available() && split.visibleBounds().contains(mouse.x(), mouse.y());
                if (!hovering) {
                    require(framesInStage < 120, "Cannot hover the native overlay probe: " + overlayStatus(client, mouse, split));
                    pointAtSplit(client);
                    return false;
                }
                if (framesInStage < 2 || split.gpu().status() != DetailStatus.READY) {
                    require(framesInStage < 120, "The hovered GPU overlay did not acquire detail: "
                            + overlayStatus(client, mouse, split));
                    return false;
                }
                capture(client, output, "overlay", image -> {
                    boolean outline = false;
                    for (int x = X; x < X + font.width(GLYPHS); x++) {
                        if (pixel(image, client, x, SPLIT_Y) == 0xffe0d838) outline = true;
                    }
                    require(outline && pixel(image, client, 9, height - 6) != BLACK,
                            "The native diagnostic overlay did not draw its cyan bounds and detail panel");
                });
                ClientUiKernel.toggleTextReadabilityOverlay(client);
                stage = Stage.BUDGET;
            }
            case BUDGET -> {
                if (framesInStage < 20) return false;
                int sampled = 0;
                int fallback = 0;
                for (int i = 0; i < BUDGET_RUNS; i++) {
                    var diagnostic = diagnostic(page, budgetId(i));
                    if (diagnostic.sampling() == SamplingStatus.GPU) sampled++;
                    if (diagnostic.sampling() == SamplingStatus.BUDGET_EXHAUSTED) {
                        require(diagnostic.result() == Result.DRAWN
                                        && diagnostic.decision().source() == TextReadabilityReference.Source.DECLARED,
                                "An exhausted run did not draw using the declared background");
                        fallback++;
                    }
                }
                var focused = diagnostic(page, MOTION);
                require(sampled > 0 && fallback > 0 && sampled + fallback == BUDGET_RUNS
                                && focused.sampling() == SamplingStatus.GPU && focused.gpu().status() == DetailStatus.OFF
                                && stats.analyzedRuns() == sampled + 1 && stats.budgetFallbacks() == fallback,
                        "Budget priority, status, or disabled diagnostic acquisition changed: " + sampled + "/" + fallback
                                + ", focused=" + focused);
                Files.writeString(output.resolve("text-readability-gpu.tsv"), statistics);
                result = "Native text readability GPU smoke passed\n"
                        + "Java=" + Runtime.version() + "\nGL renderer=" + GL11.glGetString(GL11.GL_RENDERER)
                        + "\nGL version=" + GL11.glGetString(GL11.GL_VERSION) + "\nFramebuffer="
                        + client.getMainRenderTarget().viewWidth + "x" + client.getMainRenderTarget().viewHeight
                        + ", GUI scale=" + client.getWindow().getGuiScale()
                        + "\nCPU/GPU uniform and split candidate/protection parity, contrast tolerance=" + METRIC_TOLERANCE
                        + "\nFirst bright frame retained white with black backing; stable bright switched to unprotected black"
                        + "\nActual composited glass, adaptive Rich Text spans, expired and valid foreground contexts"
                        + "\nLocalized native sequences through submit and drawNow, including inline bold and RGB"
                        + "\nNative diagnostic overlay, invalid region, unsupported stencil path and insufficient glyph samples"
                        + "\nForeign framebuffer/viewport fallback and restoration, authored RGB, mixed-style rejection"
                        + "\nBudget crowd sampled=" + sampled + ", declared fallback=" + fallback + ", focused sampled\n";
                return true;
            }
        }
        return false;
    }

    private void verifyParity(Minecraft client, Diagnostic dark, Diagnostic split, Diagnostic declared) {
        var black = Sample.actualSrgb(TextReadabilityReference.WHOLE_RUN, BLACK, 1, 1, true);
        var white = Sample.actualSrgb(TextReadabilityReference.WHOLE_RUN, WHITE, 1, 1, true);
        var uniform = reference(client, Background.actual(List.of(black, black, black, black), BLACK));
        var divided = reference(client,
                Background.actual(List.of(black, white, black, white, black, white, black, white), BLACK));
        require(dark.gpu().sourceFrame() > 0 && dark.gpu().sourceFrame() <= dark.sourceFrame()
                        && split.gpu().sourceFrame() > 0 && split.gpu().sourceFrame() <= split.sourceFrame()
                        && dark.gpu().decision().source() == TextReadabilityReference.Source.ACTUAL
                        && split.gpu().decision().source() == TextReadabilityReference.Source.ACTUAL
                        && declared.sampling() == SamplingStatus.DECLARED_ONLY
                        && declared.decision().source() == TextReadabilityReference.Source.DECLARED,
                "Asynchronous decisions did not identify the actual and declared sources");
        require(split.gpu().samples().stream().anyMatch(point -> point.luminance() < .001)
                        && split.gpu().samples().stream().anyMatch(point -> point.luminance() > .999),
                "Split samples did not span both sides");
        double scale = client.getWindow().getGuiScale();
        require(split.gpu().samples().stream().allMatch(point ->
                        split.visibleBounds().contains(point.x() / scale, point.y() / scale)),
                "A reported sample fell outside the scaled glyph bounds");
        parity(uniform, dark.gpu().decision(), "uniform actual");
        parity(uniform, declared.decision(), "uniform declared");
        parity(divided, split.gpu().decision(), "split actual");
    }

    private void pointAtSplit(Minecraft client) throws Exception {
        var window = client.getWindow();
        if (GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) {
            GLFW.glfwFocusWindow(window.getWindow());
        }
        if (!client.isWindowActive()) client.setWindowActive(true);
        double x = X + font.width(GLYPHS) * .5 + (framesInStage % 2 == 0 ? 2 : -2);
        double y = SPLIT_Y + font.lineHeight * .5;
        GLFW.glfwSetCursorPos(window.getWindow(),
                x * window.getScreenWidth() / window.getGuiScaledWidth(),
                y * window.getScreenHeight() / window.getGuiScaledHeight());
        // A new GLFW window can reuse the previous cursor position without delivering a move to Minecraft.
        KernelClientSmoke.move(client, x, y);
    }

    private static String overlayStatus(Minecraft client, UiMouseState mouse, Diagnostic split) {
        long window = client.getWindow().getWindow();
        double[] x = {0}, y = {0};
        GLFW.glfwGetCursorPos(window, x, y);
        return "mouse=" + mouse + ", glfwFocused=" + GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED)
                + ", glfwHovered=" + GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_HOVERED)
                + ", glfwCursor=" + x[0] + "/" + y[0] + ", diagnostic=" + split.sampling()
                + "/" + split.gpu().status() + ", bounds=" + split.visibleBounds();
    }

    private static boolean declaredFallback(Diagnostic diagnostic) {
        return diagnostic.result() == Result.DRAWN && diagnostic.decision() != null
                && diagnostic.decision().source() == TextReadabilityReference.Source.DECLARED;
    }

    private Decision reference(Minecraft client, Background background) {
        return TextReadabilityReference.evaluate(new TextReadabilityReference.Input(WHITE, false, false, true,
                4.5, 1, font.lineHeight * client.getWindow().getGuiScale(), STABILITY, background), null, 0);
    }

    private static void parity(Decision reference, Decision gpu, String scenario) {
        require(reference.fillArgb() == gpu.fillArgb() && reference.protection() == gpu.protection()
                        && reference.protectionArgb() == gpu.protectionArgb()
                        && Math.abs(reference.fillP10() - gpu.fillP10()) <= METRIC_TOLERANCE
                        && Math.abs(reference.protectedP10() - gpu.protectedP10()) <= METRIC_TOLERANCE
                        && Math.abs(reference.finalP10() - gpu.finalP10()) <= METRIC_TOLERANCE,
                "CPU/GPU parity failed for " + scenario + ": " + reference + " vs " + gpu);
    }

    void verifyClosed() {
        require(!page.isOpen() && service.diagnostics(module, page, MOTION).isEmpty()
                        && service.drawNow(module, expired, request(page, MOTION, GLYPHS, X, MOTION_Y,
                        BLACK, SamplingHint.FOCUSED)) == Result.SCOPE_CLOSED,
                "Closed Screen retained its context or diagnostics");
    }

    String result() { return result; }

    String stage() { return stage + "/" + framesInStage; }

    private void submit(Request request) {
        expect(Result.SUBMITTED, service.submit(module, request));
    }

    private Request request(ScopeHandle scope, TextId id, FormattedCharSequence glyphs, int x, int y,
                            Integer background, SamplingHint hint) {
        var run = new TextReadabilityService.Run(glyphs, 0, 0, new Rect(0, 0, font.width(glyphs), font.lineHeight),
                font.lineHeight);
        return new Request(scope, id, run, WHITE_TEXT, Transform.translation(x, y),
                new Rect(0, 0, width, height), 1, background).withSampling(hint);
    }

    private Diagnostic diagnostic(ScopeHandle scope, TextId text) {
        return service.diagnostics(module, scope, text).orElseThrow(() -> new AssertionError("Missing diagnostic " + text));
    }

    private int ink(NativeImage image, Minecraft client, int left, int top, int width, int color) {
        int count = 0;
        for (int y = top; y < top + font.lineHeight; y++) {
            for (int x = left; x < left + width; x++) {
                if (pixel(image, client, x, y) == color) count++;
            }
        }
        return count;
    }

    private static void capture(Minecraft client, Path output, String file,
                                Consumer<NativeImage> checks) throws IOException {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(output.resolve("text-readability-gpu-" + file + ".png").toFile());
            checks.accept(image);
        }
    }

    private void guard(Runnable draw) {
        if (failure != null) return;
        try {
            draw.run();
        } catch (RuntimeException | LinkageError | AssertionError error) {
            failure = error;
        }
    }

    private static void expect(Result expected, Result actual) {
        require(actual == expected, "Expected " + expected + " but received " + actual);
    }

    private static int px(int gui, double scale) { return (int) Math.round(gui * scale); }
    private static TextId budgetId(int index) { return text("budget/" + index); }
    private static TextId text(String path) { return new TextId(id(path)); }
    private static UiId id(String path) { return new UiId("sh_better_ui", "smoke/readability_gpu/" + path); }
}
