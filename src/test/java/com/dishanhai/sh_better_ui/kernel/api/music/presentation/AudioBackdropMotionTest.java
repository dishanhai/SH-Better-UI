package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioBackdropMotionTest {
    final ArtworkPalette palette=ArtworkPalette.solid(0xff334455);
    @Test void absentAudioNeverStartsAnAmbientLoop() {
        var motion=new ArtworkStageMotion();
        for(long time=0;time<10000;time+=17)assertEquals(0,motion.sample(palette,time,false,false,0).phase());
    }
    @Test void actualActivityAttacksHoldsAndReleases() {
        var motion=new ArtworkStageMotion();
        motion.sample(palette,0,false,false,0);
        double attack=motion.sample(palette,65,false,false,.8f).phase();
        assertTrue(attack>0&&attack<1.92);
        assertEquals(attack,motion.sample(palette,80,false,false,0).phase(),1e-6);
        assertTrue(motion.sample(palette,400,false,false,0).phase()<attack);
        assertEquals(0,motion.sample(palette,5000,false,false,0).phase(),.03);
    }
    @Test void preferencesSnapToStaticAndDoNotReplayOldActivity() {
        var motion=new ArtworkStageMotion();
        motion.sample(palette,0,false,false,1);
        motion.sample(palette,100,false,false,1);
        assertEquals(0,motion.sample(palette,101,true,false,1).phase());
        assertEquals(0,motion.sample(palette,200,false,false,0).phase());
        assertEquals(0,motion.sample(palette,300,false,true,1).phase());
    }
    @Test void resetAndInvalidActivityCannotKeepOldTrackMoving() {
        var motion=new ArtworkStageMotion();
        motion.sample(palette,0,false,false,1);motion.sample(palette,100,false,false,1);
        motion.reset();
        assertEquals(0,motion.sample(palette,200,false,false,Float.NaN).phase());
        assertEquals(0,motion.sample(palette,190,false,false,0).phase());
    }
}
