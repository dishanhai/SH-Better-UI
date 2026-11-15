// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.kernel.internal.NativePageOptions;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

final class GlideSelectPreview implements PreviewScene {
    static final String TITLE = "sh_better_ui.panel.developer.gallery.sample.glide_select";
    static final int PREFERRED_HEIGHT = 104;
    private static final String KEY = "sh_better_ui.panel.developer.gallery.glide_select.";
    private static final List<Component> FORMATS = List.<Component>of(
            Component.translatable(KEY + "png"), Component.translatable(KEY + "jpg"),
            Component.translatable(KEY + "webp"));
    private static final List<Component> TAGS = List.<Component>of(
            Component.translatable(KEY + "lossless"), Component.translatable(KEY + "small"),
            Component.translatable(KEY + "modern"));

    private UiLocalModal modal;
    private int selected;

    GlideSelectPreview() {}

    private GlideSelectPreview(int selected) {
        this.selected = selected;
    }

    @Override
    public void layout(PreviewContext host) {
        suspend();
        Bounds viewport = host.viewport();
        int width = Math.min(176, viewport.width());
        if (width < 40 || viewport.height() < 64) return;
        var screen = host.screen();
        var popup = host.context().interactions().localModal(screen, host.preferences());
        modal = popup;
        var trigger = new UiButton(viewport.x() + (viewport.width() - width) / 2, viewport.y() + 4,
                width, 22, FORMATS.get(selected), pressed -> {
                    if (!host.interactive()) return;
                    NativePageOptions.openGlide(screen, viewport, popup, (UiButton) pressed, FORMATS, TAGS,
                            () -> selected, index -> {
                                selected = index;
                                pressed.setMessage(FORMATS.get(index));
                            });
                }, host.preferences(), false, UiStyle.DEFAULT);
        trigger.setSampleBackground(false);
        trigger.setLeadingIcon(GraphicIcon.EXPAND);
        host.add(trigger);
    }

    @Override
    public void suspend() {
        if (modal != null) {
            modal.interrupt();
            modal = null;
        }
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        int value = selected;
        return () -> new GlideSelectPreview(value);
    }
}
