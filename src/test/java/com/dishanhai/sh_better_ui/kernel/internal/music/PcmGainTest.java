package com.dishanhai.sh_better_ui.kernel.internal.music;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PcmGainTest {
    private static int sample(byte[] data,int n){return (short)((data[n*2]&255)|(data[n*2+1]<<8));}
    @Test void unityMuteAndHalfGainPreserveSignedLittleEndianSamples(){
        short[] input={Short.MIN_VALUE,Short.MAX_VALUE,-1000,1000};byte[] out=new byte[8];
        assertEquals(8,PcmGain.write(input,0,4,2,out,1,1));
        for(int i=0;i<4;i++)assertEquals(input[i],sample(out,i));
        PcmGain.write(input,0,4,2,out,0,0);for(byte b:out)assertEquals(0,b);
        PcmGain.write(input,0,4,2,out,.5f,.5f);assertEquals(-16384,sample(out,0));assertEquals(16384,sample(out,1));
    }
    @Test void rampIsMonotoneAndStereoFramesShareGain(){
        byte[] out=new byte[8];PcmGain.write(new short[]{1000,1000,1000,1000},0,4,2,out,0,1);
        assertEquals(500,sample(out,0));assertEquals(sample(out,0),sample(out,1));assertEquals(1000,sample(out,2));assertEquals(sample(out,2),sample(out,3));
    }
    @Test void clampsWithoutAmplifyingAndRejectsInvalidValues(){
        assertEquals(0,PcmGain.normalize(-1));assertEquals(1,PcmGain.normalize(2));
        assertThrows(IllegalArgumentException.class,()->PcmGain.normalize(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->PcmGain.normalize(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->PcmGain.write(new short[2],0,2,2,new byte[1],1,1));
    }
}
