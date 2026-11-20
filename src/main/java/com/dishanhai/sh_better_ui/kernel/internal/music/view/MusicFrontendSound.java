// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlinePlaybackOccurrence;

/** An editor for the borrowed player's own settings, not a second playback session. */
final class MusicFrontendSound {
    enum Result { APPLIED, CHANGED, UNAVAILABLE, BUSY }

    record State(boolean volumeAvailable, int volume, boolean crossfadeAvailable, int crossfade,
                 boolean speedAvailable, double speed, boolean eqAvailable, OnlineMusicEqualizerPreset eq,
                 boolean qqAvailable, OnlineMusicSoundEffect qq, OnlineTrack track, long occurrence) {
        boolean sameOccurrence(State next) { return track == next.track && occurrence == next.occurrence; }
    }

    // The workspace can be reopened around the same background player. Keep only weak keys,
    // and use a quiet restoration level for a session that was muted elsewhere before we saw it.
    private static final java.util.Map<OnlineMusicPlayer, Float> AUDIBLE = new java.util.WeakHashMap<>();
    private final OnlineMusicPlayer player;

    MusicFrontendSound(OnlineMusicPlayer player) { this.player = player; }

    State read() {
        synchronized (player) {
            var media = player.snapshot();
            boolean open = media.state() != OnlineMusicPlayer.State.CLOSED;
            float volume = player.volume();
            if (open && player.supportsVolume() && volume > 0f) {
                synchronized (AUDIBLE) { AUDIBLE.put(player, volume); }
            }
            return new State(open && player.supportsVolume(), volume == 0f ? 0 : Math.max(1, Math.round(volume * 100)),
                    open && player.supportsCrossfade(), player.crossfadeSeconds(),
                    open && player.supportsPlaybackRate(), player.playbackRate(),
                    open && player.supportsEqualizer(), player.equalizerPreset(),
                    open && player.supportsSoundEffects(), player.soundEffect(), media.track(),
                    OnlinePlaybackOccurrence.generation(player));
        }
    }

    Result volume(int percent) {
        return update(null, s -> s.volumeAvailable && percent >= 0 && percent <= 100, () -> player.volume(percent / 100f));
    }

    Result toggleMute(State expected) {
        return update(null, s -> s.volumeAvailable && expected.volume == s.volume,
                () -> {
                    float gain;
                    synchronized (AUDIBLE) { gain = AUDIBLE.getOrDefault(player, .25f); }
                    player.volume(player.volume() > 0f ? 0f : gain);
                });
    }

    Result fade(int seconds) {
        return update(null, s -> s.crossfadeAvailable && seconds >= 0 && seconds <= 12,
                () -> player.crossfadeSeconds(seconds));
    }

    Result speed(State expected, double value) {
        return update(expected, s -> s.speedAvailable && PlaybackRateChoices.VALUES.contains(value),
                () -> player.playbackRate(value));
    }

    Result eq(State expected, OnlineMusicEqualizerPreset value) {
        return update(expected, s -> s.eqAvailable, () -> player.equalizerPreset(value));
    }

    Result qq(State expected, OnlineMusicSoundEffect value) {
        return update(expected, s -> s.qqAvailable, () -> player.soundEffect(value));
    }

    private Result update(State expected, java.util.function.Predicate<State> canApply, Runnable setter) {
        synchronized (player) {
            var now = read();
            if (expected != null && !expected.sameOccurrence(now)) return Result.CHANGED;
            if (!canApply.test(now)) return Result.UNAVAILABLE;
            try {
                setter.run();
                // The native rate setter can accept a restart and then surface its output error in the snapshot.
                if (expected != null && player.snapshot().state() == OnlineMusicPlayer.State.FAILED)
                    return Result.UNAVAILABLE;
                return Result.APPLIED;
            } catch (OnlineMusicQueue.TransitionInProgress busy) { return Result.BUSY; }
            catch (UnsupportedOperationException unavailable) { return Result.UNAVAILABLE; }
        }
    }
}
