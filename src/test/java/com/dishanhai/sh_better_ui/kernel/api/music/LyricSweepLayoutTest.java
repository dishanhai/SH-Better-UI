// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LyricSweepLayoutTest {
    @Test void sweepMovesInsideGlyphRatherThanChangingWholeWordOpacity(){
        var layout=LyricSweepLayout.measure(List.of(List.of(new LyricSweepLayout.Glyph(0,10),new LyricSweepLayout.Glyph(0,6))),1);
        var span=layout.row(0).get(0);
        assertEquals(0,layout.revealed(span,0));assertEquals(4,layout.revealed(span,.25f));
        assertEquals(8,layout.revealed(span,.5f));assertEquals(12,layout.revealed(span,.75f));assertEquals(16,layout.revealed(span,1));
        // Seek backwards uses the same geometry, without any animation-state lag.
        assertEquals(4,layout.revealed(span,.25f));
    }
    @Test void wrappedWordConsumesItsCombinedMeasuredWidthNotOneSweepPerRow(){
        var layout=LyricSweepLayout.measure(List.of(
                List.of(new LyricSweepLayout.Glyph(0,12)),
                List.of(new LyricSweepLayout.Glyph(0,8),new LyricSweepLayout.Glyph(1,9))),2);
        var first=layout.row(0).get(0);var second=layout.row(1).get(0);
        assertEquals(10,layout.revealed(first,.5f));assertEquals(0,layout.revealed(second,.5f));
        assertEquals(12,layout.revealed(first,.75f));assertEquals(3,layout.revealed(second,.75f));
        assertEquals(8,layout.row(1).get(1).x());
    }
    @Test void unicodeMetricsAreSuppliedByFontNotUtf16CountsAndBidiFallsBack(){
        var layout=LyricSweepLayout.measure(List.of(List.of(
                new LyricSweepLayout.Glyph(0,12), // emoji glyph: not two surrogate widths
                new LyricSweepLayout.Glyph(0,0), // combining mark
                new LyricSweepLayout.Glyph(0,4))),1);
        assertEquals(8,layout.revealed(layout.row(0).get(0),.5f));
        assertTrue(LyricSweepLayout.leftToRight("窗边😀e\u0301"));
        assertFalse(LyricSweepLayout.leftToRight("hello שלום"));
        assertEquals(0,layout.revealed(layout.row(0).get(0),Float.NaN));
    }
    @Test void sameTimestampRemainsFrozenAndZeroOrInvalidMetricsNeverMakeInvalidClips(){
        var layout=LyricSweepLayout.measure(List.of(List.of(new LyricSweepLayout.Glyph(0,Float.NaN),new LyricSweepLayout.Glyph(0,-2))),1);
        assertEquals(0,layout.revealed(layout.row(0).get(0),1));
        assertEquals(LyricPresentation.wordFraction(250,100,500),LyricPresentation.wordFraction(250,100,500));
    }
}
