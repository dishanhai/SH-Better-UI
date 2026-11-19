package com.dishanhai.sh_better_ui.kernel.internal.music;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PcmActivityTest {
    @Test void silenceAndUnobservedAudioAreStatic() {
        var activity=new PcmActivity();assertEquals(0,activity.sample(0));
        activity.accept(new byte[64],64,100);assertEquals(0,activity.sample(101));
    }
    @Test void oppositeStereoChannelsDoNotCancelAndStaleSamplesExpire() {
        var activity=new PcmActivity();
        activity.accept(new byte[]{0,32,0,-32},4,100);
        assertEquals(.75f,activity.sample(150),.001);
        assertEquals(0,activity.sample(351));assertEquals(0,activity.sample(99));
    }
    @Test void MeasuresAlreadyGainedSamplesWithoutAmplifyingAudio() {
        var activity=new PcmActivity();
        activity.accept(new byte[]{0,16},2,0);assertEquals(.375f,activity.sample(0),.001);
        activity.accept(new byte[]{0,0},2,1);assertEquals(0,activity.sample(1));
        assertThrows(IllegalArgumentException.class,()->activity.accept(new byte[4],3,2));
    }
}
