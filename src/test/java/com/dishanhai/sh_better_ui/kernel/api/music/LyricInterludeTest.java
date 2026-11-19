// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LyricInterludeTest {
    @Test void knownLeadInAndLongGapProduceAPauseStableFade(){
        var lines=List.of(new MusicLyricsModel.Line("One",3000,4000),new MusicLyricsModel.Line("Two",7000,8000));
        assertEquals(0,LyricInterlude.at(lines,1200).next());
        assertNull(LyricInterlude.at(lines,3400));
        assertEquals(0,LyricInterlude.at(lines,4000).opacity());
        var midpoint=LyricInterlude.at(lines,5500);
        assertEquals(1,midpoint.next());
        assertEquals(.5f,midpoint.progress());
        assertEquals(1,midpoint.opacity());
        assertEquals(midpoint,LyricInterlude.at(lines,5500));
        assertTrue(LyricInterlude.at(lines,6900).opacity()<1);
        assertNull(LyricInterlude.at(lines,7000));
    }
    @Test void untimedEndAndShortPausesDoNotInventAnInterlude(){
        assertNull(LyricInterlude.at(List.of(new MusicLyricsModel.Line("One",0),
                new MusicLyricsModel.Line("Two",5000)),3500));
        assertNull(LyricInterlude.at(List.of(new MusicLyricsModel.Line("One",0,2500),
                new MusicLyricsModel.Line("Two",3500)),2700));
    }
}
