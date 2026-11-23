// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.controls.ControlPaint;
import com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTheme;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekModel;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekPresentation;
import com.muoniumplayer.core.interfaces.SharedRenderingConstants;
import com.muoniumplayer.core.management.FontManager;

import java.util.Optional;

/** UUK slider adapter for the embedded player's layout and drawing coordinates. */
public final class EmbeddedMusicSlider implements SharedRenderingConstants {
    private final MusicSeekModel model = new MusicSeekModel();
    private double x, centerY, width, hitHeight;
    private float engagement;
    private long lastFrame;

    public void layout(double x, double centerY, double width, double hitHeight) {
        this.x = x;
        this.centerY = centerY;
        this.width = width;
        this.hitHeight = hitHeight;
    }

    public void sync(String key, long position, long duration, boolean enabled) {
        model.sync(key, position, duration, enabled && width > 0);
    }

    public boolean hits(double mouseX, double mouseY) {
        return model.enabled() && width > 0 && mouseX >= x && mouseX <= x + width
                && Math.abs(mouseY - centerY) <= hitHeight * .5;
    }

    public boolean press(double mouseX, double mouseY) {
        return hits(mouseX, mouseY) && model.begin(fractionAt(mouseX)).isPresent();
    }

    public void drag(double mouseX) {
        model.drag(fractionAt(mouseX));
    }

    public Optional<MusicSeekModel.Edit> release(double mouseX) {
        drag(mouseX);
        return model.release();
    }

    public boolean cancel() {
        return model.cancel().isPresent();
    }

    public boolean editing() {
        return model.editing();
    }

    public long position() {
        return model.positionMs();
    }

    public long duration() {
        return model.durationMs();
    }

    public double fraction() {
        return model.fraction();
    }

    public double fractionAt(double mouseX) {
        return width > 0 ? Math.max(0, Math.min(1, (mouseX - x) / width)) : 0;
    }

    public double paintTrack(boolean hovered, boolean flat, double idleHeight, int accent, float opacity) {
        boolean engaged = hovered || editing();
        long now = System.nanoTime();
        var preferences = UiPreferences.configured();
        engagement = MusicSeekPresentation.engagement(engagement, engaged,
                (now - lastFrame) * 1e-9, preferences.reducedMotion());
        lastFrame = now;
        double height = idleHeight + 1.5 * engagement;
        float trackOpacity = preferences.highContrast() || editing() ? 1f : hovered ? .94f : .72f;
        int track = reAlpha(WidgetTheme.color(WidgetTheme.Role.BORDER), opacity * trackOpacity);
        int fill = reAlpha(accent, opacity);
        double top = centerY - height * .5;
        if (flat) {
            ControlPaint.slider((px, py, w, h, color) -> roundedRect(px, py, w, h, 0, color),
                    x, top, width, fraction(), height, track, fill);
        } else {
            roundedRect(x, top, width, height, height * .5, track);
            double filled = width * fraction();
            if (filled > 0) roundedRect(x, top, filled, height, Math.min(height, filled) * .5, fill);
        }
        return height;
    }

    public void paintThumb(float opacity) {
        if (engagement <= .02f) return;
        double radius = (editing() ? 3.5 : 2.75) * engagement;
        double thumb = x + width * fraction();
        roundedRect(thumb - radius, centerY - radius, 2 * radius, 2 * radius, radius,
                reAlpha(WidgetTheme.color(WidgetTheme.Role.PRIMARY_TEXT), opacity * engagement));
    }

    public void paintPreview(String text, double fraction, float opacity) {
        var font = FontManager.pf12bold;
        double textWidth = font.getStringWidthD(text);
        int bubbleWidth = (int) Math.ceil(textWidth + 12);
        if (bubbleWidth > width) return;
        double left = x + MusicSeekPresentation.bubbleX(0, (int) width, bubbleWidth, fraction);
        double height = Math.max(13, font.getFontHeight() + 5);
        double top = centerY - height - 7;
        roundedRect(left - .5, top - .5, bubbleWidth + 1, height + 1, 5,
                reAlpha(WidgetTheme.color(WidgetTheme.Role.BORDER), opacity));
        roundedRect(left, top, bubbleWidth, height, 4.5,
                reAlpha(WidgetTheme.color(WidgetTheme.Role.INPUT_BACKGROUND), opacity));
        font.drawString(text, left + (bubbleWidth - textWidth) * .5, top + 2,
                reAlpha(WidgetTheme.color(WidgetTheme.Role.PRIMARY_TEXT), opacity));
    }
}
