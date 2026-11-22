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
    private double ambientPhase;

    void reset() {
        lastFrame = 0;
        spectrumSession = Long.MIN_VALUE;
        spectrumSince = 0;
        ambientPhase = 0;
        motion.reset();
        backdrop.close();
    }

    boolean render(double x, double y, double width, double height, float alpha,
                   CloudMusic.PlaybackSnapshot snapshot, boolean staticMode, boolean highContrast) {
        Canvas canvas = SkijaGfx.canvas();
        if (canvas == null || width <= 0 || height <= 0) return false;
        long now = System.nanoTime();
        double dt = lastFrame == 0 || now - lastFrame > 400_000_000L
                ? 0 : Math.min(.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (!staticMode) ambientPhase += .34 * dt;

        Music track = snapshot.music;
        int[] palette = track == null ? null : track.getRepresentativeColors();
        if (track != null && (palette == null || palette.length == 0)) palette = track.getCoverPalette();
        for (int i = 0; i < fallbackColors.length; i++) {
            fallbackColors[i] = AppleLyricEffects.backdropColor(palette, i);
        }

        // A mirror can report a playback clock without supplying audio to the local analyser.
        boolean playing = !staticMode && snapshot.player != null && CloudMusic.isAudibleSnapshot(snapshot);
        float onset = playing ? PlaybackPulse.beat() : 0f;
        float loudness = playing ? PlaybackPulse.loudness() : 0f;
        if (!playing || spectrumSession != snapshot.sessionId || dt == 0) {
            spectrumSession = snapshot.sessionId;
            spectrumSince = now;
        }
        float low = playing ? lowFrequency(now) : 0f;
        var response = motion.sample(dt, snapshot.sessionId, snapshot.positionMs, playing, staticMode, onset, loudness, low);
        backdrop.draw(canvas, Rect.makeXYWH((float) x, (float) y, (float) width, (float) height),
                alpha, track == null ? "" : track.getStableKey(), fallbackColors, now, staticMode, highContrast,
                (float) (ambientPhase + .55 * response.getPhase()), (float) response.getDeformation());
        return true;
    }

    private float lowFrequency(long now) {
        var frame = AudioSpectrumAnalyzer.requestFrame(now);
        if (frame.capturedAt < spectrumSince || now - frame.capturedAt >= 180_000_000L) return 0f;
        // The analyser's first ten log bands cover approximately 40–140 Hz.
        float total = 0f;
        for (int band = 0; band < 10; band++) total += frame.band(band);
        return total / 10f;
    }
}
