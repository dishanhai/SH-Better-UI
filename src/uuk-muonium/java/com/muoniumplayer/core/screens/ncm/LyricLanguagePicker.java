package com.muoniumplayer.core.screens.ncm;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.internal.PlayerMenuMotion;
import com.dishanhai.sh_better_ui.kernel.internal.PlayerMenuStyle;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.compat.lwjgl2.Mouse;
import com.muoniumplayer.core.interfaces.SharedRenderingConstants;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import com.muoniumplayer.render.skija.SkijaGfx;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** Compact full-screen control; does not request or generate translations. */
final class LyricLanguagePicker implements SharedRenderingConstants {
    private static final double BUTTON_SIZE = 26;
    private final PlayerMenuMotion motion = new PlayerMenuMotion();
    private final PlayerMenuMotion.Row[] rowMotion = Arrays.stream(LyricLanguage.values())
            .map(option -> new PlayerMenuMotion.Row()).toArray(PlayerMenuMotion.Row[]::new);
    private List<LyricLanguage> options = List.of(LyricLanguage.ORIGINAL);
    private LyricLanguage selected;
    private LyricLanguage preferred = LyricLanguage.ORIGINAL;
    private long revision = Long.MIN_VALUE;
    private boolean visible, open, focused;
    private int menuFocus;
    private double left, top, menuWidth;
    private Box visibleMenu;
    private float progress;

    LyricLanguage selected() { return selected; }
    boolean isVisible() { return visible; }
    boolean matches(long trackRevision) { return revision == trackRevision; }
    boolean isOpen() { return visible && open; }
    boolean isFocused() { return visible && focused; }
    boolean canFocus() { return visible; }
    void setFocused(boolean focus) { focused = visible && focus; }

    void hide() {
        visible = open = focused = false;
        motion.reset();
        visibleMenu = null;
    }

    void update(long trackRevision, Iterable<LyricLine> lines, double x, double y,
                double width, double height, boolean showSecondary, boolean showPronunciation) {
        if (revision != trackRevision) {
            revision = trackRevision;
            boolean translation = false, pronunciation = false;
            for (LyricLine line : lines) {
                if (LyricLanguage.nonBlank(line.lyric) == null) continue;
                translation |= LyricLanguage.nonBlank(line.translationText) != null;
                pronunciation |= LyricLanguage.nonBlank(line.romanizationText) != null;
                if (translation && pronunciation) break;
            }
            options = LyricLanguage.options(translation, pronunciation);
            if (selected != null && !options.contains(selected)) selected = null;
            hide();
        }
        if (options.size() < 2 || width < 48 || height < BUTTON_SIZE + 12 + menuHeight()) {
            hide();
            return;
        }
        preferred = showSecondary
                ? (showPronunciation && options.contains(LyricLanguage.PRONUNCIATION)
                        ? LyricLanguage.PRONUNCIATION
                        : (options.contains(LyricLanguage.TRANSLATION)
                                ? LyricLanguage.TRANSLATION : LyricLanguage.ORIGINAL))
                : LyricLanguage.ORIGINAL;
        visible = true;
        left = x + 3;
        top = y + height - BUTTON_SIZE - 4;
        double desired = 84;
        for (LyricLanguage option : options) {
            desired = Math.max(desired, FontManager.pf12bold.getStringWidthD(label(option))
                    + PlayerMenuStyle.PADDING * 2 + PlayerMenuStyle.ROW_INSET * 2 + 16);
        }
        menuWidth = Math.min(width - 6, desired);
    }

    boolean blocksLyrics(double x, double y) {
        return visible && (open || inside(x, y, left, top, BUTTON_SIZE, BUTTON_SIZE));
    }

    boolean click(double x, double y, int button) {
        if (!blocksLyrics(x, y)) return false;
        if (button != 0) return true;
        if (inside(x, y, left, top, BUTTON_SIZE, BUTTON_SIZE)) {
            if (open) open = false;
            else openMenu();
        } else if (open) {
            int row = rowAt(x, y);
            if (row >= 0) choose(row);
            else if (visibleMenu != null && !visibleMenu.contains(x, y)) {
                open = false;
            }
        }
        return true;
    }

    boolean key(int keyCode) {
        if (!visible) return false;
        if (open) {
            if (keyCode == Keyboard.KEY_ESCAPE) open = false;
            if (keyCode == Keyboard.KEY_UP) menuFocus = Math.floorMod(menuFocus - 1, options.size());
            if (keyCode == Keyboard.KEY_DOWN) menuFocus = Math.floorMod(menuFocus + 1, options.size());
            if (keyCode == Keyboard.KEY_TAB) {
                int direction = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? -1 : 1;
                menuFocus = Math.floorMod(menuFocus + direction, options.size());
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_SPACE) choose(menuFocus);
            return true;
        }
        if (focused && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_SPACE)) {
            openMenu();
            return true;
        }
        return false;
    }

    void render(double mouseX, double mouseY, float alpha) {
        if (!visible) return;
        boolean hover = inside(mouseX, mouseY, left, top, BUTTON_SIZE, BUTTON_SIZE);
        if (hover) CursorUtils.setOverride(CursorUtils.HAND);
        roundedRect(left, top, BUTTON_SIZE, BUTTON_SIZE, 13,
                hexColor(.10f, .10f, .12f, alpha * (open || hover ? .94f : .72f)));
        if (focused || open) {
            roundedRect(left + 6, top + 22, 14, 1.5, .75, hexColor(1f, 1f, 1f, alpha));
        }
        drawCentered("A↔", left, top, BUTTON_SIZE, BUTTON_SIZE, alpha);

        var preferences = UiPreferences.configured();
        long now = System.nanoTime() / 1_000_000L;
        boolean still = preferences.reducedMotion() || preferences.highContrast();
        progress = motion.sample(open, now, still);
        if (progress > .002f) {
            renderMenu(mouseX, mouseY, alpha, now, still, preferences.highContrast());
        } else if (!open && (hover || focused)) {
            String title = Component.translatable("sh_better_ui.muonium.lyric_language.title").getString();
            double labelWidth = Math.min(menuWidth, FontManager.pf12bold.getStringWidthD(title) + 12);
            double labelY = top - PlayerMenuStyle.ROW_HEIGHT - 4;
            PlayerMenuStyle.panel(new Box(left, labelY, labelWidth, PlayerMenuStyle.ROW_HEIGHT), 1, alpha,
                    preferences.highContrast());
            drawCentered(title, left, labelY, labelWidth, PlayerMenuStyle.ROW_HEIGHT, alpha);
        }
    }

    private void renderMenu(double mouseX, double mouseY, float alpha, long now, boolean still, boolean highContrast) {
        var canvas = SkijaGfx.canvas();
        if (canvas == null) return;
        Box bounds = menuBounds();
        visibleMenu = PlayerMenuMotion.frame(bounds, left + BUTTON_SIZE * .5, top, progress);
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) CursorUtils.setOverride(CursorUtils.HAND);
        float scale = (float) (visibleMenu.width() / bounds.width());
        int saved = canvas.save();
        canvas.translate((float) visibleMenu.x(), (float) visibleMenu.y());
        canvas.scale(scale, scale);
        canvas.translate((float) -bounds.x(), (float) -bounds.y());
        try {
            PlayerMenuStyle.panel(bounds, 1, alpha * progress, highContrast);
            var font = FontManager.pf12bold;
            for (int i = 0; i < options.size(); i++) {
                float rowAlpha = alpha * PlayerMenuMotion.rowReveal(progress, i);
                if (rowAlpha <= .002f) continue;
                LyricLanguage option = options.get(i);
                Box row = menuRow(i);
                boolean hover = i == hovered;
                float fill = rowMotion[option.ordinal()].sample(hover, hover && Mouse.isButtonDown(0), now, still);
                PlayerMenuStyle.row(row, rowAlpha * fill);
                if (focused && i == menuFocus) PlayerMenuStyle.focus(row, rowAlpha, highContrast);
                double inset = PlayerMenuStyle.ROW_INSET;
                String label = font.trim(label(option), row.width() - inset * 2 - 16);
                font.drawString(label, row.x() + inset, row.centerY() - font.getFontHeight() * .5,
                        PlayerMenuStyle.text(rowAlpha, highContrast));
                if (option == effectiveSelection()) {
                    FoliaLucideIcons.draw(FoliaLucideIcons.CHECK, row.right() - inset - 5,
                            row.centerY(), 10, PlayerMenuStyle.accent(rowAlpha, highContrast));
                }
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    private LyricLanguage effectiveSelection() { return selected == null ? preferred : selected; }

    private void choose(int index) {
        selected = options.get(index);
        open = false;
    }

    private void openMenu() {
        open = true;
        menuFocus = Math.max(0, options.indexOf(effectiveSelection()));
    }

    private double menuHeight() {
        return PlayerMenuStyle.PADDING * 2 + options.size() * PlayerMenuStyle.ROW_PITCH - PlayerMenuStyle.ROW_GAP;
    }

    private Box menuBounds() {
        double height = menuHeight();
        return new Box(left, top - 4 - height, menuWidth, height);
    }

    private Box menuRow(int index) {
        Box content = menuBounds().inset(PlayerMenuStyle.PADDING);
        return new Box(content.x(), content.y() + index * PlayerMenuStyle.ROW_PITCH,
                content.width(), PlayerMenuStyle.ROW_HEIGHT);
    }

    private int rowAt(double x, double y) {
        if (!open || visibleMenu == null || !visibleMenu.contains(x, y)) return -1;
        Box bounds = menuBounds();
        for (int i = 0; i < options.size(); i++) {
            if (PlayerMenuMotion.rowReveal(progress, i) >= .72f
                    && PlayerMenuMotion.mapRow(menuRow(i), bounds, visibleMenu).contains(x, y)) return i;
        }
        return -1;
    }

    private static String label(LyricLanguage option) {
        return Component.translatable(option.key).getString();
    }

    private static boolean inside(double x, double y, double l, double t, double w, double h) {
        return x >= l && x < l + w && y >= t && y < t + h;
    }

    private void drawCentered(String text, double x, double y, double w, double h, float alpha) {
        var font = FontManager.pf12bold;
        if (font.getStringWidthD(text) > w) {
            String[] rows = font.fitWidth(text, w);
            if (rows.length == 0) return;
            text = rows[0];
        }
        font.drawString(text, x + (w - font.getStringWidthD(text)) * .5,
                y + (h - font.getFontHeight()) * .5,
                hexColor(1f, 1f, 1f, alpha));
    }
}
