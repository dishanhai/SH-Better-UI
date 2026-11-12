// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;
import java.util.List;

final class GlassOpticsSmoke {
    static final GlassRegion DRAG_ZONE = GlassRegion.capsule(90, 30, 58, 24);
    static final int INITIAL_OPTICAL_DRAWS = 9;
    private static final int STRIPE_A = 0xffbad6e9;
    private static final int STRIPE_B = 0xffeaa27c;
    private static final int FIRST = 76;
    private static final int SECOND = 172;
    private static final int TOP = 76;
    private static final int WIDTH = 76;
    private static final int HEIGHT = 28;
    private static final int CENTER_Y = TOP + HEIGHT / 2;
    private static final int FROST_TOP = 184;
    private static final int CONTRAST_X = 265;
    private static final int CONTRAST_BACKGROUND = 0xfff8f2de;
    private static final int FRONT_MARKER = 0xffe72938;
    private static final int BEHIND_MARKER = 0xff3cb64a;

    private GlassOpticsSmoke() {}

    static void drawReload(RenderService.Context context) {
        var material = UnifiedUI.get().glass().material(GlassVariant.REGULAR);
        context.glass(List.of(new GlassDraw(GlassRegion.capsule(76, 76, 76, 28), material)));
    }

    static void drawWorld(RenderService.Context context) {
        var material = UnifiedUI.get().glass().material(GlassVariant.CLEAR);
        int x = (context.frame().width() - 108) / 2;
        var label = Component.translatable("menu.game");
        var bounds = new GlassDraw.ForegroundBounds(x + 19, 39, Minecraft.getInstance().font.width(label) + 6, 13);
        context.glass(List.of(new GlassDraw(GlassRegion.capsule(x, 30, 108, 30), material, -.7f, -.45f, .7f,
                GlassDraw.Profile.SNELL, bounds)));
        context.text(label, x + 22, 41, material.foreground().argb());
    }

    static void draw(RenderService.Context context) {
        for (int x = FIRST - 8; x < SECOND + WIDTH + 8; x += 4) {
            context.fill(x, TOP - 8, x + 4, TOP + HEIGHT + 8, ((x - FIRST) / 4 & 1) == 0 ? STRIPE_A : STRIPE_B);
        }
        context.fill(FIRST - 8, 119, SECOND + WIDTH + 8, 164, CONTRAST_BACKGROUND);
        context.fill(CONTRAST_X, FROST_TOP, CONTRAST_X + 54, FROST_TOP + HEIGHT, CONTRAST_BACKGROUND);
        for (int x : new int[] { FIRST, SECOND }) {
            context.fill(x - 8, FROST_TOP - 8, x + WIDTH / 2, FROST_TOP + HEIGHT + 8, STRIPE_A);
            context.fill(x + WIDTH / 2, FROST_TOP - 8, x + WIDTH + 8, FROST_TOP + HEIGHT + 8, STRIPE_B);
        }
        var glass = UnifiedUI.get().glass();
        var regular = glass.material(GlassVariant.REGULAR);
        var clear = glass.material(GlassVariant.CLEAR);
        var optics = clear.optics();
        var neutral = new GlassMaterial(GlassVariant.CLEAR,
                new GlassOptics(optics.transmission(), 0, optics.lensing(), optics.scattering(), 0,
                        optics.thickness(), optics.blurRadius()), clear.quality(), clear.foreground());
        var lens = new GlassMaterial(GlassVariant.CLEAR,
                new GlassOptics(optics.transmission(), 8, optics.lensing(), optics.scattering(), 0,
                        optics.thickness(), optics.blurRadius()), clear.quality(), clear.foreground());
        var dark = new GlassMaterial(GlassVariant.CLEAR, clear.optics(), GlassQuality.ULTRA, GlassMaterial.Foreground.DARK);
        var fallback = new GlassMaterial(GlassVariant.REGULAR, regular.optics(), GlassQuality.FALLBACK, regular.foreground());

        context.clip(FIRST - 8, TOP - 8, 316, 164, () -> {
            RenderSystem.disableDepthTest();
            try {
                int[] before = glState();
                context.glass(List.of(new GlassDraw(GlassRegion.capsule(FIRST, TOP, WIDTH, HEIGHT), neutral,
                                GlassDraw.Profile.SNELL, null),
                        new GlassDraw(GlassRegion.capsule(SECOND, TOP, WIDTH, HEIGHT), lens, GlassDraw.Profile.SNELL, null),
                        new GlassDraw(GlassRegion.ellipse(268, 76, 40, 28), dark)));
                int[] after = glState();
                require(Arrays.equals(before, after), "Glass changed native draw state "
                        + Arrays.toString(before) + " -> " + Arrays.toString(after));
            } finally {
                RenderSystem.enableDepthTest();
            }

        });
        var sharp = new GlassMaterial(GlassVariant.REGULAR, new GlassOptics(1, 0, 0, 0, 0, 3, 24),
                GlassQuality.BALANCED, regular.foreground());
        var frost = new GlassMaterial(GlassVariant.REGULAR, new GlassOptics(1, 0, 0, 1, 0, 3, 24),
                GlassQuality.BALANCED, regular.foreground());
        var bounds = new GlassDraw.ForegroundBounds(CONTRAST_X + 18, FROST_TOP + 9, 18, 10);
        context.glass(List.of(new GlassDraw(DRAG_ZONE, regular),
                new GlassDraw(GlassRegion.capsule(FIRST, FROST_TOP, WIDTH, HEIGHT), sharp, GlassDraw.Profile.SNELL, null),
                new GlassDraw(GlassRegion.capsule(SECOND, FROST_TOP, WIDTH, HEIGHT), frost, GlassDraw.Profile.SNELL, null),
                new GlassDraw(GlassRegion.capsule(CONTRAST_X, FROST_TOP, 54, HEIGHT), sharp, GlassDraw.Profile.SNELL, bounds)));
        context.glass(List.of(new GlassDraw(GlassRegion.capsule(265, 128, 55, 28), fallback)));
    }

    static void drawHighContrast(RenderService.Context context) {
        var glass = UnifiedUI.get().glass();
        var regular = glass.material(GlassVariant.REGULAR);
        var clear = glass.material(GlassVariant.CLEAR);
        var dark = new GlassMaterial(GlassVariant.CLEAR, clear.optics(), GlassQuality.ULTRA, GlassMaterial.Foreground.DARK);
        context.fill(FIRST - 8, 119, SECOND + WIDTH + 8, 164, CONTRAST_BACKGROUND);
        context.glass(List.of(new GlassDraw(GlassRegion.continuous(FIRST, 128, WIDTH, 28, 13), regular,
                        -.7f, -.5f, 1, GlassDraw.Profile.SNELL, null),
                new GlassDraw(GlassRegion.rounded(SECOND, 128, WIDTH, 28, 7), dark, GlassDraw.Profile.SNELL, null)));
        context.text(Component.translatable("gui.done"), FIRST + 20, 138, regular.foreground().argb());
        context.text(Component.translatable("gui.back"), SECOND + 20, 138, dark.foreground().argb());
    }

    static List<TaskHandle> submitLayerChecks(ModuleContext module, ScopeHandle scope) {
        var engine = module.engine();
        var renderer = engine.require(UIApis.RENDER);
        var material = engine.glass().material(GlassVariant.REGULAR);
        var surface = RenderService.Surface.SCREEN;
        return List.of(
                renderer.submit(module, scope, surface, RenderService.Layer.TOOLTIP, 0,
                        context -> context.fill(26, 111, 44, 129, FRONT_MARKER)),
                renderer.submit(module, scope, surface, RenderService.Layer.OVERLAY, 0,
                        context -> context.clip(88, 108, 121, 132, () ->
                                context.glass(List.of(new GlassDraw(GlassRegion.capsule(88, 108, 64, 24), material))))),
                renderer.submit(module, scope, surface, RenderService.Layer.CONTENT, 1,
                        context -> {
                            context.glass(List.of(new GlassDraw(GlassRegion.capsule(16, 108, 52, 24), material)));
                            context.fill(88, 108, 152, 132, BEHIND_MARKER);
                        }));
    }

    static void verifyLayers(NativeImage image, Minecraft client, boolean fallback) {
        require(pixel(image, client, 32, 120) == FRONT_MARKER,
                "Later CONTENT glass covered earlier TOOLTIP pixels");
        int protectedGlass = pixel(image, client, 106, 120);
        require(fallback ? protectedGlass == GlassMaterial.Foreground.LIGHT.fallbackArgb() : protectedGlass != BEHIND_MARKER,
                "Later CONTENT pixels covered earlier OVERLAY glass");
        require(pixel(image, client, 131, 120) == BEHIND_MARKER,
                "Clipped OVERLAY glass occluded CONTENT pixels outside the scissor");
    }

    static void verify(NativeImage image, Minecraft client) {
        var stats = UnifiedUI.get().glass().diagnostics();
        require(stats.shaderReady() && stats.passesWithCapture() == 1 && stats.opticalDraws() == INITIAL_OPTICAL_DRAWS - 2
                        && stats.fallbackDraws() == 1 && stats.captureBytes() > 4L * image.getWidth() * image.getHeight()
                        && stats.captureBytes() <= 32L << 20,
                "Glass did not render against one budgeted capture with blur levels " + stats);
        int changed = 0;
        for (int offset = 2; offset < 10; offset++) {
            if (pixel(image, client, FIRST + offset, CENTER_Y) != pixel(image, client, SECOND + offset, CENTER_Y)) changed++;
        }
        require(changed >= 3, "Refraction did not move the stripe edges");
        require(pixel(image, client, FIRST + WIDTH / 2, CENTER_Y) == pixel(image, client, SECOND + WIDTH / 2, CENTER_Y),
                "The center did not preserve the transmitted backdrop");
        require(pixel(image, client, SECOND + WIDTH / 2, CENTER_Y) == STRIPE_B,
                "Clear glass altered the original scene color in the flat center");
        int middle = FROST_TOP + HEIGHT / 2;
        double protectedLevel = luminance(pixel(image, client, CONTRAST_X + 27, middle));
        double transitionLevel = luminance(pixel(image, client, CONTRAST_X + 39, middle));
        double backgroundLevel = luminance(pixel(image, client, CONTRAST_X + 47, middle));
        require(protectedLevel <= .183,
                "Snell glass did not protect the foreground bounds");
        require(transitionLevel > protectedLevel + .08 && transitionLevel < backgroundLevel - .08,
                "Snell foreground contrast does not feather beyond the text bounds: " + transitionLevel);
        require(pixel(image, client, CONTRAST_X + 47, middle) == CONTRAST_BACKGROUND,
                "Snell foreground contrast changed the backdrop away from the text");
        require(pixel(image, client, 292, 142) == GlassMaterial.Foreground.LIGHT.fallbackArgb(),
                "The solid fallback did not draw");
        require(pixel(image, client, FIRST + WIDTH / 2 - 1, middle) == STRIPE_A
                        && pixel(image, client, FIRST + WIDTH / 2, middle) == STRIPE_B,
                "Sharp glass changed the sampled color");
        double sharpEdge = edgeContrast(image, client, FIRST + WIDTH / 2, middle);
        double frostedEdge = edgeContrast(image, client, SECOND + WIDTH / 2, middle);
        require(frostedEdge < sharpEdge * 0.2, "Wide frost retained a sharp backdrop edge: " + sharpEdge + " -> " + frostedEdge);
    }

    static void verifyHighContrast(NativeImage image, Minecraft client) {
        var stats = UnifiedUI.get().glass().diagnostics();
        require(stats.passesWithCapture() == 2 && stats.opticalDraws() == INITIAL_OPTICAL_DRAWS,
                "High-contrast glass did not render against a fresh capture " + stats);
        require(luminance(pixel(image, client, FIRST + 12, 142)) < .105,
                "Light text is missing high-contrast protection");
        require(luminance(pixel(image, client, SECOND + 12, 142)) > .29,
                "Dark text is missing high-contrast protection");
    }

    private static double edgeContrast(NativeImage image, Minecraft client, int x, int y) {
        return Math.abs(luminance(pixel(image, client, x, y)) - luminance(pixel(image, client, x - 1, y)));
    }

    private static int pixel(NativeImage image, Minecraft client, int x, int y) {
        int abgr = KernelClientSmoke.pixel(image, client, x, y);
        return abgr & 0xff00ff00 | (abgr & 255) << 16 | (abgr >> 16 & 255);
    }

    private static double luminance(int color) {
        return .2126 * linear(color >> 16 & 255) + .7152 * linear(color >> 8 & 255) + .0722 * linear(color & 255);
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static int[] glState() {
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            var scissor = stack.mallocInt(4);
            var color = stack.malloc(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, color);
            return new int[] {
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0, GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0,
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0, GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0,
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0, GL11.glIsEnabled(GL11.GL_STENCIL_TEST) ? 1 : 0,
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3),
                    color.get(0), color.get(1), color.get(2), color.get(3)
            };
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
