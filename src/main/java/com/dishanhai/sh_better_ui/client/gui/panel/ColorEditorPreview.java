// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiColorEditor;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/** Owned demo state; interaction never modifies the user's actual theme. */
final class ColorEditorPreview implements PreviewScene {
    private static final int DEFAULT = 0xcc64a8ff;
    private int committed = DEFAULT;
    private UiColorEditor editor;
    @Override public void layout(PreviewContext host) {
        suspend();
        var area = host.viewport();
        if (area.width() < UiColorEditor.MIN_WIDTH || area.height() < UiColorEditor.MIN_HEIGHT) return;
        int width = Math.min(300, area.width());
        editor = host.add(ShBetterUiApi.controls().colorEditor(
                new Bounds(area.x() + (area.width() - width) / 2, area.y(), width, UiColorEditor.MIN_HEIGHT),
                Component.translatable("sh_better_ui.color_editor.demo"), committed, DEFAULT,
                host.preferences(), ignored -> {}, color -> { if (host.interactive()) committed = color; }));
        editor.active = host.interactive();
    }
    @Override public void suspend() { if (editor != null) editor.cancelInteraction(); editor = null; }
    @Override public void close() { suspend(); }
    @Override public Supplier<? extends PreviewScene> saveForRemount() {
        int saved = committed;
        return () -> { var scene = new ColorEditorPreview(); scene.committed = saved; return scene; };
    }
}
