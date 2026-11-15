// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.text.UiShortcutKeycaps;
import com.dishanhai.sh_better_ui.client.gui.text.UiShortcutKeycaps.Modifier;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

final class ShortcutKeycapsDemo {
    static final String ID = "hud_shortcut_keycaps";
    static final int ORDER = 35;
    static final int HEIGHT = 88;
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.gui.text.UiShortcutKeycaps;
            import net.minecraft.client.KeyMapping;
            import net.minecraft.network.chat.Component;

            private Component shortcutHint(KeyMapping mapping, Component action) {
                return Component.empty().append(UiShortcutKeycaps.binding(mapping)).append(" ").append(action);
            }
            """;

    private ShortcutKeycapsDemo() {}

    static List<Component> rows() {
        return List.of(
                row("function_keys", UiShortcutKeycaps.key(GLFW.GLFW_KEY_F8), UiShortcutKeycaps.key(GLFW.GLFW_KEY_F10)),
                row("page_keys", UiShortcutKeycaps.chord(Modifier.ALT, UiShortcutKeycaps.key(GLFW.GLFW_KEY_LEFT)),
                        UiShortcutKeycaps.chord(Modifier.ALT, UiShortcutKeycaps.key(GLFW.GLFW_KEY_RIGHT))),
                row("mouse", UiShortcutKeycaps.mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT),
                        UiShortcutKeycaps.mouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
                        UiShortcutKeycaps.mouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)),
                row("unbound", UiShortcutKeycaps.unbound()));
    }

    private static Component row(String key, Component... icons) {
        var text = Component.empty();
        for (var icon : icons) text.append(icon).append(" ");
        return text.append(Component.translatable("sh_better_ui.panel.developer.gallery." + ID + "." + key));
    }
}
