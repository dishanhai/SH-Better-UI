package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.audio.AudioBand;
import com.dishanhai.sh_better_ui.client.audio.MusicMotion;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.glass.GlassMotion;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.ClickRipple;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.FluidMorph;
import com.dishanhai.sh_better_ui.client.gui.scaled.GlassEffectMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.GlassEffectRenderer;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWaves;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.RepeatCurve;
import com.dishanhai.sh_better_ui.client.gui.scaled.ShapeWaves;
import com.dishanhai.sh_better_ui.client.gui.scaled.ShapeWavesProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.SpringTable;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAttention;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRecipePageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import com.dishanhai.sh_better_ui.client.gui.text.TextLayoutMotion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** 山海：时间轴、切页、连续动画和局部动效。每个工厂调用创建独立状态。 */
public final class MotionApi {
    MotionApi() {}

    public MusicMotion musicMotion() {
        return new MusicMotion();
    }

    public MusicMotion musicMotion(AudioBand band, float attackMs, float releaseMs) {
        return new MusicMotion(band, attackMs, releaseMs);
    }

    /** 独立的多频段律动状态，输入一帧音频即可同时驱动多层视觉。 */
    public MusicRhythm musicRhythm() {
        return new MusicRhythm();
    }

    public long nowMs() {
        return PageTransition.nowMs();
    }

    /** Each adaptive label or text block keeps its own transition state. */
    public TextLayoutMotion textLayout() {
        return new TextLayoutMotion();
    }

    public PageTransition pageTransition() {
        return new PageTransition();
    }

    public UiPageSlide pageSlide() {
        return new UiPageSlide();
    }

    public void drawPageSlide(GuiGraphics graphics, Bounds viewport, UiPageSlide.Frame frame, UiPreferences preferences,
                              UiPageSlide.Effects effects, Runnable drawPage) {
        UiPageSlide.draw(graphics, viewport, frame, preferences, effects, drawPage);
    }

    public UiRecipePageTransition recipeTransition() {
        return new UiRecipePageTransition();
    }

    public void drawRecipeTransition(GuiGraphics graphics, Bounds viewport, UiRecipePageTransition.Frame frame,
                                     UiPreferences preferences, Runnable outgoing, Runnable incoming) {
        UiRecipePageTransition.draw(graphics, viewport, frame, preferences, outgoing, incoming);
    }

    public UiCapsuleReveal capsuleReveal() {
        return new UiCapsuleReveal();
    }

    public UiScreenReturn screenReturn(Screen owner) {
        return new UiScreenReturn(owner);
    }

    /** 在旧页仍显示时创建独立的屏幕交接。进入新页后用 renderCrossfade 绘制底层。 */
    public UiScreenReturn screenCrossfade(Screen owner, Screen previous, UiPreferences preferences) {
        var transition = screenReturn(owner);
        transition.beginHandoff(previous, preferences);
        return transition;
    }

    public GuiRect capsuleSeed(float centerX, float centerY) {
        return FluidMorph.seed(centerX, centerY);
    }

    public FluidMorph fluidMorph(GuiRect capsule, GuiRect panel, float panelRadius, float progress) {
        return FluidMorph.between(capsule, panel, panelRadius, progress);
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

    public Spring openingSpring(Spring.Preset preset) {
        return Spring.opening(preset, nowMs());
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

    public ClickRipple clickRipple() {
        return new ClickRipple();
    }

    public void drawRipple(GuiGraphics graphics, Bounds area, ClickRipple ripple, long nowMs,
                           boolean reducedMotion, int argb) {
        GlassEffectRenderer.drawRipple(graphics, area.x(), area.y(), area.width(), area.height(), ripple,
                nowMs, reducedMotion, argb);
    }

    public void drawFlowBorder(GuiGraphics graphics, Bounds area, long nowMs, boolean reducedMotion, int argb) {
        GlassEffectRenderer.drawFlowBorder(graphics, area.x(), area.y(), area.width(), area.height(), nowMs,
                reducedMotion, argb);
    }

    public void drawBreathingGlow(GuiGraphics graphics, Bounds area, float focusAmount, long nowMs,
                                  boolean reducedMotion, int argb) {
        GlassEffectRenderer.drawBreathingGlow(graphics, area.x(), area.y(), area.width(), area.height(), focusAmount,
                nowMs, reducedMotion, argb);
    }

    public UiDotMatrix dotMatrix() {
        return new UiDotMatrix();
    }

    public UiDotMatrix dotMatrix(DotMatrixProfile profile) {
        return new UiDotMatrix(profile);
    }

    public PatternWaves patternWaves() {
        return new PatternWaves();
    }

    public PatternWaves patternWaves(PatternWavesProfile profile) {
        return new PatternWaves(profile);
    }

    public ShapeWaves shapeWaves() {
        return new ShapeWaves();
    }

    public ShapeWaves shapeWaves(ShapeWavesProfile profile) {
        return new ShapeWaves(profile);
    }

    public GlassMotion.Sample pageBlurSample(float progress) {
        return GlassMotion.pageSlide(progress);
    }

    public float pageBlurSpeed(float progress) {
        return GlassMotion.speed(progress);
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

    public float phase(long nowMs, long periodMs) {
        return GlassEffectMotion.phase(nowMs, periodMs);
    }

    public float flowIntensity(float position, float phase, boolean reducedMotion) {
        return GlassEffectMotion.flowIntensity(position, phase, reducedMotion);
    }

    public float breath(long nowMs, boolean reducedMotion) {
        return GlassEffectMotion.breath(nowMs, reducedMotion);
    }

    public int withOpacity(int argb, float amount) {
        return GlassEffectMotion.withOpacity(argb, amount);
    }
}
