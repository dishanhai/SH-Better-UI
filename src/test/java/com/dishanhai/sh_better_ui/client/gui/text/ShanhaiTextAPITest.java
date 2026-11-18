// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ShanhaiTextAPITest {
    @BeforeEach void freezeFacadeClock() { ShanhaiTextAPI.lockTime(); }
    @AfterEach void resetFacadeClock() { ShanhaiTextAPI.resetCompatibilityState(); }
    @Test void styledKeepsSourceFallbackNullAndCamelCaseContracts() {
        assertEquals("", ShanhaiTextAPI.styled(null, "rainbow").getString());
        assertNull(ShanhaiTextAPI.styled("plain", null).getStyle().getInsertion());
        assertEquals(glyphs(ShanhaiTextAPI.ultimateRainbow("unknown")), glyphs(ShanhaiTextAPI.styled("unknown", "not_a_palette")));
        assertEquals(glyphs(ShanhaiTextAPI.bodySilver("正文")), glyphs(ShanhaiTextAPI.styled("正文", "bodySilver")));
        assertEquals(glyphs(ShanhaiTextAPI.bodySilver("正文")), glyphs(ShanhaiTextAPI.body("正文", "silver")));
    }

    @Test void defaultFormatOnlyEntersParserForAmpersandAndNonNullDefault() {
        assertEquals(glyphs(ShanhaiTextAPI.fire("plain")), glyphs(ShanhaiTextAPI.format("plain", "fire")));
        var literal = ShanhaiTextAPI.format("&$ice-raw", null);
        assertEquals("&$ice-raw", literal.getString()); assertNull(literal.getStyle().getInsertion());
        var formatted = ShanhaiTextAPI.format("&$ice-clean", "fire");
        assertEquals("clean", formatted.getString()); assertEquals(marker("ice"), formatted.getStyle().getInsertion());
        assertEquals("", ShanhaiTextAPI.format(null, "fire").getString());
        assertEquals("", ShanhaiTextAPI.format(null).getString());
        assertEquals("R&D", ShanhaiTextAPI.format("R&D", "fire").getString());
        assertNull(ShanhaiTextAPI.format("R&D", "fire").getStyle().getInsertion());
    }

    @Test void fcsRetainsUnknownThemeInsteadOfStyledFallbackAndFiltersEffects() {
        var marked = ShanhaiTextAPI.fcs("not an effect ~~~ 2 ^", "字🌊", "unknown");
        assertEquals("字🌊", marked.getString());
        assertEquals("shbui:fcs:unknown|~^", marked.getStyle().getInsertion());
        assertNull(UiTextEffects.format(marked.getStyle()));
        assertEquals(marker("ultimate"), ShanhaiTextAPI.fcs(null, null, null).getStyle().getInsertion());
    }

    @Test void concatBothOverloadsPreserveSegmentsAndSourceDefaults() {
        var varied = ShanhaiTextAPI.concat(new String[]{"一", "🌊", "三"}, new String[]{"ice", null});
        assertEquals("一🌊三", varied.getString());
        assertEquals(glyphs(Component.empty().append(ShanhaiTextAPI.ice("一")).append(Component.literal("🌊"))
                .append(ShanhaiTextAPI.ultimateRainbow("三"))), glyphs(varied));
        var same = ShanhaiTextAPI.concat(new String[]{"a", null, "b"}, (String)null);
        assertEquals("ab", same.getString());
        assertEquals(glyphs(Component.empty().append(ShanhaiTextAPI.ultimateRainbow("a")).append(ShanhaiTextAPI.ultimateRainbow("b"))), glyphs(same));
        assertEquals("", ShanhaiTextAPI.concat(new String[0], "fire").getString());
    }

    @Test void inlineUsesUltimateForRawAndSourcePermissiveUnknownAndUnclosedTags() {
        var mixed = ShanhaiTextAPI.inline("raw {fire}火{/} <ice>冰</> {{golden}}金{{/}}");
        assertEquals("raw 火 冰 金", mixed.getString());
        assertEquals(glyphs(ShanhaiTextAPI.ultimateRainbow("raw ")), glyphs(mixed).subList(0, 4));
        assertEquals("字", ShanhaiTextAPI.inline("{unknown}字{/}").getString());
        assertEquals(glyphs(ShanhaiTextAPI.ultimateRainbow("字")), glyphs(ShanhaiTextAPI.inline("{unknown}字{/}")));
        assertEquals(glyphs(ShanhaiTextAPI.ice("suffix")), glyphs(ShanhaiTextAPI.inline("{ice}suffix")));
        assertEquals("{broken", ShanhaiTextAPI.inline("{broken").getString());
        assertEquals("", ShanhaiTextAPI.inline(null).getString());
        // Compatibility behavior is scoped here, not silently imposed on the existing parser.
        assertEquals("{unknown}字{/}", UiTextEffects.parse("{unknown}字{/}").getString());
        assertEquals("{ice}suffix", UiTextEffects.parse("{ice}suffix").getString());
    }

    @Test void inlineFindsNestedBoundariesWithoutReinterpretingTheInnerLiteral() {
        assertEquals("A {ice}B{/} C tail", ShanhaiTextAPI.inline("{fire}A {ice}B{/} C{/} tail").getString());
        assertEquals("A {{ice}}B{{/}} C tail", ShanhaiTextAPI.inline("{{fire}}A {{ice}}B{{/}} C{{/}} tail").getString());
    }

    @Test void fromLegacyPreservesUnicodeNativeFormattingAndDoesNotInterpretAmpersands() {
        var component = ShanhaiTextAPI.fromLegacy("§cé🌊§lBold§r &$ice-literal");
        assertEquals("é🌊Bold &$ice-literal", component.getString());
        component.visit((style, text) -> {
            if (text.equals("é🌊")) assertEquals(0xff5555, style.getColor().getValue());
            if (text.equals("Bold")) assertTrue(style.isBold());
            if (text.contains("literal")) { assertFalse(style.isBold()); assertNull(style.getColor()); }
            assertNull(style.getInsertion());
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals("§c字", ShanhaiTextAPI.inline("{ice}§c字{/}").getString());
        assertNull(ShanhaiTextAPI.inline("§c字").getSiblings().get(0).getStyle().getInsertion());
    }

    @Test void namedConvenienceMethodsReferenceRegisteredPalettes() throws Exception {
        String[] names = {"ultimateRainbow", "rainbow", "obfuscatedRainbow", "golden", "fire", "water", "magic",
                "nature", "electric", "ice", "lava", "sunset", "aurora", "crimson", "neon", "sakura", "cosmic",
                "voidText", "jade", "plasma", "starlight", "abyss", "all", "random", "fullRandom",
                "bodyGolden", "bodyFire", "bodyWater", "bodyMagic", "bodyNature", "bodyCrimson", "bodySilver",
                "bodySunset", "bodyAurora", "bodyNeon", "bodyElectric", "bodyIce", "bodyLava", "bodyCream",
                "bodyAmber", "bodySlate", "bodyRose", "bodyMoss", "ultimateRainbowDistort", "bodyCreamDistort",
                "bodySilverDistort", "ultimateRainbowWobble", "bodyCreamWobble"};
        for (String name : names) {
            Component text = (Component) ShanhaiTextAPI.class.getMethod(name, String.class).invoke(null, "字🌊");
            assertEquals("字🌊", text.getString());
            if (name.equals("all") || name.equals("random") || name.contains("Wobble")) assertNotNull(UiTextEffects.format(text.getStyle()), name);
            else {
                assertTrue(glyphs(text).stream().anyMatch(glyph -> glyph.style().getColor() != null), name);
                if (!name.contains("Wobble")) assertTrue(glyphs(text).stream().allMatch(glyph -> glyph.style().getInsertion() == null), name);
            }
        }
        assertEquals("~", UiTextEffects.format(ShanhaiTextAPI.wobble("字", "ice").getStyle()).symbols());
        assertEquals("^", UiTextEffects.format(ShanhaiTextAPI.scan("字", "ice").getStyle()).symbols());
    }

    @Test void snapshotWrappersUseIsolatedClockAndLiveFcsRemainsSeparate() {
        var first = glyphs(ShanhaiTextAPI.styled("abcdef🌊", "rainbow"));
        var live = ShanhaiTextAPI.fcs("~", "abcdef🌊", "rainbow");
        ShanhaiTextAPI.setSpeed(1);
        ShanhaiTextAPI.setDirection(-1);
        assertNotEquals(first, glyphs(ShanhaiTextAPI.styled("abcdef🌊", "rainbow")));
        assertEquals(live, ShanhaiTextAPI.fcs("~", "abcdef🌊", "rainbow"));
        assertEquals("text", ShanhaiTextAPI.customRGB("text", new int[]{0x123456}, 100, 0).getString());
        assertEquals("text", ShanhaiTextAPI.custom("text", new net.minecraft.ChatFormatting[]{net.minecraft.ChatFormatting.RED}, 100, -1).getString());
        assertEquals("ab", ShanhaiTextAPI.gradientFiller("ab", 0, 0xffffff).getString());
        assertEquals(2, ShanhaiTextAPI.multiLineText(new String[]{"a", "b"}, "rainbow").length);
        assertTrue(ShanhaiTextAPI.getStartColor("rainbow") >= 0);
        assertTrue(ShanhaiTextAPI.getEndColor("rainbow", "🌊") >= 0);
        assertEquals("a　", ShanhaiTextAPI.reveal("ab", 1, "rainbow", 0).getString());
    }

    @Test void explicitSnapshotPreferencesCanDisableColorAndMotionWithoutClientConfiguration() {
        var contrast = new com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences(true, false, false);
        assertEquals(glyphs(Component.literal("abc")), glyphs(ShanhaiTextAPI.styled("abc", "rainbow", contrast)));
        var reduced = new com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences(false, true, false);
        var quiet = ShanhaiTextAPI.styled("abc", "body_cream_wobble", reduced);
        assertTrue(glyphs(quiet).stream().allMatch(g -> g.style().getInsertion() == null && !g.style().isObfuscated()));
    }

    @Test void unknownLegacyCodesNeverSplitSupplementaryCharacters() {
        for (String input : new String[]{"§🌊X", "§zX", "X§", "§𠀀é"}) {
            assertEquals(input, ShanhaiTextAPI.fromLegacy(input).getString());
            assertArrayEquals(input.codePoints().toArray(), ShanhaiTextAPI.fromLegacy(input).getString().codePoints().toArray());
        }
    }

    private static String marker(String name) { return "shbui:fcs:" + name + "|"; }
    private record Glyph(int cp, Style style) {}
    private static List<Glyph> glyphs(Component text) {
        var result = new ArrayList<Glyph>();
        text.visit((style, value) -> { value.codePoints().forEach(cp -> result.add(new Glyph(cp, style))); return Optional.empty(); }, Style.EMPTY);
        return result;
    }
}
