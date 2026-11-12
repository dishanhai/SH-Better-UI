// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

/** Keeps editable input separate until it can be committed as one valid document edit. */
final class RichTextEditorProperties {
    enum Field {
        TEXT, KEY, FALLBACK, COLOR, FONT, TEXTURE, ALT, WIDTH, HEIGHT, SPACING;
        String key() { return "field." + name().toLowerCase(Locale.ROOT); }
    }

    enum Flag {
        BOLD, ITALIC, UNDERLINED, STRIKETHROUGH, OBFUSCATED;
        String key() { return "style." + name().toLowerCase(Locale.ROOT); }
    }

    private final InlineNode source;
    private final Map<Field, String> original = new EnumMap<>(Field.class);
    private final Map<Field, String> values = new EnumMap<>(Field.class);
    private final EnumSet<Flag> originalFlags = EnumSet.noneOf(Flag.class);
    private final EnumSet<Flag> flags;

    RichTextEditorProperties(InlineNode source) {
        this.source = source;
        if (source instanceof TextRun text) {
            original.put(Field.TEXT, text.text());
            readStyle(text.style());
        } else if (source instanceof LocalizedText localized) {
            original.put(Field.KEY, localized.key());
            original.put(Field.FALLBACK, localized.fallback());
            readStyle(localized.style());
        } else if (source instanceof ImageNode image) {
            original.put(Field.TEXTURE, image.texture().toString());
            original.put(Field.ALT, image.altText());
            original.put(Field.WIDTH, Integer.toString(image.intrinsicWidth()));
            original.put(Field.HEIGHT, Integer.toString(image.intrinsicHeight()));
        } else if (source instanceof ParagraphBreak paragraph) {
            original.put(Field.SPACING, Integer.toString(paragraph.spacing()));
        } else if (source instanceof RichExtensionNode extension) {
            original.put(Field.ALT, extension.altText());
        }
        values.putAll(original);
        flags = originalFlags.clone();
    }

    InlineNode source() { return source; }
    String get(Field field) { return values.get(field); }
    void set(Field field, String value) { values.put(field, value); }
    boolean enabled(Flag flag) { return flags.contains(flag); }

    void toggle(Flag flag) {
        if (!flags.remove(flag)) flags.add(flag);
    }

    boolean dirty() { return !values.equals(original) || !flags.equals(originalFlags); }

    InlineNode node() {
        var id = source.id();
        if (source instanceof TextRun) return new TextRun(id, get(Field.TEXT), style());
        if (source instanceof LocalizedText localized) {
            return new LocalizedText(id, get(Field.KEY), get(Field.FALLBACK), localized.arguments(), style());
        }
        if (source instanceof ImageNode) {
            return new ImageNode(id, UiId.parse(get(Field.TEXTURE).trim()), number(Field.WIDTH), number(Field.HEIGHT),
                    get(Field.ALT));
        }
        if (source instanceof ParagraphBreak) return new ParagraphBreak(id, number(Field.SPACING));
        if (source instanceof RichExtensionNode extension) {
            return new RichExtensionNode(id, extension.type(), extension.version(), extension.intrinsicWidth(),
                    extension.intrinsicHeight(), extension.logicalHeight(), get(Field.ALT), extension.payload(), false);
        }
        return source;
    }

    private int number(Field field) { return Integer.parseInt(get(field).trim()); }

    private void readStyle(RichStyle style) {
        original.put(Field.COLOR, style.color() == null ? "" : String.format(Locale.ROOT, "#%06x", style.color()));
        original.put(Field.FONT, style.font() == null ? "" : style.font().toString());
        if (style.bold()) originalFlags.add(Flag.BOLD);
        if (style.italic()) originalFlags.add(Flag.ITALIC);
        if (style.underlined()) originalFlags.add(Flag.UNDERLINED);
        if (style.strikethrough()) originalFlags.add(Flag.STRIKETHROUGH);
        if (style.obfuscated()) originalFlags.add(Flag.OBFUSCATED);
    }

    private RichStyle style() {
        String rgb = get(Field.COLOR).trim();
        if (rgb.startsWith("#")) rgb = rgb.substring(1);
        if (!rgb.isEmpty() && !rgb.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Expected RGB");
        String font = get(Field.FONT).trim();
        return new RichStyle(rgb.isEmpty() ? null : Integer.parseInt(rgb, 16), enabled(Flag.BOLD), enabled(Flag.ITALIC),
                enabled(Flag.UNDERLINED), enabled(Flag.STRIKETHROUGH), enabled(Flag.OBFUSCATED),
                font.isEmpty() ? null : UiId.parse(font));
    }
}
