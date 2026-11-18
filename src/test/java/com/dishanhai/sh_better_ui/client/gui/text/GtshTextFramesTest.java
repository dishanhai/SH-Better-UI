// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GtshTextFramesTest {
    private record Glyph(int point, Style style) {}

    private static List<Glyph> glyphs(Component text) {
        var result = new ArrayList<Glyph>();
        text.visit((style, segment) -> {
            segment.codePoints().forEach(point -> result.add(new Glyph(point, style)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static List<Integer> colors(Component text) {
        return glyphs(text).stream().map(g -> g.style().getColor().getValue()).toList();
    }

    @Test void ordinaryNamedTextIsASnapshotAndOnlyCompatibilityContextUsesItsClock() {
        var now = new AtomicLong(0);
        var frames = new GtshTextFrames(now::get);
        var other = new GtshTextFrames(now::get);
        frames.lockTime();
        var original = frames.styled("hello", "rainbow");
        now.set(123);
        assertEquals(colors(original), colors(frames.styled("hello", "rainbow")));
        assertNotEquals(colors(original), colors(other.styled("hello", "rainbow")));
        assertTrue(glyphs(original).stream().allMatch(g -> g.style().getInsertion() == null));
        frames.unlockTime();
        assertNotEquals(colors(original), colors(frames.styled("hello", "rainbow")));
        frames.lockTime();
        now.set(234);
        frames.lockTime();
        now.set(345);
        assertEquals(colors(otherAt(234).styled("hello", "rainbow")), colors(frames.styled("hello", "rainbow")));
        frames.reset();
        assertEquals(colors(other.styled("hello", "rainbow")), colors(frames.styled("hello", "rainbow")));
    }

    private static GtshTextFrames otherAt(long time) { return new GtshTextFrames(() -> time); }

    @Test void speedEightyRestoresAdaptiveAndBodyDefaultsAndLongSpeedDoesNotOverflow() {
        var frames = otherAt(123);
        var palette = TextPalettes.get("rainbow");
        assertEquals(TextPalettes.color(palette, 0, 6, 123, UiTextEffects.Timing.DEFAULT),
                colors(frames.styled("abcdef", "rainbow")).get(0));
        frames.setSpeed(12);
        frames.setSpeed(80);
        assertEquals(TextPalettes.color(TextPalettes.get("body_silver"), 0, 6, 123, UiTextEffects.Timing.DEFAULT),
                colors(frames.styled("abcdef", "body_silver")).get(0));
        frames.setSpeed(0);
        var zero = colors(frames.styled("abc", "rainbow"));
        frames.setSpeed(-3);
        assertEquals(zero, colors(frames.styled("abc", "rainbow")));
        var huge = otherAt(Long.MAX_VALUE);
        huge.setSpeed(Long.MAX_VALUE);
        assertEquals(palette.rgb()[1], huge.getStartColor("rainbow"));
        huge.setDirection(0);
        assertEquals(palette.rgb()[3], colors(huge.styled("abc", "rainbow")).get(2));
        huge.setDirection(-1);
        assertEquals(palette.rgb()[palette.rgb().length - 1], colors(huge.styled("abc", "rainbow")).get(2));
    }

    @Test void customSnapshotsUseWallClockStrideAndMaskRatherThanGlobalSettings() {
        var now = new AtomicLong(0);
        var frames = new GtshTextFrames(now::get);
        frames.lockTime(); frames.setSpeed(999); frames.setDirection(-1);
        int[] palette = {0x112233, 0x445566, 0x778899};
        now.set(100);
        assertEquals(List.of(palette[1], palette[0], palette[2]), colors(frames.customRGB("a🌊c", palette, 100, 2)));
        assertEquals(List.of(palette[1], palette[1]), colors(frames.customRGB("ab", palette, 100, 0)));
        assertEquals(List.of(palette[1], palette[0]), colors(frames.customRGB("ab", palette, 100, -1)));
        now.set(256);
        var result = frames.custom("🌊a", new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BLUE}, 100, 255);
        assertEquals(List.of(ChatFormatting.RED.getColor(), ChatFormatting.BLUE.getColor()), colors(result));
        assertEquals(2, glyphs(result).size());
        assertTrue(glyphs(frames.custom("x", new ChatFormatting[]{ChatFormatting.BOLD}, 1, 255)).get(0).style().isBold());
        assertThrows(IllegalArgumentException.class, () -> frames.customRGB("x", new int[0], 1, 1));
        assertThrows(IllegalArgumentException.class, () -> frames.custom("x", new ChatFormatting[0], 1, 1));
        assertEquals("", frames.customRGB(null, palette, 1, 1).getString());
    }

    @Test void fillerPreservesMidpointAndTruncationWithoutSplittingSupplementaryCharacters() {
        var frames = otherAt(0);
        assertEquals(List.of(0x7f7f7f), colors(frames.gradientFiller("🌊", 0, 0xffffff)));
        assertEquals(List.of(0, 0x7f7f7f, 0xffffff), colors(frames.gradientFiller("abc", 0, 0xffffff)));
        assertEquals(" ", frames.gradientFiller(null, 0, 0xffffff).getString());
        assertEquals(" ", frames.gradientFiller("", 0, 0xffffff).getString());
    }

    @Test void multilineUsesNormalizedCodePointLengthAndOneFrameWithEmptyRowsPreserved() {
        var frames = otherAt(137);
        var lines = frames.multiLineText(new String[]{"🌊——", "", "X"}, "body_cream_static");
        assertEquals("🌊----", lines[0].getString());
        assertEquals(" ", lines[1].getString());
        // Source multiline helpers use raw colors without body/static semantics.
        var raw = new TextPalettes.Palette(TextPalettes.get("body_cream").rgb(), false, TextPalettes.Mode.CYCLE, false);
        var all = new ArrayList<Integer>(colors(lines[0])); all.addAll(colors(lines[2]));
        for (int i = 0; i < 6; i++) assertEquals(TextPalettes.color(raw, i, 6, 137, UiTextEffects.Timing.DEFAULT), all.get(i));
        assertEquals(all.get(5), frames.getEndColor("body_cream_static", "🌊——X"));
        assertEquals(TextPalettes.color(raw, 0, 1, 137, UiTextEffects.Timing.DEFAULT), frames.getStartColor("body_cream_static"));
        assertEquals("——", frames.multiLineText(new String[]{"——"}, "missing")[0].getString());
        assertEquals(0xffffff, frames.getStartColor("missing"));
    }

    @Test void revealUsesLockedColorButWallClockTailAndDoesNotClampRevealedBeforeTailMath() {
        var now = new AtomicLong(0);
        var frames = new GtshTextFrames(now::get); frames.lockTime();
        var first = glyphs(frames.reveal("🌊AB", 1, "rainbow", 1, UiPreferences.DEFAULT));
        assertTrue(first.get(0).style().isObfuscated());
        assertEquals("🌊　　", frames.reveal("🌊AB", 1, "rainbow", 1, UiPreferences.DEFAULT).getString());
        now.set(200);
        var second = glyphs(frames.reveal("🌊AB", 1, "rainbow", 1, UiPreferences.DEFAULT));
        assertFalse(second.get(0).style().isObfuscated());
        assertEquals(first.get(0).style().getColor(), second.get(0).style().getColor());
        assertEquals("　　　", frames.reveal("🌊AB", -1, "rainbow", 1, UiPreferences.DEFAULT).getString());
        assertTrue(glyphs(frames.reveal("🌊AB", 100, "rainbow", 1, UiPreferences.DEFAULT)).stream()
                .noneMatch(g -> g.style().isObfuscated()));
        var high = glyphs(frames.reveal("🌊AB", 3, "rainbow", 3, new UiPreferences(true, false, false)));
        assertTrue(high.stream().allMatch(g -> g.style().getColor() == null && !g.style().isObfuscated()));
        var reduced = new UiPreferences(false, true, false);
        var quiet = glyphs(frames.reveal("abc", 3, "rainbow", 3, reduced));
        now.set(1000);
        assertEquals(quiet, glyphs(frames.reveal("abc", 3, "rainbow", 3, reduced)));
        assertTrue(quiet.stream().noneMatch(g -> g.style().isObfuscated()));
    }

    @Test void explicitStyledPreferencesSuppressColorDecorationsAndFreezeMotion() {
        var now = new AtomicLong(0);
        var frames = new GtshTextFrames(now::get);
        var quiet = new UiPreferences(false, true, false);
        for (String name : List.of("rainbow", "obfuscatedrainbow", "body_cream_wobble", "random")) {
            var first = glyphs(frames.styled("test", name, quiet));
            now.addAndGet(400);
            assertEquals(first, glyphs(frames.styled("test", name, quiet)));
            assertTrue(first.stream().noneMatch(g -> g.style().isObfuscated() || g.style().getInsertion() != null));
            assertTrue(glyphs(frames.styled("test", name, new UiPreferences(true, false, false))).stream()
                    .allMatch(g -> g.style().getColor() == null && !g.style().isObfuscated()));
        }
    }

    @Test void namedModesRespectSnapshotPolicyAndAvoidLongScanDivisionByZero() {
        var frames = otherAt(400);
        assertEquals(1300, frames.styled("x".repeat(1300), "body_cream_scan").getString().length());
        assertEquals(1, colors(frames.styled("abc", "body_cream_static")).stream().distinct().count());
        assertEquals(1, colors(frames.styled("abc", "obfuscatedrainbow")).stream().distinct().count());
        assertTrue(glyphs(frames.styled("abc", "obfuscatedrainbow")).stream().allMatch(g -> g.style().isObfuscated()));
        assertNotNull(UiTextEffects.format(glyphs(frames.styled("abc", "all")).get(0).style()));
        assertNotNull(UiTextEffects.format(glyphs(frames.styled("abc", "random")).get(0).style()));
    }
}
