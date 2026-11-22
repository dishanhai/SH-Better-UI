// Author: dishanhai
package com.muoniumplayer.core.rendering;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.core.rendering.font.CFontRenderer;
import com.muoniumplayer.core.rendering.rendersystem.RenderSystem;
import com.muoniumplayer.core.rendering.ui.widgets.LabelWidget;
import com.muoniumplayer.core.screens.ncm.LyricLine;
import com.muoniumplayer.render.skija.SkijaGfx;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

import java.util.function.Supplier;

/** Displays a track title and its current lyric in a single, width-limited row. */
public final class PlaybackTitleWidget extends LabelWidget {
    private static final double LYRIC_GAP = 12.0;
    private static final long TITLE_HOLD_MS = 5000L;

    enum Layout { TITLE_ONLY, SIDE_BY_SIDE, LYRIC_ONLY }

    private final MusicCapsuleLyrics lyricText = new MusicCapsuleLyrics();
    private LyricLine line;
    private long positionMs;
    private long lyricOffsetMs;
    private int lyricColor;
    private boolean replaceTitle;

    public PlaybackTitleWidget(Supplier<String> title, CFontRenderer font) {
        super(title, font);
        setClickable(false);
    }

    public void setLyric(LyricLine line, long positionMs, long lyricOffsetMs, int color, boolean replaceTitle) {
        this.line = line;
        this.positionMs = positionMs;
        this.lyricOffsetMs = lyricOffsetMs;
        this.lyricColor = color;
        this.replaceTitle = replaceTitle;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        CFontRenderer font = getFont();
        if (line == null || font == null || getMaxWidth() <= 0) {
            super.onRender(mouseX, mouseY);
            return;
        }

        String title = getLabel();
        double titleWidth = font.getStringWidthD(title);
        double width = getMaxWidth();
        // Reserve a readable preview width so the title stays in place as lyric lengths change.
        Layout layout = layout(width, titleWidth, font.getFontHeight() * 6, positionMs, replaceTitle);
        if (layout != Layout.LYRIC_ONLY) super.onRender(mouseX, mouseY);
        if (layout == Layout.TITLE_ONLY) return;

        String text = line.lyric.trim();
        lyricText.prepare(line, text, font);
        double lyricLeft = layout == Layout.SIDE_BY_SIDE ? titleWidth + LYRIC_GAP : 0.0;
        drawLyric(font, text, getX() + lyricLeft, getY(), width - lyricLeft);
        setBounds(Math.min(width, lyricLeft + lyricText.width()), font.getStringHeight(title));
    }

    static Layout layout(double width, double titleWidth, double minimumLyricWidth,
                         long positionMs, boolean replaceTitle) {
        if (width >= titleWidth + LYRIC_GAP + minimumLyricWidth) return Layout.SIDE_BY_SIDE;
        if (width > 0 && replaceTitle && positionMs >= TITLE_HOLD_MS) return Layout.LYRIC_ONLY;
        return Layout.TITLE_ONLY;
    }

    private void drawLyric(CFontRenderer font, String text, double x, double y, double width) {
        Canvas canvas = SkijaGfx.canvas();
        if (canvas == null) return;
        double revealed = lyricText.revealedWidth((double) positionMs + lyricOffsetMs);
        double offset = UiPreferences.configured().reducedMotion() ? 0.0
                : MusicCapsuleLyrics.followOffset(lyricText.width(), width, revealed);
        double height = font.getFontHeight() + 4.0;
        int saved = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH((float) x, (float) (y - 2), (float) width, (float) height));
            int dim = RenderSystem.reAlpha(lyricColor, getAlpha());
            font.drawString(text, x - offset, y, dim);
            if (lyricText.timed()) {
                double highlightedWidth = Math.min(width, revealed - offset);
                if (highlightedWidth > 0) {
                    canvas.clipRect(Rect.makeXYWH((float) x, (float) (y - 2), (float) highlightedWidth, (float) height));
                    font.drawString(text, x - offset, y, getHexColor());
                }
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
