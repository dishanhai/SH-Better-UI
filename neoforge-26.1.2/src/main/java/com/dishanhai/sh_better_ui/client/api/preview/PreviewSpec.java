// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api.preview;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Supplier;

/** A lazily mounted developer gallery scene. */
public record PreviewSpec(String id, Category category, int order, String titleKey, int preferredHeight,
                          int toolbarRightInset, String code, Supplier<? extends PreviewScene> factory) {
    public enum Category {
        CONTROLS("sh_better_ui.panel.developer.gallery.controls"),
        MATERIALS("sh_better_ui.panel.developer.gallery.materials");

        private final String titleKey;
        Category(String titleKey) { this.titleKey = titleKey; }
        public String titleKey() { return titleKey; }
    }

    public PreviewSpec {
        Objects.requireNonNull(id);
        if (id.indexOf(':') <= 0 || id.endsWith(":") || Identifier.tryParse(id) == null) {
            throw new IllegalArgumentException("Expected a namespaced developer preview ID: " + id);
        }
        Objects.requireNonNull(category);
        Objects.requireNonNull(titleKey);
        if (titleKey.isBlank() || preferredHeight <= 0 || toolbarRightInset < 0) {
            throw new IllegalArgumentException("Invalid developer preview layout: " + id);
        }
        Objects.requireNonNull(code);
        Objects.requireNonNull(factory);
    }

    public PreviewSpec(String id, Category category, int order, String titleKey,
                       Supplier<? extends PreviewScene> factory) {
        this(id, category, order, titleKey, 46, 0, "", factory);
    }
}
