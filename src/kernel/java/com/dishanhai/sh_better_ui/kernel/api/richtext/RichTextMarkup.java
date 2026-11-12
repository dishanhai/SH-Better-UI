// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strict, passive markup. Style tags b, i, u, s, color and font can nest. Use [br], [p],
 * [img=namespace:path;width;height;alt], [tr=key;fallback;arg], and [[ for a literal opening bracket.
 */
public final class RichTextMarkup {
    private static final Pattern RGB = Pattern.compile("#[0-9a-fA-F]{6}");

    private RichTextMarkup() {}

    /** Escape arbitrary text before inserting it between markup tags. */
    public static String escape(String text) {
        return Objects.requireNonNull(text, "text").replace("[", "[[");
    }

    public static RichDocument parse(UiId prefix, String source) {
        return parseInternal(prefix, source, null);
    }

    /** Opt into bounded, versioned [ext=type;version;width;height;logicalHeight;alt;payload] tags. */
    public static RichDocument parse(UiId prefix, String source, RichExtensionInput extensions) {
        return parseInternal(prefix, source, Objects.requireNonNull(extensions, "extensions"));
    }

    private static RichDocument parseInternal(UiId prefix, String source, RichExtensionInput extensions) {
        RichTextInput.utf8Bytes(source, RichTextInput.MAX_UTF8_BYTES);
        return new Parser(RichText.document(prefix), extensions).parse(source);
    }

    private record Scope(String tag, RichStyle previous) {}

    private static final class Parser {
        private final RichText.Builder document;
        private final RichExtensionInput extensions;
        private final StringBuilder text = new StringBuilder();
        private final ArrayDeque<Scope> scopes = new ArrayDeque<>();
        private RichStyle style = RichStyle.EMPTY;
        private int nodes;
        private int extensionBytes;

        private Parser(RichText.Builder document, RichExtensionInput extensions) {
            this.document = document;
            this.extensions = extensions;
        }

        private RichDocument parse(String source) {
            for (int index = 0; index < source.length();) {
                char next = source.charAt(index++);
                if (next != '[') {
                    text.append(next);
                } else if (index < source.length() && source.charAt(index) == '[') {
                    text.append('[');
                    index++;
                } else {
                    int end = source.indexOf(']', index);
                    if (end < 0) throw invalid("Unterminated markup tag", index - 1);
                    tag(source.substring(index, end), index - 1);
                    index = end + 1;
                }
            }
            if (!scopes.isEmpty()) throw invalid("Unclosed " + scopes.peek().tag() + " tag", source.length());
            flush();
            return document.build();
        }

        private void tag(String token, int offset) {
            if (token.equals("br")) {
                text.append('\n');
                return;
            }
            flush();
            if (token.startsWith("/")) {
                if (scopes.isEmpty() || !scopes.peek().tag().equals(token.substring(1))) {
                    throw invalid("Unexpected markup closing tag", offset);
                }
                style = scopes.pop().previous();
                return;
            }
            if (token.equals("p")) {
                countNode();
                document.paragraph(4);
                return;
            }
            int separator = token.indexOf('=');
            String name = separator < 0 ? token : token.substring(0, separator);
            String value = separator < 0 ? null : token.substring(separator + 1);
            if (name.equals("img") && value != null) {
                image(value, offset);
                return;
            }
            if (name.equals("tr") && value != null) {
                localized(value, offset);
                return;
            }
            if (name.equals("ext") && value != null && extensions != null) {
                extension(value, offset);
                return;
            }
            if (scopes.size() >= RichTextInput.MAX_NESTING) throw invalid("Markup nesting limit exceeded", offset);
            RichStyle updated = styled(name, value, offset);
            scopes.push(new Scope(name, style));
            style = updated;
        }

        private RichStyle styled(String name, String value, int offset) {
            boolean bold = style.bold(), italic = style.italic();
            boolean underline = style.underlined(), strike = style.strikethrough();
            Integer color = style.color();
            UiId font = style.font();
            switch (name) {
                case "b" -> { requireFlag(value, offset); bold = true; }
                case "i" -> { requireFlag(value, offset); italic = true; }
                case "u" -> { requireFlag(value, offset); underline = true; }
                case "s" -> { requireFlag(value, offset); strike = true; }
                case "color" -> {
                    if (value == null || !RGB.matcher(value).matches()) throw invalid("Expected #RRGGBB", offset);
                    color = Integer.parseInt(value.substring(1), 16);
                }
                case "font" -> {
                    if (value == null) throw invalid("Expected a namespaced font", offset);
                    font = RichTextInput.resource(value);
                }
                default -> throw invalid("Unknown markup tag", offset);
            }
            return new RichStyle(color, bold, italic, underline, strike, style.obfuscated(), font);
        }

        private void image(String value, int offset) {
            String[] parts = value.split(";", 4);
            if (parts.length != 4) throw invalid("Expected image ID, width, height and alt text", offset);
            int width = dimension(parts[1], offset);
            int height = dimension(parts[2], offset);
            countNode();
            document.image(RichTextInput.image(parts[0], width, height), width, height, parts[3]);
        }

        private void localized(String value, int offset) {
            String[] parts = value.split(";", RichTextInput.MAX_ARGUMENTS + 3);
            if (parts.length < 2 || parts.length > RichTextInput.MAX_ARGUMENTS + 2) {
                throw invalid("Expected translation key, fallback and up to 32 literal arguments", offset);
            }
            countNode();
            document.localized(parts[0], parts[1], Arrays.asList(parts).subList(2, parts.length), style);
        }

        private void extension(String value, int offset) {
            String[] parts = value.split(";", 7);
            if (parts.length != 7 || parts[1].length() > 5 || parts[1].isEmpty()
                    || !parts[1].chars().allMatch(c -> c >= '0' && c <= '9')) {
                throw invalid("Expected extension type, version, dimensions, alt text and payload", offset);
            }
            countNode();
            var envelope = new RichExtensionNode(document.nextId(), UiId.parse(parts[0]), Integer.parseInt(parts[1]),
                    dimension(parts[2], offset), dimension(parts[3], offset), dimension(parts[4], offset),
                    parts[5], parts[6], false);
            var node = extensions.read(envelope, true);
            extensionBytes += RichTextInput.utf8Bytes(node.payload(), RichTextInput.MAX_UTF8_BYTES - extensionBytes);
            document.add(node);
        }

        private void flush() {
            if (text.isEmpty()) return;
            countNode();
            document.text(text.toString(), style);
            text.setLength(0);
        }

        private void countNode() {
            if (++nodes > RichTextInput.MAX_NODES) throw new IllegalArgumentException("Rich text has too many nodes");
        }
    }

    private static int dimension(String value, int offset) {
        if (value.isEmpty() || value.length() > 4 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw invalid("Expected a positive inline dimension", offset);
        }
        return Integer.parseInt(value);
    }

    private static void requireFlag(String value, int offset) {
        if (value != null) throw invalid("Unexpected markup tag argument", offset);
    }

    private static IllegalArgumentException invalid(String message, int offset) {
        return new IllegalArgumentException(message + " at character " + offset);
    }
}
