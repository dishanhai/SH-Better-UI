package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

/** Playback-driven phase; paused, reduced-motion and inactive time never accumulate into a jump. */
public final class VinylMotion {
    private long previous=-1;
    private float angle;
    public float sample(long now,boolean playing,boolean reducedMotion) {
        if(previous>=0&&playing&&!reducedMotion)
            angle=(angle+Math.min(100,Math.max(0,now-previous))*.018f)%360;
        previous=now;
        return angle;
    }
    public void reset(){previous=-1;angle=0;}
}
