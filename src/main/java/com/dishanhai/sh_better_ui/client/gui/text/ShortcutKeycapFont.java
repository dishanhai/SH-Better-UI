// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

final class ShortcutKeycapFont {
    static final Style STYLE = Style.EMPTY.withFont(
            ResourceLocation.fromNamespaceAndPath("sh_better_ui", "hud_shortcut_keycaps"));

    private ShortcutKeycapFont() {}
}
