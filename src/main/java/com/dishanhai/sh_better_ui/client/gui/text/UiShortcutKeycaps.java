// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Inline bitmap keycaps compatible with Minecraft text measurement and UUK text rendering. */
public final class UiShortcutKeycaps {
    private static final int FONT_START = 0xE000;
    private static final int UNKNOWN = 0;
    private static final int UNBOUND = 1;
    private static final int MOUSE_START = 368;
    private static final int GENERIC_MOUSE = 376;

    public enum Modifier {
        ALT(360), CONTROL(361), SHIFT(362);

        private final int icon;

        Modifier(int icon) {
            this.icon = icon;
        }
    }

    private UiShortcutKeycaps() {}

    public static Component binding(KeyMapping mapping) {
        if (mapping.isUnbound()) return unbound();
        var key = mapping.getKey();
        var modifier = mapping.getKeyModifier();
        Component cap = switch (key.getType()) {
            case KEYSYM -> key(key.getValue());
            case MOUSE -> mouse(key.getValue());
            case SCANCODE -> glyph(UNKNOWN);
        };
        if (modifier.matches(key)) return cap;
        return switch (modifier) {
            case ALT -> chord(Modifier.ALT, cap);
            case CONTROL -> chord(Modifier.CONTROL, cap);
            case SHIFT -> chord(Modifier.SHIFT, cap);
            default -> cap;
        };
    }

    /** Returns the keycap for a GLFW keyboard code. */
    public static Component key(int code) {
        return glyph(hasKeycap(code) ? code : UNKNOWN);
    }

    /** Returns the mouse icon for a zero-based GLFW button code. */
    public static Component mouse(int button) {
        return glyph(button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST
                ? MOUSE_START + button : GENERIC_MOUSE);
    }

    public static Component unbound() {
        return glyph(UNBOUND);
    }

    public static Component chord(Modifier modifier, Component keycap) {
        return Component.empty().append(glyph(modifier.icon)).append(" ").append(keycap);
    }

    private static boolean hasKeycap(int code) {
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_COMMA,
                    GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_SEMICOLON,
                    GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_BACKSLASH,
                    GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_GRAVE_ACCENT,
                    GLFW.GLFW_KEY_WORLD_1, GLFW.GLFW_KEY_WORLD_2 -> true;
            default -> (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9)
                    || (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z)
                    || (code >= GLFW.GLFW_KEY_ESCAPE && code <= GLFW.GLFW_KEY_END)
                    || (code >= GLFW.GLFW_KEY_CAPS_LOCK && code <= GLFW.GLFW_KEY_PAUSE)
                    || (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25)
                    || (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_EQUAL)
                    || (code >= GLFW.GLFW_KEY_LEFT_SHIFT && code <= GLFW.GLFW_KEY_MENU);
        };
    }

    private static Component glyph(int icon) {
        return Component.literal(Character.toString(FONT_START + icon)).withStyle(ShortcutKeycapFont.STYLE);
    }
}
