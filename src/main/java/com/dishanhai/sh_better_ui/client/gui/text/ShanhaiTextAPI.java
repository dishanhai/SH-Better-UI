// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.ChatFormatting;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.util.Locale;

/**
 * Source-level GTSH text facade in SHBUI's namespace, not a binary replacement of the GTSH mod.
 * Ordinary styled/shortcut calls capture a construction-time color frame. Explicit FCS and
 * ALL/RANDOM use live markers; WOBBLE remains a live movement effect. This facade's compatibility
 * clock does not change UiTextEffects or other SHBUI rendering clocks. Snapshot constructors use
 * DEFAULT preferences unless a host supplies the explicit preference overload; they never read Minecraft.
 * Inline compatibility and clean-text normalization are source-level, not byte-for-byte parity.
 * fromLegacy additionally preserves native bold/italic/decorations that upstream discarded.
 * Compatibility choices (fallback palettes and permissive inline tags) are confined to this facade.
 */
public final class ShanhaiTextAPI {
    private static final GtshTextFrames FRAMES = new GtshTextFrames(System::currentTimeMillis);

    private ShanhaiTextAPI() {}

    /** These controls affect only this facade's construction-time snapshots, never the global renderer. */
    public static synchronized void setSpeed(long millis) { FRAMES.setSpeed(millis); }
    public static synchronized void setDirection(int direction) { FRAMES.setDirection(direction); }
    public static synchronized void lockTime() { FRAMES.lockTime(); }
    public static synchronized void unlockTime() { FRAMES.unlockTime(); }
    public static synchronized void resetCompatibilityState() { FRAMES.reset(); }

    public static synchronized Component custom(String text, ChatFormatting[] colors, int intervalMs, int mask) {
        return FRAMES.custom(text, colors, intervalMs, mask);
    }
    public static synchronized Component customRGB(String text, int[] colors, int intervalMs, int direction) {
        return FRAMES.customRGB(text, colors, intervalMs, direction);
    }
    public static synchronized Component gradientFiller(String text, int startColor, int endColor) {
        return FRAMES.gradientFiller(text, startColor, endColor);
    }
    public static synchronized Component[] multiLineText(String[] lines, String style) {
        return FRAMES.multiLineText(lines, style);
    }
    public static synchronized int getStartColor(String style) { return FRAMES.getStartColor(style); }
    public static synchronized int getEndColor(String style, String text) { return FRAMES.getEndColor(style, text); }
    public static Component reveal(String text, int revealed) { return reveal(text, revealed, "body_silver", 3); }
    public static Component reveal(String text, int revealed, String style) { return reveal(text, revealed, style, 3); }
    /** Pure construction: hosts that need accessibility preferences should use the explicit overload. */
    public static Component reveal(String text, int revealed, String style, int obfuscatedTail) {
        return reveal(text, revealed, style, obfuscatedTail, UiPreferences.DEFAULT);
    }
    public static synchronized Component reveal(String text, int revealed, String style, int obfuscatedTail,
                                                UiPreferences preferences) {
        return FRAMES.reveal(text, revealed, style, obfuscatedTail, preferences);
    }

    public static Component format(String input) {
        return input == null || input.isEmpty() ? Component.empty() : UiTextEffects.parse(input);
    }

    /** GTSH contract: the default applies to the whole string unless & markup and a non-null default coexist. */
    public static Component format(String input, String defaultStyle) {
        if (input == null || input.isEmpty()) return Component.empty();
        return !input.contains("&") || defaultStyle == null ? styled(input, defaultStyle) : format(input);
    }

    /** Null styles are literal; unknown styles intentionally fall back to ultimate, as in GTSH. */
    public static Component styled(String text, String style) {
        return styled(text, style, UiPreferences.DEFAULT);
    }

    /** Hosts pass accessibility preferences explicitly; snapshot construction does not query Minecraft. */
    public static synchronized Component styled(String text, String style, UiPreferences preferences) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (style == null) return Component.literal(text);
        String key = style.toLowerCase(Locale.ROOT);
        if (UiTextEffects.find(key) == null) key = style.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (UiTextEffects.find(key) == null) key = "ultimate";
        return FRAMES.styled(text, key, preferences);
    }

    public static Component body(String text, String style) { return styled(text, "body_" + style); }

    /** Unknown theme names remain inactive markers; unlike styled(), this does not substitute a palette. */
    public static Component fcs(String effects, String text, String style) {
        String theme = style == null || style.isEmpty() ? "ultimate" : style;
        String symbols = effects == null ? "" : effects;
        StringBuilder selected = new StringBuilder();
        for (int i = 0; i < UiTextEffects.SYMBOLS.length(); i++) {
            char symbol = UiTextEffects.SYMBOLS.charAt(i);
            if (symbols.indexOf(symbol) >= 0) selected.append(symbol);
        }
        return Component.literal(text == null ? "" : text)
                .setStyle(UiTextEffects.mark(Style.EMPTY, theme, selected.toString()));
    }

    public static Component concat(String[] texts, String[] styles) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < texts.length; i++) result.append(styled(texts[i], i < styles.length ? styles[i] : "ultimate"));
        return result;
    }

    public static Component concat(String[] texts, String style) {
        MutableComponent result = Component.empty();
        for (String text : texts) result.append(styled(text, style == null ? "ultimate" : style));
        return result;
    }

    /** Parse only Minecraft section codes, never interpret FCS or inline tags in this literal entry point. */
    public static Component fromLegacy(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < legacy.length();) {
            int cp = legacy.codePointAt(i);
            if (cp == '§' && i + 1 < legacy.length()
                    && ChatFormatting.getByCode(legacy.charAt(i + 1)) != null) {
                if (!run.isEmpty()) { result.append(Component.literal(run.toString()).setStyle(style)); run.setLength(0); }
                ChatFormatting format = ChatFormatting.getByCode(legacy.charAt(i + 1));
                if (format != null) style = format == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(format);
                i += 2;
            } else { run.appendCodePoint(cp); i += Character.charCount(cp); }
        }
        if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(style));
        return result;
    }

    // GTSH inline is deliberately separate from the stricter UiTextEffects parser:
    // plain fragments get ultimate, unknown themes fall back, and an unclosed opener consumes the suffix.
    private static final String[] OPEN = {"{{", "{", "<"};
    private static final String[] END = {"}}", "}", ">"};
    private static final String[] CLOSE = {"{{/}}", "{/}", "</>"};

    public static Component inline(String input) {
        MutableComponent result = Component.empty();
        if (input == null || input.isEmpty()) return result;
        for (int cursor = 0; cursor < input.length();) {
            int best = -1, pair = -1;
            for (int i = 0; i < OPEN.length; i++) {
                int next = input.indexOf(OPEN[i], cursor);
                if (next >= 0 && (best < 0 || next < best)) { best = next; pair = i; }
            }
            if (best < 0) { appendRaw(result, input.substring(cursor)); break; }
            appendRaw(result, input.substring(cursor, best));
            int end = input.indexOf(END[pair], best + OPEN[pair].length());
            if (end < 0) { appendRaw(result, input.substring(best)); break; }
            String name = input.substring(best + OPEN[pair].length(), end).trim();
            int contentStart = end + END[pair].length();
            if (name.isEmpty() || name.equals("/")) { cursor = contentStart; continue; }
            int close = matchingClose(input, contentStart, pair);
            result.append(styled(input.substring(contentStart, close < 0 ? input.length() : close), name));
            cursor = close < 0 ? input.length() : close + CLOSE[pair].length();
        }
        return result;
    }

    private static void appendRaw(MutableComponent result, String text) {
        if (!text.isEmpty()) result.append(text.indexOf('§') >= 0 ? fromLegacy(text) : styled(text, "ultimate"));
    }

    private static int matchingClose(String input, int start, int pair) {
        int depth = 1, cursor = start;
        while (cursor < input.length()) {
            int close = input.indexOf(CLOSE[pair], cursor);
            if (close < 0) return -1;
            int open = input.indexOf(OPEN[pair], cursor);
            if (open >= 0 && open < close) {
                int end = input.indexOf(END[pair], open + OPEN[pair].length());
                if (end < 0) { cursor = open + 1; continue; }
                String candidate = input.substring(open + OPEN[pair].length(), end).trim();
                if (candidate.equals("/") || UiTextEffects.find(candidate) != null) depth++;
                cursor = end + END[pair].length();
            } else {
                if (--depth == 0) return close;
                cursor = close + CLOSE[pair].length();
            }
        }
        return -1;
    }

    public static Component rainbow(String text) { return styled(text, "rainbow"); }
    public static Component obfuscatedRainbow(String text) { return styled(text, "obfuscatedRainbow"); }
    public static Component golden(String text) { return styled(text, "golden"); }
    public static Component fire(String text) { return styled(text, "fire"); }
    public static Component water(String text) { return styled(text, "water"); }
    public static Component magic(String text) { return styled(text, "magic"); }
    public static Component nature(String text) { return styled(text, "nature"); }
    public static Component electric(String text) { return styled(text, "electric"); }
    public static Component ice(String text) { return styled(text, "ice"); }
    public static Component lava(String text) { return styled(text, "lava"); }
    public static Component sunset(String text) { return styled(text, "sunset"); }
    public static Component aurora(String text) { return styled(text, "aurora"); }
    public static Component crimson(String text) { return styled(text, "crimson"); }
    public static Component neon(String text) { return styled(text, "neon"); }
    public static Component sakura(String text) { return styled(text, "sakura"); }
    public static Component cosmic(String text) { return styled(text, "cosmic"); }
    public static Component jade(String text) { return styled(text, "jade"); }
    public static Component plasma(String text) { return styled(text, "plasma"); }
    public static Component starlight(String text) { return styled(text, "starlight"); }
    public static Component abyss(String text) { return styled(text, "abyss"); }
    public static Component all(String text) { return styled(text, "all"); }
    public static Component random(String text) { return styled(text, "random"); }
    public static Component ultimateRainbow(String text) { return styled(text, "ultimate"); }
    public static Component voidText(String text) { return styled(text, "void"); }
    public static Component fullRandom(String text) { return styled(text, "fullrandom"); }
    public static Component bodyGolden(String text) { return styled(text, "body_golden"); }
    public static Component bodyFire(String text) { return styled(text, "body_fire"); }
    public static Component bodyWater(String text) { return styled(text, "body_water"); }
    public static Component bodyMagic(String text) { return styled(text, "body_magic"); }
    public static Component bodyNature(String text) { return styled(text, "body_nature"); }
    public static Component bodyCrimson(String text) { return styled(text, "body_crimson"); }
    public static Component bodySilver(String text) { return styled(text, "body_silver"); }
    public static Component bodySunset(String text) { return styled(text, "body_sunset"); }
    public static Component bodyAurora(String text) { return styled(text, "body_aurora"); }
    public static Component bodyNeon(String text) { return styled(text, "body_neon"); }
    public static Component bodyElectric(String text) { return styled(text, "body_electric"); }
    public static Component bodyIce(String text) { return styled(text, "body_ice"); }
    public static Component bodyLava(String text) { return styled(text, "body_lava"); }
    public static Component bodyCream(String text) { return styled(text, "body_cream"); }
    public static Component bodyAmber(String text) { return styled(text, "body_amber"); }
    public static Component bodySlate(String text) { return styled(text, "body_slate"); }
    public static Component bodyRose(String text) { return styled(text, "body_rose"); }
    public static Component bodyMoss(String text) { return styled(text, "body_moss"); }
    public static Component ultimateRainbowDistort(String text) { return styled(text, "ultimateRainbow_distort"); }
    public static Component bodyCreamDistort(String text) { return styled(text, "body_cream_distort"); }
    public static Component bodySilverDistort(String text) { return styled(text, "body_silver_distort"); }
    public static Component ultimateRainbowWobble(String text) { return styled(text, "ultimateRainbow_wobble"); }
    public static Component bodyCreamWobble(String text) { return styled(text, "body_cream_wobble"); }
    public static Component wobble(String text, String style) { return fcs("~", text, style); }
    public static Component scan(String text, String style) { return fcs("^", text, style); }
    public static Component glitch(String text, String style) { return fcs("?", text, style); }
    public static Component breatheFx(String text, String style) { return fcs("+", text, style); }
    public static Component chase(String text, String style) { return fcs(">", text, style); }
    public static Component obfuscatedFcs(String text, String style) { return fcs("`", text, style); }
}
