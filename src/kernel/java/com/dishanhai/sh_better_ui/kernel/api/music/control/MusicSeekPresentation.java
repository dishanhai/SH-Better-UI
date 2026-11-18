// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

/** Pure presentation geometry; never changes playback or the seek transaction. */
public final class MusicSeekPresentation {
    private MusicSeekPresentation() {}
    public static int bubbleX(int x,int width,int bubbleWidth,double fraction) {
        double bounded=Double.isFinite(fraction)?Math.max(0,Math.min(1,fraction)):0;
        int size=Math.max(0,Math.min(width,bubbleWidth));
        int center=x+4+(int)Math.round(Math.max(0,width-8)*bounded);
        return Math.max(x,Math.min(x+width-size,center-size/2));
    }
    /** Frame-rate independent, interruptible settling. Reduced motion uses immediate feedback. */
    public static float engagement(float current,boolean engaged,double elapsedSeconds,boolean reducedMotion) {
        float target=engaged?1:0;
        if(reducedMotion)return target;
        double dt=Double.isFinite(elapsedSeconds)?Math.max(0,Math.min(.1,elapsedSeconds)):0;
        return (float)(current+(target-current)*(1-Math.exp(-dt/0.075)));
    }
}
