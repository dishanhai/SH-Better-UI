// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native text bridge. Prepare logical components before measuring, wrapping or applying bidi ordering. */
public final class UiRichText {
    public static final int ITEM_GLYPH = 0xfffc;
    public static final int ITEM_WIDTH = 10;
    private static final String ITEM_MARKER = "shbui:inline_item:";
    private static final String IMAGE_MARKER = "shbui:inline_image:";
    private static final String ID = "[a-z0-9_.-]+:[a-z0-9/._-]+";
    private static final String RENDERER = ",rndr:[a-z0-9_.-]+(?::[a-z0-9/._-]+)?";
    private static final Pattern TAG = Pattern.compile("<rich:item(?<pairedRender>" + RENDERER
            + ")?>(?<paired>" + ID + ")</rich:item>|<rich:(?:item=(?<item>" + ID
            + ")|(?<alias>[a-z0-9_.-]+))(?<render>" + RENDERER + ")?>");
    private static final Pattern ITEM_ID = Pattern.compile(ID);
    private static final Pattern ALIAS = Pattern.compile("[a-z0-9_.-]+");
    private static final java.util.Map<String, InlineImage> IMAGES = new ConcurrentHashMap<>();

    static {
        IMAGES.put("vmaterial-shop", new InlineImage("sh_better_ui:textures/gui/rich/material-shop.png"));
    }

    private UiRichText() {}

    public static Component parse(String text) {
        return prepare(UiTextEffects.parse(Objects.requireNonNull(text, "text")));
    }

    /** Keeps the caller's original component when there are no inline tags. Language changes require re-preparing. */
    public static Component prepare(Component text) {
        return (Component) prepare((FormattedText) Objects.requireNonNull(text, "text"));
    }

    public static FormattedText prepare(FormattedText text) {
        Objects.requireNonNull(text, "text");
        if (text.visit(raw -> raw.contains("<rich:") ? Optional.of(true) : Optional.empty()).isEmpty()) return text;
        var result = Component.empty();
        boolean[] changed = {false};
        text.visit((style, raw) -> {
            var matcher = TAG.matcher(raw);
            int start = 0;
            while (matcher.find()) {
                Component replacement = replacement(matcher, style);
                if (replacement == null) continue;
                result.append(Component.literal(raw.substring(start, matcher.start())).setStyle(style));
                result.append(replacement);
                changed[0] = true;
                start = matcher.end();
            }
            result.append(Component.literal(raw.substring(start)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : text;
    }

    /** An atomic ten-pixel item box. Native click/hover metadata is retained; insertion is reserved on this glyph. */
    public static Component item(String id, Style style) {
        if (!ITEM_ID.matcher(Objects.requireNonNull(id, "id")).matches()) {
            throw new IllegalArgumentException("Expected a namespaced item ID: " + id);
        }
        return object(ITEM_MARKER + id, style);
    }

    private static Component object(String marker, Style style) {
        return Component.literal("\ufffc").setStyle(InlineItemPlatform.style(Objects.requireNonNull(style, "style"))
                .withInsertion(marker).withBold(false).withItalic(false)
                .withUnderlined(false).withStrikethrough(false).withObfuscated(false));
    }

    /** Registers a local PNG alias, usable as <rich:alias>. Closing revokes future parses, not existing snapshots. */
    public static AutoCloseable registerImage(String alias, String texture) {
        if (!ALIAS.matcher(Objects.requireNonNull(alias, "alias")).matches() || alias.equals("item")) {
            throw new IllegalArgumentException("Invalid rich image alias: " + alias);
        }
        if (!ITEM_ID.matcher(Objects.requireNonNull(texture, "texture")).matches() || !texture.endsWith(".png")
                || texture.contains("://")) {
            throw new IllegalArgumentException("Expected a local namespaced PNG texture: " + texture);
        }
        var image = new InlineImage(texture);
        if (IMAGES.putIfAbsent(alias, image) != null) throw new IllegalArgumentException("Rich alias already registered: " + alias);
        return () -> IMAGES.computeIfPresent(alias, (key, current) -> current == image ? null : current);
    }

    private static Component replacement(Matcher matcher, Style style) {
        String modifier = matcher.group("pairedRender") != null ? matcher.group("pairedRender") : matcher.group("render");
        var renderer = UiRichIconRenderers.find(modifier == null ? null : modifier.substring(6));
        if (renderer == null) return null;
        String id = matcher.group("paired") != null ? matcher.group("paired") : matcher.group("item");
        if (id != null) {
            // Native 3D item models can share geometry effects, but texture shaders require a PNG alias.
            if (renderer.effect() != UiRichIconRenderers.Effect.NONE) return null;
            return object(ITEM_MARKER + id + (modifier == null ? "" : modifier), style);
        }
        var image = IMAGES.get(matcher.group("alias"));
        return image == null ? null : object(IMAGE_MARKER + image.texture() + (modifier == null ? "" : modifier), style);
    }

    public static Component item(String id) { return item(id, Style.EMPTY); }

    /** Uses a neutral root so following text never inherits the inline object's private font. */
    public static Component withItem(String id, Component label) {
        return Component.empty().append(item(id)).append(" ").append(Objects.requireNonNull(label));
    }

    /** Fits a native label without flattening styles or splitting inline item/image glyphs. */
    public static Component fit(Font font, Component text, int width) {
        Component prepared = prepare(text);
        if (width <= 0) return Component.empty();
        if (font.width(prepared) <= width) return prepared;
        var splitter = font.getSplitter();
        var marker = splitter.headByWidth(CommonComponents.ELLIPSIS, width, Style.EMPTY);
        var prefix = splitter.headByWidth(prepared, Math.max(0, width - (int) Math.ceil(splitter.stringWidth(marker))), Style.EMPTY);
        var result = Component.empty();
        prefix.visit((style, raw) -> {
            result.append(Component.literal(raw).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        marker.visit((style, raw) -> {
            result.append(Component.literal(raw).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static FormattedCharSequence sequence(Component text) {
        return prepare(UiTextEffects.normalize(text)).getVisualOrderText();
    }

    /** Also accepts raw forward sequences supplied by tooltip and third-party GUI APIs. */
    public static FormattedCharSequence prepare(FormattedCharSequence text) {
        Objects.requireNonNull(text, "text");
        boolean[] candidate = {false};
        text.accept((index, style, cp) -> {
            candidate[0] = cp == '<';
            return !candidate[0];
        });
        if (!candidate[0]) return text;
        var input = new ArrayList<Glyph>();
        text.accept((index, style, cp) -> { input.add(new Glyph(index, style, cp)); return true; });
        var output = new ArrayList<Glyph>();
        boolean changed = false;
        for (int from = 0; from < input.size();) {
            Style style = input.get(from).style();
            int to = from + 1;
            while (to < input.size() && style.equals(input.get(to).style())) to++;
            var raw = new StringBuilder();
            for (int i = from; i < to; i++) raw.appendCodePoint(input.get(i).cp());
            var matcher = TAG.matcher(raw);
            int cursor = 0;
            while (matcher.find()) {
                Component component = replacement(matcher, style);
                if (component == null) continue;
                int start = from + raw.codePointCount(0, matcher.start());
                int end = from + raw.codePointCount(0, matcher.end());
                output.addAll(input.subList(from + cursor, start));
                output.add(new Glyph(input.get(start).index(), component.getStyle(), ITEM_GLYPH));
                cursor = end - from;
                changed = true;
            }
            output.addAll(input.subList(from + cursor, to));
            from = to;
        }
        if (!changed) return text;
        List<Glyph> glyphs = List.copyOf(output);
        return sink -> {
            for (var glyph : glyphs) if (!sink.accept(glyph.index(), glyph.style(), glyph.cp())) return false;
            return true;
        };
    }

    public static String itemId(Style style, int codePoint) {
        String marker = style.getInsertion();
        if (codePoint != ITEM_GLYPH || marker == null || !marker.startsWith(ITEM_MARKER)
                || !InlineItemPlatform.isItemFont(style)) return null;
        String id = resource(marker.substring(ITEM_MARKER.length()));
        return ITEM_ID.matcher(id).matches() ? id : null;
    }

    public static String imageTexture(Style style, int codePoint) {
        String marker = style.getInsertion();
        if (codePoint != ITEM_GLYPH || marker == null || !marker.startsWith(IMAGE_MARKER)
                || !InlineItemPlatform.isItemFont(style)) return null;
        String texture = resource(marker.substring(IMAGE_MARKER.length()));
        return ITEM_ID.matcher(texture).matches() && texture.endsWith(".png") && !texture.contains("://") ? texture : null;
    }

    public static boolean isObject(Style style, int codePoint) {
        return itemId(style, codePoint) != null || imageTexture(style, codePoint) != null;
    }

    private static String resource(String marker) {
        int modifier = marker.indexOf(",rndr:");
        return modifier < 0 ? marker : marker.substring(0, modifier);
    }

    public static UiRichIconRenderers.Renderer renderer(Style style, int codePoint) {
        if (!isObject(style, codePoint)) return UiRichIconRenderers.Renderer.NONE;
        int modifier = style.getInsertion().indexOf(",rndr:");
        var renderer = UiRichIconRenderers.find(modifier < 0 ? null : style.getInsertion().substring(modifier + 6));
        return renderer == null ? UiRichIconRenderers.Renderer.NONE : renderer;
    }

    /** Outline passes keep advance widths without submitting duplicate items or images. */
    public static FormattedCharSequence withoutObjects(FormattedCharSequence text) {
        return sink -> text.accept((index, style, cp) -> sink.accept(index,
                isObject(style, cp) ? style.withInsertion(null) : style, cp));
    }

    /** Outline passes keep advance widths but must not submit duplicate item models. */
    public static FormattedCharSequence withoutItems(FormattedCharSequence text) {
        return sink -> text.accept((index, style, cp) -> sink.accept(index,
                itemId(style, cp) == null ? style : style.withInsertion(null), cp));
    }

    private record Glyph(int index, Style style, int cp) {}
    private record InlineImage(String texture) {}
}
