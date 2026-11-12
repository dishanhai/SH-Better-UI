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
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingHint;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

final class TextReadabilityServiceSmoke extends Screen {
    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private static final int INLINE_GREEN = 0xff33dd55;
    private static final int POPUP_BLUE = 0xff224478;
    private static final int UNKNOWN_BACKDROP = 0xff805050;
    private static final int X = 18;
    private static final int CONTROL_Y = 16;
    private static final int BLUR_Y = 50;
    private static final int PROTECTED_X = 111;
    private static final int COLOR_X = 248;
    private static final int COLOR_Y = 86;
    private static final int UNKNOWN_Y = 110;
    private static final int FADE_Y = 155;
    private static final int INVALID_Y = 182;
    private static final int IMMEDIATE_Y = 201;
    private static final Rect POPUP_BOUNDS = new Rect(100, 39, 100, 58);
    private static final UiPreferences PREFERENCES = new UiPreferences(false, true, false);
    private static final FormattedCharSequence GLYPHS = FormattedCharSequence.forward("MMMMMMMMMM",
            net.minecraft.network.chat.Style.EMPTY);
    private static final FormattedCharSequence COLORED_GLYPHS = FormattedCharSequence.forward("MMMMMMMMMM",
            net.minecraft.network.chat.Style.EMPTY.withColor(INLINE_GREEN & 0xffffff));
    private static final TextId PROTECTED = text("protected");
    private static final TextId COLOR_ONLY = text("color_only");
    private static final TextId CLIPPED = text("clipped");
    private static final TextId UNKNOWN = text("unknown");
    private static final TextId FADE = text("fade");
    private static final TextId HIDDEN = text("hidden");
    private static final TextId INVALID = text("invalid");
    private static final TextId LATE = text("late");
    private static final TextId WRONG_SCOPE = text("wrong_scope");
    private static final TextId IMMEDIATE = text("immediate");
    private static final TextId POPUP = text("popup");
    private static final UiId OLD_DATA_ITEM = id("retired_data");
    private static final TextId RETIRED_ROW = new TextId(id("row_label"), OLD_DATA_ITEM, null);

    private final ModuleContext module;
    private final TextReadabilityService service;
    private final List<Integer> order = new ArrayList<>();
    private ScopeHandle page;
    private ScopeHandle colorOnly;
    private ScopeHandle adaptiveProtected;
    private ScopeHandle popup;
    private ThemeScope popupTheme;
    private UiButton popupSource;
    private UiLocalModal modal;
    private VisualLayoutBinding layout;
    private AlignmentLayout.Node scrollText;
    private Rect scrollClip;
    private FrameViewSnapshot lastLayout;
    private RenderService.Context expired;
    private Throwable failure;
    private long firstFrame;
    private int frames;
    private int popupFrames;
    private boolean opened;
    private String pageEvidence;

    TextReadabilityServiceSmoke(ModuleContext module) {
        super(Component.translatable("gui.done"));
        this.module = module;
        service = module.engine().require(UIApis.TEXT_READABILITY);
        GaussBlur.allowScreen(screen -> screen instanceof TextReadabilityServiceSmoke);
    }

    @Override protected void init() {
        if (page != null && page.isOpen()) return;
        var engine = module.engine();
        page = engine.require(UIApis.SCREENS).mount(module, this, id("page"));
        colorOnly = page.openChild(ScopeKind.COMPONENT, id("color"));
        adaptiveProtected = page.openChild(ScopeKind.COMPONENT, id("adaptive_protected"));
        popup = page.openChild(ScopeKind.COMPONENT, id("popup"));
        service.configure(module, page, new TextReadabilityPolicy(false, true, null, null, null));
        service.configure(module, colorOnly, new TextReadabilityPolicy(true, false, null, null, null));
        service.configure(module, adaptiveProtected, new TextReadabilityPolicy(true, true, null, null, null));
        popupTheme = MinecraftThemes.manager().global().child("readability_smoke_popup");
        service.configurePopup(module, popup, popupTheme, null,
                new TextReadabilityPolicy(false, true, null, null, null));
        modal = new UiLocalModal(this, PREFERENCES);
        popupSource = addRenderableWidget(new UiButton(103, 41, 32, 16, Component.translatable("gui.done"), ignored -> {}));

        var alignment = new AlignmentLayout(viewport());
        var parent = alignment.node("scroll", AlignmentFrame.box(new Rect(18, 130, 92, 18))
                .withClip(new Rect(4, 0, 31, 18)));
        scrollText = alignment.node("scroll-text", parent, AlignmentFrame.box(new Rect(0, 0, 85, 9))
                .withPose(Transform.translation(0.5, 1)).withClip(new Rect(8, 0, 70, 9)));
        layout = engine.require(UIApis.LAYOUT).bindVisual(module, page, alignment, time -> {});
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failure != null) return;
        guard(() -> drawFrame(graphics, partialTick));
    }

    private void drawFrame(GuiGraphics graphics, float partialTick) {
        frames++;
        if (firstFrame == 0) firstFrame = System.nanoTime();
        order.clear();
        graphics.fill(0, 0, width, height, BLACK);
        graphics.fill(X - 5, UNKNOWN_Y - 3, 95, FADE_Y + 13, UNKNOWN_BACKDROP);
        graphics.fill(X - 5, 127, 96, 150, BLACK);
        graphics.drawString(font, GLYPHS, X, CONTROL_Y, WHITE, false);
        graphics.drawString(font, GLYPHS, X, BLUR_Y, WHITE, false);

        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        GaussBlur.request(0, pixel(42, scale), pixel(width, scale), pixel(27, scale), 1,
                pixel(10, scale), 0, 0, 0, 0, GlassStyle.NONE);
        var protectedRun = positioned(page, PROTECTED, PROTECTED_X, BLUR_Y, WHITE, null, 1);
        expect(Result.SUBMITTED, service.submit(module, protectedRun), "page protection");
        expect(Result.ALREADY_CONSUMED, service.submit(module, protectedRun), "duplicate queued request");
        var styledRun = new TextReadabilityService.Run(COLORED_GLYPHS, 0, 0, runBounds(0, 0), font.lineHeight);
        expect(Result.SUBMITTED, service.submit(module, new Request(page, text("inline_style"), styledRun, style(WHITE),
                Transform.translation(PROTECTED_X, CONTROL_Y), viewport(), 1)), "original inline glyph style");
        expect(Result.DRAWN, service.submit(module, positioned(colorOnly, COLOR_ONLY, COLOR_X, COLOR_Y,
                BLACK, BLACK, 1)), "color-only inline");
        expect(Result.DRAWN, service.submit(module, positioned(colorOnly, text("early_color"), COLOR_X, BLUR_Y,
                WHITE, BLACK, 1)), "color-only inline before blur");
        expect(Result.SUBMITTED, service.submit(module, positioned(adaptiveProtected, UNKNOWN, X, UNKNOWN_Y,
                WHITE, null, 1)), "unknown backdrop");
        expect(Result.SUBMITTED, service.submit(module, positioned(adaptiveProtected, FADE, X, FADE_Y,
                WHITE, null, 0.5f)), "translucent group");
        expect(Result.HIDDEN, service.submit(module, positioned(adaptiveProtected, HIDDEN, 270, INVALID_Y,
                WHITE, null, 0)), "zero opacity group");
        expect(Result.SUBMITTED, service.submit(module, positioned(adaptiveProtected, RETIRED_ROW, 260, IMMEDIATE_Y,
                WHITE, BLACK, 1)), "queued recycled row");
        service.retireDataItem(module, adaptiveProtected, OLD_DATA_ITEM);

        var frame = new UiFrame(graphics, new Bounds(0, 0, width, height), UiMouseState.read(),
                PREFERENCES, UiFrameScheduler.nowMs(), partialTick);
        layout.renderScreen(frame, pass -> pass.at(scrollText, visual -> {
            require(pass.snapshot().view(scrollText) == visual.view(), "Scroll clip was not from this frame's layout");
            scrollClip = pass.snapshot().clip(scrollText);
            require(scrollClip != null, "Layout did not publish the ancestor scroll clip");
            var request = Request.forVisual(page, CLIPPED, run(), style(WHITE), pass.snapshot(), scrollText,
                    1, null, 0).withSampling(SamplingHint.DECLARED_ONLY);
            expect(Result.SUBMITTED, service.submit(module, request), "scroll clip");
            if (lastLayout != null) {
                var stale = Request.forVisual(page, text("stale_layout"), run(), style(WHITE), lastLayout,
                        scrollText, 1, null, 0);
                expect(Result.STALE_LAYOUT, service.submit(module, stale), "stale layout snapshot");
            }
            lastLayout = pass.snapshot();
            visual.foreground(1, context -> order.add(2));
        }));

        var invalid = positioned(page, INVALID, X, INVALID_Y, WHITE, null, 1);
        expect(Result.INVALID_STAGE, service.drawNow(module, expired, invalid), "outside/expired foreground stage");
        expect(Result.SUBMITTED, service.foreground(module, page, 2, context -> guard(() -> {
            order.add(3);
            expect(Result.INVALID_STAGE, service.drawNow(module, context,
                    positioned(colorOnly, WRONG_SCOPE, 100, INVALID_Y, WHITE, null, 1)), "wrong foreground scope");
            expect(Result.QUEUE_CLOSED, service.submit(module,
                    positioned(page, LATE, 182, INVALID_Y, WHITE, null, 1)), "closed page queue");
            var immediate = positioned(page, IMMEDIATE, X, IMMEDIATE_Y, WHITE, null, 1);
            expect(Result.DRAWN, service.drawNow(module, context, immediate), "current foreground context");
            expect(Result.ALREADY_CONSUMED, service.drawNow(module, context, immediate), "duplicate immediate draw");
            expired = context;
        })), "page foreground callback");
        expect(Result.SUBMITTED, service.foreground(module, page, -1, context -> order.add(1)),
                "earlier foreground callback");
    }

    private void openPopup() {
        opened = true;
        Minecraft.getInstance().setWindowActive(true);
        boolean accepted = modal.open(popupSource, new Bounds(0, 0, width, height),
                (source, viewport) -> new Bounds((int) POPUP_BOUNDS.x(), (int) POPUP_BOUNDS.y(),
                        (int) POPUP_BOUNDS.width(), (int) POPUP_BOUNDS.height()),
                UiExpansionController.Config.DEFAULT,
                session -> (graphics, frame, mouseX, mouseY, delta) -> guard(() -> drawPopup(graphics, frame)), () -> {});
        require(accepted, "Native local modal did not open");
    }

    private void drawPopup(GuiGraphics graphics, UiLocalModal.Frame frame) {
        var bounds = frame.bounds();
        service.renderPopup(module, popup, graphics, () -> {
            graphics.fill(bounds.x() + 2, bounds.y() + 2, bounds.right() - 2, bounds.bottom() - 2, POPUP_BLUE);
            var popupClip = new Rect(bounds.x() + 9, bounds.y() + 30, 30, 12);
            var run = request(popup, POPUP, Transform.translation(bounds.x() + 9, bounds.y() + 31),
                    popupClip, WHITE, POPUP_BLUE, 1);
            expect(Result.SUBMITTED, service.submit(module, run), "popup foreground submit");
        });
        popupFrames++;
    }

    boolean verify(Minecraft client, Path output) throws IOException {
        if (failure != null) throw new AssertionError("Native text readability service failed", failure);
        if (frames < 2 || System.nanoTime() - firstFrame < 300_000_000L || opened && popupFrames < 2) return false;
        require(GaussBlur.isReady(), "Native frame-end blur unavailable");
        require(order.equals(List.of(1, 2, 3)), "Foreground z order changed " + order);
        double scale = client.getWindow().getGuiScale();
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            if (!opened) {
                image.writeToFile(output.resolve("text-readability-service-page.png").toFile());
                pageEvidence = verifyPage(image, scale);
                openPopup();
                return false;
            }
            image.writeToFile(output.resolve("text-readability-service-popup.png").toFile());
            var cover = new Rect(PROTECTED_X, BLUR_Y, font.width(GLYPHS), font.lineHeight);
            require(count(image, scale, cover, POPUP_BLUE) == countPixels(scale, cover),
                    "Page's protected text pierced the upper modal");
            int popupWhite = count(image, scale, new Rect(109, 70, 30, 9), WHITE);
            require(popupWhite > 0 && count(image, scale, new Rect(139, 70, 32, 9), WHITE) == 0,
                    "Popup text failed its layer clip");
            var popupDecision = service.diagnostics(module, popup, POPUP).orElseThrow();
            require(popupDecision.result() == Result.DRAWN && popupDecision.renderLayerGeneration() == popup.generation(),
                    "Popup decision was recorded in the wrong layer");
            Files.writeString(output.resolve("success.txt"), "Text readability public service native smoke passed\n"
                    + "Java=" + Runtime.version() + "\nGL renderer=" + GL11.glGetString(GL11.GL_RENDERER)
                    + "\nGL version=" + GL11.glGetString(GL11.GL_VERSION) + "\nFramebuffer=" + image.getWidth() + 'x'
                    + image.getHeight() + ", GUI scale=" + scale + "\n" + pageEvidence
                    + "\nPopup cover=" + countPixels(scale, cover) + " opaque pixels, clipped white glyph="
                    + popupWhite + "\n");
            return true;
        }
    }

    private String verifyPage(NativeImage image, double scale) {
        var control = countRun(image, scale, X, CONTROL_Y, WHITE);
        var early = countRun(image, scale, X, BLUR_Y, WHITE);
        var protectedWhite = countRun(image, scale, PROTECTED_X, BLUR_Y, WHITE);
        require(control > 0 && early < control / 4 && protectedWhite == control,
                "Protected glyph mismatch (control/early/protected): " + control + '/' + early + '/' + protectedWhite);
        require(countRun(image, scale, PROTECTED_X, CONTROL_Y, INLINE_GREEN) == control,
                "Protection-only changed the original inline glyph style");
        require(countRun(image, scale, COLOR_X, BLUR_Y, WHITE) < control / 4,
                "Color-only text escaped its original draw order");
        int adjusted = countPixels(scale, runBounds(COLOR_X, COLOR_Y)) - countRun(image, scale, COLOR_X, COLOR_Y, BLACK);
        require(adjusted == control, "Declared black background did not produce a readable glyph: " + adjusted);
        int inside = count(image, scale, scrollClip, WHITE);
        require(inside > 0 && count(image, scale, new Rect(scrollClip.right(), 128, 45, 20), WHITE) == 0
                        && count(image, scale, new Rect(X, 128, scrollClip.x() - X, 20), WHITE) == 0,
                "Ancestor scroll clip did not restrict transformed glyphs");
        require(count(image, scale, new Rect(X, INVALID_Y, 240, 10), BLACK)
                        == countPixels(scale, new Rect(X, INVALID_Y, 240, 10)),
                "Rejected request modified framebuffer pixels");
        require(countRun(image, scale, X, IMMEDIATE_Y, WHITE) == control, "Valid immediate draw did not produce glyphs");
        require(countRun(image, scale, 260, IMMEDIATE_Y, BLACK) == countPixels(scale, runBounds(260, IMMEDIATE_Y))
                        && service.diagnostics(module, adaptiveProtected, RETIRED_ROW).isEmpty(),
                "A recycled row's queued text or diagnostic survived retirement");
        require(service.diagnostics(module, page, PROTECTED).orElseThrow().result() == Result.DRAWN,
                "Protected text did not report consumption");
        require(service.diagnostics(module, page, INVALID).orElseThrow().result() == Result.INVALID_STAGE,
                "Expired context diagnostic missing");
        var declared = service.diagnostics(module, colorOnly, COLOR_ONLY).orElseThrow();
        var unknown = service.diagnostics(module, adaptiveProtected, UNKNOWN).orElseThrow();
        var faded = service.diagnostics(module, adaptiveProtected, FADE).orElseThrow();
        require(unknown.result() == Result.DRAWN && faded.result() == Result.DRAWN,
                "Unknown-background protection did not draw");
        require(declared.decision() != null && declared.decision().source() == TextReadabilityReference.Source.DECLARED
                        && unknown.decision() != null && unknown.decision().source() == TextReadabilityReference.Source.UNKNOWN
                        && unknown.decision().protection() == TextReadabilityReference.Protection.BACKING,
                "Background decision source was lost");
        require(faded.decision() != null && faded.decision().diagnostics().contains(
                        TextReadabilityReference.Diagnostic.GROUP_OPACITY_CONTRAST_INSUFFICIENT),
                "Group opacity contrast limit was not diagnosed");
        require(countRun(image, scale, 270, INVALID_Y, BLACK) == countPixels(scale, runBounds(270, INVALID_Y)),
                "Zero-opacity backing or glyph remained visible");
        int backing = read(image, scale, X - 1, UNKNOWN_Y);
        int fadedBacking = read(image, scale, X - 1, FADE_Y);
        require(backing == BLACK && matchesHalfLinear(fadedBacking, BLACK, UNKNOWN_BACKDROP),
                "Unexpected unknown backing/fade pixels: " + Integer.toHexString(backing) + '/'
                        + Integer.toHexString(fadedBacking));
        int fadedInk = fadedInk(image, scale);
        require(matchesHalfLinear(fadedInk, WHITE, UNKNOWN_BACKDROP),
                "Glyph and backing did not share group opacity: " + Integer.toHexString(fadedInk));
        return "Control/early/protected white=" + control + '/' + early + '/' + protectedWhite
                + ", adapted glyph pixels=" + adjusted + ", clipped white=" + inside
                + ", backing/fade/glyph over fade=" + Integer.toHexString(backing) + '/'
                + Integer.toHexString(fadedBacking) + '/' + Integer.toHexString(fadedInk);
    }

    private int fadedInk(NativeImage image, double scale) {
        var bounds = runBounds(X, UNKNOWN_Y);
        int offset = pixel(FADE_Y, scale) - pixel(UNKNOWN_Y, scale);
        for (int y = pixel(bounds.y(), scale); y < pixel(bounds.bottom(), scale); y++) {
            for (int x = pixel(bounds.x(), scale); x < pixel(bounds.right(), scale); x++) {
                if (argb(image.getPixelRGBA(x, y)) == WHITE) return argb(image.getPixelRGBA(x, y + offset));
            }
        }
        throw new AssertionError("Unknown background had no opaque glyph interior");
    }

    private static boolean matchesHalfLinear(int actual, int layer, int backdrop) {
        for (int shift = 0; shift <= 16; shift += 8) {
            double linear = 0.5 * (decode(layer >>> shift & 255) + decode(backdrop >>> shift & 255));
            double encoded = linear <= 0.0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
            int expected = (int) Math.round(encoded * 255);
            if (Math.abs((actual >>> shift & 255) - expected) > 2) return false;
        }
        return true;
    }

    private static double decode(int channel) {
        double encoded = channel / 255.0;
        return encoded <= 0.04045 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    @Override public void removed() {
        if (popupTheme != null && !popupTheme.isClosed()) popupTheme.close();
    }

    private Request positioned(ScopeHandle scope, TextId id, int x, int y, int argb, Integer background, float alpha) {
        return request(scope, id, Transform.translation(x, y), viewport(), argb, background, alpha);
    }

    private Request request(ScopeHandle scope, TextId id, Transform transform, Rect clip, int argb,
                            Integer background, float alpha) {
        return new Request(scope, id, run(), style(argb), transform, clip, alpha, background)
                .withSampling(SamplingHint.DECLARED_ONLY);
    }

    private TextReadabilityService.Run run() {
        return new TextReadabilityService.Run(GLYPHS, 0, 0, runBounds(0, 0), font.lineHeight);
    }

    private static TextReadabilityService.Style style(int argb) {
        return new TextReadabilityService.Style(argb, false, TextReadabilityPolicy.Usage.BODY);
    }

    private Rect runBounds(int x, int y) { return new Rect(x, y, font.width(GLYPHS), font.lineHeight); }
    private Rect viewport() { return new Rect(0, 0, width, height); }

    private int countRun(NativeImage image, double scale, int x, int y, int color) {
        return count(image, scale, runBounds(x, y), color);
    }

    private static int count(NativeImage image, double scale, Rect rect, int color) {
        int found = 0;
        for (int y = pixel(rect.y(), scale); y < pixel(rect.bottom(), scale); y++) {
            for (int x = pixel(rect.x(), scale); x < pixel(rect.right(), scale); x++) {
                if (argb(image.getPixelRGBA(x, y)) == color) found++;
            }
        }
        return found;
    }

    private static int read(NativeImage image, double scale, int x, int y) {
        return argb(image.getPixelRGBA(pixel(x, scale), pixel(y, scale)));
    }

    private static int argb(int abgr) {
        return 0xff000000 | (abgr & 255) << 16 | (abgr & 0xff00) | (abgr >>> 16 & 255);
    }

    private static int countPixels(double scale, Rect rect) {
        return (pixel(rect.right(), scale) - pixel(rect.x(), scale))
                * (pixel(rect.bottom(), scale) - pixel(rect.y(), scale));
    }

    private static int pixel(double gui, double scale) { return (int) Math.round(gui * scale); }

    private static void expect(Result expected, Result actual, String operation) {
        require(actual == expected, operation + " expected " + expected + " but returned " + actual);
    }

    private void guard(Runnable draw) {
        try {
            draw.run();
        } catch (RuntimeException | AssertionError error) {
            if (failure == null) failure = error;
        }
    }

    private static UiId id(String path) { return new UiId("sh_better_ui", "smoke/readability/" + path); }
    private static TextId text(String path) { return new TextId(id(path)); }
}
