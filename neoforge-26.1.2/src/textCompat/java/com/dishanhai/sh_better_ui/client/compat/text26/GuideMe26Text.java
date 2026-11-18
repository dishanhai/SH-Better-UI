// Author: dishanhai
package com.dishanhai.sh_better_ui.client.compat.text26;

import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Instance-scoped markup association for GuideME 26.1.17-beta; no global string matching. */
public final class GuideMe26Text {
    public static final AtomicInteger LAYOUTS = new AtomicInteger();
    public static final AtomicInteger ATTACHED = new AtomicInteger();
    public static final AtomicInteger RENDERED = new AtomicInteger();
    public static final AtomicInteger UNSUPPORTED = new AtomicInteger();
    private GuideMe26Text() {}

    public interface StyledRun { void shbui$setStyled(Component component, boolean literalFallback); }
    private record Glyph(int point, Style style) {}

    /**
     * GuideME owns font metrics. This slice accepts FCS color/displacement markup only;
     * legacy section formatting is left verbatim, rather than pretending its bold/font advances match.
     */
    public static Layout prepare(String raw) {
        Component literal = Component.literal(raw);
        Component parsed = UiTextEffects.normalizeLegacy(literal);
        if (parsed == literal) return null;
        if (raw.indexOf('§') >= 0 || parsed.visit((style, text) ->
                style.isBold() || style.isItalic() || style.isUnderlined() || style.isStrikethrough()
                        || !style.getFont().equals(Style.EMPTY.getFont())
                        ? java.util.Optional.of(true) : java.util.Optional.empty(), Style.EMPTY).isPresent()) {
            UNSUPPORTED.incrementAndGet();
            return new Layout(literal, true);
        }
        LAYOUTS.incrementAndGet();
        return new Layout(parsed, false);
    }

    /** Unsupported formatting is literal logical-order output, not a claim of RTL/Arabic shaping. */
    public static FormattedCharSequence renderSequence(Component styled, String raw, Style host, boolean literalFallback) {
        if (literalFallback) return FormattedCharSequence.forward(raw, host);
        return Language.getInstance().getVisualOrder(Component.empty().setStyle(host).append(styled));
    }

    public static final class Layout {
        private final String text;
        private final boolean literalFallback;
        private final List<Glyph> glyphs;
        private int cursor;
        private boolean invalidated;
        private Layout(Component component, boolean literalFallback) {
            this.literalFallback = literalFallback;
            text = component.getString();
            var collected = new ArrayList<Glyph>();
            component.visit((style, run) -> {
                run.codePoints().forEach(cp -> collected.add(new Glyph(cp, style)));
                return java.util.Optional.empty();
            }, Style.EMPTY);
            glyphs = List.copyOf(collected);
        }
        public String text() { return text; }
        public boolean literalFallback() { return literalFallback; }
        private Component invalidate() {
            invalidated = true;
            UNSUPPORTED.incrementAndGet();
            return null;
        }

        /** Consume one native emitted run; whitespace collapse is allowed, skipped visible text is not. */
        public Component take(String emitted) {
            if (invalidated) return null;
            int next = cursor;
            var result = Component.empty();
            for (int point : emitted.codePoints().toArray()) {
                while (next < glyphs.size() && Character.isWhitespace(glyphs.get(next).point())
                        && !Character.isWhitespace(point)) next++;
                if (next >= glyphs.size()) return invalidate();
                Glyph glyph = glyphs.get(next++);
                if (glyph.point() != point && !(Character.isWhitespace(glyph.point()) && Character.isWhitespace(point))) {
                    return invalidate();
                }
                result.append(Component.literal(new String(Character.toChars(point))).setStyle(glyph.style()));
            }
            cursor = next;
            ATTACHED.incrementAndGet();
            return result;
        }
    }
}
