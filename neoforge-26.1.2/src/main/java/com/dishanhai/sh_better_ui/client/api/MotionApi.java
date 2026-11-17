// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.audio.AudioBand;
import com.dishanhai.sh_better_ui.client.audio.MusicMotion;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.RepeatCurve;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.SpringTable;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAttention;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiKeyframeAnimation;
import java.util.List;

/** Renderer-independent motion factories. Every call creates view-owned state; sample with a monotonic clock. This is the supported 26.x subset of the shared API. */
public final class MotionApi {
    MotionApi() {}

    public MusicMotion musicMotion() {
        return new MusicMotion();
    }

    public MusicMotion musicMotion(AudioBand band, float attackMs, float releaseMs) {
        return new MusicMotion(band, attackMs, releaseMs);
    }

    public MusicRhythm musicRhythm() {
        return new MusicRhythm();
    }

    public UiKeyframeAnimation keyframes(List<UiKeyframeAnimation.Keyframe> frames) {
        return new UiKeyframeAnimation(frames);
    }

    public UiTimeline timeline(int fps, int durationFrames) {
        return new UiTimeline(fps, durationFrames);
    }

    public InterruptibleAnimation animation(Spring.Preset preset) {
        return new InterruptibleAnimation(preset);
    }

    public InterruptibleAnimation animation(Spring.Preset preset, float initial) {
        return new InterruptibleAnimation(preset, initial);
    }

    public Spring spring(Spring.Preset preset, float initial) {
        return new Spring(preset, initial);
    }

    public <K> SpringTable<K> springTable(Spring.Preset preset) {
        return new SpringTable<>(preset);
    }

    public RepeatCurve repeatCurve(long initialDelayMs, long startIntervalMs, double factor, long minIntervalMs) {
        return new RepeatCurve(initialDelayMs, startIntervalMs, factor, minIntervalMs);
    }

    public UiAttention attention() {
        return new UiAttention();
    }

    public float clamp01(float value) {
        return GuiMotion.clamp01(value);
    }

    public float lerp(float from, float to, float progress) {
        return GuiMotion.lerp(from, to, progress);
    }

    public int lerpColor(int from, int to, float progress) {
        return GuiMotion.lerpColor(from, to, progress);
    }

    public float panelScale(float progress) {
        return GuiMotion.panelScale(progress);
    }

    public long staggerDelay(int index) {
        return GuiMotion.staggerDelay(index);
    }

    public float scrollBlur(float velocity) {
        return GuiMotion.scrollBlur(velocity);
    }
}
