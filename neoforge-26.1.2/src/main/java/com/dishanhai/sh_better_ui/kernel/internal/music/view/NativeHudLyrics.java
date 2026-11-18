// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicLyricsModel;
import java.util.List;

/** Reuses the bounded, word-timed player projection without creating a player page. */
public final class NativeHudLyrics {
    private NativeHudLyrics() {}
    public static List<MusicLyricsModel.Line> lines(LyricTimeline timeline) { return OnlineLyricPresentation.lines(timeline); }
}
