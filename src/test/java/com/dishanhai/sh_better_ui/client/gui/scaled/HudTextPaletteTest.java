// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Source;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTextPaletteTest {
    @Test
    void adaptsInlineBlueOnTheSampledGlassAndKeepsGlyphStyles() {
        var palette = new TextReadabilityPalette();
        int blue = 0xff347fee;
        int glass = 0xff5672a4;
        palette.update(sample(glass), 0xff000000, 0xff767676);

        var glyph = FormattedCharSequence.codepoint('9', Style.EMPTY.withColor(blue & 0xffffff).withBold(true));
        palette.glyphs(glyph).accept((index, style, codePoint) -> {
            assertTrue(style.isBold());
            assertEquals('9', codePoint);
            int adjusted = 0xff000000 | style.getColor().getValue();
            assertNotEquals(blue, adjusted);
            assertTrue(contrast(adjusted, glass) >= 4.5);
            return true;
        });
    }

    @Test
    void baseAndInlineFillsRejectFlashesAndRenderIntermediateColors() {
        var palette = new TextReadabilityPalette();
        int white = 0xffffffff;
        int black = 0xff000000;
        int blue = 0xff347fee;
        var bright = sample(white);
        var glyph = FormattedCharSequence.codepoint('9', Style.EMPTY.withColor(blue & 0xffffff).withBold(true));
        palette.update(null, black, black, 0);
        assertEquals(white, palette.baseColor(white));
        int oldInline = inlineColor(palette, glyph);

        palette.update(bright, black, black, 10_000_000L);
        assertEquals(white, palette.baseColor(white));
        palette.update(null, black, black, 90_000_000L);
        assertEquals(white, palette.baseColor(white));

        for (long now : new long[]{100_000_000L, 220_000_000L}) {
            palette.update(bright, black, black, now);
            assertEquals(white, palette.baseColor(white));
            assertEquals(oldInline, inlineColor(palette, glyph));
        }
        palette.update(bright, black, black, 1_000_000_000L);
        assertEquals(0xffdfdfdf, palette.baseColor(white));
        assertEquals(0xffdfdfdf, palette.baseColor(white));
        assertEquals(black, palette.outlineColor(0xffdfdfdf));
        int middleInline = inlineColor(palette, glyph);
        assertNotEquals(oldInline, middleInline);
        for (int frame = 1; frame <= 7; frame++) {
            palette.update(bright, black, black, 1_000_000_000L + frame * 40_000_000L);
            palette.baseColor(white);
            inlineColor(palette, glyph);
        }
        assertEquals(black, palette.baseColor(white));
        assertNotEquals(middleInline, inlineColor(palette, glyph));
        assertEquals(0, palette.outlineColor(black));
    }

    private static int inlineColor(TextReadabilityPalette palette, FormattedCharSequence glyph) {
        int[] argb = {0};
        palette.glyphs(glyph).accept((index, style, codePoint) -> {
            assertTrue(style.isBold());
            argb[0] = 0xff000000 | style.getColor().getValue();
            return true;
        });
        return argb[0];
    }

    @Test
    void anIsolatedBrighterSampleStillConstrainsTheSharedTextColor() {
        var palette = new TextReadabilityPalette();
        int[] pixels = new int[16];
        Arrays.fill(pixels, 0xff34476b);
        int skyBehindGlass = 0xff5576b2;
        pixels[15] = skyBehindGlass;
        palette.update(sample(pixels, Stage.POST_MATERIAL_PRE_TEXT), 0xff000000, 0xff767676);
        for (int original : new int[]{0xfff7f7fa, 0xff347fee}) {
            int fill = palette.color(original);
            assertTrue(contrast(fill, pixels[0]) >= 4.5);
            assertTrue(contrast(fill, skyBehindGlass) >= 4.5);
        }
    }

    @Test
    void aPreMaterialSampleDoesNotOverrideTheDeclaredSurface() {
        var palette = new TextReadabilityPalette();
        palette.update(sample(new int[]{0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
                Stage.BACKDROP_PRE_MATERIAL), 0xff000000, 0xff343b46);
        assertEquals(0xfff7f7fa, palette.color(0xfff7f7fa));
    }

    private static BackgroundSample sample(int color) {
        int[] pixels = new int[16];
        Arrays.fill(pixels, color);
        return sample(pixels, Stage.POST_MATERIAL_PRE_TEXT);
    }

    private static BackgroundSample sample(int[] pixels, Stage stage) {
        return BackgroundSample.pixels(new PixelRegion(0, 0, 32, 32), 4, pixels.length / 4, pixels,
                stage, Source.GPU_DOWNSAMPLED_PIXELS, 1, 1, 1);
    }

    private static double contrast(int first, int second) {
        return TextReadabilityReference.contrastRatio(TextReadabilityReference.srgbLuminance(first),
                TextReadabilityReference.srgbLuminance(second));
    }
}
