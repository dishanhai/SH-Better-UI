// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;
import static com.dishanhai.sh_better_ui.client.visual.VisualProjection.BoundsMode.RENDER;

/** Optional client effects for one rich-text viewport. The visual pass owns the targets, clip and draw order. */
public record RichTextEffects(GlassStyle glassStyle, boolean gradient, RevealProfile reveal, float lateBlurPixels,
                              int foregroundZ, long maxPixelPasses, boolean shadow) {
    private static final RevealProfile NO_REVEAL = new RevealProfile(0, 0.15f, 0.85f);
    private static final long BALANCED_BUDGET = 3_000_000L;
    private static final int WHITE = 0xffffffff;
    private static final int BLACK = 0xff000000;
    private static final int GLASS_SCRIM = 0xe20b1019;
    private static final int SOLID_SCRIM = 0xff0b1019;
    private static final int CONTRAST_SCRIM = 0xff090c12;
    private static final int GRADIENT_CENTER = 0xcc6bc4f2;
    private static final int GRADIENT_EDGE = 0x485a3691;
    private static final float STATIC_ORIGIN = 0.64f;

    public static final RichTextEffects NONE = new RichTextEffects(
            GlassStyle.NONE, false, NO_REVEAL, 0, 0, 0, false);
    public static final RichTextEffects GLASS = new RichTextEffects(
            GlassStyle.LIQUID_REGULAR, true, NO_REVEAL, 0, 0, BALANCED_BUDGET, true);
    public static final RichTextEffects REVEAL = new RichTextEffects(
            GlassStyle.LIQUID_REGULAR, true, RevealProfile.DEFAULT, 3f, 0, BALANCED_BUDGET, true);

    public RichTextEffects {
        Objects.requireNonNull(glassStyle, "glassStyle");
        Objects.requireNonNull(reveal, "reveal");
        if (!(lateBlurPixels >= 0 && lateBlurPixels <= 32) || maxPixelPasses < 0) {
            throw new IllegalArgumentException("Invalid rich text blur radius or pixel budget");
        }
    }

    /**
     * During LayoutPass.at, submit effects behind the content using the same frame and preferences as the host.
     * Draw the returned plate and document together, using VisualNodePass.foreground if deferred is true.
     */
    public Sample drawBackdrop(VisualNodePass visual, FrameViewSnapshot frame, UiPreferences preferences) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(preferences, "preferences");
        if (!preferences.highContrast() && glassStyle == GlassStyle.NONE && !gradient
                && reveal.maxRadius() == 0 && lateBlurPixels == 0 && !shadow) {
            return Sample.PLAIN;
        }

        var projection = visual.project(RENDER);
        var region = visual.localBounds();
        long area = (long) projection.pixels().getW() * projection.pixels().getH();
        var plan = plan(area, preferences, frame.nowMs());
        var skipped = projection.result() == DRAWN ? DISABLED : projection.result();
        var glassResult = plan.glass() ? visual.backdrop(0.8f, glassStyle, 7f, 7f) : skipped;
        var gradientResult = plan.gradient() ? drawGradient(visual, region, plan.originU()) : skipped;
        var revealResult = plan.reveal() ? visual.revealRegion(reveal, RevealDirection.LEFT_TO_RIGHT) : skipped;
        var lateResult = plan.lateBlur() ? visual.requestBlur(0.42f, lateBlurPixels, GlassStyle.NONE) : skipped;
        boolean highContrast = preferences.highContrast();
        boolean layered = glassResult == DRAWN || gradientResult == DRAWN || gradientResult == FALLBACK
                || revealResult == DRAWN || lateResult == ACCEPTED;
        int scrim = highContrast ? CONTRAST_SCRIM : layered ? GLASS_SCRIM : SOLID_SCRIM;
        return new Sample(region, scrim, highContrast, shadow && !highContrast, glassResult, gradientResult,
                revealResult, lateResult, plan.pixelPasses());
    }

    private static VisualEffectResult drawGradient(VisualNodePass visual, Rect region, float originU) {
        var result = visual.radialGradient(originU, 0.29f, 0.9f, GRADIENT_CENTER, GRADIENT_EDGE);
        if (result != UNAVAILABLE && result != FAILED) return result;
        visual.draw(graphics -> graphics.fillGradient(left(region), top(region), right(region), bottom(region),
                GRADIENT_CENTER, GRADIENT_EDGE));
        return FALLBACK;
    }

    /**
     * One viewport's admission estimate, in visible framebuffer pixels times nominal pass weights.
     * Glass, reveal and late blur each reserve two units per pixel. No GPU time is inferred.
     */
    RenderPlan plan(long area, UiPreferences preferences, long nowMs) {
        long capacity = area <= 0 || preferences.highContrast() ? 0 : maxPixelPasses / area;
        boolean allowGradient = gradient && capacity >= 1;
        if (allowGradient) capacity--;
        boolean allowGlass = glassStyle != GlassStyle.NONE && preferences.useBackdropBlur() && capacity >= 2;
        if (allowGlass) capacity -= 2;
        boolean allowReveal = reveal.maxRadius() > 0 && preferences.useBackdropBlur() && capacity >= 2;
        if (allowReveal) capacity -= 2;
        boolean allowLateBlur = lateBlurPixels > 0 && preferences.useBackdropBlur() && capacity >= 2;
        int passes = (allowGradient ? 1 : 0) + (allowGlass ? 2 : 0) + (allowReveal ? 2 : 0)
                + (allowLateBlur ? 2 : 0);
        float origin = STATIC_ORIGIN;
        if (allowGradient && reveal.maxRadius() > 0 && !preferences.reducedMotion()) {
            double phase = Math.floorMod(nowMs, 3600) * (2 * Math.PI / 3600);
            origin += 0.14f * (float) Math.sin(phase);
        }
        return new RenderPlan(allowGlass, allowGradient, allowReveal, allowLateBlur, area * passes, origin);
    }

    record RenderPlan(boolean glass, boolean gradient, boolean reveal, boolean lateBlur, long pixelPasses, float originU) {}

    /** A single draw's immutable effect results and foreground policy, owned by the current visual node. */
    public static final class Sample {
        private static final Sample PLAIN = new Sample(null, 0, false, false, DISABLED, DISABLED, DISABLED, DISABLED, 0);

        private final Rect region;
        private final int scrim;
        private final boolean highContrast;
        private final boolean shadow;
        private final VisualEffectResult glass;
        private final VisualEffectResult gradient;
        private final VisualEffectResult reveal;
        private final VisualEffectResult lateBlur;
        private final long plannedPixelPasses;

        private Sample(Rect region, int scrim, boolean highContrast, boolean shadow, VisualEffectResult glass,
                       VisualEffectResult gradient, VisualEffectResult reveal, VisualEffectResult lateBlur,
                       long plannedPixelPasses) {
            this.region = region;
            this.scrim = scrim;
            this.highContrast = highContrast;
            this.shadow = shadow;
            this.glass = glass;
            this.gradient = gradient;
            this.reveal = reveal;
            this.lateBlur = lateBlur;
            this.plannedPixelPasses = plannedPixelPasses;
        }

        public VisualEffectResult glass() { return glass; }
        public VisualEffectResult gradient() { return gradient; }
        public VisualEffectResult reveal() { return reveal; }
        public VisualEffectResult lateBlur() { return lateBlur; }
        public long plannedPixelPasses() { return plannedPixelPasses; }
        boolean shadow() { return shadow; }
        /** A competing late blur can also cover this viewport, so draw the foreground after it. */
        public boolean deferred() { return lateBlur == ACCEPTED || lateBlur == CONFLICT; }

        /** Draw once, before selection highlights and inline foreground, inside the chosen node-local pass. */
        public void drawReadabilityPlate(GuiGraphics graphics) {
            if (region != null && !region.empty()) {
                graphics.fill(left(region), top(region), right(region), bottom(region), scrim);
            }
        }

        /** Keeps the selection background within the same luminance bound as the plate. */
        public int selectionColor(int argb) {
            return region == null ? argb : readableSelection(argb, highContrast);
        }

        /** Keeps the caller's visual order, font, style metadata and measured positions. */
        public void drawText(GuiGraphics graphics, Font font, FormattedCharSequence sequence,
                             RichLayoutSnapshot.Rect box, int argb) {
            if (region == null) {
                graphics.drawString(font, sequence, box.x(), box.y(), argb, false);
            } else {
                graphics.drawString(font, protect(sequence, highContrast), box.x(), box.y(),
                        readableForeground(argb, highContrast), shadow);
            }
        }
    }

    // Over the brightest possible scene, the normal scrim bounds linear-light luminance below #646464.
    // This also bounds sRGB blending, which is darker for the same endpoints. Keep selection within that bound.
    static int backgroundBound(boolean highContrast) { return highContrast ? 0xff505050 : 0xff646464; }

    static int readableSelection(int argb, boolean highContrast) {
        double limit = ContrastPolicy.ratio(WHITE, backgroundBound(highContrast), BLACK);
        if (ContrastPolicy.ratio(WHITE, argb | 0xff000000, BLACK) >= limit) return argb;
        return highContrast ? 0xff26354b : 0xff304763;
    }

    static int readableForeground(int argb, boolean highContrast) {
        return ContrastPolicy.readable(argb | 0xff000000, backgroundBound(highContrast), BLACK,
                highContrast ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
    }

    static FormattedCharSequence protect(FormattedCharSequence source, boolean highContrast) {
        return output -> source.accept(new FormattedCharSink() {
            private Style previous;
            private Style protectedStyle;

            @Override public boolean accept(int index, Style style, int codePoint) {
                if (style != previous) {
                    previous = style;
                    var color = style.getColor();
                    protectedStyle = style;
                    if (color != null) {
                        int readable = readableForeground(color.getValue(), highContrast) & 0x00ffffff;
                        if (readable != color.getValue()) protectedStyle = style.withColor(readable);
                    }
                }
                return output.accept(index, protectedStyle, codePoint);
            }
        });
    }

    private static int left(Rect region) { return (int) Math.floor(region.x()); }
    private static int top(Rect region) { return (int) Math.floor(region.y()); }
    private static int right(Rect region) { return (int) Math.ceil(region.right()); }
    private static int bottom(Rect region) { return (int) Math.ceil(region.bottom()); }
}
