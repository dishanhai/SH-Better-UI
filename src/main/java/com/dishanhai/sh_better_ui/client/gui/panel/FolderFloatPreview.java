// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.FolderFloatControl;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFolderFloat;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

final class FolderFloatPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.folder_float.";
    private static final int HEIGHT = 128;
    private static final String EXAMPLE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.FolderFloatControl;
            import net.minecraft.network.chat.CommonComponents;
            import net.minecraft.network.chat.Component;
            import java.util.List;

            private void addFolderFloat(int x, int y) {
                var note = Component.translatable("sh_better_ui.panel.developer.gallery.folder_float.note_palette");
                var folder = ShBetterUiApi.controls().folderFloat(font, new Bounds(x, y, 260, 128),
                        Component.translatable("sh_better_ui.panel.developer.gallery.folder_float.label"),
                        CommonComponents.EMPTY, List.of(new FolderFloatControl.Item<>(note, "palette")),
                        FolderFloatControl.Options.DEFAULT, ShBetterUiApi.settings().preferences(),
                        (value, index) -> { /* Handle selection. */ }, open -> {});
                addRenderableWidget(folder);
            }
            """;

    private UiFolderFloat<Component> widget;
    private Component subtitle = CommonComponents.EMPTY;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:folder_float", PreviewSpec.Category.CONTROLS, 33,
                "sh_better_ui.panel.developer.gallery.sample.folder_float", HEIGHT, 0, EXAMPLE, FolderFloatPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        suspend();
        var view = host.viewport();
        if (view.width() < FolderFloatControl.MIN_WIDTH || view.height() < FolderFloatControl.MIN_HEIGHT) {
            if (view.width() >= 40 && view.height() >= 22) {
                var notice = ShBetterUiApi.controls().button(new Bounds(view.x(), view.y(), view.width(), 22),
                        Component.translatable("sh_better_ui.panel.compact"), ignored -> {},
                        host.preferences(), false, UiStyle.DEFAULT);
                notice.active = false;
                host.add(notice);
            }
            return;
        }
        int width = Math.min(view.width(), 300), height = Math.min(view.height(), HEIGHT);
        var area = new Bounds(view.x() + (view.width() - width) / 2, view.y() + (view.height() - height) / 2,
                width, height);
        var items = List.of(note("palette"), note("spacing"), note("logo"), note("hero"));
        widget = ShBetterUiApi.controls().folderFloat(host.font(), area, Component.translatable(KEY + "label"),
                subtitle, items, FolderFloatControl.Options.DEFAULT, host.preferences(), (selected, index) -> {
                    subtitle = Component.translatable(KEY + "selected", selected);
                    if (widget != null) widget.setSublabel(subtitle);
                }, open -> {});
        widget.setInputEnabled(host::interactive);
        host.add(widget);
    }

    private static FolderFloatControl.Item<Component> note(String key) {
        Component text = Component.translatable(KEY + "note_" + key);
        return new FolderFloatControl.Item<>(text, text);
    }

    @Override
    public void suspend() {
        if (widget != null) widget.cancelInteraction();
        widget = null;
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        Component saved = subtitle;
        return () -> {
            var preview = new FolderFloatPreview();
            preview.subtitle = saved;
            return preview;
        };
    }
}
