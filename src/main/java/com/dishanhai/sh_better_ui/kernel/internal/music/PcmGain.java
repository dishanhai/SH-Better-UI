// Author: dishanhai. Software gain for decoded signed-16 PCM, independent of mixer support.
package com.dishanhai.sh_better_ui.kernel.internal.music;

final class PcmGain {
    private PcmGain() {}
    static float normalize(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Volume must be finite");
        return Math.max(0f, Math.min(1f, value));
    }
    /** Smooth each block's gain transition, sharing gain between channels of a frame. */
    static int write(short[] source, int from, int to, int channels, byte[] target, float previous, float requested) {
        if (channels <= 0 || from < 0 || to < from || to > source.length || (to-from)%channels != 0
                || target.length < (to-from)*2) throw new IllegalArgumentException("Invalid PCM bounds");
        previous=normalize(previous);requested=normalize(requested);
        int frames=(to-from)/channels, length=0;
        for (int frame=0; frame<frames; frame++) {
            float gain=previous+(requested-previous)*(frame+1)/(float)frames;
            for (int channel=0; channel<channels; channel++) {
                short value=(short)Math.round(source[from+frame*channels+channel]*gain);
                target[length++]=(byte)value;target[length++]=(byte)(value>>8);
            }
        }
        return length;
    }
}
