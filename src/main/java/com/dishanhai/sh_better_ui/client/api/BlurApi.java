package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GlassGeometry;
import com.dishanhai.sh_better_ui.client.glass.GlassMotion;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.gui.scaled.VisualReveal;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** 山海：即时毛玻璃、帧末后处理和渐进清晰。像素入口以 framebuffer 左上角为原点。 */
public final class BlurApi {
    BlurApi() {}

    public void allowScreen(Predicate<Screen> guard) {
        GaussBlur.allowScreen(guard);
    }

    public boolean isAllowed(Screen screen) {
        return GaussBlur.isAllowed(screen);
    }

    public void warmUp() {
        GlassBlur.warmUp();
    }

    public boolean isReady() {
        return GaussBlur.isReady();
    }

    public void backdrop(GuiGraphics graphics, GuiRect area) {
        GlassBlur.backdrop(graphics, area);
    }

    public void backdrop(GuiGraphics graphics, GuiRect area, float strength) {
        GlassBlur.backdrop(graphics, area, strength);
    }

    public void backdrop(GuiGraphics graphics, GuiRect area, float strength, GlassStyle style, float radius) {
        GlassBlur.backdrop(graphics, area, strength, style, radius);
    }

    public boolean roundedBackdrop(GuiGraphics graphics, GuiRect area, GlassStyle style, float radius, float corner) {
        return GlassBlur.roundedBackdrop(graphics, area, style, radius, corner);
    }

    public boolean tryBackdrop(GuiGraphics graphics, GuiRect area, float strength, GlassStyle style,
                               float radius, float corner) {
        return GlassBlur.tryBackdrop(graphics, area, strength, style, radius, corner);
    }

    public boolean tryBackdrop(GuiGraphics graphics, GuiRect area, float strength, GlassStyle style,
                               float radius, float corner, GlassBackdropSettings settings) {
        return GlassBlur.tryBackdrop(graphics, area, strength, style, radius, corner, settings);
    }

    public void requestPage(GuiGraphics graphics, GuiRect viewport, GlassMotion.Sample sample) {
        GlassBlur.requestPage(graphics, viewport, sample);
    }

    public boolean pageSlideNow(GuiGraphics graphics, GuiRect viewport, GlassMotion.Sample sample) {
        return GlassBlur.pageSlideNow(graphics, viewport, sample);
    }

    public void requestPopup(GuiGraphics graphics, GuiRect area, float openAmount) {
        GlassBlur.requestPopup(graphics, area, openAmount);
    }

    public boolean revealRegion(GuiGraphics graphics, GuiRect area, RevealProfile profile) {
        if (profile == RevealProfile.DEFAULT) profile = ThemeEffects.profile("reveal", profile);
        return GlassBlur.revealRegion(graphics, area, profile.maxRadius(), profile.fullBlurUntil(), profile.sharpFrom());
    }

    public boolean revealRegion(GuiGraphics graphics, GuiRect area, RevealProfile profile, RevealDirection direction) {
        if (profile == RevealProfile.DEFAULT) profile = ThemeEffects.profile("reveal", profile);
        return GlassBlur.revealRegion(graphics, area, profile.maxRadius(), profile.fullBlurUntil(), profile.sharpFrom(),
                direction);
    }

    public void drawRevealTexture(GuiGraphics graphics, ResourceLocation texture, Bounds area, RevealProfile profile) {
        VisualReveal.drawTexture(graphics, texture, area.x(), area.y(), area.width(), area.height(), profile);
    }

    public void drawRadialGradient(GuiGraphics graphics, Bounds area) {
        VisualReveal.drawRadialGradient(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public void drawRadialGradient(GuiGraphics graphics, Bounds area, float originU, float originV,
                                   int innerArgb, int outerArgb) {
        VisualReveal.drawRadialGradient(graphics, area.x(), area.y(), area.width(), area.height(), originU, originV,
                innerArgb, outerArgb);
    }

    /** Registers one current native Screen primary. Pixels are top-left framebuffer coordinates. */
    public com.dishanhai.sh_better_ui.client.visual.VisualEffectResult requestManagedPixels(
            GuiGraphics graphics, PixelRect area, float strength, float radiusPixels, GlassStyle style) {
        return com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator.requestPixels(
                graphics, area, strength, radiusPixels, style);
    }

    /** 帧末请求每帧重交。没有挖空区域时传入 {@link PixelRect#EMPTY}。 */
    public void requestPixels(PixelRect area, float strength, float radiusPixels, PixelRect hole, GlassStyle style) {
        GaussBlur.request(area.getX(), area.getY(), area.getW(), area.getH(), strength, radiusPixels,
                hole.getX(), hole.getY(), hole.getW(), hole.getH(), style);
    }

    public void requestSmoothPixels(PixelRect area, float strength, float radiusPixels, PixelRect hole,
                                    GlassStyle style) {
        GaussBlur.requestSmooth(area.getX(), area.getY(), area.getW(), area.getH(), strength, radiusPixels,
                hole.getX(), hole.getY(), hole.getW(), hole.getH(), style);
    }

    public void requestSmoothCells(int[] xywh, int count, float strength, float radiusPixels, GlassStyle style) {
        GaussBlur.requestSmoothCells(xywh, count, strength, radiusPixels, style);
    }

    public void requestExtraPixels(PixelRect area, float strength, float radiusPixels, PixelRect hole, GlassStyle style,
                                    float featherPixels) {
        GaussBlur.requestExtra(area.getX(), area.getY(), area.getW(), area.getH(), strength, radiusPixels,
                hole.getX(), hole.getY(), hole.getW(), hole.getH(), style, featherPixels);
    }

    public void requestDirectionalPixels(PixelRect area, float strength, float radiusPixels, float axisX, float axisY,
                                          GlassStyle style) {
        GaussBlur.requestDirectional(area.getX(), area.getY(), area.getW(), area.getH(), strength, radiusPixels,
                axisX, axisY, style);
    }

    public void requestWavePixels(PixelRect area, float strength, float kernelPixels, float radiusPixels,
                                  float thicknessPixels, float refractPixels, float glow) {
        GaussBlur.requestWave(area.getX(), area.getY(), area.getW(), area.getH(), strength, kernelPixels,
                radiusPixels, thicknessPixels, refractPixels, glow);
    }

    public void setWaveOrigin(float originU, float originV) {
        GaussBlur.setWaveOrigin(originU, originV);
    }

    public boolean hasWaveRequest() {
        return GaussBlur.hasWaveRequest();
    }

    public boolean blurPixelsNow(GuiGraphics graphics, PixelRect area, float strength, float radiusPixels,
                                 GlassStyle style, float cornerPixels) {
        return GaussBlur.blurNow(graphics, area.getX(), area.getY(), area.getW(), area.getH(), strength, radiusPixels,
                style, cornerPixels);
    }

    public boolean sampleBackdropPixelsNow(GuiGraphics graphics, float x, float y, float width, float height,
                                           float strength, float radiusPixels, GlassStyle style,
                                           float cornerPixels, float yToXScale, GlassBackdropSettings settings) {
        return GaussBlur.sampleBackdropNow(graphics, x, y, width, height, strength, radiusPixels, style, cornerPixels,
                yToXScale, settings);
    }

    public boolean revealPixelsNow(GuiGraphics graphics, PixelRect area, float radiusPixels,
                                   float fullBlurUntil, float sharpFrom, RevealDirection direction) {
        return GaussBlur.revealNow(graphics, area.getX(), area.getY(), area.getW(), area.getH(), radiusPixels,
                fullBlurUntil, sharpFrom, direction);
    }

    public boolean directionalPixelsNow(GuiGraphics graphics, PixelRect area, float strength, float radiusPixels,
                                        float axisX, float axisY) {
        return GaussBlur.directionalNow(graphics, area.getX(), area.getY(), area.getW(), area.getH(), strength,
                radiusPixels, axisX, axisY);
    }

    public void clearRequests() {
        GaussBlur.clear();
    }

    public void deferTopLayer(Screen owner, Consumer<GuiGraphics> draw) {
        GaussBlur.deferTopLayer(owner, draw);
    }

    public void clearTopLayer() {
        GaussBlur.clearTopLayer();
    }

    public GuiRect applyAffine(GuiRect area, float scaleX, float scaleY, float x, float y) {
        return GlassGeometry.applyAffine(area, scaleX, scaleY, x, y);
    }

    public GuiRect expand(GuiRect area, float margin) {
        return GlassGeometry.expand(area, margin);
    }

    public PixelRect toPixels(GuiRect area, int framebufferWidth, int framebufferHeight, int guiWidth, int guiHeight) {
        return GlassGeometry.toPixels(area, framebufferWidth, framebufferHeight, guiWidth, guiHeight);
    }
}
