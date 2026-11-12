// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.PublishedNode;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.VisualReveal;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;
import static com.dishanhai.sh_better_ui.client.visual.VisualProjection.BoundsMode.RENDER;

/** Effects attached to one immutable published node. Valid only during LayoutPass.at. */
public final class VisualNodePass {
    private final VisualFrameCoordinator.Frame frame;
    private final Node node;
    private final PublishedNode entry;
    private VisualProjection renderProjection;
    private VisualProjection visualProjection;
    private boolean live = true;

    VisualNodePass(VisualFrameCoordinator.Frame frame, Node node, PublishedNode entry) {
        this.frame = frame;
        this.node = node;
        this.entry = entry;
    }

    public View view() { requireLive(); return entry.view(); }
    public Rect localBounds() { requireLive(); return entry.localRender(); }

    public VisualProjection project(VisualProjection.BoundsMode mode) {
        requireLive();
        return switch (mode) {
            case RENDER -> {
                if (renderProjection == null) renderProjection = projectBounds(mode);
                yield renderProjection;
            }
            case VISUAL -> {
                if (visualProjection == null) visualProjection = projectBounds(mode);
                yield visualProjection;
            }
        };
    }

    private VisualProjection projectBounds(VisualProjection.BoundsMode mode) {
        var projection = frame.target.project(entry.view(), mode, frame.snapshot.clip());
        if (mode == VisualProjection.BoundsMode.VISUAL && projection.result() == EMPTY_REGION
                && !entry.view().transform().map(entry.localVisual()).empty()) {
            return new VisualProjection(projection.area(), projection.visible(), projection.pixels(), FULLY_CLIPPED);
        }
        return projection;
    }

    /** Immediate ordinary drawing. Graphics has a node-local pose and a screen-space scissor. */
    public void draw(Consumer<GuiGraphics> draw) {
        requireLive();
        Objects.requireNonNull(draw);
        VisualCanvas.draw(frame.graphics, frame.target, frame.snapshot.clip(), entry.view(),
                graphics -> ThemeEffects.preview(frame.settings,
                        () -> UiTheme.preview(frame.palette, () -> draw.accept(graphics))));
    }

    public VisualEffectResult backdrop(float strength, GlassStyle style, float radiusGui, float cornerGui) {
        return backdrop(RENDER, strength, style, radiusGui, cornerGui);
    }

    public VisualEffectResult backdrop(VisualProjection.BoundsMode mode, float strength, GlassStyle style,
                                       float radiusGui, float cornerGui) {
        requireLive();
        Objects.requireNonNull(style);
        if (!(strength >= 0 && strength <= 1 && radiusGui >= 0 && radiusGui <= 32)
                || !Float.isFinite(cornerGui) || cornerGui < 0) throw new IllegalArgumentException("Invalid backdrop parameters");
        var projection = project(mode);
        if (projection.result() != DRAWN) return projection.result();
        if (!frame.preferences.useBackdropBlur() || !frame.glass.enabled() || frame.glass.strength() <= 0.01f
                || strength <= 0.01f) return DISABLED;
        var bounds = localBounds(mode);
        var area = new GuiRect((float) bounds.x(), (float) bounds.y(), (float) bounds.width(), (float) bounds.height());
        return immediate(graphics -> ShBetterUiApi.blur().tryBackdrop(graphics, area, strength, style,
                radiusGui, cornerGui, frame.glass) ? DRAWN : UNAVAILABLE);
    }

    /** Registers a postprocess on this frame. Radius is measured in physical pixels. */
    public VisualEffectResult requestBlur(float strength, float radiusPixels, GlassStyle style) {
        return requestBlur(RENDER, strength, radiusPixels, style);
    }

    public VisualEffectResult requestBlur(VisualProjection.BoundsMode mode, float strength, float radiusPixels,
                                          GlassStyle style) {
        RenderSystem.assertOnRenderThread();
        if (!frame.binding.isActive()) return INVALID_SCOPE;
        if (!live || !frame.acceptsNodes()) return STALE_FRAME;
        VisualFrameCoordinator.validateBlur(com.dishanhai.sh_better_ui.client.glass.PixelRect.EMPTY,
                strength, radiusPixels, style);
        var projection = project(mode);
        return projection.result() == DRAWN
                ? frame.request(frame.binding, frame.target, projection.pixels(), strength, radiusPixels, style)
                : projection.result();
    }

    /** Apply progressive clarity to already drawn pixels in the original render rectangle. */
    public VisualEffectResult revealRegion(RevealProfile profile, RevealDirection direction) {
        return revealRegion(RENDER, profile, direction);
    }

    public VisualEffectResult revealRegion(VisualProjection.BoundsMode mode, RevealProfile profile,
                                           RevealDirection direction) {
        requireLive();
        Objects.requireNonNull(profile);
        Objects.requireNonNull(direction);
        var projection = project(mode);
        if (projection.result() != DRAWN) return projection.result();
        var effective = profile == RevealProfile.DEFAULT ? frame.reveal : profile;
        if (!frame.preferences.useBackdropBlur() || !frame.glass.enabled() || frame.glass.strength() <= 0.01f
                || frame.glass.blurRadiusScale() <= 0f || effective.maxRadius() == 0f) return DISABLED;
        var area = frame.target.edges(entry.view().transform().map(localBounds(mode)));
        var transform = entry.view().transform();
        var grid = frame.target.grid();
        float radius = (float) (effective.maxRadius() * Math.min(transform.xx() * grid.pixelsPerGuiX(),
                transform.yy() * grid.pixelsPerGuiY()));
        return immediate(graphics -> {
            boolean drawn = ShBetterUiApi.blur().revealPixelsNow(graphics, area, radius,
                    effective.fullBlurUntil(), effective.sharpFrom(), direction);
            return drawn ? DRAWN : UNAVAILABLE;
        });
    }

    /** Bounds are node-local GUI units and may be fractional. */
    public VisualEffectResult revealTexture(ResourceLocation texture, Rect bounds, RevealProfile profile,
                                            RevealDirection direction) {
        requireLive();
        Objects.requireNonNull(texture);
        Objects.requireNonNull(profile);
        Objects.requireNonNull(direction);
        var effective = profile == RevealProfile.DEFAULT ? frame.reveal : profile;
        var result = visibility(bounds);
        return result != DRAWN ? result : immediate(graphics -> VisualReveal.drawTextureChecked(graphics, texture,
                bounds, effective, direction, frame.glass, frame.preferences.useBackdropBlur()));
    }

    /** Radius is a multiple of the local distance from the center to the farthest corner. */
    public VisualEffectResult radialGradient(float originU, float originV, float radius, int innerArgb, int outerArgb) {
        requireLive();
        VisualReveal.validateGradient(originU, originV, radius);
        var bounds = entry.localRender();
        var result = visibility(bounds);
        return result != DRAWN ? result : immediate(graphics -> VisualReveal.drawRadialGradient(
                graphics, bounds, originU, originV, radius, innerArgb, outerArgb));
    }

    public void foreground(int z, Consumer<DrawContext> draw) {
        foreground(z, draw, null);
    }

    void foreground(int z, Consumer<DrawContext> draw, Consumer<GuiGraphics> afterClip) {
        requireLive();
        frame.foreground.add(new VisualFrameCoordinator.Foreground(node, z, Objects.requireNonNull(draw), afterClip));
    }

    Consumer<RenderService.Context> serviceForeground(BiConsumer<GuiGraphics, RenderService.Context> draw,
                                                       Consumer<GuiGraphics> afterClip) {
        requireLive();
        var snapshot = frame.snapshot;
        return context -> VisualFrameCoordinator.drawNodeForeground(frame, snapshot, node,
                visual -> draw.accept(visual.graphics(), context), afterClip);
    }

    private Rect localBounds(VisualProjection.BoundsMode mode) {
        return mode == RENDER ? entry.localRender() : entry.localVisual();
    }

    private VisualEffectResult visibility(Rect bounds) {
        if (bounds.empty()) return EMPTY_REGION;
        return entry.view().transform().map(bounds).intersect(VisualCanvas.clip(frame.snapshot.clip(), entry.view())).empty()
                ? FULLY_CLIPPED : DRAWN;
    }

    private VisualEffectResult immediate(Function<GuiGraphics, VisualEffectResult> draw) {
        var result = new VisualEffectResult[]{FULLY_CLIPPED};
        try {
            draw(graphics -> result[0] = draw.apply(graphics));
            return result[0];
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            frame.report(node, "immediate effect", failure);
            return FAILED;
        }
    }

    private void requireLive() {
        RenderSystem.assertOnRenderThread();
        if (!live || !frame.acceptsNodes()) throw new IllegalStateException("Visual node pass has expired");
    }

    void expire() { live = false; }

    /** This graphics reference is valid only inside the current foreground callback. */
    public static final class DrawContext {
        private final FrameViewSnapshot snapshot;
        private final PublishedNode node;
        private final UiPreferences preferences;
        private GuiGraphics graphics;

        DrawContext(FrameViewSnapshot snapshot, PublishedNode node, UiPreferences preferences, GuiGraphics graphics) {
            this.snapshot = snapshot;
            this.node = node;
            this.preferences = preferences;
            this.graphics = graphics;
        }

        public FrameViewSnapshot snapshot() { return snapshot; }
        public View view() { return node.view(); }
        public Rect localBounds() { return node.localRender(); }
        public Rect clip() { return VisualCanvas.clip(snapshot.clip(), node.view()); }
        public UiPreferences preferences() { return preferences; }
        public GuiGraphics graphics() {
            RenderSystem.assertOnRenderThread();
            if (graphics == null) throw new IllegalStateException("Foreground graphics has expired");
            return graphics;
        }

        void expire() { graphics = null; }
    }
}
