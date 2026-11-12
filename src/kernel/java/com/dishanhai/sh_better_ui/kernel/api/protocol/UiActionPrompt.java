// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import java.util.Objects;

/** Authority-supplied preview of the proposed operation. */
public record UiActionPrompt(String subject, long quantity, String consequence) {
    public UiActionPrompt {
        validate(subject, 128);
        validate(consequence, 256);
        if (quantity < 1) throw new IllegalArgumentException("Action preview quantity must be positive");
    }

    private static void validate(String text, int limit) {
        Objects.requireNonNull(text, "preview text");
        if (text.isBlank() || text.length() > limit || text.codePoints().anyMatch(character ->
                Character.isISOControl(character) || character == 0xA7
                        || Character.getType(character) == Character.SURROGATE
                        || Character.getType(character) == Character.FORMAT
                        || Character.getType(character) == Character.LINE_SEPARATOR
                        || Character.getType(character) == Character.PARAGRAPH_SEPARATOR)) {
            throw new IllegalArgumentException("Invalid action preview text");
        }
    }
}
