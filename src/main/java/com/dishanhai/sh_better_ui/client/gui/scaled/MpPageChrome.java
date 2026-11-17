// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared native music/settings chrome. Uses SHBUI materials and existing accessible control motion. */
public final class MpPageChrome {
    private MpPageChrome() {}

    /** A bounded app window, leaving the running world visible around the app. */
    public static void window(GuiGraphics g,int screenWidth,int screenHeight,int x,int y,int w,int h,
                              UiTheme theme,UiPreferences preferences) {
        g.fill(0,0,screenWidth,screenHeight,0x66040910);
        RoundedCorners.draw(g,x-2,y+3,w+4,h+4,13,0,0,0x36000000);
        int fill=preferences.highContrast()?0xff000000:(theme.background()&0xffffff)|0xf2000000;
        int edge=preferences.highContrast()?0xffffffff:(theme.light()?0x18000000:0x18ffffff);
        RoundedCorners.draw(g,x,y,w,h,11,1,edge,fill);
    }
    public static int muted(UiTheme theme,UiPreferences preferences) {
        return preferences.highContrast()?foreground(theme,preferences):GuiMotion.lerpColor(theme.text(),theme.background()|0xff000000,.33f);
    }
    public static void row(GuiGraphics g,int x,int y,int w,int h,UiTheme theme,UiPreferences preferences) {
        int fill=preferences.highContrast()?0xff000000:GuiMotion.lerpColor(theme.background()|0xff000000,theme.surface()|0xff000000,.72f);
        RoundedCorners.draw(g,x,y,w,h,7,preferences.highContrast()?1:0,0xffffffff,fill);
    }

    public static void heading(GuiGraphics g,Font font,Component title,int x,int y,int color) {
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(1.35f,1.35f,1);
        g.drawString(font,MpTypography.text(title),0,0,color,false);g.pose().popPose();
    }

    public static int foreground(UiTheme theme, UiPreferences preferences) {
        return preferences.highContrast() ? 0xFFFFFFFF : theme.text();
    }

    /** Low-performance/no-blur must retain a solid, legible fallback instead of naked translucent text. */
    public static int background(UiTheme theme, UiPreferences preferences) {
        if (preferences.highContrast()) return 0xFF000000;
        if (!theme.liquidGlass() || !preferences.useBackdropBlur()) return theme.background() | 0xFF000000;
        return (theme.background() & 0xFFFFFF) | 0xD8000000;
    }

    public static void backdrop(GuiGraphics g, int width, int height, UiTheme theme, UiPreferences preferences) {
        if (width <= 0 || height <= 0) return;
        g.fill(0, 0, width, height, background(theme, preferences));
        if (!preferences.highContrast()) {
            int wash = (theme.accent() & 0xFFFFFF) | 0x18000000;
            g.fillGradient(0, 0, width, Math.min(height, 160), wash, theme.accent() & 0xFFFFFF);
        }
    }

    public static void header(GuiGraphics g, Font font, Component title, Component subtitle,
                              int width, UiTheme theme, UiPreferences preferences) {
        g.drawCenteredString(font, title, width / 2, 10, foreground(theme, preferences));
        if (subtitle != null && !subtitle.getString().isEmpty())
            g.drawCenteredString(font, font.plainSubstrByWidth(subtitle.getString(), Math.max(0, width - 24)), width / 2, 24,
                    foreground(theme, preferences));
    }

    public static void card(GuiGraphics g, int x, int y, int width, int height,
                            UiTheme theme, UiPreferences preferences) {
        if (width <= 0 || height <= 0) return;
        if (theme.liquidGlass() && preferences.useBackdropBlur()) {
            UiTheme.preview(theme, () -> LiquidGlassTheme.plate(g, x, y, width, height, 12f,
                    theme.border(), theme.elevatedSurface(), preferences));
        } else {
            DarkModeTheme.rounded(g, x, y, width, height, 12f, preferences.highContrast() ? 2 : 1,
                    preferences.highContrast() ? 0xFFFFFFFF : theme.border(),
                    preferences.highContrast() ? 0xFF000000 : theme.surface() | 0xFF000000);
        }
    }

    /** Retains native narration, keyboard focus, press/hover feedback and reduced-motion behavior. */
    public static UiButton button(int x, int y, int width, int height, Component label,
                                  Button.OnPress action, UiPreferences preferences, boolean primary) {
        UiButton button = new UiButton(x, y, width, height, label, action, preferences, primary);
        button.setCornerRadius(8f);
        return button;
    }
}
