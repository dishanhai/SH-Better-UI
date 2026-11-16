// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.util.Objects;
import java.util.OptionalLong;
/** Captures what was rendered; asynchronous lyric replacement must not retarget a click. */
record LyricSeekTarget(OnlineTrack track, LyricTimeline timeline, long startMs, long offsetMs) {
    OptionalLong position(OnlineMusicPlayer.Snapshot current) {
        if (track == null || !Objects.equals(track, current.track()) || timeline != current.lyrics()
                || current.lyricsState() != OnlineMusicPlayer.LyricsState.READY || current.durationMs() <= 0
                || !(current.state() == OnlineMusicPlayer.State.PLAYING || current.state() == OnlineMusicPlayer.State.PAUSED
                    || current.state() == OnlineMusicPlayer.State.ENDED)) return OptionalLong.empty();
        return OptionalLong.of(Math.max(0, startMs - offsetMs));
    }
}
