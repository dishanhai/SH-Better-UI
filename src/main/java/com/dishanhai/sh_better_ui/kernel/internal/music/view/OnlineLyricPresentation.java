// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.util.ArrayList;
import java.util.List;

/** Converts the audible device snapshot, never elapsed wall time, into lyric presentation state. */
final class OnlineLyricPresentation {
    private OnlineLyricPresentation() {}
    static long position(OnlineMusicPlayer.Snapshot snapshot) {
        if(snapshot.track()==null||snapshot.lyricsState()!=OnlineMusicPlayer.LyricsState.READY)return -1;
        return switch(snapshot.state()) {
            case PLAYING,PAUSED,ENDED -> Math.max(0,snapshot.durationMs()>0?Math.min(snapshot.positionMs(),snapshot.durationMs()):snapshot.positionMs());
            default -> -1;
        };
    }
    static long songPosition(long clipPosition,long offset) {
        if(clipPosition<0)return -1;
        return clipPosition+Math.min(Math.max(0,offset),Long.MAX_VALUE-clipPosition);
    }
    static boolean seekable(OnlineMusicPlayer.Snapshot snapshot) {
        return position(snapshot)>=0&&snapshot.durationMs()>0;
    }
    private static String bounded(String text) {
        if(text.length()<=MusicLyricsModel.MAX_LINE_CHARACTERS)return text;
        int end=MusicLyricsModel.MAX_LINE_CHARACTERS;if(Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end);
    }
    static List<MusicLyricsModel.Line> lines(LyricTimeline timeline) {
        var lines=new ArrayList<MusicLyricsModel.Line>();long characters=0;
        for(int i=0;i<Math.min(timeline.size(),MusicLyricsModel.MAX_LINES);i++) {
            var line=timeline.line(i);String text=bounded(line.text()),translation=bounded(line.translation()),speaker=bounded(line.speaker());
            var words=new ArrayList<MusicLyricsModel.Word>();
            if(text.equals(line.text())&&line.words().size()<=MusicLyricsModel.MAX_LINE_CHARACTERS) {
                for(var word:line.words())if(word.text().length()<=MusicLyricsModel.MAX_LINE_CHARACTERS)
                    words.add(new MusicLyricsModel.Word(word.text(),word.startMs(),word.endMs()));
            }
            long size=text.length()+translation.length()+speaker.length()+words.stream().mapToLong(w->w.text().length()).sum();
            if(characters+size>1_048_576)break;characters+=size;
            lines.add(new MusicLyricsModel.Line(text,line.startMs(),line.endMs(),words,translation,speaker));
        }
        return List.copyOf(lines);
    }
}
