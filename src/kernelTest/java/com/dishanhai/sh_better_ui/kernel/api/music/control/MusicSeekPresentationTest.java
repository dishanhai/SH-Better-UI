package com.dishanhai.sh_better_ui.kernel.api.music.control;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicSeekPresentationTest {
    @Test void bubbleStaysInsideTimelineAtBothEndsAndNarrowWidths(){
        for(int width:new int[]{16,32,120,360})for(double position:new double[]{-1,0,.1,.5,.9,1,2,Double.NaN}){
            int size=Math.min(width,90),x=MusicSeekPresentation.bubbleX(23,width,size,position);
            assertTrue(x>=23);assertTrue(x+size<=23+width);
        }
    }
    @Test void motionSettlesAndReversesWithoutJumping(){
        float value=MusicSeekPresentation.engagement(0,true,.016,false);
        assertTrue(value>0&&value<1);
        float reversed=MusicSeekPresentation.engagement(value,false,.016,false);
        assertTrue(reversed>=0&&reversed<value);
        assertEquals(1,MusicSeekPresentation.engagement(value,true,.016,true));
        assertEquals(0,MusicSeekPresentation.engagement(value,false,.016,true));
    }
    @Test void motionIsFrameRateIndependentForNormalFrameIntervals(){
        float value=0;for(int i=0;i<6;i++)value=MusicSeekPresentation.engagement(value,true,.01,false);
        assertEquals(MusicSeekPresentation.engagement(0,true,.06,false),value,.00001);
    }
}
