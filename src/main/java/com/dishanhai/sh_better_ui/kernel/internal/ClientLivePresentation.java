// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Content;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Snapshot;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Text;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService.Plan;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService.View;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.*;
import com.dishanhai.sh_better_ui.kernel.internal.live.LiveSurfacePainter;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Interruption;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Motion;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Target;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Layout, motion and native painting of the kernel's single presentation plan. */
final class ClientLivePresentation {
    private static final Target<Content> HIDDEN = new Target<>(SurfacePresentation.HIDDEN, SurfaceGeometry.HIDDEN, null);
    private static final Target<Content> SUSPENDED = new Target<>(SurfacePresentation.SUSPENDED, SurfaceGeometry.HIDDEN, null);
    private final KernelHost host;
    private final ClientSpatial spatial;
    private final LiveSurfaceRuntime live;
    private final ClientLiveInput input;
    private final List<Rect> bossThisFrame = new ArrayList<>();
    private List<Rect> bossLastFrame = List.of();
    private List<Rect> occupied = List.of();
    private SurfaceTransition<Content> first = new SurfaceTransition<>();
    private SurfaceTransition<Content> second = new SurfaceTransition<>();
    private LiveSurfaceSettings.Motion spring;
    private Motion appliedMotion = Motion.FULL;
    private LiveSurfacePainter painter;
    private SpatialScene scene;
    private ModuleContext module;
    private Rect viewport = new Rect(0, 0, 0, 0);
    private SurfaceTokens tokens = SurfaceTokens.DARK;
    private Result placement = Result.HIDDEN;
    private SplitSizing splitSizing;
    private LayoutKey layoutKey;
    private Target<Content> firstTarget = HIDDEN, secondTarget = HIDDEN;
    private Snapshot plannedFirst, plannedSecond;
    private SpatialStyle.Settings sceneSettings;
    private long resourceVersion = -1;
    private int expensiveFrames, reducedFrames;
    private boolean scoreboard, renderFailed;

    ClientLivePresentation(KernelHost host, ClientSpatial spatial, LiveSurfaceRuntime live, ClientLiveInput input) {
        this.host = host;
        this.spatial = spatial;
        this.live = live;
        this.input = input;
    }

    void initialize(ModuleContext module) { this.module = module; }

    void beginFrame() {
        if (!bossLastFrame.equals(bossThisFrame)) bossLastFrame = List.copyOf(bossThisFrame);
        bossThisFrame.clear();
    }

    void scoreboard(boolean present) { scoreboard = present; }

    void boss(int x, int y, int titleWidth) {
        int width = Math.max(182, titleWidth) + 8;
        if (bossThisFrame.size() < 16) bossThisFrame.add(new Rect(x + 91 - width / 2d, y - 13, width, 23));
    }

    void measure(Minecraft minecraft, LiveSurfaceSettings settings, long resources) {
        long start = System.nanoTime();
        if (resourceVersion != resources) {
            resourceVersion = resources;
            renderFailed = false;
        }
        var plan = live.plan();
        plannedFirst = plan.primary();
        plannedSecond = plan.secondary();
        var overlay = minecraft.screen == null ? null : UiOverlayHost.current(minecraft.screen);
        if (!settings.layout().enabled() || !minecraft.isWindowActive() || minecraft.getOverlay() != null
                || overlay != null && overlay.isModalOpen()
                || minecraft.screen == null && minecraft.options.hideGui) {
            firstTarget = settings.layout().enabled() ? SUSPENDED : HIDDEN;
            secondTarget = firstTarget;
            return;
        }
        if (plannedFirst == null) {
            firstTarget = secondTarget = HIDDEN;
            placement = Result.HIDDEN;
            layoutKey = null;
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth(), height = minecraft.getWindow().getGuiScaledHeight();
        viewport = new Rect(6, 6, Math.max(0, width - 12), Math.max(0, height - 12));
        tokens = palette(settings.theme());
        var content = content(plannedFirst);
        var side = plannedSecond == null ? null : content(plannedSecond);
        occupied = collectObstacles(minecraft, width, height);
        var key = new LayoutKey(viewport, occupied, settings.layout(), tokens, plan.requested(), metrics(content),
                side == null ? null : metrics(side), resources);
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            placement = solve(minecraft.font, plan, content, side, settings.layout());
            live.layoutMeasured(System.nanoTime() - start);
        }
        prepareTargets(content, side, settings.layout());
    }

    private Result solve(Font font, Plan plan, Content firstContent, Content secondContent, LiveSurfaceSettings.Layout layout) {
        var candidates = new ArrayList<Candidate>(4);
        double scale = layout.scale();
        var firstTokens = LiveSurfacePainter.effective(firstContent, tokens);
        splitSizing = null;
        if (plan.requested() == SurfacePresentation.EXPANDED || plan.requested() == SurfacePresentation.FLOATING) {
            var presentation = plan.requested() == SurfacePresentation.FLOATING || layout.anchor() == LiveSurfaceSettings.Anchor.BOTTOM_CENTER
                    ? SurfacePresentation.FLOATING : SurfacePresentation.EXPANDED;
            var measured = LiveSurfacePainter.measure(font, firstContent, presentation, firstTokens);
            candidates.add(candidate(presentation, measured, layout.anchor(), scale, firstTokens));
        } else if (plan.requested() == SurfacePresentation.SPLIT && secondContent != null) {
            var sideTokens = LiveSurfacePainter.effective(secondContent, tokens);
            var left = LiveSurfacePainter.measure(font, firstContent, SurfacePresentation.SPLIT, firstTokens);
            var right = LiveSurfacePainter.measure(font, secondContent, SurfacePresentation.SPLIT, sideTokens);
            int gap = 2 + Math.max(firstTokens.dimensions().gap(), sideTokens.dimensions().gap());
            splitSizing = new SplitSizing(left, right, Math.max(gap, gap * scale));
            var measured = new LiveSurfacePainter.Measured(left.width() + right.width() + gap, Math.max(left.height(), right.height()),
                    (float) (left.minWidth() + right.minWidth() + splitSizing.gap), Math.max(left.minHeight(), right.minHeight()));
            candidates.add(candidate(SurfacePresentation.SPLIT, measured, layout.anchor(), scale, firstTokens));
        }
        if (layout.compact() && plan.requested() != SurfacePresentation.MINIMAL) {
            var compact = LiveSurfacePainter.measure(font, firstContent, SurfacePresentation.COMPACT, firstTokens);
            candidates.add(candidate(SurfacePresentation.COMPACT, compact, layout.anchor(), scale, firstTokens));
        }
        var minimal = LiveSurfacePainter.measure(font, firstContent, SurfacePresentation.MINIMAL, firstTokens);
        candidates.add(candidate(SurfacePresentation.MINIMAL, minimal, layout.anchor(), scale, firstTokens));
        return SurfacePlacement.place(viewport, candidates, occupied, CollisionPolicy.AVOID, placement);
    }

    private Candidate candidate(SurfacePresentation mode, LiveSurfacePainter.Measured size,
                                LiveSurfaceSettings.Anchor anchor, double scale, SurfaceTokens appearance) {
        double width = Math.max(size.minWidth(), size.width() * scale);
        double height = Math.max(size.minHeight(), size.height() * scale);
        double x = switch (anchor) {
            case TOP_LEFT -> viewport.x();
            case TOP_RIGHT -> viewport.right() - width;
            default -> viewport.x() + (viewport.width() - width) / 2;
        };
        double y = anchor == LiveSurfaceSettings.Anchor.BOTTOM_CENTER ? viewport.bottom() - height : viewport.y();
        return new Candidate(mode, new SurfaceGeometry(x, y, width, height, radius(appearance, mode, height, scale), 1),
                size.minWidth(), size.minHeight());
    }

    private static double radius(SurfaceTokens tokens, SurfacePresentation mode, double height, double scale) {
        double requested = tokens.dimensions().forPresentation(mode).radius();
        return requested < 0 ? height / 2 : requested * scale;
    }

    private void prepareTargets(Content content, Content side, LiveSurfaceSettings.Layout layout) {
        var mode = placement.presentation();
        var geometry = placement.geometry();
        secondTarget = HIDDEN;
        if (!mode.visible()) {
            firstTarget = HIDDEN;
            return;
        }
        if (mode == SurfacePresentation.SPLIT && side != null) {
            var split = splitSizing;
            double usable = geometry.width() - split.gap;
            double desired = usable * split.left.width() / (split.left.width() + split.right.width());
            double left = Math.max(split.left.minWidth(), Math.min(usable - split.right.minWidth(), desired));
            double rightRadius = radius(LiveSurfacePainter.effective(side, tokens), mode, geometry.height(), layout.scale());
            secondTarget = new Target<>(mode, new SurfaceGeometry(geometry.x() + left + split.gap, geometry.y(),
                    usable - left, geometry.height(), rightRadius, geometry.opacity()), side, identity(plannedSecond, side));
            geometry = new SurfaceGeometry(geometry.x(), geometry.y(), left, geometry.height(), geometry.radius(), geometry.opacity());
        }
        firstTarget = new Target<>(mode, geometry, content, identity(plannedFirst, content));
    }

    void animate(LiveSurfaceSettings settings) {
        if (!firstTarget.presentation().visible() && !first.state().animated().visible()
                && !second.state().animated().visible() && firstTarget.presentation() == first.state().target().presentation()) return;
        long start = System.nanoTime();
        var motionSettings = settings.motion();
        if (spring == null || spring.response() != motionSettings.response() || spring.damping() != motionSettings.damping()) {
            var preset = new Spring.Preset(motionSettings.response(), motionSettings.damping(), .05f, 1);
            if (spring == null) {
                first = new SurfaceTransition<>(preset);
                second = new SurfaceTransition<>(preset);
            } else {
                first = reconfigure(first, preset);
                second = reconfigure(second, preset);
            }
            spring = motionSettings;
        }
        var preferences = ClientUiSettings.current();
        var motion = switch (motionSettings.mode()) {
            case NONE -> Motion.NONE;
            case REDUCED -> Motion.REDUCED;
            case FULL -> preferences.reducedMotion() || MinecraftThemes.manager().global().preferences().reducedMotion()
                    || reducedFrames > 0 ? Motion.REDUCED : Motion.FULL;
        };
        appliedMotion = motion;
        long time = host.nowNanos() / 1_000_000L;
        animate(first, firstTarget, time, motion);
        animate(second, secondTarget, time, motion);
        live.animationMeasured(System.nanoTime() - start);
    }

    private SurfaceTransition<Content> reconfigure(SurfaceTransition<Content> transition, Spring.Preset preset) {
        var next = new SurfaceTransition<Content>(preset);
        var state = transition.state();
        var current = state.input().presentation().visible() ? state.input() : state.current();
        var sampled = new Target<>(current.presentation(), state.animated(), current.content(), current.contentKey());
        next.update(sampled, viewport, host.nowNanos() / 1_000_000L, Motion.NONE, Interruption.SNAP);
        return next;
    }

    private void animate(SurfaceTransition<Content> transition, Target<Content> target, long time, Motion motion) {
        var frame = transition.update(target, viewport, time, motion, Interruption.PRESERVE_VELOCITY);
        if (target.presentation().visible() && blocked(frame.animated(), occupied)) {
            transition.update(target, viewport, time, motion, Interruption.SNAP);
        }
    }

    void render(Minecraft minecraft, GuiGraphics graphics, RenderServiceFrame frame, LiveSurfaceSettings settings) {
        var one = first.state();
        var two = second.state();
        if (!one.animated().visible() && !two.animated().visible()) {
            input.painted(null, null, plannedFirst, plannedSecond);
            if (live.view() != View.EMPTY || live.plan().primary() != null) {
                live.present(new View(live.plan(), one.target().presentation(), one.current().presentation(),
                        SurfaceGeometry.HIDDEN, SurfaceGeometry.HIDDEN, false, one.running() || two.running()));
            }
            return;
        }
        long start = System.nanoTime();
        var layer = layer(one, plannedFirst, bossThisFrame);
        var side = layer(two, plannedSecond, bossThisFrame);
        try {
            if (painter == null) {
                scene = spatial.mount(module, module.scope());
                painter = new LiveSurfacePainter(scene);
            }
            var next = new SpatialStyle.Settings(!renderFailed, settings.theme().shadow() && reducedFrames == 0,
                    false, false, settings.theme().quality(), settings.diagnostics());
            if (!next.equals(sceneSettings)) {
                scene.settings(next);
                sceneSettings = next;
            }
            var drawn = painter.render(graphics, minecraft.font, frame.width, frame.height, frame.guiScale, tokens, layer, side,
                    appliedMotion != Motion.FULL);
            if (drawn.primaryTargetPainted() && plannedFirst != null) {
                live.markPainted(plannedFirst);
                if (minecraft.screen == null && layer != null) {
                    double retraction = live.shortcutHintRetraction(plannedFirst, appliedMotion == Motion.FULL);
                    if (retraction < 1) {
                        drawShortcutHints(graphics, minecraft.font, layer.geometry(), settings.layout().expanded(), retraction);
                    }
                }
            }
            var hit = hit(layer, plannedFirst, drawn.primaryControls());
            var sideHit = hit(side, plannedSecond, drawn.secondaryControls());
            input.painted(hit, sideHit, plannedFirst, plannedSecond);
            if (drawn.secondaryTargetPainted() && plannedSecond != null) live.markPainted(plannedSecond);
            live.present(new View(live.plan(), one.target().presentation(), one.current().presentation(),
                    layer == null ? SurfaceGeometry.HIDDEN : layer.geometry(),
                    side == null ? SurfaceGeometry.HIDDEN : side.geometry(), hit != null || sideHit != null,
                    one.running() || two.running()));
        } catch (RuntimeException | LinkageError failure) {
            if (!renderFailed) host.report("Adaptive live surface render; falling back to simple geometry", failure);
            renderFailed = true;
            input.resetPresentation();
            live.present(View.EMPTY);
        } finally {
            long elapsed = System.nanoTime() - start;
            live.renderMeasured(elapsed);
            adapt(elapsed, settings.performance());
        }
    }

    private void adapt(long elapsed, LiveSurfaceSettings.Performance performance) {
        if (!performance.adaptive()) {
            reducedFrames = expensiveFrames = 0;
        } else if (reducedFrames > 0) {
            reducedFrames--;
        } else if (elapsed <= performance.frameBudgetMs() * 1_000_000) {
            expensiveFrames = 0;
        } else if (++expensiveFrames >= 6) {
            reducedFrames = 90;
            expensiveFrames = 0;
        }
    }

    private void drawShortcutHints(GuiGraphics graphics, Font font, SurfaceGeometry geometry, boolean expandable,
                                   double retraction) {
        var details = Component.translatable("sh_better_ui.live.shortcut.details");
        var pointer = Component.translatable("sh_better_ui.live.shortcut.pointer");
        int detailsWidth = expandable ? font.width(details) : 0;
        int pointerWidth = font.width(pointer);
        int line = font.lineHeight + 1;
        var island = new Rect(geometry.x(), geometry.y(), geometry.width(), geometry.height());
        var obstacles = new ArrayList<>(occupied);
        obstacles.addAll(bossThisFrame);
        int gap = expandable ? 8 : 0;
        var bounds = shortcutBounds(viewport, island, detailsWidth + gap + pointerWidth + 6, line + 2, obstacles);
        boolean stacked = bounds == null && expandable;
        if (stacked) {
            bounds = shortcutBounds(viewport, island, Math.max(detailsWidth, pointerWidth) + 6, line * 2 + 2, obstacles);
        }
        if (bounds == null) return;

        double progress = retraction * retraction * (3 - 2 * retraction);
        double opacity = 1 - progress;
        int foreground = hintColor(tokens.title(), opacity);
        if ((foreground >>> 24) < 4) return;

        int width = (int) bounds.width(), height = (int) bounds.height();
        double hintX = bounds.x() + width / 2.0, hintY = bounds.y() + height / 2.0;
        double islandX = island.x() + island.width() / 2, islandY = island.y() + island.height() / 2;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(hintX + (islandX - hintX) * progress, hintY + (islandY - hintY) * progress, 0);
            pose.scale((float) opacity, (float) opacity, 1);
            pose.translate(-width / 2.0, -height / 2.0, 0);
            graphics.fill(0, 0, width, height, hintColor(tokens.surface(), opacity));
            if (expandable) graphics.drawString(font, details, 3, 1, foreground, true);
            graphics.drawString(font, pointer, stacked ? 3 : 3 + detailsWidth + gap, stacked ? 1 + line : 1, foreground, true);
        } finally {
            pose.popPose();
        }
    }

    private static int hintColor(int argb, double opacity) {
        return argb & 0xffffff | (int) Math.round((argb >>> 24) * opacity) << 24;
    }

    static Rect shortcutBounds(Rect viewport, Rect island, int width, int height, List<Rect> obstacles) {
        if (width > viewport.width() || height > viewport.height()) return null;
        double x = Math.floor(UiMath.clamp(island.x() + (island.width() - width) / 2,
                viewport.x(), viewport.right() - width));
        double y = Math.floor(UiMath.clamp(island.y() + (island.height() - height) / 2,
                viewport.y(), viewport.bottom() - height));
        for (var bounds : new Rect[] {
                new Rect(x, Math.ceil(island.bottom()) + 3, width, height),
                new Rect(x, Math.floor(island.y()) - 3 - height, width, height),
                new Rect(Math.ceil(island.right()) + 3, y, width, height),
                new Rect(Math.floor(island.x()) - 3 - width, y, width, height) }) {
            if (bounds.x() >= viewport.x() && bounds.y() >= viewport.y() && bounds.right() <= viewport.right()
                    && bounds.bottom() <= viewport.bottom() && obstacles.stream().noneMatch(bounds::intersects)) return bounds;
        }
        return null;
    }

    private LiveSurfacePainter.Layer layer(SurfaceTransition.State<Content> state, Snapshot snapshot, List<Rect> currentBoss) {
        if (!state.animated().visible() || blocked(state.animated(), currentBoss)) return null;
        var interaction = input.available() ? state.input() : HIDDEN;
        long instance = snapshot == null ? 0 : snapshot.instance();
        var geometry = state.animated();
        float offset = input.swipeOffset(instance);
        if (offset != 0) {
            var translated = SurfacePlacement.fit(new SurfaceGeometry(geometry.x() + offset, geometry.y(), geometry.width(),
                    geometry.height(), geometry.radius(), geometry.opacity()), viewport);
            if (!blocked(translated, occupied) && !blocked(translated, currentBoss)) {
                geometry = translated;
                if (interaction.geometry().visible()) {
                    interaction = new Target<>(interaction.presentation(), geometry, interaction.content(), interaction.contentKey());
                }
            }
        }
        return new LiveSurfacePainter.Layer(geometry, state.contentMix(), state.target().contentKey(), interaction, input.focused(instance),
                input.hovered(instance), input.pressed(instance), input.seekPreview(instance));
    }

    private static ClientLiveInput.Hit hit(LiveSurfacePainter.Layer layer, Snapshot snapshot,
                                           List<LiveSurfacePainter.Control> controls) {
        return layer != null && snapshot != null && layer.input().presentation().visible() && layer.input().geometry().visible()
                ? new ClientLiveInput.Hit(snapshot, layer.input().geometry(), controls) : null;
    }

    private List<Rect> collectObstacles(Minecraft minecraft, int width, int height) {
        return ClientSafeAreas.collect(minecraft, width, height, live.occupied(), bossLastFrame, scoreboard, 0).stream()
                .map(area -> new Rect(area.bounds().x(), area.bounds().y(), area.bounds().width(), area.bounds().height())).toList();
    }

    List<com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot.ReservedArea> correctnessAreas(
            Minecraft minecraft, int width, int height, long frame) {
        var areas = new ArrayList<>(ClientSafeAreas.collect(minecraft, width, height, live.occupied(), bossLastFrame, scoreboard, frame));
        var view = live.view();
        for (var geometry : List.of(view.primary(), view.secondary())) {
            if (!geometry.visible() || areas.size() >= 128) continue;
            var bounds = new com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect(
                    geometry.x(), geometry.y(), geometry.width(), geometry.height(),
                    com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space.GUI);
            areas.add(new com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot.ReservedArea(
                    com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:live_surface/" + areas.size()), bounds,
                    com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport.Evidence.OBSERVED, Math.max(0, frame - 1)));
        }
        return List.copyOf(areas);
    }

    private static boolean blocked(SurfaceGeometry geometry, List<Rect> obstacles) {
        if (!geometry.visible()) return false;
        var bounds = new Rect(geometry.x(), geometry.y(), geometry.width(), geometry.height());
        for (var obstacle : obstacles) if (bounds.intersects(obstacle)) return true;
        return false;
    }

    private Content content(Snapshot snapshot) {
        var content = snapshot.content();
        boolean actions = live.hasActions(snapshot);
        if (actions && snapshot.occurrences() == 1 || !actions && content.controls().isEmpty()
                && !content.seekable() && snapshot.occurrences() == 1) return content;
        var title = snapshot.occurrences() == 1 ? content.title() : bounded(Component.translatable("sh_better_ui.live.count",
                LiveSurfacePainter.component(content.title()), snapshot.occurrences()).getString(), content.title());
        return new Content(title, content.detail(), content.icon(), content.progress(), actions && content.seekable(),
                actions ? content.controls() : List.of(), content.rhythm(), content.appearance());
    }

    static Text bounded(String translated, Text fallback) {
        if (translated.isBlank()) return fallback;
        if (translated.length() <= 512) return Text.literal(translated);
        int end = Character.isHighSurrogate(translated.charAt(510)) ? 510 : 511;
        return Text.literal(translated.substring(0, end) + "…");
    }

    private static Metrics metrics(Content value) {
        return new Metrics(value.title(), value.detail(), value.icon() != null, value.progress() >= 0,
                value.controls(), !value.rhythm().isEmpty(), value.appearance());
    }

    private static Object identity(Snapshot snapshot, Content value) {
        return new Identity(snapshot.instance(), value.title(), value.icon());
    }

    private static SurfaceTokens palette(LiveSurfaceSettings.Theme theme) {
        if (ClientUiSettings.current().highContrast() || MinecraftThemes.manager().global().preferences().highContrast()) {
            return SurfaceTokens.HIGH_CONTRAST;
        }
        var base = SurfaceTokens.DARK;
        return switch (theme.mode()) {
            case DARK -> base;
            case CUSTOM -> new SurfaceTokens(theme.background(), base.title(), base.detail(), theme.accent(),
                    base.progressTrack(), base.button(), base.buttonPressed(), base.focus(), base.shadow(), false);
            case KERNEL -> {
                var scope = MinecraftThemes.manager().global();
                yield new SurfaceTokens(scope.resolve(ThemeTokens.SURFACE).argb(), scope.resolve(ThemeTokens.TEXT).argb(),
                        scope.resolve(ThemeTokens.TEXT_SECONDARY).argb(), scope.resolve(ThemeTokens.ACCENT).argb(),
                        scope.resolve(ThemeTokens.SURFACE_SECONDARY).argb(), scope.resolve(ThemeTokens.SURFACE_ELEVATED).argb(),
                        base.buttonPressed(), base.focus(), scope.resolve(ThemeTokens.SHADOW).color().argb(), false);
            }
        };
    }

    record RenderServiceFrame(int width, int height, double guiScale) {}
    private record Identity(long instance, Text title, com.dishanhai.sh_better_ui.kernel.api.UiId icon) {}
    private record Metrics(Text title, Text detail, boolean icon, boolean progress, List<ActivityService.Control> controls,
                           boolean rhythm, SurfaceTokens appearance) {}
    private record SplitSizing(LiveSurfacePainter.Measured left, LiveSurfacePainter.Measured right, double gap) {}
    private record LayoutKey(Rect viewport, List<Rect> obstacles, LiveSurfaceSettings.Layout layout, SurfaceTokens tokens,
                             SurfacePresentation mode, Metrics primary, Metrics secondary, long resources) {}
}
