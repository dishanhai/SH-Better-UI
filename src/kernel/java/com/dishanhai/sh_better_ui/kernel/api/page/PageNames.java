// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;
import java.util.regex.Pattern;

final class PageNames {
    static final int MAX_NAME_LENGTH = 128;
    static final int MAX_TEXT_LENGTH = 4096;
    static final int MAX_COLLECTION_SIZE = 512;
    static final int MAX_VALUE_DEPTH = 12;
    static final int MAX_VALUE_NODES = 8192;

    private static final Pattern NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_-]*(\\.[a-zA-Z_][a-zA-Z0-9_-]*)*");

    private PageNames() {}

    static String name(String value) {
        Objects.requireNonNull(value, "name");
        if (value.length() > MAX_NAME_LENGTH || !NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid page name or data path");
        }
        return value;
    }

    static UiId id(UiId value) {
        Objects.requireNonNull(value, "id");
        if (value.namespace().length() + value.path().length() + 1 > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Page resource ID exceeds the length limit");
        }
        return value;
    }

    static String text(String value, int maxSize) {
        Objects.requireNonNull(value, "text");
        if (value.length() > maxSize) {
            throw new IllegalArgumentException("Page text exceeds the length limit");
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException("Page text contains an invalid Unicode sequence");
                }
            } else if (Character.isLowSurrogate(current) || (Character.isISOControl(current)
                    && current != '\n' && current != '\t')) {
                throw new IllegalArgumentException("Page text contains an invalid character");
            }
        }
        return value;
    }

    static void size(int size, int maxSize) {
        if (size > maxSize) {
            throw new IllegalArgumentException("Page collection exceeds the entry limit");
        }
    }
}
