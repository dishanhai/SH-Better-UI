// Author: dishanhai. Deterministic presentation geometry, independent of playback ownership.
package com.dishanhai.sh_better_ui.kernel.api.music;

final class LyricWordEffects {
    private LyricWordEffects() {}
    // Fixed separable [1,2,1] kernel. Spatial samples, not an opacity-only approximation.
    static final float[] OFFSETS={-1,0,1};
    static final float[] WEIGHTS={.25f,.5f,.25f};
    static boolean blurFuture(long position,long lineStart,boolean current,boolean reading,
                              boolean reducedMotion,boolean highContrast){
        return position>=0&&lineStart>position&&!current&&!reading&&!reducedMotion&&!highContrast;
    }
    static float lift(float fraction,boolean reduce){
        return reduce?0:.45f*Math.max(0,Math.min(1,fraction*3));
    }
    /** A vertical shear keeps every advance/x position unchanged and caps the raised right edge. */
    static float rise(float fraction,float width,boolean reduce){
        if(reduce||!(width>0))return 0;
        return Math.min(.55f,width*.018f)*Math.max(0,Math.min(1,fraction*3));
    }
}
