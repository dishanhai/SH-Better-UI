// Author: dishanhai.
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import java.util.Objects;

/** One stage's interruption-safe palette and slow ambient clock; no image or GPU ownership. */
public final class ArtworkStageMotion {
    public record Frame(ArtworkPalette palette,double phase) {}
    private ArtworkPalette from,target,shown;
    private double elapsed=650,phase;
    private long last;
    private double audioLevel;
    private long audioLast, holdUntil;
    private boolean audioStarted;
    public void reset() { from=target=shown=null;elapsed=650;phase=0;last=0;audioLevel=0;audioStarted=false;holdUntil=0; }
    /** Audio-driven mode: phase is an envelope, never a free-running clock. */
    public Frame sample(ArtworkPalette next,long now,boolean reducedMotion,boolean lowPerformance,float activity) {
        var colors=sample(next,now,reducedMotion,lowPerformance);
        double target=Float.isFinite(activity)?Math.max(0,Math.min(1,activity)):0;
        double dt=audioStarted && now>=audioLast?Math.min(1000,(double)now-audioLast):0;
        audioStarted=true;audioLast=now;
        if(reducedMotion || lowPerformance) {audioLevel=0;holdUntil=0;return new Frame(colors.palette(),0);}
        if(target>audioLevel) holdUntil=now+80;
        else if(now<holdUntil) target=audioLevel;
        audioLevel+=(target-audioLevel)*(1-Math.exp(-dt/(target>audioLevel?65:260)));
        if(audioLevel<.0001)audioLevel=0;
        return new Frame(colors.palette(),audioLevel*2.4);
    }
    public Frame sample(ArtworkPalette next,long now,boolean reducedMotion,boolean lowPerformance) {
        Objects.requireNonNull(next);
        if(shown==null || reducedMotion || lowPerformance) {
            from=target=shown=next;elapsed=650;last=now;return new Frame(next,phase);
        }
        long delta=now>last?now-last:0;last=now;
        double dt=Math.min(50,delta<0?50:delta);
        phase=(phase+dt*.000025)%(Math.PI*2);
        if(!next.equals(target)) { from=shown;target=next;elapsed=0; }
        else elapsed=Math.min(650,elapsed+dt);
        double t=elapsed/650;t=t*t*(3-2*t);
        shown=from.interpolate(next,t);return new Frame(shown,phase);
    }
}
