// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.FuseButtonStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.FuseButtonStyle.Fuse;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFuseButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Illustrates the three countdown placements with local callbacks. */
final class FuseButtonDemo {
    static final int HEIGHT = 102;
    static final String EXAMPLE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.FuseButtonStyle;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiFuseButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState;
            import net.minecraft.network.chat.Component;

            private UiFuseButton archive;
            private boolean archived;

            private void addArchive(int x, int y) {
                archive = ShBetterUiApi.controls().fuseButton(
                        new Bounds(x, y, 152, FuseButtonStyle.Size.MD.height()),
                        Component.translatable("sh_better_ui.fuse_button.action"),
                        Component.translatable("sh_better_ui.fuse_button.undo"),
                        Component.translatable("sh_better_ui.fuse_button.done"),
                        UiPreferences.configured(), FuseButtonStyle.DEFAULT, FuseButtonState.Options.DEFAULT,
                        new FuseButtonState.Callbacks(reason -> archived = true, () -> archived = false));
                addRenderableWidget(archive);
            }

            @Override
            public void tick() {
                super.tick();
                if (archive != null) archive.tick();
            }

            @Override
            public void removed() {
                if (archive != null) archive.undo();
                super.removed();
            }

            // Route Escape to the focused button before the screen handles Back.
            """;
    private static final int ROW = 26;
    private final UiFuseButton[] buttons = new UiFuseButton[3];
    private Component status = label("instructions");

    void layout(Bounds area, UiPreferences preferences, Consumer<UiFuseButton> add) {
        int width = Math.min(168, area.width() - 8);
        if (width < 60 || area.height() < HEIGHT) return;
        int x = area.x() + (area.width() - width) / 2;
        int y = top(area);
        for (int i = 0; i < buttons.length; i++) {
            var options = i == 2
                    ? new FuseButtonState.Options(4_000, FuseButtonState.CommitOn.FUSE_END, true, FuseButtonState.Settle.STAY)
                    : FuseButtonState.Options.DEFAULT;
            if (buttons[i] == null) {
                var fuse = Fuse.values()[i];
                var name = label(switch (fuse) {
                    case OUTLINE -> "outline";
                    case BOTTOM -> "bottom";
                    case TOP -> "top";
                });
                var callbacks = new FuseButtonState.Callbacks(reason -> status = label("done"),
                        () -> status = label("undone"), () -> status = label("expired"), phase -> {
                            if (phase == FuseButtonState.Phase.ARMED && options.commitOn() == FuseButtonState.CommitOn.FUSE_END) {
                                status = label("pending");
                            }
                        });
                buttons[i] = ShBetterUiApi.controls().fuseButton(new Bounds(x, y, width, 22), name, label("undo"),
                        label("done"), preferences, FuseButtonStyle.DEFAULT.withFuse(fuse), options, callbacks);
            } else {
                buttons[i].setX(x);
                buttons[i].setY(y);
                buttons[i].setWidth(width);
                buttons[i].setPreferences(preferences);
            }
            add.accept(buttons[i]);
            y += ROW;
        }
    }

    void tick() {
        for (var button : buttons) if (button != null) button.tick();
    }

    void cancelPress() {
        for (var button : buttons) if (button != null) button.cancelPress();
    }

    void close() {
        for (var button : buttons) {
            if (button != null) {
                button.cancelPress();
                button.undo();
            }
        }
    }

    int statusY(Bounds area) { return top(area) + ROW * buttons.length + 2; }

    Component status() { return status; }

    private static int top(Bounds area) { return area.y() + (area.height() - HEIGHT) / 2; }

    private static Component label(String key) {
        return Component.translatable("sh_better_ui.fuse_button." + key);
    }
}
