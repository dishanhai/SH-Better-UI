// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.kernel.internal.NativeMusicOverlay;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

/** Independent native About page. No MP runtime, branding, or font dependencies. */
public final class NativeAboutScreen extends Screen {
    static final String NOTICE_RESOURCE = "META-INF/licenses/muoniumplayer/NOTICE";
    static final String PROJECT = "https://github.com/dishanhai/SH-Better-UI";
    private final Screen parent;
    private List<Component> paragraphs = List.of();
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int scroll, left, bodyTop, bodyBottom, bodyWidth;
    private MpFlatButton up, down;
    private int windowX, windowY, windowWidth, windowHeight;
    private Component error;
    private boolean compact;

    public NativeAboutScreen(Screen parent) { super(text("title")); this.parent = parent; }
    static Component text(String key, Object... args) { return Component.translatable("sh_better_ui.about." + key, args); }
    @Override protected void init() {
        compact = width < 220 || height < 190;
        if (compact) {
            lines.clear(); up = down = null;
            addRenderableWidget(new MpFlatButton(Math.min(8,width/4), Math.max(0,height-24),
                    Math.max(1,width-2*Math.min(8,width/4)), Math.max(1,Math.min(22,height)),
                    Component.translatable("gui.back"), b -> onClose(), UiPreferences.configured(), false));
            return;
        }
        windowWidth = Math.max(100, Math.min(520, width - 32));
        windowHeight = Math.max(140, Math.min(352, height - 32));
        windowX = (width - windowWidth) / 2; windowY = (height - windowHeight) / 2;
        left = windowX + 18; bodyWidth = Math.max(1, windowWidth - 66);
        bodyTop = windowY + 50; bodyBottom = Math.max(bodyTop + 12, windowY + windowHeight - 76);
        var music = ShBetterUiApi.music().getSnapshot();
        paragraphs = List.of(text("identity"),
                text("version", PlatformUiApi.modInfo("sh_better_ui").map(PlatformUiApi.ModInfo::version).orElse("unknown")),
                text("platform", PlatformUiApi.loaderId(), SharedConstants.getCurrentVersion().getName()),
                text("overlay", text(NativeMusicOverlay.available() ? "available" : "unavailable")),
                text("music", music.providerId(), music.state().name()),
                text("capabilities", music.session() == null ? text("none") : Component.literal(music.session().capabilities().toString())),
                text("snapshot"), text("license"), text("source"), text("scope"), text("navigation"));
        lines.clear();
        for (var paragraph : paragraphs) { lines.addAll(font.split(MpTypography.text(paragraph), bodyWidth)); lines.add(FormattedCharSequence.EMPTY); }
        up = addRenderableWidget(new MpFlatButton(left + bodyWidth + 8, bodyTop, 22, 22, text("up"), b -> move(-3 * MpTypography.LINE_HEIGHT), UiPreferences.configured(), false));
        down = addRenderableWidget(new MpFlatButton(left + bodyWidth + 8, bodyBottom - 22, 22, 22, text("down"), b -> move(3 * MpTypography.LINE_HEIGHT), UiPreferences.configured(), false));
        up.displayText(Component.literal("↑")); down.displayText(Component.literal("↓"));
        up.visible = down.visible = bodyBottom - bodyTop >= 44;
        // Full-width link rows remain reachable by Tab, independent of text scroll.
        int linkWidth = Math.max(1, (windowWidth - 36 - 12) / 3);
        link(left, windowY + windowHeight - 62, linkWidth, "project", PROJECT);
        link(left + linkWidth + 4, windowY + windowHeight - 62, linkWidth, "docs", PROJECT + "/tree/main/docs");
        link(left + 2 * (linkWidth + 4), windowY + windowHeight - 62, linkWidth, "notices", PROJECT + "/blob/main/src/main/resources/" + NOTICE_RESOURCE);
        addRenderableWidget(new MpFlatButton(windowX + windowWidth - 90, windowY + windowHeight - 30, 72, 22,
                Component.translatable("gui.back"), b -> onClose(), UiPreferences.configured(), false));
        move(0);
    }
    private void link(int x, int y, int w, String key, String url) {
        var button = new MpFlatButton(x, y, w, 22, text(key), b -> {
            if (!ShBetterUiApi.links().open(this, url, () -> getFocused())) error = text("link_failed");
        }, UiPreferences.configured(), false);
        addRenderableWidget(button);
    }
    static int clampScroll(int value, int content, int viewport) { return Math.max(0, Math.min(value, Math.max(0, content - viewport))); }
    private void move(int delta) {
        scroll = clampScroll(scroll + delta, lines.size() * MpTypography.LINE_HEIGHT, bodyBottom - bodyTop);
        if (up != null) up.active = scroll > 0;
        if (down != null) down.active = scroll < Math.max(0, lines.size() * MpTypography.LINE_HEIGHT - (bodyBottom - bodyTop));
    }
    public boolean mouseScrolled(double x, double y, double delta) {
        if (compact || x < left || x >= windowX + windowWidth - 12 || y < bodyTop || y >= bodyBottom || delta == 0) return false;
        move(delta > 0 ? -3 * MpTypography.LINE_HEIGHT : 3 * MpTypography.LINE_HEIGHT); return true;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return mouseScrolled(x, y, vertical); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (compact) return super.keyPressed(key, scan, mods);
        switch (key) {
            case 266 -> move(-(bodyBottom - bodyTop));
            case 267 -> move(bodyBottom - bodyTop);
            case 268 -> move(-Integer.MAX_VALUE);
            case 269 -> move(Integer.MAX_VALUE - scroll);
            default -> { return super.keyPressed(key, scan, mods); }
        }
        return true;
    }
    // Screen.render invokes this on NeoForge; never blur already-drawn text.
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        var theme = UiTheme.current();
        var preferences = UiPreferences.configured();
        if (compact) {
            g.fill(0,0,width,height,theme.background()|0xFF000000);
            if (height > 40) g.drawString(font,fit(MpTypography.text(title),width-16),8,8,theme.text(),false);
            super.render(g,x,y,partial); return;
        }
        MpPageChrome.window(g, width, height, windowX, windowY, windowWidth, windowHeight, theme, preferences);
        MpPageChrome.heading(g, font, title, left, windowY + 17, MpPageChrome.foreground(theme, preferences));
        g.enableScissor(left, bodyTop, left + bodyWidth, bodyBottom);
        try { for (int i = 0; i < lines.size(); i++) {
            int lineY = bodyTop + i * MpTypography.LINE_HEIGHT - scroll;
            if (lineY + MpTypography.LINE_HEIGHT > bodyTop && lineY < bodyBottom) g.drawString(font, lines.get(i), left, lineY, theme.text(), false);
        }} finally { g.disableScissor(); }
        super.render(g, x, y, partial);
        if (error != null) g.drawString(font, fit(MpTypography.text(error), windowWidth - 128), left, windowY + windowHeight - 25, theme.text(), false);
    }
    @Override public Component getNarrationMessage() {
        var result = title.copy();
        for (var paragraph : paragraphs) result.append(". ").append(paragraph);
        return result;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private net.minecraft.util.FormattedCharSequence fit(Component value, int available) {
        return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(value, Math.max(0, available)));
    }

}
