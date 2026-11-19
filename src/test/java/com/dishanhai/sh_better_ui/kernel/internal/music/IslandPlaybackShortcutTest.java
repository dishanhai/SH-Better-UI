// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService.*;
import static com.dishanhai.sh_better_ui.kernel.internal.music.IslandPlaybackShortcut.*;
import static org.junit.jupiter.api.Assertions.*;

final class IslandPlaybackShortcutTest {
    private static final String ONLINE = "sh_better_ui:online_music";
    private static final BackgroundTarget TARGET = new BackgroundTarget("session", "track");

    @Test void ignoresRepeatReleaseHiddenWorldAndAmbiguousBindings() {
        assertNull(select(false, true, key -> key == NEXT));
        assertNull(select(true, false, key -> key == NEXT));
        assertNull(select(true, true, key -> key == NEXT || key == PREVIOUS));
        assertNull(select(true, true, key -> false));
        assertEquals(NEXT, select(true, true, key -> key == NEXT));
    }

    @Test void noControlsForClosedHiddenStaleOrExternalSessions() {
        var player = new Transport();
        var current = sample(ONLINE, "session", "track", 10_000, 60_000);
        assertFalse(PLAY_PAUSE.dispatch(player, null, current, 0));
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, sample(ONLINE, "session", "different", 0, 10_000), 0));
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, sample(ONLINE, "different", "track", 0, 10_000), 0));
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, sample("system", "session", "track", 0, 10_000), 0));
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, new MusicSnapshot(ONLINE, MusicSnapshot.State.CLOSED, null), 0));
        assertEquals(0, player.queries);
        player.controls = null;
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, current, 0));
        player.controls = new BackgroundControls(new BackgroundTarget("new", "track"), true, false, true, true, true);
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, current, 0));
        assertEquals(0, player.commands.size());
    }

    @Test void usesCurrentCapabilitiesAndClampsSeekAtBothEnds() {
        var player = new Transport();
        var paused = sample(ONLINE, "session", "track", 1_500, 60_000);
        assertTrue(PLAY_PAUSE.dispatch(player, TARGET, paused, 0));
        assertEquals(BackgroundCommand.RESUME, player.last());
        player.controls = new BackgroundControls(TARGET, true, false, true, true, true);
        assertTrue(PLAY_PAUSE.dispatch(player, TARGET, paused, 0));
        assertEquals(BackgroundCommand.PAUSE, player.last());
        assertTrue(NEXT.dispatch(player, TARGET, paused, 0));
        assertEquals(BackgroundCommand.NEXT, player.last());
        player.controls = new BackgroundControls(TARGET, false, false, true, false, true);
        assertFalse(PLAY_PAUSE.dispatch(player, TARGET, paused, 0));
        assertFalse(NEXT.dispatch(player, TARGET, paused, 0));
        assertTrue(PREVIOUS.dispatch(player, TARGET, paused, 0));
        assertEquals(BackgroundCommand.PREVIOUS, player.last());
        assertTrue(SEEK_BACK.dispatch(player, TARGET, paused, 0));
        assertEquals(0, player.fraction);
        assertTrue(SEEK_FORWARD.dispatch(player, TARGET, sample(ONLINE, "session", "track", 58_000, 60_000), 0));
        assertEquals(1, player.fraction);
        assertTrue(SEEK_FORWARD.dispatch(player, TARGET, sample(ONLINE, "session", "track", 20_000, 60_000), 0));
        assertEquals(.5, player.fraction);
        assertFalse(SEEK_FORWARD.dispatch(player, TARGET, sample(ONLINE, "session", "track", -1, 60_000), 0));
        assertFalse(SEEK_FORWARD.dispatch(player, TARGET, sample(ONLINE, "session", "track", 1000, 0), 0));
    }

    private static MusicSnapshot sample(String provider, String session, String track, long position, long duration) {
        return new MusicSnapshot(provider, MusicSnapshot.State.AVAILABLE,
                new MusicSnapshot.Session(session, MusicSnapshot.Track.of(track, "Title", null, null),
                        MusicSnapshot.PlaybackStatus.PAUSED, OptionalLong.of(position), OptionalLong.of(duration),
                        OptionalDouble.empty(), 0, null, Set.of()));
    }

    private static final class Transport implements OnlineMusicService {
        BackgroundControls controls = new BackgroundControls(TARGET, false, true, true, true, true);
        final List<BackgroundCommand> commands = new ArrayList<>();
        int queries;
        double fraction;

        @Override public Optional<BackgroundControls> backgroundControls(BackgroundTarget target) {
            queries++;
            return Optional.ofNullable(controls);
        }

        @Override public boolean controlBackground(BackgroundTarget target, BackgroundCommand command, double fraction) {
            assertEquals(TARGET, target);
            this.fraction = fraction;
            commands.add(command);
            return true;
        }

        BackgroundCommand last() { return commands.get(commands.size() - 1); }
        @Override public OnlineMusicPlayer open() { throw new AssertionError("No implicit player start"); }
        @Override public OnlineMusicPlayer open(List<OnlineMusicSource> sources) { throw new AssertionError("No implicit player start"); }
        @Override public OnlineMusicView view() { throw new AssertionError("No implicit view"); }
        @Override public OnlineMusicView view(OnlineMusicPlayer player) { throw new AssertionError("No implicit view"); }
    }
}
