// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Renderer-independent, immutable-input lyric timing and interaction state. No synthesized word timing. */
public final class MusicLyricsModel {
    public static final int MAX_LINES=4096, MAX_LINE_CHARACTERS=4096;
    public record Line(String text,long startMillis,long endMillis) {
        public Line {
            Objects.requireNonNull(text);
            if(text.length()>MAX_LINE_CHARACTERS||startMillis< -1||endMillis< -1
                    ||(startMillis>=0&&endMillis>=0&&endMillis<startMillis))
                throw new IllegalArgumentException("Invalid lyric line");
        }
        public Line(String text,long startMillis){this(text,startMillis,-1);}
        public static Line unsynchronized(String text){return new Line(text,-1,-1);}
    }
    private List<Line> lines=List.of();
    private Object key;
    private boolean synchronizedLyrics,seekable,browsing;
    private long position=-1;
    private int focus=-1;
    public void content(Object key,List<Line> lines){
        Objects.requireNonNull(lines);
        if(lines.size()>MAX_LINES)throw new IllegalArgumentException("Too many lyric lines");
        var copy=List.copyOf(lines);
        long characters=0;for(var line:copy)characters+=line.text().length();
        if(characters>1_048_576)throw new IllegalArgumentException("Lyric document too large");
        if(Objects.equals(this.key,key)&&this.lines.equals(copy))return;
        this.key=key;this.lines=copy;focus=-1;browsing=false;position=-1;seekable=false;
        long previous=-1; synchronizedLyrics=!copy.isEmpty();
        for(var line:copy){if(line.startMillis()<0||line.startMillis()<previous)synchronizedLyrics=false;previous=line.startMillis();}
    }
    public Object contentKey(){return key;}
    public List<Line> lines(){return lines;}
    public boolean synchronizedLyrics(){return synchronizedLyrics;}
    public void playback(long positionMillis,boolean seekable){position=Math.max(-1,positionMillis);this.seekable=seekable;}
    public int activeIndex(){
        if(!synchronizedLyrics)return -1;
        int low=0,high=lines.size()-1,result=-1;
        while(low<=high){int mid=(low+high)>>>1;if(lines.get(mid).startMillis()<=position){result=mid;low=mid+1;}else high=mid-1;}
        if(result>=0&&lines.get(result).endMillis()>=0&&position>=lines.get(result).endMillis())return -1;
        return result;
    }
    public boolean browsing(){return browsing;}
    public void browse(){browsing=true;}
    public void follow(){browsing=false;focus=-1;}
    public int focusedIndex(){return focus;}
    public void focus(int index){if(lines.isEmpty()){focus=-1;return;}focus=Math.max(0,Math.min(lines.size()-1,index));browsing=true;}
    public void moveFocus(int delta){focus((focus<0?Math.max(0,activeIndex()):focus)+delta);}
    public OptionalLong seekTarget(int index){
        if(!seekable||!synchronizedLyrics||index<0||index>=lines.size())return OptionalLong.empty();
        return OptionalLong.of(lines.get(index).startMillis());
    }
}
