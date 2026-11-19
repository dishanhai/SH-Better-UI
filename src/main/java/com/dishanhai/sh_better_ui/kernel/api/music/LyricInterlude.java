// Author: dishanhai. Interlude indicators use real gaps, not synthesized word timing.
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;

record LyricInterlude(int next, float opacity, float progress) {
    private static final long MIN_PAUSE=2400, FADE=280;
    static LyricInterlude at(List<MusicLyricsModel.Line> lines,long position) {
        if(position<0||lines.isEmpty())return null;
        int lo=0,hi=lines.size();
        while(lo<hi){
            int mid=(lo+hi)>>>1;
            if(lines.get(mid).startMillis()<=position)lo=mid+1;else hi=mid;
        }
        if(lo>=lines.size()||lines.get(lo).text().isBlank())return null;
        long start=lo==0?0:lines.get(lo-1).endMillis();
        long end=lines.get(lo).startMillis();
        if(start<0||position<start||end-start<MIN_PAUSE)return null;
        float progress=(float)((double)(position-start)/(end-start));
        float opacity=LyricWordEffects.smooth((float)(position-start)/FADE)
                *LyricWordEffects.smooth((float)(end-position)/FADE);
        return new LyricInterlude(lo,opacity,progress);
    }
}
