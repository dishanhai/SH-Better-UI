// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RichTextEffectsTest {
    @Test
    void prioritizesGradientThenGlassWithinThePhysicalPixelBudget() {
        var effects = RichTextEffects.REVEAL;
        var full = effects.plan(100_000, UiPreferences.DEFAULT, 900);
        assertTrue(full.glass());
        assertTrue(full.gradient());
        assertTrue(full.reveal());
        assertTrue(full.lateBlur());
        assertEquals(700_000, full.pixelPasses());

        var limited = effects.plan(1_000_000, UiPreferences.DEFAULT, 900);
        assertTrue(limited.gradient());
        assertTrue(limited.glass());
        assertFalse(limited.reveal());
        assertFalse(limited.lateBlur());
        assertEquals(3_000_000, limited.pixelPasses());

        var disabled = effects.plan(Long.MAX_VALUE, UiPreferences.DEFAULT, 900);
        assertFalse(disabled.gradient());
        assertEquals(0, disabled.pixelPasses());
    }

    @Test
    void displayPreferencesSuppressSamplingAndMotionIndependently() {
        var effects = RichTextEffects.REVEAL;
        var still = effects.plan(100_000, new UiPreferences(false, true, true), 900);
        assertEquals(still, effects.plan(100_000, new UiPreferences(false, true, true), 2000));
        assertTrue(still.reveal());
        assertNotEquals(effects.plan(100_000, UiPreferences.DEFAULT, 0).originU(),
                effects.plan(100_000, UiPreferences.DEFAULT, 900).originU());

        var flat = effects.plan(100_000, new UiPreferences(false, false, false), 900);
        assertTrue(flat.gradient());
        assertFalse(flat.glass() || flat.reveal() || flat.lateBlur());
        assertEquals(0, effects.plan(100_000, new UiPreferences(true, false, true), 900).pixelPasses());
    }

    @Test
    void textAndSelectionKeepTheContrastFloorOnEitherBacking() {
        for (boolean highContrast : new boolean[]{false, true}) {
            int bound = RichTextEffects.backgroundBound(highContrast);
            double floor = highContrast ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO;
            int selection = RichTextEffects.readableSelection(0xfff0f0f0, highContrast);
            for (int input : new int[]{0x40f0f0f0, 0xff001030, 0xffff0000, 0xffffffff, 0xffaaddff}) {
                int output = RichTextEffects.readableForeground(input, highContrast);
                assertEquals(0xff, output >>> 24);
                assertTrue(ContrastPolicy.ratio(output, bound, 0xff000000) >= floor);
                assertTrue(ContrastPolicy.ratio(output, selection, 0xff000000) >= floor);
            }
        }
    }

    @Test
    void nativeVisualOrderFontAndMetadataSurviveColorProtection() {
        var style = Style.EMPTY.withColor(0x081019).withBold(true).withUnderlined(true)
                .withFont(ResourceLocation.tryParse("sh_better_ui:fixture")).withInsertion("fixture");
        FormattedCharSequence source = sink -> sink.accept(7, style, 'ב') && sink.accept(3, style, 'א');
        var indices = new ArrayList<Integer>();
        var points = new ArrayList<Integer>();
        assertTrue(RichTextEffects.protect(source, true).accept((index, rendered, point) -> {
            indices.add(index);
            points.add(point);
            assertEquals(style.withColor(0xffffff), rendered);
            return true;
        }));
        assertEquals(List.of(7, 3), indices);
        assertEquals(List.of((int) 'ב', (int) 'א'), points);
        assertFalse(RichTextEffects.protect(source, false).accept((index, rendered, point) -> false));
    }
}
