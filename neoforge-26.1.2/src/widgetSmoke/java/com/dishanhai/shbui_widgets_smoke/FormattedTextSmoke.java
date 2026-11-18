// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

/** Actual native String/Component/FCS extraction and framebuffer validation; no mock renderer. */
final class FormattedTextSmoke {
    private static final Identifier[] IDS = {Identifier.parse("smoke:fcs_string"),
            Identifier.parse("smoke:fcs_component"), Identifier.parse("smoke:fcs_sequence")};
    private static final int[] calls = new int[3];
    private static Probe screen;
    private static int frames, stage;
    private static long firstCapture;
    private static boolean pending, secondPhase;
    private static int[] baselineProbe, baselinePalette;

    static void frame(Minecraft client) {
        if (WidgetSmokePlatform.hasOverlay(client)) return;
        require(++frames < 3600, "frame timeout");
        if (screen == null) {
            client.options.guiScale().set(2);
            for (int i = 0; i < IDS.length; i++) {
                int entry = i;
                UiTextEffects.registerEffect(IDS[i], context -> {
                    calls[entry]++;
                    return new UiTextEffects.Glyph(secondPhase ? 0x55ffbb : 0xff5566,
                            0, (float)Math.sin(context.timeMillis() / 180.0 + context.index()) * 1.2f, false, false);
                });
            }
            screen = new Probe();
            WidgetSmokePlatform.open(client, screen);
            GLFW.glfwFocusWindow(client.getWindow().handle());
            return;
        }
        if (pending || stage == 2 || screen.drawn < 12) return;
        if (stage == 1 && Util.getMillis() - firstCapture < 475) return;
        require(calls[0] > 0 && calls[1] > 0 && calls[2] > 0,
                "native hooks did not sample all three entries: " + java.util.Arrays.toString(calls));
        pending = true;
        int scale = client.getWindow().getGuiScale();
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(client), image -> {
            try (image) {
                int[] probe = region(image, scale, 18, 40, 360, 18);
                int[] palette = region(image, scale, 18, 112, 360, 21);
                image.writeToFile(client.gameDirectory.toPath().resolve("formatted-text-" + stage + ".png"));
                if (stage == 0) {
                    baselineProbe = probe;
                    baselinePalette = palette;
                    firstCapture = Util.getMillis();
                    secondPhase = true;
                    stage = 1;
                } else {
                    int probeChanged = differences(baselineProbe, probe);
                    int paletteChanged = differences(baselinePalette, palette);
                    require(probeChanged > 40, "native callback pixels unchanged: " + probeChanged);
                    require(paletteChanged > 40, "wall-clock palette pixels unchanged: " + paletteChanged);
                    String result = "PASS native String/Component/FCS hooks; width=" + screen.measuredWidth
                            + " wrappedLines=" + screen.wrapped.size() + " hookCalls=" + java.util.Arrays.toString(calls)
                            + " probeChanged=" + probeChanged + " paletteChanged=" + paletteChanged
                            + " elapsedMs=" + (Util.getMillis() - firstCapture) + " backend=" + WidgetSmokePlatform.backend()
                            + "\nUnicode and bidi source is retained; actual glyph coverage depends on installed game fonts.\n";
                    Files.writeString(client.gameDirectory.toPath().resolve("formatted-text.txt"), result);
                    System.out.println("SHBUI_FORMATTED_TEXT_PASS " + result);
                    stage = 2;
                    for (var id : IDS) UiTextEffects.unregister(id);
                    client.stop();
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally { pending = false; }
        });
    }

    private static int[] region(com.mojang.blaze3d.platform.NativeImage image, int scale,
                                int left, int top, int width, int height) {
        int[] pixels = new int[width * height * scale * scale];
        int offset = 0;
        for (int y = top * scale; y < (top + height) * scale; y++)
            for (int x = left * scale; x < (left + width) * scale; x++) pixels[offset++] = image.getPixel(x, y);
        return pixels;
    }

    private static int differences(int[] before, int[] after) {
        require(before.length == after.length, "framebuffer dimensions changed");
        int result = 0;
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) result++;
        return result;
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException("Formatted text smoke: " + detail);
    }

    private static final class Probe extends Screen {
        private static final String TEXT = "山海 Native e\u0301 🌊";
        private static final String RAW = "&$smoke:fcs_string-" + TEXT;
        private final Component component = UiTextEffects.apply(Component.literal("Component 粗体与字体 e\u0301")
                .withStyle(Style.EMPTY.withBold(true).withFont(new FontDescription.Resource(Identifier.parse("minecraft:uniform")))), IDS[1]);
        private final FormattedCharSequence sequence = UiTextEffects.apply(
                Component.literal("FCS 𠀀 e\u0301 עברית العربية"), IDS[2]).getVisualOrderText();
        private final Component animated = UiTextEffects.parse("&~$rainbow-动态色板与波动 Animation e\u0301 🌊");
        private List<FormattedCharSequence> wrapped;
        private int measuredWidth, drawn;

        Probe() { super(Component.literal("GTSH formatted text migration")); }

        @Override protected void init() {
            measuredWidth = font.width(RAW);
            require(measuredWidth == font.width(TEXT), "String markup included in native width");
            var normalized = UiTextEffects.parse(RAW);
            require(font.width(normalized) == measuredWidth, "Component and String width differ");
            require(font.width(normalized.getVisualOrderText()) == measuredWidth, "FCS and String width differ");
            wrapped = font.split(UiTextEffects.parse("&~$rainbow-换行仍保留色板与样式，每行保持独立字形簇。Wrapped native lines keep their animation and width."), 210);
            require(wrapped.size() > 1, "expected wrapped native lines");
            for (var line : wrapped) require(font.width(line) <= 210, "wrapped line exceeds native width");
        }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
            graphics.fill(0, 0, width, height, 0xff111827);
            graphics.text(font, "GTSH / native formatted text", 18, 14, 0xffffffff, false);
            graphics.text(font, "String  / Unicode + live callback", 18, 29, 0xff9ca3af, false);
            graphics.text(font, RAW, 18, 44, 0xffffffff, false);
            graphics.text(font, component, 18, 70, 0xffffffff, true);
            graphics.text(font, sequence, 18, 94, 0xffffffff, false);
            graphics.text(font, animated, 18, 118, 0xffffffff, false);
            graphics.text(font, "Native wrap / width verified", 18, 144, 0xff9ca3af, false);
            int y = 160;
            for (var line : wrapped) { graphics.text(font, line, 18, y, 0xffffffff, false); y += 13; }
            drawn++;
        }
    }
}
