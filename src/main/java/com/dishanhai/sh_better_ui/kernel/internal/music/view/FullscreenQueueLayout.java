// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;

/** Shares the available height between the controls and the scrollable content. */
record FullscreenQueueLayout(double left, double width, double top, double headerHeight,
                             Box modes, double modeGap, Box review, double notesTitleWidth,
                             Box viewport) {
    private static final int INSET = 7;
    private static final int VERTICAL_INSET = 6;
    private static final int MODE_HEIGHT = 25;
    private static final int MODE_SPACING = 8;
    private static final int REVIEW_HEIGHT = 22;
    private static final int REVIEW_SPACING = 6;
    private static final int NOTES_TITLE_HEIGHT = 22;
    private static final int MIN_MODE_WIDTH = 16;

    static FullscreenQueueLayout of(Box column, int modeCount, int queueRowHeight, int noteLineHeight,
                                    boolean hasTrack, boolean showingNotes, double notesTitleWidth,
                                    double reviewWidth) {
        double left = column.x() + INSET;
        double width = Math.max(0, column.width() - 2 * INSET);
        double top = column.y() + VERTICAL_INSET;
        double height = Math.max(0, column.height() - 2 * VERTICAL_INSET);
        int modeRowHeight = MODE_HEIGHT + MODE_SPACING;
        int notesMinimum = REVIEW_HEIGHT + REVIEW_SPACING + noteLineHeight;
        boolean showModes = width >= modeCount * MIN_MODE_WIDTH
                && height >= modeRowHeight + Math.max(queueRowHeight, notesMinimum);
        double bodyHeight = height - (showModes ? modeRowHeight : 0);
        // Leave room for a section heading and a track, or a review button and two notes lines.
        double reserve = Math.max(2 * queueRowHeight, notesMinimum + noteLineHeight);
        double headerHeight = hasTrack && width >= 96 && bodyHeight >= reserve + 46 ? 46
                : bodyHeight >= reserve + 27 ? 27 : 0;
        Box modes = showModes ? new Box(left, top + headerHeight, width, MODE_HEIGHT) : null;
        double modeGap = showModes ? Math.min(6, (width - modeCount * MIN_MODE_WIDTH) / (modeCount - 1)) : 0;
        double pageTop = top + headerHeight + (showModes ? modeRowHeight : 0);
        Box review = null;
        double titleWidth = 0;
        double listTop = pageTop;
        double available = top + height - pageTop;
        if (showingNotes && available >= notesMinimum) {
            double buttonWidth = Math.min(width, reviewWidth);
            boolean sameRow = notesTitleWidth + buttonWidth + 8 <= width;
            boolean stack = !sameRow && available >= notesMinimum + NOTES_TITLE_HEIGHT + noteLineHeight;
            if (sameRow || stack) titleWidth = stack ? width : width - buttonWidth - 8;
            review = new Box(sameRow ? left + width - buttonWidth : left,
                    pageTop + (stack ? NOTES_TITLE_HEIGHT : 0), sameRow ? buttonWidth : width, REVIEW_HEIGHT);
            listTop = review.bottom() + REVIEW_SPACING;
        }
        Box viewport = new Box(left, listTop, width, Math.max(0, top + height - listTop));
        return new FullscreenQueueLayout(left, width, top, headerHeight, modes, modeGap, review, titleWidth, viewport);
    }
}
