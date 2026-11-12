// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

/** Shared validation for bounded passive interchange formats. */
final class RichTextInput {
    static final int MAX_UTF8_BYTES = 256 * 1024;
    static final int MAX_NODES = 4096;
    static final int MAX_NESTING = 16;
    static final int MAX_ARGUMENTS = 32;
    private static final int MAX_IMAGE_SIDE = 4096;
    private static final int MAX_IMAGE_PIXELS = 4 * 1024 * 1024;

    private RichTextInput() {}

    static int utf8Bytes(String text, int max) {
        if (text == null || text.length() > max) throw new IllegalArgumentException("Rich text input is too large");
        int bytes = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) {
                    throw new IllegalArgumentException("Unpaired UTF-16 surrogate");
                }
                bytes += 4;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Unpaired UTF-16 surrogate");
            } else {
                bytes += current < 0x80 ? 1 : current < 0x800 ? 2 : 3;
            }
            if (bytes > max) throw new IllegalArgumentException("Rich text input is too large");
        }
        return bytes;
    }

    static UiId resource(String value) {
        var id = UiId.parse(value);
        if (id.namespace().equals(".") || id.namespace().equals("..")) {
            throw new IllegalArgumentException("Invalid local resource namespace");
        }
        for (var part : id.path().split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid local resource path");
            }
        }
        return id;
    }

    static UiId image(String value, int width, int height) {
        var id = resource(value);
        String path = id.path();
        boolean supported = path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".apng");
        if (!supported || width <= 0 || width > MAX_IMAGE_SIDE
                || height <= 0 || height > MAX_IMAGE_SIDE || (long) width * height > MAX_IMAGE_PIXELS) {
            throw new IllegalArgumentException("Expected a local PNG, GIF or APNG with bounded positive dimensions");
        }
        return id;
    }
}
