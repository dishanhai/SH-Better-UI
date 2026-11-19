// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Opt-in development client check; excluded from production sources. */
public final class BackgroundRenderSmoke extends Screen {
    private final com.dishanhai.sh_better_ui.client.api.BackgroundLayer layer = ShBetterUiApi.backgrounds().layer(null);
    private final Path output;
    private int frame;
    private boolean finished;
    private boolean reloaded;

    private BackgroundRenderSmoke(Path output) {
        super(Component.literal("Render state regression"));
        this.output = output;
    }

    public static void advance() {
        String path = System.getProperty("shb.renderState.smoke");
        var client = Minecraft.getInstance();
        if (path != null && client.getOverlay() == null && client.screen instanceof TitleScreen) {
            client.setScreen(new BackgroundRenderSmoke(Path.of(path)));
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (finished) return;
        try {
            var entries = ShBetterUiApi.backgrounds().entries();
            int index = frame / 24;
            if (index >= entries.size()) {
                var scene = BlackHoleBackdropRenderer.class.getDeclaredField("scene");
                scene.setAccessible(true);
                if (!reloaded) {
                    require(scene.get(null) != null, "Black hole never completed its geometry and shade passes");
                    verifyVignetteBlend(g);
                    verifyBlackHoleRelease(g, scene);
                    reloaded = true;
                }
                if (!BlackHoleBackdropRenderer.draw(g, new Bounds(30, 30, 120, 60),
                        BlackHoleBackdropProfile.DEFAULT, 0)) return;
                verifyBlackHoleFailure(g, scene);
                finish("success.txt", "PASS " + entries.size() + " backgrounds, resize, texture reveal, state, outside pixels and vignette blending and black-hole reload/failure cleanup\n");
                return;
            }
            var entry = entries.get(index);
            boolean large = frame % 24 >= 12;
            var bounds = new Bounds(30, 30, large ? 240 : 120, large ? 120 : 60);
            g.fill(0, 0, width, height, 0xFF305070);
            g.flush();
            int[] before = RenderStateSmoke.snapshot();
            layer.draw(entry.id(), g, font, bounds, null, Double.NaN, Double.NaN,
                    System.nanoTime() / 1_000_000, UiPreferences.DEFAULT);
            require(Arrays.equals(before, RenderStateSmoke.snapshot()), "State leak from " + entry.id());
            VisualRevealRenderer.drawTexture(g, ResourceLocation.fromNamespaceAndPath("sh_better_ui", "textures/gui/display/search.png"),
                    300, 30, 32, 32, RevealProfile.DEFAULT, 4);
            require(Arrays.equals(before, RenderStateSmoke.snapshot()), "Texture reveal leaked state after " + entry.id());
            var pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(2, 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            require((pixel.get(0) & 255) == 0x30 && (pixel.get(1) & 255) == 0x50 && (pixel.get(2) & 255) == 0x70,
                    "Effect overwrote pixels outside its preview: " + entry.id());
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL error from " + entry.id());
            if (frame % 24 == 23) System.out.println("PASS background render state: " + entry.id());
            frame++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish("failure.txt", failure.toString());
        }
    }

    private void verifyVignetteBlend(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xFF305070);
        g.flush();
        new com.mojang.blaze3d.shaders.BlendMode().apply();
        try {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(
                    GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
            g.setColor(0, 0, 0, 1);
            g.blit(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png"),
                    0, 0, 0, 0, width, height, width, height);
            var pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(2, 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            require((pixel.get(0) & 255) == 0x30 && (pixel.get(1) & 255) == 0x50 && (pixel.get(2) & 255) == 0x70,
                    "Vanilla vignette replaced the world color with black");
        } finally {
            g.setColor(1, 1, 1, 1);
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        }
        System.out.println("PASS vanilla vignette preserves multiplicative blend");
    }

    private static void verifyBlackHoleRelease(GuiGraphics g, java.lang.reflect.Field scene) throws Exception {
        g.flush();
        var release = BlackHoleBackdropRenderer.class.getDeclaredMethod("releaseBundle");
        release.setAccessible(true);
        int[] before = RenderStateSmoke.snapshot();
        release.invoke(null);
        require(Arrays.equals(before, RenderStateSmoke.snapshot()), "Black-hole reload cleanup changed the host bindings");
        require(scene.get(null) == null, "Black-hole reload retained its scene target");
        System.out.println("PASS black-hole reload cleanup keeps host bindings");
    }

    private static void verifyBlackHoleFailure(GuiGraphics g, java.lang.reflect.Field scene) throws Exception {
        g.flush();
        ((com.dishanhai.sh_better_ui.kernel.internal.GuiColorTarget) scene.get(null)).close();
        int[] before = RenderStateSmoke.snapshot();
        require(!BlackHoleBackdropRenderer.draw(g, new Bounds(30, 30, 120, 60),
                BlackHoleBackdropProfile.DEFAULT, 0), "Injected black-hole failure was not caught");
        require(scene.get(null) == null, "Failed black-hole resources were retained");
        require(Arrays.equals(before, RenderStateSmoke.snapshot()), "Black-hole failure cleanup unbound the host target");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL error after black-hole failure cleanup");
        System.out.println("PASS black-hole direct draw failure cleanup and host target restoration");
    }

    private void finish(String name, String result) {
        finished = true;
        try { Files.createDirectories(output); Files.writeString(output.resolve(name), result); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().tell(() -> Minecraft.getInstance().stop());
    }

    @Override public void removed() { layer.close(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
