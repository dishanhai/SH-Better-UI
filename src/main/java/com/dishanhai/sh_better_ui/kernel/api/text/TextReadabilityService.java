// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.function.Consumer;

/** Client-thread text policy and foreground contract for module-owned Screen trees. */
public interface TextReadabilityService extends UIService {
    /** Replaces this node's field overrides. An inherited field follows the enclosing policy. */
    void configure(ModuleContext module, ScopeHandle scope, TextReadabilityPolicy policy);

    /**
     * Establishes an independent popup render layer. Its theme supplies defaults. A non-null owning page
     * applies that page's overrides before the popup's overrides. The owning page must be on the same Screen.
     */
    void configurePopup(ModuleContext module, ScopeHandle popup, ThemeScope theme, ScopeHandle owningPage,
                        TextReadabilityPolicy policy);

    TextReadabilityPolicy.Resolved policy(ModuleContext module, ScopeHandle scope);

    /** Draws in the current content pass, or submits to its layer's foreground when protection is enabled. */
    Result submit(ModuleContext module, Request request);

    /** Schedules a callback after this layer's background effects, in Z and registration order. */
    Result foreground(ModuleContext module, ScopeHandle scope, int z, Consumer<RenderService.Context> draw);

    /**
     * Runs an attached popup during the native overlay pass. Its foreground is painted before returning, while the
     * caller's enclosing clip is active. The content callback draws local backgrounds and immediate effects.
     */
    void renderPopup(ModuleContext module, ScopeHandle popup, GuiGraphics graphics, Runnable content);

    /** Only a live context from {@link #foreground} for this frame and the exact request scope can draw. */
    Result drawNow(ModuleContext module, RenderService.Context context, Request request);

    /** The last decision for this stable ID, while its scope and decision are current. */
    Optional<Diagnostic> diagnostics(ModuleContext module, ScopeHandle scope, TextId text);

    /** Requests asynchronous GPU detail for stable IDs in this owned subtree until disabled or closed. */
    void setGpuDiagnostics(ModuleContext module, ScopeHandle scope, boolean enabled);

    /** Frame totals from the preceding native frame. GPU timing may describe an earlier completed frame. */
    SamplingStatistics samplingStatistics(ModuleContext module);

    /** Releases text state when a virtualized row drops or replaces its current data item. */
    void retireDataItem(ModuleContext module, ScopeHandle scope, UiId dataItem);

    /**
     * A visual-order run from the caller's existing layout, with its draw origin and measured bounds in local GUI units.
     * Supply individual runs for separate lines, and for separate colors when local color is disabled.
     * The local color policy can measure clusters directly in a native visual sequence. A null font uses the
     * Minecraft client font.
     */
    record Run(FormattedCharSequence glyphs, double x, double y, Rect bounds, double lineHeight, Font font) {
        public Run {
            Objects.requireNonNull(glyphs, "glyphs");
            Objects.requireNonNull(bounds, "bounds");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(lineHeight) || lineHeight <= 0) {
                throw new IllegalArgumentException("Invalid positioned text run");
            }
        }

        public Run(FormattedCharSequence glyphs, double x, double y, Rect bounds, double lineHeight) {
            this(glyphs, x, y, bounds, lineHeight, null);
        }
    }

    /** Inline glyph styles are retained by the run; argb is the caller's original base color. */
    record Style(int argb, boolean shadow, TextReadabilityPolicy.Usage usage,
                 boolean lockHue, boolean allowOutline, boolean allowBacking) {
        public Style { Objects.requireNonNull(usage, "usage"); }

        public Style(int argb, boolean shadow, TextReadabilityPolicy.Usage usage) {
            this(argb, shadow, usage, false, true, true);
        }
    }

    /** The transform maps local to screen GUI units. Clip is the effective ancestor/viewport clip in screen GUI units. */
    record Request(ScopeHandle scope, TextId text, Run run, Style style, Transform transform, Rect clip,
                   float groupOpacity, Integer declaredBackgroundArgb, int foregroundZ, FrameViewSnapshot snapshot,
                   SamplingHint sampling, TextReadabilityPolicy policyOverride) {
        public Request {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(clip, "clip");
            Objects.requireNonNull(sampling, "sampling");
            Objects.requireNonNull(policyOverride, "policyOverride");
            if (!Float.isFinite(groupOpacity) || groupOpacity < 0 || groupOpacity > 1) {
                throw new IllegalArgumentException("Text group opacity must be in [0, 1]");
            }
            if (declaredBackgroundArgb != null && (declaredBackgroundArgb >>> 24) != 255) {
                throw new IllegalArgumentException("The declared representative background must be opaque");
            }
        }

        public Request(ScopeHandle scope, TextId text, Run run, Style style, Transform transform, Rect clip,
                       float groupOpacity) {
            this(scope, text, run, style, transform, clip, groupOpacity, null, 0);
        }

        public Request(ScopeHandle scope, TextId text, Run run, Style style, Transform transform, Rect clip,
                       float groupOpacity, Integer declaredBackgroundArgb) {
            this(scope, text, run, style, transform, clip, groupOpacity, declaredBackgroundArgb, 0);
        }

        public Request(ScopeHandle scope, TextId text, Run run, Style style, Transform transform, Rect clip,
                       float groupOpacity, Integer declaredBackgroundArgb, int foregroundZ) {
            this(scope, text, run, style, transform, clip, groupOpacity, declaredBackgroundArgb, foregroundZ, null);
        }

        public Request(ScopeHandle scope, TextId text, Run run, Style style, Transform transform, Rect clip,
                       float groupOpacity, Integer declaredBackgroundArgb, int foregroundZ, FrameViewSnapshot snapshot) {
            this(scope, text, run, style, transform, clip, groupOpacity, declaredBackgroundArgb, foregroundZ,
                    snapshot, SamplingHint.DEFAULT, TextReadabilityPolicy.inherit());
        }

        public Request withSampling(SamplingHint hint) {
            return new Request(scope, text, run, style, transform, clip, groupOpacity, declaredBackgroundArgb,
                    foregroundZ, snapshot, hint, policyOverride);
        }

        public Request withPolicy(TextReadabilityPolicy overrides) {
            return new Request(scope, text, run, style, transform, clip, groupOpacity, declaredBackgroundArgb,
                    foregroundZ, snapshot, sampling, overrides);
        }

        public Request withStyle(Style replacement) {
            return new Request(scope, text, run, replacement, transform, clip, groupOpacity, declaredBackgroundArgb,
                    foregroundZ, snapshot, sampling, policyOverride);
        }

        /** Uses the transform and effective viewport/ancestor clip of the same presented node. */
        public static Request forVisual(ScopeHandle scope, TextId text, Run run, Style style,
                                        FrameViewSnapshot snapshot, AlignmentLayout.Node node, float groupOpacity,
                                        Integer declaredBackgroundArgb, int foregroundZ) {
            return new Request(scope, text, run, style, snapshot.view(node).transform(), snapshot.clip(node),
                    groupOpacity, declaredBackgroundArgb, foregroundZ, snapshot);
        }
    }

    /** Interaction hints describe the owning control. AUTO hints retain the page's explicit-only restriction. */
    enum SamplingHint {
        DEFAULT, IMPORTANT, HOVERED, FOCUSED, DECLARED_ONLY, AUTO_HOVERED, AUTO_FOCUSED;

        public boolean explicitlyMarked() {
            return this == IMPORTANT || this == HOVERED || this == FOCUSED;
        }
    }

    enum SamplingStatus {
        DISABLED, DECLARED_ONLY, EXPLICIT_ONLY, BUDGET_EXHAUSTED, UNAVAILABLE, INVALID_REGION, GPU,
        /** At least one cluster of the submitted run used the shared GPU backdrop. */
        LOCAL_GPU
    }

    enum DetailStatus { OFF, PENDING, READY, BUSY, EXPIRED, INVALIDATED }

    /** Positions use top-left framebuffer coordinates. Luminance is linear. */
    record SamplePoint(int x, int y, double luminance, double outlineCoverage) {}

    record GpuDetails(DetailStatus status, long sourceFrame, long pendingFrame, int attemptedSamples,
                      TextReadabilityReference.Decision decision, List<SamplePoint> samples) {
        public GpuDetails { samples = List.copyOf(samples); }
    }

    record SamplingStatistics(long sourceFrame, int analyzedRuns, int attemptedSamples, int copiedPixels,
                              int budgetFallbacks, int unavailableFallbacks, int invalidRegions,
                              long cpuNanos, long gpuSourceFrame, int timedGpuRuns, long gpuNanos,
                              long allocatedGpuBytes, long peakGpuBytes, int liveStateSlots) {}

    enum Result {
        SUBMITTED, DRAWN, CLIPPED, HIDDEN, INVALID_STAGE, SCOPE_CLOSED, INVALID_REQUEST, RENDER_UNAVAILABLE,
        QUEUE_CLOSED, ALREADY_CONSUMED, STALE_LAYOUT, RENDER_FAILED
    }

    /** The source frame is the UUK frame counter. The decision is present when adaptive color evaluated this run. */
    record Diagnostic(long sourceFrame, long scopeGeneration, long renderLayerGeneration, TextId text, Result result,
                      TextReadabilityPolicy.Resolved policy, double targetContrast, Rect visibleBounds,
                      TextReadabilityReference.Decision decision, SamplingStatus sampling, Rect effectiveClip,
                      GpuDetails gpu) {
        public Diagnostic(long sourceFrame, long scopeGeneration, long renderLayerGeneration, TextId text, Result result,
                          TextReadabilityPolicy.Resolved policy, double targetContrast, Rect visibleBounds,
                          TextReadabilityReference.Decision decision) {
            this(sourceFrame, scopeGeneration, renderLayerGeneration, text, result, policy, targetContrast,
                    visibleBounds, decision, SamplingStatus.DISABLED, visibleBounds,
                    new GpuDetails(DetailStatus.OFF, -1, -1, 0, null, List.of()));
        }
    }
}
