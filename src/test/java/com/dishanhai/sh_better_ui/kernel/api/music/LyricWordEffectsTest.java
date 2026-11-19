// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricWordEffectsTest {
    @Test void featherAdvancesOverMeasuredGlyphsAndHasExactEndpoints(){
        assertEquals(0,LyricWordEffects.coverage(0,20,5,10));
        assertEquals(1,LyricWordEffects.coverage(1,20,15,10));
        float a=LyricWordEffects.coverage(.28f,20,5,10);
        float b=LyricWordEffects.coverage(.32f,20,5,10);
        assertTrue(a>0&&b>a&&b<1);
        assertTrue(LyricWordEffects.coverage(.5f,20,5,10)>LyricWordEffects.coverage(.5f,20,15,10));
    }

    @Test void liftAndTiltReturnToBaselineBeforeNextWord(){
        for(float endpoint:new float[]{0,1}){
            assertEquals(0,LyricWordEffects.lift(endpoint,2));
            assertEquals(0,LyricWordEffects.rise(endpoint,60,2));
        }
        assertTrue(LyricWordEffects.lift(.4f,2)>1);
        assertTrue(LyricWordEffects.lift(.4f,2)>LyricWordEffects.lift(.85f,2));
        assertTrue(LyricWordEffects.rise(.4f,60,2)>.5f);
        assertTrue(LyricWordEffects.rise(.5f,1000,2.5f)<=1.2f);
    }

    @Test void inkExpansionIsBoundedAndReturnsExactlyToOriginalGeometry(){
        for(float endpoint:new float[]{-1,0,1,2})
            assertEquals(1,LyricWordEffects.verticalScale(endpoint,true));
        for(int i=0;i<=1000;i++){
            float fraction=i/1000f;
            float expansion=LyricWordEffects.verticalScale(fraction,true);
            assertTrue(expansion>=1&&expansion<=1.025f);
            assertEquals(1,LyricWordEffects.verticalScale(fraction,false),
                    "Reduced motion, browsing and scrub frames must retain static geometry");
        }
        assertEquals(1.025f,LyricWordEffects.verticalScale(.5f,true));
        assertTrue(LyricWordEffects.verticalScale(.85f,true)<LyricWordEffects.verticalScale(.5f,true));
    }

    @Test void inkExpansionIsStatelessOnBackwardAndForwardSeeks(){
        float original=LyricWordEffects.verticalScale(.4f,true);
        LyricWordEffects.verticalScale(.9f,true);
        LyricWordEffects.verticalScale(.1f,false);
        assertEquals(original,LyricWordEffects.verticalScale(.4f,true));
        assertEquals(0,LyricWordEffects.verticalScale(.0001f,true)-1,1e-6);
        assertEquals(0,LyricWordEffects.verticalScale(.9999f,true)-1,1e-6);
    }

    @Test void focusIsContinuousAboveAndBelowTheAnchorAndEdgesFade(){
        assertEquals(0,LyricWordEffects.focusDistance(100,100,20));
        assertEquals(LyricWordEffects.focusDistance(80,100,20),LyricWordEffects.focusDistance(120,100,20));
        assertTrue(LyricWordEffects.focusDistance(138,100,20)>LyricWordEffects.focusDistance(137,100,20));
        assertEquals(1,LyricWordEffects.focusDistance(200,100,20));
        assertEquals(0,LyricWordEffects.edgeOpacity(0,100,20));
        assertEquals(.5f,LyricWordEffects.edgeOpacity(10,100,20));
        assertEquals(1,LyricWordEffects.edgeOpacity(50,100,20));
        assertEquals(0,LyricWordEffects.edgeOpacity(100,100,20));
    }

    @Test void incomingTimedRowKeepsResidualBlurUntilItsInkSettles(){
        assertEquals(.4f,LyricWordEffects.softMix(.4f,0));
        assertEquals(.2f,LyricWordEffects.softMix(.4f,.5f));
        assertEquals(0,LyricWordEffects.softMix(.4f,1));
    }
}
