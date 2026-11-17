// Author: dishanhai. Uses the bundled OFL-1.1 SH Sans UI static derivative of Noto Sans SC.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Scoped UI typography: never replaces Minecraft's global font or specialized child icon fonts. */
public final class MpTypography {
    public static final ResourceLocation FONT = ResourceLocation.tryParse("sh_better_ui:mp_ui");
    public static final ResourceLocation SEMIBOLD = ResourceLocation.tryParse("sh_better_ui:mp_ui_semibold");
    public static final int LINE_HEIGHT = 12;
    private MpTypography() {}
    public static MutableComponent text(Component source) {
        return source.copy().withStyle(style -> style.withFont(SEMIBOLD.equals(style.getFont()) ? SEMIBOLD : FONT));
    }
    public static MutableComponent bold(Component source) {
        return source.copy().withStyle(style -> style.withFont(SEMIBOLD));
    }
    public static MutableComponent bold(String source) { return bold(Component.literal(source)); }
    public static MutableComponent text(String source) { return text(Component.literal(source)); }
    public static int width(Font font, Component source) { return font.width(text(source)); }
    public static void draw(GuiGraphics graphics, Font font, Component source, int x, int y, int color) {
        graphics.drawString(font, text(source), x, y, color, false);
    }
}
