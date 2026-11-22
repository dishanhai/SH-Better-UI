package com.muoniumplayer.core.screens.ncm;

import com.dishanhai.sh_better_ui.kernel.internal.music.view.CoverFlowBackdrop;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.CoverRhythmMotion;
import com.muoniumplayer.core.ncm.music.AudioSpectrumAnalyzer;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.PlaybackPulse;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.render.skija.SkijaGfx;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

/** Adapts the fullscreen player's artwork and audio clocks to the UUK backdrop. */
final class AppleCoverBackdrop {
    private final CoverFlowBackdrop backdrop = new CoverFlowBackdrop();
    private final CoverRhythmMotion motion = new CoverRhythmMotion();
    private final int[] fallbackColors = new int[3];
    private long lastFrame;
    private long spectrumSession = Long.MIN_VALUE;
    private long spectrumSince;

    void reset() {
        lastFrame = 0;
        spectrumSession = Long.MIN_VALUE;
        spectrumSince = 0;
        motion.reset();
        backdrop.close();
    }

    boolean render(double x, double y, double width, double height, float alpha,
                   CloudMusic.PlaybackSnapshot snapshot, boolean staticMode, boolean highContrast,
                   double lyricScrollPixels) {
        Canvas canvas = SkijaGfx.canvas();
        if (canvas == null || width <= 0 || height <= 0) return false;
        long now = System.nanoTime();
        double dt = lastFrame == 0 || now - lastFrame > 400_000_000L
                ? 0 : Math.min(.1, (now - lastFrame) / 1e9);
        lastFrame = now;

        Music track = snapshot.music;
        int[] palette = track == null ? null : track.getRepresentativeColors();
        if (track != null && (palette == null || palette.length == 0)) palette = track.getCoverPalette();
        for (int i = 0; i < fallbackColors.length; i++) {
            fallbackColors[i] = AppleLyricEffects.backdropColor(palette, i);
        }

        // A mirror can report a playback clock without supplying audio to the local analyser.
        boolean playing = !staticMode && CloudMusic.isAudibleSnapshot(snapshot);
        boolean localAudio = playing && snapshot.player != null;
        long seekNanos = snapshot.player == null ? 0L : snapshot.player.getLastSeekNanos();
        float onset = localAudio ? PlaybackPulse.beat() : 0f;
        float loudness = localAudio ? PlaybackPulse.loudness() : 0f;
        if (!localAudio || spectrumSession != snapshot.sessionId || dt == 0) {
            spectrumSession = snapshot.sessionId;
            spectrumSince = now;
        }
        float low = localAudio ? lowFrequency(now, Math.max(spectrumSince, seekNanos)) : 0f;
        var response = motion.sample(dt, snapshot.sessionId, snapshot.positionMs, playing, staticMode,
                onset, loudness, low, seekNanos, lyricScrollPixels);
        backdrop.draw(canvas, Rect.makeXYWH((float) x, (float) y, (float) width, (float) height),
                alpha, track == null ? "" : track.getStableKey(), fallbackColors, now, staticMode, highContrast,
                (float) response.getPhase(), (float) response.getDeformation());
        return true;
    }

    private float lowFrequency(long now, long earliest) {
        var frame = AudioSpectrumAnalyzer.requestFrame(now);
        if (frame.capturedAt < earliest || now - frame.capturedAt >= 180_000_000L) return 0f;
        // The analyser's first ten log bands cover approximately 40–140 Hz.
        float total = 0f;
        for (int band = 0; band < 10; band++) total += frame.band(band);
        return total / 10f;
    }
}
