// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricWordEffectsTest {
    @Test void onlyFutureRowsBlurNotCurrentRemainderOrHistory(){
        assertTrue(LyricWordEffects.blurFuture(200,500,false,false,false,false));
        assertFalse(LyricWordEffects.blurFuture(200,100,false,false,false,false));
        assertFalse(LyricWordEffects.blurFuture(200,500,true,false,false,false));
        assertFalse(LyricWordEffects.blurFuture(-1,500,false,false,false,false));
        assertFalse(LyricWordEffects.blurFuture(200,500,false,true,false,false));
        assertFalse(LyricWordEffects.blurFuture(200,500,false,false,true,false));
        assertFalse(LyricWordEffects.blurFuture(200,500,false,false,false,true));
    }
    @Test void geometryIsPlaybackDeterministicBoundedAndReducedMotionIsStatic(){
        float fraction=LyricPresentation.wordFraction(250,100,500);
        assertEquals(LyricWordEffects.lift(fraction,false),LyricWordEffects.lift(fraction,false));
        assertEquals(0,LyricWordEffects.lift(0,false));
        assertEquals(0,LyricWordEffects.lift(fraction,true));
        assertEquals(0,LyricWordEffects.rise(fraction,100,true));
        assertTrue(LyricWordEffects.rise(1,1000,false)<=.55f);
        assertTrue(LyricWordEffects.lift(1,false)<=.45f);
        assertTrue(LyricWordEffects.lift(.1f,false)<LyricWordEffects.lift(.9f,false));
    }
    @Test void spatialKernelIsCenteredNormalizedAndHasNonzeroOffsets(){
        float weight=0,x=0,y=0;
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            float w=LyricWordEffects.WEIGHTS[row]*LyricWordEffects.WEIGHTS[col];
            weight+=w;x+=w*LyricWordEffects.OFFSETS[col];y+=w*LyricWordEffects.OFFSETS[row];
        }
        assertEquals(1,weight);assertEquals(0,x);assertEquals(0,y);
        assertTrue(LyricWordEffects.OFFSETS[0]<0);assertTrue(LyricWordEffects.OFFSETS[2]>0);
    }
    @Test void shearPreservesHorizontalAdvanceAndRaisesOnlyBoundedRightEdge(){
        float width=40,rise=LyricWordEffects.rise(.5f,width,false),slope=rise/width;
        var transform=new org.joml.Matrix4f().m01(-slope);
        var left=transform.transformPosition(new org.joml.Vector3f(0,0,0));
        var right=transform.transformPosition(new org.joml.Vector3f(width,0,0));
        assertEquals(0,left.x);assertEquals(width,right.x);assertEquals(-rise,right.y,0.0001f);
    }
}
