// Author: dishanhai. Only the current output's post-gain PCM is measured, never system capture.
package com.dishanhai.sh_better_ui.kernel.internal.music;

final class PcmActivity {
    private record Reading(float level, long at) {}
    private volatile Reading latest;
    void accept(byte[] pcm, int length, long now) {
        if (length < 0 || length > pcm.length || (length & 1) != 0) throw new IllegalArgumentException("PCM length");
        double power=0;
        for(int i=0;i<length;i+=2) {
            double sample=(short)((pcm[i]&255)|(pcm[i+1]<<8))/32768d;
            power+=sample*sample;
        }
        float rms=length==0?0:(float)Math.sqrt(power/(length/2));
        latest=new Reading(rms<.002f?0:Math.min(1f,rms*3f),now);
    }
    float sample(long now) {
        var value=latest;
        return value==null || now<value.at || now-value.at>250 ? 0f : value.level;
    }
}
