// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationGlassStyleTest {
    @Test void bothLabelsRemainReadableAcrossAllGrayBackgrounds() {
        var style = new ConfirmationGlassStyle();
        for (int fill : new int[]{0xff000000, 0xffffffff, 0xff777777, 0xff2266cc}) {
            for (int text : new int[]{0xff000000, 0xffffffff, 0xff757575}) {
                var p = style.palette(fill, text, 0xff888888, true);
                assertTrue((p.fill() >>> 24) >= 184);
                for (int value = 0; value <= 255; value++) {
                    int scene = 0xff000000 | value * 0x010101;
                    assertTrue(ContrastPolicy.ratio(p.title(), p.fill(), scene) >= 4.5);
                    assertTrue(ContrastPolicy.ratio(p.details(), p.fill(), scene) >= 4.5);
                }
                assertSame(p, style.palette(fill, text, 0xff888888, true));
                var solid = style.palette(fill, text, 0xff888888, false);
                assertEquals(fill, solid.fill());
                assertEquals(p.title(), solid.title());
                assertEquals(p.details(), solid.details());
            }
        }
    }

    @Test void paletteChangesInvalidateTheCachedOpticalResult() {
        var style = new ConfirmationGlassStyle();
        var dark = style.palette(0xff17202a, -1, 0xffcccccc, true);
        var light = style.palette(0xffeeeeee, 0xff000000, 0xff333333, true);
        assertNotEquals(dark, light);
        assertEquals(0xff000000, light.title());
        assertEquals(255, style.palette(0xffeeeeee, 0xff000000, 0xff333333, false).fill() >>> 24);
    }

    @Test void finalGlyphInkOverridesComponentsTranslationArgumentsAndLegacyCodes() {
        var heading = net.minecraft.network.chat.Component.literal("Heading")
                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
                .append(net.minecraft.network.chat.Component.literal(" details").withStyle(net.minecraft.ChatFormatting.BLUE));
        var argument = net.minecraft.network.chat.Component.literal("colored argument")
                .withStyle(net.minecraft.ChatFormatting.DARK_BLUE, net.minecraft.ChatFormatting.ITALIC);
        var translation = net.minecraft.network.chat.Component.translatableWithFallback(
                "sh_better_ui.test.glass_translation", "Confirm %s now", argument);
        var legacy = net.minecraft.network.chat.Component.literal("§1Dark §rplain");
        assertInk(heading, "Heading details");
        assertInk(translation, "Confirm colored argument now");
        assertInk(legacy, "Dark plain");
        var sequence = ConfirmationGlassStyle.ink(net.minecraft.locale.Language.getInstance().getVisualOrder(heading), 0xffeeeeee);
        var bold = new java.util.concurrent.atomic.AtomicBoolean();
        sequence.accept((index, style, point) -> { if (style.isBold()) bold.set(true); return true; });
        assertTrue(bold.get());
    }

    private static void assertInk(net.minecraft.network.chat.Component input, String expected) {
        var sequence = ConfirmationGlassStyle.ink(net.minecraft.locale.Language.getInstance().getVisualOrder(input), 0xffeeeeee);
        var text = new StringBuilder();
        sequence.accept((index, style, point) -> {
            assertNotNull(style.getColor());
            assertEquals(0xeeeeee, style.getColor().getValue() & 0xffffff);
            text.appendCodePoint(point);
            return true;
        });
        assertEquals(expected, text.toString());
    }

    @Test void opticalSurfaceUsesExactDialogBounds() {
        var draw = ConfirmationGlassStyle.surface(8, 20, 200, 120, -1);
        assertEquals(GlassDraw.Profile.SNELL, draw.profile());
        assertTrue(draw.material().optics().refraction() > 0);
        assertEquals(8, draw.region().x());
        assertEquals(20, draw.region().y());
        assertEquals(200, draw.region().width());
        assertEquals(120, draw.region().height());
    }
}
