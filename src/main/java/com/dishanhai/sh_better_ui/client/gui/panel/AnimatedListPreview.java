// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAnimatedList;
import com.dishanhai.sh_better_ui.kernel.api.controls.AnimatedListState;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** Live, focusable port of the supplied file-list sample. */
final class AnimatedListPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.animated_list.";
    private UiAnimatedList<String> widget;
    private int selected = -1;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:animated_list", PreviewSpec.Category.CONTROLS, 34,
                KEY + "title", 240, 0, """
                var list = ShBetterUiApi.controls().animatedList(font, new Bounds(x, y, 300, 240),
                    Component.translatable("sh_better_ui.animated_list.title"), items, -1,
                    AnimatedListState.Options.DEFAULT, UiAnimatedList.Style.DEFAULT,
                    ShBetterUiApi.settings().preferences(), (value, index) -> onFileSelected(value));
                addRenderableWidget(list);
                // Call list.cancelInteraction() when removing it from the screen.
                """, AnimatedListPreview::new));
    }

    @Override public void layout(PreviewContext host) {
        suspend();
        var v = host.viewport();
        if (v.width() < 48 || v.height() < 48) return;
        int w = Math.min(420, v.width() - 8), h = Math.min(440, v.height() - 8);
        var area = new Bounds(v.x() + (v.width() - w) / 2, v.y() + (v.height() - h) / 2, w, h);
        var entries = List.of(item("assets", GraphicIcon.FOLDER), item("hero", GraphicIcon.FILE),
                item("tour", GraphicIcon.RUN), item("pricing", GraphicIcon.DOCUMENT), item("landing", GraphicIcon.CODE),
                item("onboarding", GraphicIcon.PLAN), item("ambient", GraphicIcon.MUSIC), item("forecast", GraphicIcon.MONITOR),
                item("guidelines", GraphicIcon.GUIDE), item("investor", GraphicIcon.DOCUMENT), item("icons", GraphicIcon.ARCHIVE),
                item("moodboard", GraphicIcon.FILE), item("release", GraphicIcon.FILE), item("archive", GraphicIcon.FOLDER),
                item("teaser", GraphicIcon.RUN));
        widget = ShBetterUiApi.controls().animatedList(host.font(), area, Component.translatable(KEY + "title"),
                entries, selected, AnimatedListState.Options.DEFAULT, UiAnimatedList.Style.DEFAULT, host.preferences(),
                (value, index) -> selected = index);
        widget.setInputEnabled(host::interactive);
        host.add(widget);
    }

    private static UiAnimatedList.Item<String> item(String key, GraphicIcon icon) {
        String prefix = KEY + "sample." + key;
        return new UiAnimatedList.Item<>(key, key, Component.translatable(prefix), Component.translatable(prefix + ".description"),
                Component.translatable(prefix + ".meta"), icon);
    }

    @Override public void suspend() {
        if (widget != null) { selected = widget.selectedIndex(); widget.cancelInteraction(); }
        widget = null;
    }

    @Override public Supplier<? extends PreviewScene> saveForRemount() {
        int saved = widget == null ? selected : widget.selectedIndex();
        return () -> { var scene = new AnimatedListPreview(); scene.selected = saved; return scene; };
    }
}
