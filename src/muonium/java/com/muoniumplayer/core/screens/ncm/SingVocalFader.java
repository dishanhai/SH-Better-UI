package com.muoniumplayer.core.screens.ncm;

import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.interfaces.SharedRenderingConstants;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.SingVocalControl;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import net.minecraft.network.chat.Component;

/** Fullscreen lyrics microphone button and its directly draggable, vertical vocal volume. */
final class SingVocalFader implements SharedRenderingConstants {
    private static final double W = 32, H = 30, SHAFT = 66;
    private final SingVocalControl sing = new SingVocalControl();
    private CloudMusic.PlaybackSnapshot track;
    private SingVocalControl.State state = SingVocalControl.State.UNAVAILABLE;
    private double x, y, right, viewportLeft, viewportTop;
    private boolean visible, interactive, expanded, focused, pressed, dragging, consumeRelease;
    private float initialLevel;

    void dispose() {
        dismiss();
        sing.cancel();
    }

    void dismiss() {
        rollbackDrag();
        visible = interactive = expanded = focused = pressed = false;
        consumeRelease = false;
    }

    private void rollbackDrag() {
        if (dragging) sing.setLevel(initialLevel);
        dragging = false;
    }

    void layout(CloudMusic.PlaybackSnapshot next, boolean show, boolean canInteract,
                double left, double top, double right, double bottom) {
        if (track == null || track.player != next.player || track.sessionId != next.sessionId) {
            dismiss();
        }
        track = next;
        state = sing.state(next);
        this.right = right;
        this.viewportLeft = left;
        this.viewportTop = top;
        x = right - W - 14;
        y = bottom - H - 14;
        visible = show && right - left >= 100 && bottom - top >= 128
                && state != SingVocalControl.State.UNAVAILABLE;
        interactive = visible && canInteract;
        if (!interactive) {
            rollbackDrag();
            pressed = focused = expanded = false;
        }
    }

    boolean hit(double px, double py) {
        return interactive && px >= x && px < x + W && py >= (expanded ? y - SHAFT : y) && py < y + H;
    }

    boolean isVisible() { return visible; }

    void yieldFocus() {
        rollbackDrag();
        expanded = focused = pressed = false;
    }

    boolean capturesDrag() { return dragging; }

    boolean hasPress() { return consumeRelease; }

    boolean press(double px, double py) {
        if (!hit(px, py)) {
            focused = expanded = false;
            return false;
        }
        focused = true;
        pressed = true;
        consumeRelease = true;
        if (expanded && state == SingVocalControl.State.READY && py < y) {
            initialLevel = sing.level();
            dragging = true;
            setAt(py);
        }
        return true;
    }

    boolean release(double px, double py) {
        if (!consumeRelease) return false;
        consumeRelease = false;
        if (dragging) {
            if (interactive) setAt(py);
            else rollbackDrag();
        } else if (pressed && hit(px, py) && py >= y) {
            activate();
        }
        dragging = pressed = false;
        return true;
    }

    boolean scroll(int wheel, double x, double y) {
        if (wheel == 0 || !hit(x, y)) return false;
        if (expanded && state == SingVocalControl.State.READY)
            sing.setLevel(sing.level() + Math.copySign(.05f, wheel));
        return true;
    }

    private void setAt(double py) {
        double top = y - SHAFT + 9, bottom = y - 8;
        sing.setLevel((float) ((bottom - py) / (bottom - top)));
    }

    boolean key(int key) {
        if (!interactive) return false;
        // Explicit keyboard entry without stealing the lyrics page's existing Tab cycle.
        if (key == Keyboard.KEY_V && (Keyboard.isKeyDown(Keyboard.KEY_LMENU)
                || Keyboard.isKeyDown(Keyboard.KEY_RMENU))) {
            focused = true;
            return true;
        }
        if (!focused) return false;
        if (key == Keyboard.KEY_TAB) {
            focused = false;
            return false;
        }
        if (key == Keyboard.KEY_ESCAPE && (expanded || dragging || state == SingVocalControl.State.PREPARING)) {
            rollbackDrag();
            expanded = false;
            if (state == SingVocalControl.State.PREPARING) sing.cancel();
            return true;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_SPACE) {
            activate();
            return true;
        }
        if (expanded && state == SingVocalControl.State.READY) {
            if (key == Keyboard.KEY_UP) sing.setLevel(sing.level() + .05f);
            else if (key == Keyboard.KEY_DOWN) sing.setLevel(sing.level() - .05f);
            else if (key == Keyboard.KEY_HOME) sing.setLevel(1f);
            else if (key == Keyboard.KEY_END) sing.setLevel(0f);
            else return false;
            return true;
        }
        return false;
    }

    private void activate() {
        switch (state) {
            case READY -> expanded = !expanded;
            case PREPARING -> sing.cancel();
            case IDLE, MODEL_MISSING, FAILED -> sing.prepare(track);
            default -> { }
        }
        state = sing.state(track);
    }

    void render(double px, double py, boolean down, float opacity) {
        if (!visible) return;
        if (dragging && down && interactive) setAt(py);
        double top = expanded ? y - SHAFT : y;
        double h = y + H - top;
        boolean hover = hit(px, py);
        float level = sing.level();
        roundedRect(x, top, W, h, 14, hexColor(.08f, .08f, .10f, opacity * (hover || focused ? .91f : .76f)));
        if (focused || expanded) {
            roundedOutline(x, top, W, h, 14, focused ? 1.3 : .75,
                    new java.awt.Color(1f, 1f, 1f, opacity * (focused ? .8f : .35f)));
        }
        if (expanded) {
            double railTop = top + 9, bottom = y - 8;
            double thumb = bottom - Math.max(0, Math.min(1, level)) * (bottom - railTop);
            double center = x + W * .5;
            roundedRect(center - 2.5, railTop, 5, bottom - railTop, 2.5, hexColor(1, 1, 1, opacity * .24f));
            if (thumb < bottom) roundedRect(center - 2.5, thumb, 5, bottom - thumb, 2.5,
                    hexColor(1, 1, 1, opacity * .85f));
            roundedRect(center - 5, thumb - 5, 10, 10, 5, hexColor(1, 1, 1, opacity));
        }
        float iconAlpha = state == SingVocalControl.State.UNSUPPORTED ? .42f : pressed ? .68f : 1f;
        FoliaLucideIcons.draw(FoliaLucideIcons.MIC, x + W * .5, y + H * .5, 14,
                hexColor(1, 1, 1, opacity * iconAlpha));
        if (state == SingVocalControl.State.PREPARING) {
            double progress = (W - 12) * sing.progress() / 100.0;
            roundedRect(x + 6, y + H - 5, W - 12, 1.5, .75, hexColor(1, 1, 1, opacity * .23f));
            if (progress > 0) roundedRect(x + 6, y + H - 5, progress, 1.5, .75, hexColor(1, 1, 1, opacity));
        }
        if (hover || focused || dragging) drawLabel(top, opacity, level);
    }

    private void drawLabel(double top, float opacity, float level) {
        String key = state.name().toLowerCase(java.util.Locale.ROOT);
        String text = Component.translatable("sh_better_ui.muonium.sing." + key,
                state == SingVocalControl.State.PREPARING ? sing.progress() : Math.round(level * 100)).getString();
        var font = FontManager.pf34bold;
        double padding = 5, labelWidth = font.getStringWidth(text) + 2 * padding;
        if (labelWidth > right - viewportLeft - 4 || font.getHeight() + 14 > top - viewportTop) {
            text = Component.translatable("sh_better_ui.muonium.sing.label").getString();
            labelWidth = font.getStringWidth(text) + 2 * padding;
            if (labelWidth > right - viewportLeft - 4) return;
        }
        double labelLeft = right - labelWidth - 3;
        double labelTop = top - font.getHeight() - 12;
        roundedRect(labelLeft, labelTop, labelWidth, font.getHeight() + 8, 6,
                hexColor(.08f, .08f, .10f, opacity * .94f));
        font.drawString(text, labelLeft + padding, labelTop + 4, hexColor(1, 1, 1, opacity));
    }
}
