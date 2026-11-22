// Author: dishanhai
package com.muoniumplayer.core.rendering;

import org.junit.jupiter.api.Test;

import static com.muoniumplayer.core.rendering.PlaybackTitleWidget.Layout.*;
import static com.muoniumplayer.core.rendering.PlaybackTitleWidget.layout;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackTitleWidgetTest {
    @Test
    void aWideBarShowsBothBeforeAndAfterTheTitleHold() {
        assertEquals(SIDE_BY_SIDE, layout(144, 60, 72, 0, true));
        assertEquals(SIDE_BY_SIDE, layout(144, 60, 72, 6000, false));
    }

    @Test
    void aNarrowBarReplacesTheTitleAtFiveSecondsOnlyWhenEnabled() {
        assertEquals(TITLE_ONLY, layout(143, 60, 72, 4999, true));
        assertEquals(LYRIC_ONLY, layout(143, 60, 72, 5000, true));
        assertEquals(TITLE_ONLY, layout(143, 60, 72, 5000, false));
        assertEquals(TITLE_ONLY, layout(0, 60, 72, 5000, true));
    }
}
