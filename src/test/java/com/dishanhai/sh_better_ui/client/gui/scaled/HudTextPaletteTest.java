// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Source;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
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
    void semanticColorsKeepTheirHueWhenTheGlassFallbackNeedsAnOutline() {
        for (int color : new int[]{0xff55ffff, 0xff55ff55}) {
            var palette = new TextReadabilityPalette();
            palette.update(null, 0xff000000, 0xff767676, 0);
            assertNotEquals(color, palette.baseColor(color));
            assertEquals(color, palette.color(color));
            assertEquals(color, palette.animatedColor(color));
            assertEquals(0xff000000, palette.outlineColor(color));
        }
    }

    @Test
    void mixedBrightAndDarkSamplesKeepSemanticGlyphColorsAndNativeStyles() {
        var palette = new TextReadabilityPalette();
        palette.update(sample(new int[]{0xff000000, 0xffffffff, 0xff000000, 0xffffffff},
                Stage.POST_MATERIAL_PRE_TEXT), 0xff000000, 0xff767676, 0);

        var style = Style.EMPTY.withColor(0x55ffff).withBold(true).withUnderlined(true);
        var glyphs = FormattedCharSequence.codepoint('常', style);
        palette.glyphs(glyphs).accept((index, adapted, codePoint) -> {
            assertEquals(style, adapted);
            assertEquals('常', codePoint);
            return true;
        });
        assertEquals(0xff000000, palette.outlineColor(palette.color(0xff55ffff)));
    }

    @Test
    void semanticColorsStayAuthoredThroughoutBackdropTransitions() {
        var palette = new TextReadabilityPalette();
        long now = 0;
        for (int backdrop : new int[]{0xff000000, 0xffffffff, 0xff757575, 0xff000000}) {
            var observation = sample(backdrop);
            for (int frame = 0; frame < 20; frame++) {
                palette.update(observation, 0xff000000, 0xff767676, now);
                palette.baseColor(0xffffffff);
                for (int color : new int[]{0xff55ffff, 0xff55ff55, 0xff347fee, 0xffff55ff}) {
                    assertEquals(color, palette.color(color));
                    assertEquals(color, palette.animatedColor(color));
                }
                now += 40_000_000L;
            }
        }
    }

    @Test
    void protectsInlineBlueOnTheSampledGlassAndKeepsAuthoredColorAndStyles() {
        var palette = new TextReadabilityPalette();
        int blue = 0xff347fee;
        int glass = 0xff5672a4;
        palette.update(sample(glass), 0xff000000, 0xff767676);

        var glyph = FormattedCharSequence.codepoint('9', Style.EMPTY.withColor(blue & 0xffffff).withBold(true));
        palette.glyphs(glyph).accept((index, style, codePoint) -> {
            assertTrue(style.isBold());
            assertEquals('9', codePoint);
            int adjusted = 0xff000000 | style.getColor().getValue();
            assertEquals(blue, adjusted);
            assertTrue(palette.outlineColor(adjusted) >>> 24 > 0);
            return true;
        });
    }

    @Test
    void baseAndNeutralInlineFillsRejectFlashesAndRenderIntermediateColors() {
        var palette = new TextReadabilityPalette();
        int white = 0xffffffff;
        int black = 0xff000000;
        var bright = sample(white);
        var glyph = FormattedCharSequence.codepoint('9', Style.EMPTY.withColor(black & 0xffffff).withBold(true));
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
        assertEquals(0xfff4f4f4, palette.baseColor(white));
        assertEquals(0xfff4f4f4, palette.baseColor(white));
        assertEquals(black, palette.outlineColor(0xfff4f4f4));
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

    @Test
    void baseFillKeepsItsSelectedPolarityAcrossSmallSlowBackdropFluctuations() {
        var palette = new TextReadabilityPalette();
        int white = 0xffffffff;
        int black = 0xff000000;
        palette.update(null, 0xff757575, 0xff757575, 0);
        assertEquals(white, palette.baseColor(white));
        long now = 0;
        for (int cycle = 0; cycle < 6; cycle++) {
            int backdrop = cycle % 2 == 0 ? 0xff777777 : 0xff767676;
            for (int frame = 0; frame < 20; frame++) {
                now += 16_000_000L;
                palette.update(null, backdrop, backdrop, now);
                assertEquals(white, palette.baseColor(white), "Threshold noise must not restart inversion");
            }
        }
        for (int frame = 0; frame < 40; frame++) {
            now += 16_000_000L;
            palette.update(null, white, white, now);
            palette.baseColor(white);
        }
        assertEquals(black, palette.baseColor(white));
        for (int frame = 0; frame < 40; frame++) {
            now += 16_000_000L;
            palette.update(null, 0xff767676, 0xff767676, now);
            assertEquals(black, palette.baseColor(white), "Returning to the boundary must keep the committed polarity");
        }
    }

    @Test
    void aPendingReversalDoesNotFreezeTheCommittedFillTransition() {
        var palette = new TextReadabilityPalette();
        int white = 0xffffffff;
        int black = 0xff000000;
        palette.update(null, black, black, 0);
        palette.baseColor(white);
        palette.update(null, white, white, 10_000_000L);
        palette.baseColor(white);
        palette.update(null, white, white, 130_000_000L);
        palette.baseColor(white);
        palette.update(null, white, white, 170_000_000L);
        int before = palette.baseColor(white);
        palette.update(null, black, black, 186_000_000L);
        int after = palette.baseColor(white);
        assertTrue((after & 255) < (before & 255), "An unconfirmed candidate must not stop the active gradient");
        assertEquals(after, palette.baseColor(white));
    }

    @Test
    void outlineOpacityChangesContinuouslyAcrossTheContrastThreshold() {
        var palette = new TextReadabilityPalette();
        int white = 0xffffffff;
        palette.update(null, 0xff767676, 0xff767676, 0);
        int first = palette.outlineColor(white);
        palette.update(null, 0xff777777, 0xff777777, 16_000_000L);
        int second = palette.outlineColor(white);

        assertTrue(Math.abs((first >>> 24) - (second >>> 24)) <= 16,
                "A one-level backdrop change must not toggle an opaque outline");
    }

    @Test
    void outlineRetainsContrastingInkThroughTheFillTransitionMidpoint() {
        var palette = new TextReadabilityPalette();
        palette.update(null, 0xff757575, 0xff757575, 0);
        for (int level = 0; level <= 255; level++) {
            int fill = 0xff000000 | level * 0x010101;
            int outline = palette.outlineColor(fill);
            if (outline == 0) continue;
            assertTrue((outline >>> 24) >= 4, "Native Font must receive an explicit outline alpha");
            assertTrue(contrast(fill, outline) >= 4.5,
                    "A fading outline must retain contrasting ink through the intermediate grays");
        }
    }

    @Test
    void animatedNeutralColorsRetainAuthorTimingAndBlendBothBackdropMappings() {
        var palette = new TextReadabilityPalette();
        int black = 0xff000000;
        int white = 0xffffffff;
        int gray = 0xff808080;
        var bright = sample(white);
        palette.update(null, black, black, 0);
        assertEquals(white, palette.animatedColor(white));
        assertEquals(gray, palette.animatedColor(gray));

        palette.update(bright, black, black, 10_000_000L);
        assertEquals(white, palette.animatedColor(white));
        palette.update(null, black, black, 90_000_000L);
        assertEquals(gray, palette.animatedColor(gray));

        palette.update(bright, black, black, 100_000_000L);
        palette.update(bright, black, black, 220_000_000L);
        assertEquals(white, palette.animatedColor(white));
        assertEquals(gray, palette.animatedColor(gray));
        palette.update(bright, black, black, 1_000_000_000L);
        assertEquals(0xfff4f4f4, palette.animatedColor(white));
        int adjustedGray = TextReadabilityReference.readableFill(gray, 1, 1, 4.5);
        assertNotEquals(gray, palette.animatedColor(gray));
        assertNotEquals(adjustedGray, palette.animatedColor(gray));
        for (int frame = 1; frame <= 7; frame++) {
            palette.update(bright, black, black, 1_000_000_000L + frame * 40_000_000L);
        }
        assertEquals(black, palette.animatedColor(white));
        assertEquals(adjustedGray, palette.animatedColor(gray));
    }

    @Test
    void reportsPendingCaptureAndUsesDeclaredRangeUntilItArrives() {
        var palette = new TextReadabilityPalette();
        var pending = BackgroundSample.unavailable(new PixelRegion(0, 0, 32, 32), Stage.POST_MATERIAL_PRE_TEXT,
                Validity.PENDING, 1, 1, 1);
        palette.update(pending, 0xff000000, 0xff000000, 0);
        assertEquals(0xffffffff, palette.baseColor(0xffffffff));
        assertEquals(Validity.PENDING, palette.backgroundSample().orElseThrow().validity());

        var bright = sample(0xffffffff);
        palette.update(bright, 0xff000000, 0xff000000, 1);
        assertEquals(0xffffffff, palette.baseColor(0xffffffff));
        assertEquals(Validity.VALID, palette.backgroundSample().orElseThrow().validity());
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
    void anIsolatedBrighterSampleProtectsAuthoredColorsWithAnOutline() {
        var palette = new TextReadabilityPalette();
        int[] pixels = new int[16];
        Arrays.fill(pixels, 0xff34476b);
        int skyBehindGlass = 0xff5576b2;
        pixels[15] = skyBehindGlass;
        palette.update(sample(pixels, Stage.POST_MATERIAL_PRE_TEXT), 0xff000000, 0xff767676);
        for (int original : new int[]{0xfff7f7fa, 0xff347fee}) {
            int fill = palette.color(original);
            assertEquals(original, fill);
            assertTrue(palette.outlineColor(fill) >>> 24 > 0);
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
