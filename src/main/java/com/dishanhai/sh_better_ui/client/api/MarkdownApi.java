package com.dishanhai.sh_better_ui.client.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Objects;
import java.util.regex.Pattern;

/** 山海：将面板说明、更新日志等常用 Markdown 转成原生文本。 */
public final class MarkdownApi {
    private static final int MAX_LENGTH = 65_536;
    private static final int MAX_NESTING = 8;
    private static final Style CODE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}[ \t]+");
    private static final Pattern HEADING_END = Pattern.compile("(?<![ \t])[ \t]++#++[ \t]*+$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>[ \t]?");
    private static final Pattern LIST = Pattern.compile("^( *)(?:[-+*]|([0-9]{1,9})[.)])[ \t]+");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}+|~{3,}+)(.*)$");
    private static final String PUNCTUATION = "\\p{Punct}";
    private static final String DESTINATION = "(?:[^\\s()<>]++|\\([^\\s()<>]*+\\))++";
    private static final Pattern ESCAPE = Pattern.compile("\\\\(" + PUNCTUATION + ")");
    private static final Pattern INLINE = Pattern.compile(
            "(?<escape>\\\\" + PUNCTUATION + ")"
                    + "|(?<!`)(?<ticks>`++)(?<code>.+?)\\k<ticks>(?!`)"
                    + "|(?<image>!)?\\[(?<label>[^\\[\\]]++)\\]\\((?<url>" + DESTINATION + ")\\)"
                    + "|<(?<autolink>(?i:https?)://[^\\s<>]++)>"
                    + "|(?<html></?[A-Za-z][^<>]*+>)"
                    + emphasis("***") + emphasis("___") + emphasis("**") + emphasis("__")
                    + emphasis("*") + emphasis("_") + emphasis("~~"));

    MarkdownApi() {}

    /**
     * Parses common Markdown block and inline syntax. Physical newlines are preserved.
     * Only absolute HTTP(S) links receive native click events. Images display their alt text.
     * @throws IllegalArgumentException if the source exceeds 65,536 UTF-16 code units
     */
    public MutableComponent parse(String source) {
        Objects.requireNonNull(source, "source");
        if (source.length() > MAX_LENGTH) throw new IllegalArgumentException("Markdown input is too large");
        var result = Component.empty();
        String fence = null;
        boolean lineWritten = false;
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            var marker = FENCE.matcher(line);
            if (marker.matches()) {
                if (fence == null) {
                    fence = marker.group(1);
                    continue;
                }
                if (marker.group(1).startsWith(fence) && marker.group(2).isBlank()) {
                    fence = null;
                    continue;
                }
            }
            if (lineWritten) result.append("\n");
            lineWritten = true;
            if (fence == null) appendLine(result, line);
            else append(result, line, CODE);
        }
        return result;
    }

    private static void appendLine(MutableComponent result, String line) {
        var quote = QUOTE.matcher(line);
        if (quote.lookingAt()) {
            result.append("│ ");
            line = line.substring(quote.end());
        }
        var heading = HEADING.matcher(line);
        if (heading.lookingAt()) {
            String title = HEADING_END.matcher(line.substring(heading.end())).replaceFirst("");
            appendInline(result, title, Style.EMPTY.withBold(true), 0, true);
            return;
        }
        var list = LIST.matcher(line);
        if (list.lookingAt()) {
            result.append(list.group(1) + (list.group(2) == null ? "• " : list.group(2) + ". "));
            line = line.substring(list.end());
        }
        appendInline(result, line, Style.EMPTY, 0, true);
    }

    private static void appendInline(MutableComponent result, String text, Style style, int depth, boolean links) {
        if (depth == MAX_NESTING) {
            append(result, text, style);
            return;
        }
        var token = INLINE.matcher(text);
        int cursor = 0;
        while (token.find()) {
            append(result, text.substring(cursor, token.start()), style);
            if (token.group("escape") != null) {
                append(result, token.group().substring(1), style);
            } else if (token.group("code") != null) {
                append(result, token.group("code"), style.applyTo(CODE));
            } else if (token.group("label") != null) {
                var labelStyle = links && token.group("image") == null ? linkStyle(style, token.group("url")) : style;
                appendInline(result, token.group("label"), labelStyle, depth + 1, false);
            } else if (token.group("autolink") != null) {
                String address = token.group("autolink");
                append(result, address, links ? linkStyle(style, address) : style);
            } else if (token.group("html") != null) {
                append(result, token.group(), style);
            } else {
                String mark = token.group();
                int length = mark.startsWith("***") || mark.startsWith("___") ? 3
                        : mark.startsWith("**") || mark.startsWith("__") || mark.startsWith("~~") ? 2 : 1;
                var emphasis = switch (mark.charAt(0)) {
                    case '~' -> style.withStrikethrough(true);
                    default -> switch (length) {
                        case 3 -> style.withBold(true).withItalic(true);
                        case 2 -> style.withBold(true);
                        default -> style.withItalic(true);
                    };
                };
                appendInline(result, mark.substring(length, mark.length() - length), emphasis, depth + 1, links);
            }
            cursor = token.end();
        }
        append(result, text.substring(cursor), style);
    }

    private static Style linkStyle(Style style, String address) {
        var uri = LinkApi.webUri(ESCAPE.matcher(address).replaceAll("$1"));
        if (uri == null) return style;
        return style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toString()));
    }

    private static String emphasis(String marker) {
        String character = Pattern.quote(marker.substring(0, 1));
        String boundary = marker.startsWith("_") ? "[\\p{L}\\p{N}_]" : character;
        String delimiter = Pattern.quote(marker);
        return "|(?<!" + boundary + ")" + delimiter + "(?=\\S)"
                + "(?:\\\\" + PUNCTUATION + "|[^" + character + "\\\\]++|\\\\(?!" + PUNCTUATION + "))++"
                + "(?<=\\S)" + delimiter + "(?!" + boundary + ")";
    }

    private static void append(MutableComponent result, String text, Style style) {
        if (!text.isEmpty()) result.append(Component.literal(text).setStyle(style));
    }
}
