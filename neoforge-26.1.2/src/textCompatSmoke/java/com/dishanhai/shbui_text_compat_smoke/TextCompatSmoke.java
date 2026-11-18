// Author: dishanhai
package com.dishanhai.shbui_text_compat_smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Actual installed official mods, transformed mixins, native layout, renderer and framebuffers. */
@Mod(TextCompatSmoke.ID)
@EventBusSubscriber(modid = TextCompatSmoke.ID, value = Dist.CLIENT)
public final class TextCompatSmoke {
    static final String ID = "shbui_text_compat_smoke";
    private static boolean started, pending;
    private static Probe screen;
    private static int frames, stage;
    private static long firstCapture;
    private static int[] baselineGuide, baselineFtb;

    @SubscribeEvent public static void start(ClientStartedEvent event) { started = true; }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!started) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        require(++frames < 3600, "timeout");
        if (screen == null) {
            client.options.guiScale().set(2);
            screen = new Probe(); client.setScreen(screen); return;
        }
        if (pending || stage == 2 || screen.drawn < 12) return;
        if (stage == 1 && Util.getMillis() - firstCapture < 475) return;
        pending = true;
        int scale = client.getWindow().getGuiScale();
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                int[] guide = region(image, scale, 18, 110, screen.column, screen.captureHeight);
                int[] ftb = region(image, scale, 36 + screen.column, 110, screen.column, screen.captureHeight);
                image.writeToFile(client.gameDirectory.toPath().resolve("compat-" + stage + ".png"));
                if (stage == 0) {
                    baselineGuide = guide; baselineFtb = ftb; firstCapture = Util.getMillis(); stage = 1;
                } else {
                    int guideChanged = differences(baselineGuide, guide), ftbChanged = differences(baselineFtb, ftb);
                    require(guideChanged > 20, "GuideME actual page pixels unchanged");
                    require(ftbChanged > 20, "FTB actual theme pixels unchanged");
                    String evidence = screen.ftbResult + "\n" + screen.guide.evidence()
                            + "\nPASS actual provider framebuffers guideChanged=" + guideChanged + " ftbChanged=" + ftbChanged
                            + " elapsedMs=" + (Util.getMillis() - firstCapture) + "\n";
                    Files.writeString(client.gameDirectory.toPath().resolve("compat.txt"), evidence);
                    System.out.println("SHBUI_TEXT_COMPAT_PASS " + evidence);
                    stage = 2; client.stop();
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            finally { pending = false; }
        });
    }
    private static int[] region(com.mojang.blaze3d.platform.NativeImage image, int scale, int x, int y, int w, int h) {
        int[] result = new int[w * h * scale * scale]; int index = 0;
        for (int py = y * scale; py < (y + h) * scale; py++)
            for (int px = x * scale; px < (x + w) * scale; px++) result[index++] = image.getPixel(px, py);
        return result;
    }
    private static int differences(int[] a, int[] b) {
        require(a.length == b.length, "frame dimensions changed"); int changed = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) changed++;
        return changed;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Text compatibility smoke: " + message);
    }
    private static final class Probe extends Screen {
        final GuideMeCompatProbe guide = new GuideMeCompatProbe();
        String ftbResult;
        int column, captureHeight, drawn;
        Probe() { super(Component.literal("Official GuideME + FTB text compatibility")); }
        @Override protected void init() {
            column = (width - 54) / 2;
            require(column >= 100, "viewport too narrow");
            ftbResult = FtbCompatProbe.run(font);
            guide.init(font, column);
            captureHeight = Math.min(height - 130, Math.max(100, guide.height()));
            require(captureHeight > 0, "viewport too short");
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
            graphics.fill(0, 0, width, height, 0xff111827);
            graphics.text(font, "SHBUI / actual optional text integrations", 18, 18, -1, false);
            graphics.text(font, "Pinned official jars; real layout and rendering", 18, 38, 0xff9ca3af, false);
            graphics.text(font, "GuideME 26.1.17-beta", 18, 82, -1, false);
            graphics.text(font, "FTB Library 26.1.2.12", 36 + column, 82, -1, false);
            guide.render(graphics, 18, 110, column);
            FtbCompatProbe.render(graphics, font, 36 + column, 110);
            drawn++;
        }
    }
}
