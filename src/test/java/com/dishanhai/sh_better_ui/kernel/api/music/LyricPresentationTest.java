// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricPresentationTest {
    @Test void currentPendingMaskUsesMutedWhileOtherRowsRetainTheirOwnInk(){
        int muted=0xff999999,nearActive=0xffeeeeee;
        assertEquals(muted,LyricPresentation.pendingInk(true,muted,nearActive));
        assertNotEquals(nearActive,LyricPresentation.pendingInk(true,muted,nearActive));
        assertEquals(nearActive,LyricPresentation.pendingInk(false,muted,nearActive));
    }
    @Test void providerWordProgressDoesNotAdvanceWithWallClockAndHandlesSeekBack(){
        assertEquals(0,LyricPresentation.wordFraction(-1,100,300));
        assertEquals(0,LyricPresentation.wordFraction(100,100,300));
        assertEquals(.5f,LyricPresentation.wordFraction(200,100,300));
        assertEquals(.5f,LyricPresentation.wordFraction(200,100,300));
        assertEquals(1,LyricPresentation.wordFraction(300,100,300));
        assertEquals(.25f,LyricPresentation.wordFraction(150,100,300));
    }
    @Test void visualBrightnessIsBoundedAndReducedMotionIsImmediate(){
        assertEquals(1,LyricPresentation.approach(0,1,.016,true));
        float value=0;
        for(int frame=0;frame<30;frame++){
            value=LyricPresentation.approach(value,1,.016,false);
            assertTrue(value>=0&&value<=1);
        }
        assertTrue(value>.99f);
        assertEquals(LyricPresentation.distanceFade(2),LyricPresentation.distanceFade(-2));
        assertTrue(LyricPresentation.distanceFade(5)>LyricPresentation.distanceFade(1));
    }
    @Test void wrappedWordStylesKeepUnicodeAndUseOnlyTheirRealWordClock()throws Exception{
        var words=java.util.List.of(new MusicLyricsModel.Word("😀",100,300),new MusicLyricsModel.Word("世界",500,900));
        var line=new MusicLyricsModel.Line("😀世界",100,900,words);
        var source=net.minecraft.util.FormattedCharSequence.composite(
                net.minecraft.util.FormattedCharSequence.forward("😀",net.minecraft.network.chat.Style.EMPTY.withInsertion("uuk-word:0")),
                net.minecraft.util.FormattedCharSequence.forward("世界",net.minecraft.network.chat.Style.EMPTY.withInsertion("uuk-word:1")));
        var method=MusicLyricsView.class.getDeclaredMethod("timedColors",net.minecraft.util.FormattedCharSequence.class,
                MusicLyricsModel.Line.class,long.class,int.class,int.class);method.setAccessible(true);
        var result=(net.minecraft.util.FormattedCharSequence)method.invoke(null,source,line,300L,0xff444444,0xffffffff);
        var chars=new StringBuilder();var colors=new java.util.ArrayList<Integer>();
        result.accept((index,style,cp)->{chars.appendCodePoint(cp);colors.add(style.getColor().getValue());return true;});
        assertEquals("😀世界",chars.toString());assertEquals(3,colors.size());
        assertEquals(0xffffff,colors.get(0));assertEquals(0x444444,colors.get(1));assertEquals(0x444444,colors.get(2));
    }
    @Test void nonFiniteScrollCannotPoisonViewportOffset()throws Exception{
        var view=new MusicLyricsView(new Bounds(0,0,240,100),Component.empty(),UiPreferences.DEFAULT,p->{});
        assertFalse(view.mouseScrolled(10,10,Double.NaN));
        assertFalse(view.mouseScrolled(10,10,Double.POSITIVE_INFINITY));
        var offset=MusicLyricsView.class.getDeclaredField("offset");offset.setAccessible(true);
        assertEquals(0d,offset.getDouble(view));
    }
}
