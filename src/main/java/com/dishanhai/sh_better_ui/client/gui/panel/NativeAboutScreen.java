// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
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
    private UiButton up, down;
    private Component error;

    public NativeAboutScreen(Screen parent) { super(text("title")); this.parent = parent; }
    static Component text(String key, Object... args) { return Component.translatable("sh_better_ui.about." + key, args); }
    @Override protected void init() {
        left = Math.max(8, (width - 420) / 2);
        bodyWidth = Math.max(1, width - left * 2 - 66);
        bodyTop = 30; bodyBottom = Math.max(bodyTop + 1, height - 82);
        var music = ShBetterUiApi.music().getSnapshot();
        paragraphs = List.of(text("identity"),
                text("version", PlatformUiApi.modInfo("sh_better_ui").map(PlatformUiApi.ModInfo::version).orElse("unknown")),
                text("platform", PlatformUiApi.loaderId(), SharedConstants.getCurrentVersion().getName()),
                text("overlay", text(NativeMusicOverlay.available() ? "available" : "unavailable")),
                text("music", music.providerId(), music.state().name()),
                text("capabilities", music.session() == null ? text("none") : Component.literal(music.session().capabilities().toString())),
                text("snapshot"), text("license"), text("source"), text("scope"), text("navigation"));
        lines.clear();
        for (var paragraph : paragraphs) { lines.addAll(font.split(paragraph, bodyWidth)); lines.add(FormattedCharSequence.EMPTY); }
        up = addRenderableWidget(new UiButton(left + bodyWidth + 2, bodyTop, 64, 20, text("up"), b -> move(-3 * font.lineHeight)));
        down = addRenderableWidget(new UiButton(left + bodyWidth + 2, bodyBottom - 20, 64, 20, text("down"), b -> move(3 * font.lineHeight)));
        up.setMessage(text("up")); down.setMessage(text("down"));
        // Full-width link rows remain reachable by Tab, independent of text scroll.
        int linkWidth = Math.max(1, (width - left * 2 - 8) / 3);
        link(left, height - 70, linkWidth, "project", PROJECT);
        link(left + linkWidth + 4, height - 70, linkWidth, "docs", PROJECT + "/tree/main/docs");
        link(left + 2 * (linkWidth + 4), height - 70, linkWidth, "notices", PROJECT + "/blob/main/src/main/resources/" + NOTICE_RESOURCE);
        addRenderableWidget(new UiButton(left, height - 26, Math.max(1, width - 2 * left), 20,
                Component.translatable("gui.back"), b -> onClose()));
        move(0);
    }
    private void link(int x, int y, int w, String key, String url) {
        var button = new UiButton(x, y, w, 20, text(key), b -> {
            if (!ShBetterUiApi.links().open(this, url, () -> getFocused())) error = text("link_failed");
        });
        addRenderableWidget(button);
    }
    static int clampScroll(int value, int content, int viewport) { return Math.max(0, Math.min(value, Math.max(0, content - viewport))); }
    private void move(int delta) {
        scroll = clampScroll(scroll + delta, lines.size() * font.lineHeight, bodyBottom - bodyTop);
        if (up != null) up.active = scroll > 0;
        if (down != null) down.active = scroll < Math.max(0, lines.size() * font.lineHeight - (bodyBottom - bodyTop));
    }
    public boolean mouseScrolled(double x, double y, double delta) {
        if (x < left || x >= left + bodyWidth + 66 || y < bodyTop || y >= bodyBottom || delta == 0) return false;
        move(delta > 0 ? -3 * font.lineHeight : 3 * font.lineHeight); return true;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return mouseScrolled(x, y, vertical); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
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
        g.fill(0, 0, width, height, theme.background());
        g.drawCenteredString(font, title, width / 2, 10, theme.text());
        g.enableScissor(left, bodyTop, left + bodyWidth, bodyBottom);
        try { for (int i = 0; i < lines.size(); i++) {
            int lineY = bodyTop + i * font.lineHeight - scroll;
            if (lineY + font.lineHeight > bodyTop && lineY < bodyBottom) g.drawString(font, lines.get(i), left, lineY, theme.text(), false);
        }} finally { g.disableScissor(); }
        super.render(g, x, y, partial);
        if (error != null) g.drawCenteredString(font, error, width / 2, height - 44, theme.text());
    }
    @Override public Component getNarrationMessage() {
        var result = title.copy();
        for (var paragraph : paragraphs) result.append(". ").append(paragraph);
        return result;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
