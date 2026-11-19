// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LyricGlyphRunsTest {
    @Test void wrappedWordKeepsItsFeatherPositionAndOriginalUnicode(){
        var tied=Style.EMPTY.withInsertion(LyricGlyphRuns.tag(0));
        var next=Style.EMPTY.withInsertion(LyricGlyphRuns.tag(1));
        var runs=LyricGlyphRuns.measure(List.of(
                FormattedCharSequence.forward("光😀",tied),
                FormattedCharSequence.composite(FormattedCharSequence.forward("影",tied),
                        FormattedCharSequence.forward("月",next))),2,(cp,style)->cp>0xffff?12:8);
        assertEquals(28,runs.geometry().width(0));
        assertEquals(20,runs.rows().get(1).get(0).wordOffset());
        assertEquals(8,runs.rows().get(1).get(1).x());
        var got=new StringBuilder();
        runs.rows().get(0).get(0).text().accept((i,style,cp)->{assertEquals(tied,style);got.appendCodePoint(cp);return true;});
        assertEquals("光😀",got.toString());
    }
}
