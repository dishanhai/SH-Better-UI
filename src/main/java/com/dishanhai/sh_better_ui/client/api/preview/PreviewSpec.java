package com.dishanhai.sh_better_ui.client.api.preview;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 山海：开发者图库的一条声明，只有打开该示例时才创建交互场景。
 *
 * @param preferredHeight 演示所需的最小内嵌高度，单位为 GUI 逻辑像素。共享预览区高度不足时，图库提供全屏入口。
 */
public record PreviewSpec(String id, Category category, int order, String titleKey, int preferredHeight,
                          int toolbarRightInset, String code, Supplier<? extends PreviewScene> factory) {
    public enum Category {
        CONTROLS("sh_better_ui.panel.developer.gallery.controls"),
        MATERIALS("sh_better_ui.panel.developer.gallery.materials"),
        BACKGROUNDS("sh_better_ui.panel.developer.gallery.backgrounds"),
        THEMES("sh_better_ui.panel.developer.gallery.themes");

        private final String titleKey;

        Category(String titleKey) {
            this.titleKey = titleKey;
        }

        public String titleKey() {
            return titleKey;
        }
    }

    public PreviewSpec {
        Objects.requireNonNull(id);
        if (id.indexOf(':') <= 0 || id.endsWith(":") || ResourceLocation.tryParse(id) == null) {
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
