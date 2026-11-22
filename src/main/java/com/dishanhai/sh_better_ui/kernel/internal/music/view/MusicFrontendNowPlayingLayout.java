// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Immersive player geometry; every glyph uses the same rectangle for paint and input. */
record MusicFrontendNowPlayingLayout(Bounds cover, Bounds title, Bounds subtitle, Bounds detail,
                                    Bounds lyrics, Bounds queue, Bounds elapsed, Bounds remaining,
                                    MusicFrontendLayout.Transport transport, boolean split) {
    Bounds transportChrome() {
        var progress = transport.progress();
        // Stay below compact lyrics, and stay in the same column as the controls.
        return new Bounds(progress.x(), progress.y(), progress.width(), queue.bottom() - progress.y());
    }
    Bounds comments() { return new Bounds(queue.right() + 4, queue.y(), 24, queue.height()); }

    static MusicFrontendNowPlayingLayout fit(Bounds frame, boolean detailOpen) {
        int inset = frame.width() >= 420 ? 24 : 16;
        int width = frame.width() - 2 * inset;
        boolean split = detailOpen && width >= 420;
        int availableWidth = Math.min(width, split ? 810 : detailOpen ? 490 : 340);
        int top = frame.y() + 34;
        int bottom = frame.bottom() - 12;
        int modeY = bottom - 24;
        int controlY = modeY - 29;
        int progressY = controlY - 26;
        int artHeight = progressY - top - 42;
        int contentWidth = detailOpen ? availableWidth : Math.min(availableWidth, Math.max(146, artHeight));
        int left = frame.x() + (frame.width() - contentWidth) / 2;
        int leftWidth = split ? Math.min(Math.min(284, (contentWidth - 30) * 42 / 100), Math.max(146, artHeight)) : contentWidth;
        int center = left + leftWidth / 2;
        var empty = new Bounds(left, top, 0, 0);
        var transport = new MusicFrontendLayout.Transport(empty,
                new Bounds(center - 62, controlY, 36, 28), new Bounds(center - 22, controlY - 1, 44, 30),
                new Bounds(center + 26, controlY, 36, 28), new Bounds(left, progressY, leftWidth, 12));
        var elapsed = new Bounds(left, progressY + 12, leftWidth / 2, 12);
        var remaining = new Bounds(elapsed.right(), elapsed.y(), leftWidth - elapsed.width(), 12);
        var lyrics = new Bounds(center - 38, modeY, 32, 24);
        var queue = new Bounds(center + 6, modeY, 32, 24);

        int size = detailOpen && !split ? 38 : Math.min(leftWidth, artHeight);
        int stageY = detailOpen && !split ? top : top + Math.max(0, (artHeight - size) / 2);
        var cover = new Bounds(detailOpen && !split ? left : center - size / 2, stageY, size, size);
        int textX = detailOpen && !split ? cover.right() + 10 : left;
        int textY = detailOpen && !split ? stageY + 2 : cover.bottom() + 6;
        var title = new Bounds(textX, textY, left + leftWidth - textX, 18);
        var subtitle = new Bounds(textX, title.bottom() + 1, title.width(), 16);

        var detail = !detailOpen ? empty : split
                ? new Bounds(left + leftWidth + 30, top, contentWidth - leftWidth - 30, bottom - top)
                : new Bounds(left, cover.bottom() + 6, contentWidth, progressY - cover.bottom() - 7);
        return new MusicFrontendNowPlayingLayout(cover, title, subtitle, detail, lyrics, queue, elapsed, remaining, transport, split);
    }
}
