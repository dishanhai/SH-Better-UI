// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricFollowMotionTest {
    @Test void slowAndFastFramesFollowTheSameMotion(){
        var slow=new LyricFollowMotion();var fast=new LyricFollowMotion();
        double a=slow.step(80,.1),b=0;
        for(int i=0;i<10;i++)b=fast.step(80,.01);
        assertEquals(a,b,.00001);
        assertTrue(a>10&&a<70);
    }

    @Test void retargetContinuesInTheSameDirectionAndEventuallySettles(){
        var motion=new LyricFollowMotion();
        double before=motion.step(80,.095);
        assertTrue(motion.step(105,.016)>before);
        assertEquals(105,motion.step(105,1),.01);
        motion.reset(13);
        assertEquals(13,motion.step(110,0));
    }

    @Test void timelineRejectsSeekJumpsButNotNormalPlaybackOrMinorDeviceJitter(){
        assertTrue(LyricFollowMotion.discontinuity(900,10,.016));
        assertTrue(LyricFollowMotion.discontinuity(500,1800,.1));
        assertFalse(LyricFollowMotion.discontinuity(2000,2150,.15));
        assertFalse(LyricFollowMotion.discontinuity(2000,1982,.016));
        assertFalse(LyricFollowMotion.discontinuity(-1,10000,.016));
    }
}
