// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

/** Pure visual policy. Playback fractions are derived only from supplied provider timestamps. */
final class LyricPresentation {
    private LyricPresentation() {}
    static float wordFraction(long position,long start,long end){
        if(position<0||position<=start)return 0;
        if(position>=end)return 1;
        if(end<=start)return 0;
        return (float)((double)(position-start)/(end-start));
    }
    static float approach(float value,float target,double seconds,boolean reducedMotion){
        if(reducedMotion)return target;
        return value+(target-value)*(float)(1-Math.exp(-18*Math.max(0,Math.min(.05,seconds))));
    }
    /** Current-word masks must use the actual secondary ink, not the near-active row fade. */
    static int pendingInk(boolean current,int muted,int surrounding){return current?muted:surrounding;}
    static float distanceFade(int distance){return Math.min(.72f,.36f+Math.abs(distance)*.12f);}
}
