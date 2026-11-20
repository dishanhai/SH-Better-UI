// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendSound.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendSoundTest {
    @Test void adjustsOnlyTheBorrowedSessionAndRestoresExactAudibleGain() {
        var player = new Fake();
        var editor = new MusicFrontendSound(player.api);
        player.gain = 0.2345f;
        assertEquals(APPLIED, editor.toggleMute(editor.read()));
        assertEquals(0, player.gain);
        var reopened = new MusicFrontendSound(player.api);
        assertEquals(APPLIED, reopened.toggleMute(reopened.read()));
        assertEquals(0.2345f, player.gain);
        var externallyMuted = new Fake();
        externallyMuted.gain = 0f;
        var unseen = new MusicFrontendSound(externallyMuted.api);
        assertEquals(APPLIED, unseen.toggleMute(unseen.read()));
        assertEquals(.25f, externallyMuted.gain);
        assertEquals(APPLIED, editor.volume(65));
        assertEquals(.65f, player.gain);
        assertEquals(UNAVAILABLE, editor.volume(101));
        assertEquals(.65f, player.gain);
    }

    @Test void aNewTrackOrCapabilityChangeCannotReceiveAnOldNativeEffectSelection() {
        var player = new Fake();
        var editor = new MusicFrontendSound(player.api);
        var nativeQQ = editor.read();
        player.track = new OnlineTrack("apple", "2", "2", "Browser handoff", "", "", 1000);
        player.dsp = false;
        assertEquals(CHANGED, editor.speed(nativeQQ, 1.5));
        assertEquals(CHANGED, editor.qq(nativeQQ, OnlineMusicSoundEffect.SUPER_BASS));
        assertEquals(UNAVAILABLE, editor.eq(editor.read(), OnlineMusicEqualizerPreset.BASS_FOCUS));
        assertEquals(UNAVAILABLE, editor.speed(editor.read(), 1.5));
        assertEquals(UNAVAILABLE, editor.qq(editor.read(), OnlineMusicSoundEffect.SUPER_BASS));
        assertEquals(0, player.writes);
        // Configuring a future native overlap is not the same as affecting the browser.
        assertEquals(APPLIED, editor.fade(5));
        assertEquals(5, player.fade);
        assertEquals(UNAVAILABLE, editor.fade(13));
    }

    @Test void nativeSpeedHandlesAnAudibleHandoffAndReportsRealChanges() {
        var player = new Fake();
        var editor = new MusicFrontendSound(player.api);
        player.busy = true;
        assertEquals(BUSY, editor.speed(editor.read(), .75));
        assertEquals(1d, player.speed);
        player.busy = false;
        assertEquals(APPLIED, editor.speed(editor.read(), .75));
        assertEquals(.75, editor.read().speed());
        assertEquals(APPLIED, editor.eq(editor.read(), OnlineMusicEqualizerPreset.BASS_FOCUS));
        assertEquals(APPLIED, editor.qq(editor.read(), OnlineMusicSoundEffect.SUPER_BASS));
        assertEquals(OnlineMusicSoundEffect.SUPER_BASS, editor.read().qq());
        player.failRate = true;
        assertEquals(UNAVAILABLE, editor.speed(editor.read(), 2d));
        player.state = OnlineMusicPlayer.State.CLOSED;
        assertFalse(editor.read().volumeAvailable());
        assertEquals(UNAVAILABLE, editor.volume(10));
    }

    @Test void narrowAndWideGeometryLeaveRoomForFooterWithoutOverlap() {
        for (int width : new int[]{208, 320, 560, 860, 1400}) {
            for (int height : new int[]{240, 320, 600}) {
                var layout = MusicFrontendLayout.compute(new Bounds(17, 20, width, height), MusicFrontendLayout.Route.SETTINGS, false);
                var panel = MusicFrontendSoundPanel.fit(layout.body());
                assertTrue(panel.x() > layout.body().x());
                assertTrue(panel.right() < layout.body().right());
                assertTrue(panel.width() <= 440);
                assertEquals(layout.body().height(), panel.height());
                assertTrue(MusicFrontendSoundPanel.rowCapacity(panel.height()) >= 2);
                assertTrue(MusicFrontendSoundPanel.rowCapacity(panel.height()) * 23 + 22 <= panel.height());
            }
        }
    }

    private static final class Fake {
        OnlineTrack track = new OnlineTrack("qq", "1", "1", "Native", "", "", 1000);
        OnlineMusicPlayer.State state = OnlineMusicPlayer.State.PLAYING;
        OnlineMusicEqualizerPreset eq = OnlineMusicEqualizerPreset.FLAT;
        OnlineMusicSoundEffect effect = OnlineMusicSoundEffect.OFF;
        boolean dsp = true, busy, failRate;
        float gain = .75f;
        double speed = 1;
        int fade, writes;
        final OnlineMusicPlayer api = (OnlineMusicPlayer) Proxy.newProxyInstance(OnlineMusicPlayer.class.getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "snapshot" -> new OnlineMusicPlayer.Snapshot(track, state, 0, 1000,
                            OnlineMusicPlayer.LyricsState.EMPTY, null, OnlineMusicPlayer.Failure.NONE, false);
                    case "supportsVolume", "supportsCrossfade" -> true;
                    case "supportsPlaybackRate", "supportsEqualizer", "supportsSoundEffects" -> dsp;
                    case "volume" -> {
                        if (args == null) yield gain;
                        gain = (float) args[0]; writes++; yield null;
                    }
                    case "crossfadeSeconds" -> {
                        if (args == null) yield fade;
                        fade = (int) args[0]; writes++; yield null;
                    }
                    case "playbackRate" -> {
                        if (args == null) yield speed;
                        if (busy) throw new OnlineMusicQueue.TransitionInProgress();
                        if (failRate) state = OnlineMusicPlayer.State.FAILED;
                        speed = (double) args[0]; writes++; yield null;
                    }
                    case "equalizerPreset" -> {
                        if (args == null) yield eq;
                        eq = (OnlineMusicEqualizerPreset) args[0]; writes++; yield null;
                    }
                    case "soundEffect" -> {
                        if (args == null) yield effect;
                        effect = (OnlineMusicSoundEffect) args[0]; writes++; yield null;
                    }
                    default -> throw new AssertionError(method);
                });
    }
}
