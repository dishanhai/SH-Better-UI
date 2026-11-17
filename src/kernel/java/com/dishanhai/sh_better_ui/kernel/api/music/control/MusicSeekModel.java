// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import java.util.Objects;
import java.util.Optional;

/** UI-independent seek transaction. Snapshot updates never seek; release is the only pointer commit. */
public final class MusicSeekModel {
    public record Edit(String trackKey, long positionMs) {}
    private String trackKey;
    private long position, duration, preview;
    private boolean enabled, editing;

    /** Returns cancellation of an old edit when identity, duration or availability changes. */
    public Optional<Edit> sync(String key, long positionMs, long durationMs, boolean available) {
        boolean usable=key!=null&&!key.isBlank()&&durationMs>0&&available;
        Optional<Edit> cancelled=Optional.empty();
        if(editing&&(!usable||!Objects.equals(trackKey,key)||duration!=durationMs)) cancelled=cancel();
        trackKey=key;duration=Math.max(0,durationMs);position=clamp(positionMs);enabled=usable;
        if(!editing)preview=position;
        return cancelled;
    }
    public boolean enabled(){return enabled;}
    public boolean editing(){return editing;}
    public long durationMs(){return duration;}
    public long positionMs(){return editing?preview:position;}
    public double fraction(){return duration<=0?0:positionMs()/(double)duration;}
    public Optional<Edit> begin(double fraction){
        if(!enabled||editing||!Double.isFinite(fraction))return Optional.empty();
        editing=true;return drag(fraction);
    }
    public Optional<Edit> drag(double fraction){
        if(!editing||!enabled||!Double.isFinite(fraction))return Optional.empty();
        preview=Math.round(Math.max(0,Math.min(1,fraction))*duration);
        return Optional.of(new Edit(trackKey,preview));
    }
    public Optional<Edit> release(){
        if(!editing)return Optional.empty();editing=false;
        if(!enabled){preview=position;return Optional.empty();}
        position=preview;return Optional.of(new Edit(trackKey,preview));
    }
    public Optional<Edit> cancel(){
        if(!editing)return Optional.empty();editing=false;preview=position;
        return Optional.of(new Edit(trackKey,position));
    }
    /** Keyboard commands are atomic, bounded and never join an active pointer transaction. */
    public Optional<Edit> keyboard(long deltaMs){
        if(!enabled||editing)return Optional.empty();
        long target=deltaMs>0&&position>Long.MAX_VALUE-deltaMs?Long.MAX_VALUE:
                deltaMs<0&&position<Long.MIN_VALUE-deltaMs?Long.MIN_VALUE:position+deltaMs;
        return seekTo(target);
    }
    public Optional<Edit> seekTo(long target){
        if(!enabled||editing)return Optional.empty();
        position=preview=clamp(target);return Optional.of(new Edit(trackKey,position));
    }
    private long clamp(long value){return Math.max(0,Math.min(duration,value));}
}
