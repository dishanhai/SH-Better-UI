package com.muoniumplayer.core.screens.ncm;

import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.interfaces.SharedRenderingConstants;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compact full-screen control; does not request or generate translations. */
final class LyricLanguagePicker implements SharedRenderingConstants {
    private static final double BUTTON_SIZE = 26;
    private static final double ROW_HEIGHT = 20;
    private List<LyricLanguage> options = List.of(LyricLanguage.ORIGINAL);
    private LyricLanguage selected;
    private LyricLanguage preferred = LyricLanguage.ORIGINAL;
    private long revision = Long.MIN_VALUE;
    private boolean visible, open, focused;
    private int menuFocus;
    private double left, top, menuWidth;

    LyricLanguage selected() { return selected; }
    boolean isVisible() { return visible; }
    boolean matches(long trackRevision) { return revision == trackRevision; }
    boolean isOpen() { return visible && open; }
    boolean isFocused() { return visible && focused; }
    boolean canFocus() { return visible; }
    void setFocused(boolean focus) { focused = visible && focus; }

    void hide() {
        visible = open = focused = false;
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
            open = false;
        }
        if (options.size() < 2 || width < 30 || height < BUTTON_SIZE + 12 + options.size() * ROW_HEIGHT) {
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
            desired = Math.max(desired, FontManager.pf12bold.getStringWidthD(label(option)) + 24);
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
            if (inside(x, y, left, menuTop(), menuWidth, options.size() * ROW_HEIGHT)) {
                choose((int) ((y - menuTop()) / ROW_HEIGHT));
            }
            open = false;
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

        if (open) {
            double menuY = menuTop();
            roundedRect(left, menuY, menuWidth, ROW_HEIGHT * options.size(), 7,
                    hexColor(.10f, .10f, .12f, alpha * .97f));
            for (int i = 0; i < options.size(); i++) {
                LyricLanguage option = options.get(i);
                double rowY = menuY + i * ROW_HEIGHT;
                boolean rowHover = inside(mouseX, mouseY, left, rowY, menuWidth, ROW_HEIGHT);
                if (rowHover) CursorUtils.setOverride(CursorUtils.HAND);
                if (rowHover || (focused && i == menuFocus)) {
                    roundedRect(left + 2, rowY + 1, Math.max(1, menuWidth - 4), ROW_HEIGHT - 2, 4,
                            hexColor(1f, 1f, 1f, alpha * .19f));
                }
                drawCentered(label(option), left + 5, rowY, menuWidth - 18, ROW_HEIGHT, alpha);
                if (option == effectiveSelection()) {
                    drawCentered("✓", left + menuWidth - 13, rowY, 12, ROW_HEIGHT, alpha);
                }
            }
        } else if (hover || focused) {
            String title = Component.translatable("sh_better_ui.muonium.lyric_language.title").getString();
            double labelWidth = Math.min(menuWidth, FontManager.pf12bold.getStringWidthD(title) + 12);
            double labelY = top - ROW_HEIGHT - 4;
            roundedRect(left, labelY, labelWidth, ROW_HEIGHT, 6,
                    hexColor(.10f, .10f, .12f, alpha * .94f));
            drawCentered(title, left, labelY, labelWidth, ROW_HEIGHT, alpha);
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

    private double menuTop() { return top - 4 - ROW_HEIGHT * options.size(); }

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
