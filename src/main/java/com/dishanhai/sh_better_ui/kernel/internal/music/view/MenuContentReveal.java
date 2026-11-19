// Author: dishanhai. Fixed-geometry top-to-bottom clip, not a translated hit surface.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
final class MenuContentReveal {
    private final InterruptibleAnimation progress=new InterruptibleAnimation(Spring.PANEL);
    private boolean started,finished;
    private int visibleHeight;
    int sample(int height,long now,boolean still) {
        if(still)finish();
        if(!started&&!finished){started=true;progress.blendTo(1,now,0,160,InterruptibleAnimation.BlendCurve.SMOOTHSTEP);}
        visibleHeight=finished?height:Math.min(height,Math.max(0,(int)Math.ceil(height*progress.sample(now))));
        return visibleHeight;
    }
    boolean contains(double y,int top){return !started || finished || y>=top&&y<top+visibleHeight;}
    void finish(){finished=true;progress.snapTo(1);}
    void close(){progress.close();}
}
