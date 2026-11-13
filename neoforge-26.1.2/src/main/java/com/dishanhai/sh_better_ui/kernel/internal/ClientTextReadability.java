// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Insets;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Background;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Decision;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Sample;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Owns text requests during the native GUI extraction; actual sampling is scheduled in the render pass. */
final class ClientTextReadability implements TextReadabilityService {
    private static final long IDLE_NANOS = 5_000_000_000L;
    private static final int MAX_STATES = 2048;
    private static final GpuDetails DETAIL_OFF = new GpuDetails(DetailStatus.OFF, -1, -1, 0, null, List.of());
    private final KernelHost host;
    private final ClientScreens screens;
    private final ClientReadabilitySamples samples;
    private final HashMap<ScopeLifecycle.Scope, Configuration> configurations = new HashMap<>();
    private final Set<ScopeLifecycle.Scope> debugging = new HashSet<>();
    private final LinkedHashMap<Key, History> history = new LinkedHashMap<>(32, .75f, true);
    private final IdentityHashMap<Request, Work> consumed = new IdentityHashMap<>();
    private final ArrayList<Queued> foreground = new ArrayList<>();
    private final ArrayList<Queued> popupForeground = new ArrayList<>();
    private Screen screen;
    private GuiGraphicsExtractor graphics;
    private float partialTick;
    private ScopeLifecycle.Scope popup;
    private Pass context;
    private boolean sealed, popupSealed, overlay;
    private long cpuNanos;
    private long targetEpoch = -1;
    private SamplingStatistics statistics = new SamplingStatistics(-1, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0);

    ClientTextReadability(KernelHost host, ClientScreens screens, ClientReadabilitySamples samples) {
        this.host = host;
        this.screens = screens;
        this.samples = samples;
    }

    void beginFrame() {
        discardScreen();
        if (targetEpoch != samples.epoch()) clearDiagnostics();
        history.entrySet().removeIf(entry -> !entry.getKey().node.isOpen()
                || host.nowNanos() - entry.getValue().seen >= IDLE_NANOS);
        cpuNanos = 0;
    }

    void endFrame() {
        statistics = new SamplingStatistics(samples.frame(), samples.runs(), samples.samples(), samples.pixels(),
                samples.budgetFallbacks(), samples.unavailableFallbacks(), samples.invalidRegions(), cpuNanos,
                samples.sourceFrame(), 0, 0, samples.bytes(), samples.peakBytes(), history.size());
        discardScreen();
    }

    void invalidateSamples() {
        samples.invalidate();
        clearDiagnostics();
    }

    private void clearDiagnostics() {
        targetEpoch = samples.epoch();
        history.values().forEach(state -> state.diagnostic = null);
    }

    void beginScreen(Screen owner, GuiGraphicsExtractor graphics, float partialTick) {
        discardScreen();
        screen = owner;
        this.graphics = graphics;
        this.partialTick = partialTick;
    }

    void finishScreen(Screen owner) {
        if (owner != screen || sealed) return;
        sealed = true;
        try {
            if (!foreground.isEmpty()) {
                graphics.nextStratum();
                flush(foreground);
            }
            overlay = true;
        } finally {
            if (!overlay) discardScreen();
        }
    }

    void discardScreen() {
        screen = null;
        graphics = null;
        sealed = popupSealed = overlay = false;
        popup = null;
        context = null;
        foreground.clear();
        popupForeground.clear();
        consumed.clear();
    }

    boolean hasScreen(Screen owner) { return owner != null && owner == screen; }

    ClientUiKernel.ScreenStamp screenStamp(ModuleContext module, ScopeHandle scope) {
        var mount = screens.activeScope(screen);
        if (screen == null || mount == null || !scope.isOpen() || screens.ownerOf(host.ownedNode(module, scope)) != screen) {
            throw new IllegalStateException("Visual snapshots require an active Screen extraction");
        }
        return new ClientUiKernel.ScreenStamp(samples.frame(), mount.generation(), host.nowNanos() / 1_000_000,
                samples.scale());
    }

    boolean currentFrame(long frame, long mountGeneration) {
        var mount = screens.activeScope(screen);
        return screen != null && mount != null && mount.generation() == mountGeneration && samples.frame() == frame;
    }

    @Override
    public void configure(ModuleContext module, ScopeHandle scope, TextReadabilityPolicy policy) {
        var node = node(module, scope);
        var old = configurations.get(node);
        bind(node, new Configuration(Objects.requireNonNull(policy), old == null ? null : old.popup));
    }

    @Override
    public void configurePopup(ModuleContext module, ScopeHandle scope, ThemeScope theme, ScopeHandle owningPage,
                               TextReadabilityPolicy policy) {
        var node = node(module, scope);
        Objects.requireNonNull(theme);
        var page = owningPage == null ? null : node(module, owningPage);
        if (theme.isClosed() || page != null && (page.kind() != ScopeKind.UI_TREE || page == node
                || screens.ownerOf(page) != screens.ownerOf(node) || popupOf(page) != null)
                || configurations.values().stream().anyMatch(config -> config.popup != null && config.popup.page == node)) {
            throw new IllegalArgumentException("Popup requires a live theme and an owning page on the same Screen");
        }
        bind(node, new Configuration(Objects.requireNonNull(policy), new Popup(theme, page)));
    }

    @Override
    public TextReadabilityPolicy.Resolved policy(ModuleContext module, ScopeHandle scope) {
        var node = node(module, scope);
        return resolve(node, popupOf(node)).resolve();
    }

    @Override
    public Result submit(ModuleContext module, Request request) {
        return draw(module, request, null, false);
    }

    @Override
    public Result drawNow(ModuleContext module, RenderService.Context context, Request request) {
        return draw(module, request, context, true);
    }

    @Override
    public Result foreground(ModuleContext module, ScopeHandle scope, int z, Consumer<RenderService.Context> draw) {
        host.moduleAccess(module);
        if (scope == null || draw == null) return Result.INVALID_REQUEST;
        if (!scope.isOpen()) return Result.SCOPE_CLOSED;
        var node = node(module, scope);
        var layer = popupOf(node);
        var rejected = stage(node, layer);
        if (rejected != null) return rejected;
        queue(layer).add(new Queued(z, () -> runForeground(node, layer, draw)));
        return Result.SUBMITTED;
    }

    @Override
    public void renderPopup(ModuleContext module, ScopeHandle scope, GuiGraphicsExtractor graphics, Runnable content) {
        var node = node(module, scope);
        if (!overlay || node != popupOf(node) || screens.ownerOf(node) != screen || this.graphics != graphics || popup != null) {
            throw new IllegalStateException("Popup readability requires its active Screen overlay pass");
        }
        popup = node;
        try {
            Objects.requireNonNull(content).run();
            popupSealed = true;
            if (!popupForeground.isEmpty()) {
                graphics.nextStratum();
                flush(popupForeground);
            }
        } finally {
            popupForeground.clear();
            popupSealed = false;
            popup = null;
        }
    }

    private void flush(ArrayList<Queued> queue) {
        queue.sort(Comparator.comparingInt(Queued::z));
        for (var item : List.copyOf(queue)) {
            if (graphics == null) break;
            try {
                item.draw.run();
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                host.report("UUK text foreground", failure);
            }
        }
        queue.clear();
    }

    private ArrayList<Queued> queue(ScopeLifecycle.Scope layer) { return layer == null ? foreground : popupForeground; }

    private void runForeground(ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer, Consumer<RenderService.Context> draw) {
        if (screen == null || !node.isOpen() || screens.ownerOf(node) != screen || popupOf(node) != layer) return;
        var pass = new Pass(node, layer);
        context = pass;
        graphics.pose().pushMatrix();
        try {
            draw.accept(pass);
        } finally {
            graphics.pose().popMatrix();
            context = null;
        }
    }

    private Result stage(ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer) {
        if (screen == null || screens.ownerOf(node) != screen) return Result.RENDER_UNAVAILABLE;
        if (layer == null) return sealed ? Result.QUEUE_CLOSED : null;
        if (popup != layer) return Result.INVALID_STAGE;
        return popupSealed ? Result.QUEUE_CLOSED : null;
    }

    private Result draw(ModuleContext module, Request request, RenderService.Context requestedContext, boolean immediate) {
        host.moduleAccess(module);
        if (request == null) return Result.INVALID_REQUEST;
        if (!request.scope().isOpen()) return Result.SCOPE_CLOSED;
        var node = node(module, request.scope());
        var layer = popupOf(node);
        if (screen == null || screen != screens.ownerOf(node)) return Result.RENDER_UNAVAILABLE;
        var policy = resolve(node, layer).overlay(request.policyOverride());
        var boundary = layer == null ? node.ancestor(ScopeKind.UI_TREE) : layer;
        var config = configurations.get(boundary);
        if (config != null && config.policy.samplingMode() == TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY) {
            policy = policy.withSamplingMode(TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY);
        }
        var effective = policy.resolve();
        var clip = ClientTextPaint.clip(graphics, request.clip());
        double height = TextReadabilityPolicy.framebufferHeight(request.run().lineHeight()
                * Math.hypot(request.transform().xy(), request.transform().yy()), samples.scale());
        var work = new Work(request, node, layer, effective, clip, height,
                effective.targetContrast(request.style().usage(), height));
        if (immediate && (context == null || context != requestedContext || context.node != node || context.layer != layer)) {
            return report(work, Result.INVALID_STAGE);
        }
        if (!snapshotMatches(work)) return report(work, Result.STALE_LAYOUT);
        if (request.groupOpacity() == 0 || request.style().argb() >>> 24 == 0) return report(work, Result.HIDDEN);
        if (work.visible().empty() || Math.ceil(clip.x()) >= Math.floor(clip.right())
                || Math.ceil(clip.y()) >= Math.floor(clip.bottom())) return report(work, Result.CLIPPED);
        if (!immediate) {
            var rejected = stage(node, layer);
            if (rejected != null) return report(work, rejected);
        }
        if (consumed.containsKey(request)) return report(work, Result.ALREADY_CONSUMED);
        consumed.put(request, work);
        if (!immediate && effective.foregroundProtection()) {
            queue(layer).add(new Queued(request.foregroundZ(), () -> {
                if (consumed.remove(request, work)) {
                    consumed.put(request, null);
                    paint(work);
                }
            }));
            return report(work, Result.SUBMITTED);
        }
        return paint(work);
    }

    private boolean snapshotMatches(Work work) {
        FrameViewSnapshot snapshot = work.request.snapshot();
        if (snapshot == null) return true;
        if (!snapshot.isCurrent()) return false;
        for (var node = work.node; node != null; node = node.parent()) {
            if (node.generation() == snapshot.scopeGeneration()) return true;
        }
        return false;
    }

    private Result paint(Work work) {
        if (!work.node.isOpen()) return Result.SCOPE_CLOSED;
        if (screens.ownerOf(work.node) != screen || popupOf(work.node) != work.layer || !snapshotMatches(work)) {
            return report(work, Result.STALE_LAYOUT);
        }
        long started = System.nanoTime();
        try {
            var request = work.request;
            var run = request.run();
            int argb = request.style().argb();
            if (!work.policy.adaptiveColor()) {
                paintAuthored(work);
                return report(work, Result.DRAWN);
            }
            if (work.policy.localColor() && ClientTextPaint.font(run).getClass() == net.minecraft.client.gui.Font.class) {
                var clusters = NativeTextClusters.measure(run.glyphs(), (float) run.x(), argb,
                        ClientTextPaint.font(run).getSplitter()::stringWidth);
                if (clusters.size() > 1) return paintClusters(work, clusters);
            }
            Integer uniform = uniformColor(run, argb);
            if (uniform == null) {
                if (!work.policy.localColor()) return report(work, Result.INVALID_REQUEST);
                paintAuthored(work);
                return report(work, Result.DRAWN);
            }
            var parent = history(work.key(-1));
            if (parent.fill.displayedFill() == null) {
                history.entrySet().stream().filter(entry -> entry.getKey().sameText(work) && entry.getKey().cluster >= 0)
                        .sorted(Comparator.comparingInt(entry -> entry.getKey().cluster))
                        .map(entry -> entry.getValue().fill.displayedFill()).filter(Objects::nonNull).findFirst()
                        .ifPresent(parent.fill::seedDisplayedFill);
            }
            var choice = analyze(work, run, uniform, -1, parent);
            ClientTextPaint.paint(graphics, run, request.transform(), work.clip, run.bounds().outset(new Insets(2, 1, 2, 1)),
                    uniform, request.style().shadow(), request.groupOpacity(), choice.decision);
            save(work, -1, parent, Result.DRAWN, choice.decision, choice.sampling, choice.details);
            history.keySet().removeIf(key -> key.cluster >= 0 && key.sameText(work));
            return Result.DRAWN;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            host.report("Paint UUK text " + work.node.id(), failure);
            return report(work, Result.RENDER_FAILED);
        } finally {
            cpuNanos += System.nanoTime() - started;
        }
    }

    private Result paintClusters(Work work, List<NativeTextClusters.Cluster> clusters) {
        var request = work.request;
        var original = request.run();
        var parent = history(work.key(-1));
        var paints = new ArrayList<ClusterPaint>();
        boolean actual = false;
        SamplingStatus fallback = SamplingStatus.DISABLED;
        for (int i = 0; i < clusters.size(); i++) {
            var cluster = clusters.get(i);
            var bounds = new Rect(cluster.left(), original.bounds().y(), cluster.right() - (double) cluster.left(),
                    original.bounds().height()).intersect(original.bounds());
            if (bounds.empty() || request.transform().map(bounds).intersect(work.clip).empty()) continue;
            var run = new Run(cluster.sequence(), cluster.left(), original.y(), bounds, original.lineHeight(), original.font());
            var state = history(work.key(cluster.index()));
            Integer displayed = parent.fill.displayedFill();
            if (displayed != null && state.fill.displayedFill() == null) state.fill.seedDisplayedFill(displayed);
            var choice = analyze(work, run, cluster.color(), cluster.index(), state);
            actual |= choice.sampling == SamplingStatus.GPU;
            fallback = choice.sampling;
            save(work.withBounds(bounds), cluster.index(), state, Result.DRAWN, choice.decision, choice.sampling, choice.details);
            double left = i == 0 ? 2 : Math.min(2, (cluster.left() - clusters.get(i - 1).right()) / 2.0);
            double right = i + 1 == clusters.size() ? 2 : Math.min(2, (clusters.get(i + 1).left() - cluster.right()) / 2.0);
            paints.add(new ClusterPaint(run, bounds.outset(new Insets(left, 1, right, 1)), cluster.color(), choice.decision));
        }
        // Every cluster's sample comes from the same pre-text surface.
        for (var item : paints) {
            ClientTextPaint.paint(graphics, item.run, request.transform(), work.clip, item.backing, item.original,
                    request.style().shadow(), request.groupOpacity(), item.decision);
        }
        parent.fill.reset();
        var result = paints.isEmpty() ? Result.CLIPPED : Result.DRAWN;
        save(work, -1, parent, result, null, actual ? SamplingStatus.LOCAL_GPU : fallback, DETAIL_OFF);
        return result;
    }

    private void paintAuthored(Work work) {
        var request = work.request;
        ClientTextPaint.paint(graphics, request.run(), request.transform(), work.clip, request.run().bounds(),
                request.style().argb(), request.style().shadow(), request.groupOpacity(), null);
    }

    private Choice analyze(Work work, Run run, int argb, int cluster, History state) {
        var request = work.request;
        var region = samples.region(request.transform().map(run.bounds()).intersect(work.clip));
        var sampling = SamplingStatus.DECLARED_ONLY;
        var hint = request.sampling();
        ClientReadabilitySamples.Readback readback = null;
        if (hint != SamplingHint.DECLARED_ONLY) {
            if (work.policy.samplingMode() == TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY && !hint.explicitlyMarked()) {
                sampling = SamplingStatus.EXPLICIT_ONLY;
            } else {
                var points = ClientTextPaint.points(run, argb, request.transform(), work.clip, samples.scale(), region);
                int priority = switch (hint) {
                    case FOCUSED, AUTO_FOCUSED -> 3;
                    case HOVERED, AUTO_HOVERED -> 2;
                    case IMPORTANT -> 1;
                    default -> 0;
                };
                sampling = samples.schedule(graphics, state.capture, region, priority, points, false);
                if (sampling == SamplingStatus.GPU) {
                    readback = samples.current(state.capture, region);
                    if (readback == null) sampling = SamplingStatus.UNAVAILABLE;
                }
            }
        }
        var actual = new ArrayList<Sample>();
        var observed = new ArrayList<SamplePoint>();
        if (readback != null) {
            for (int i = 0; i < readback.points().size(); i++) {
                var point = readback.points().get(i);
                var sample = Sample.actualSrgb(cluster, readback.pixels()[i], 1, point.outlineCoverage(), true);
                actual.add(sample);
                observed.add(new SamplePoint(point.x(), point.y(), sample.luminance(), point.outlineCoverage()));
            }
        }
        var declared = request.declaredBackgroundArgb();
        var background = readback != null ? Background.actual(actual, declared)
                : declared == null ? Background.unknown() : Background.declared(declared);
        var style = request.style();
        boolean lockHue = work.policy.lockHue() == null ? style.lockHue() : work.policy.lockHue();
        var input = new TextReadabilityReference.Input(argb, lockHue, style.allowOutline(), style.allowBacking(),
                work.contrast, request.groupOpacity(), work.height,
                Math.max(TextReadabilityPolicy.DEFAULT_STABILITY_NANOS, work.policy.stabilityNanos()), background);
        if (state.observedFrame >= 0 && samples.frame() - state.observedFrame > 1) state.fill.reset();
        state.observedFrame = samples.frame();
        var decision = TextReadabilityReference.evaluate(input, request.text() == null ? null : state.fill, host.nowNanos());
        var details = DETAIL_OFF;
        if (debugging.stream().anyMatch(work.node::isWithin)) {
            long pending = state.capture.pending;
            details = readback == null ? new GpuDetails(pending == samples.frame() ? DetailStatus.PENDING : DetailStatus.BUSY,
                    -1, pending, 0, null, List.of())
                    : new GpuDetails(DetailStatus.READY, readback.frame(), pending, observed.size(), decision, observed);
        }
        return new Choice(decision, sampling, details);
    }

    private static Integer uniformColor(Run run, int argb) {
        int[] color = { -1 };
        boolean uniform = run.glyphs().accept((index, style, codePoint) -> {
            int next = style.getColor() == null ? argb & 0xFFFFFF : style.getColor().getValue();
            if (color[0] == -1) color[0] = next;
            return color[0] == next;
        });
        return uniform ? color[0] == -1 ? argb : argb & 0xFF000000 | color[0] : null;
    }

    @Override
    public Optional<Diagnostic> diagnostics(ModuleContext module, ScopeHandle scope, TextId text) {
        host.moduleAccess(module);
        if (scope == null || !scope.isOpen() || text == null) return Optional.empty();
        var key = new Key(node(module, scope), text, -1);
        var value = history.get(key);
        return value == null ? Optional.empty() : Optional.ofNullable(exposed(key.node, value.diagnostic));
    }

    @Override
    public List<ClusterDiagnostic> clusterDiagnostics(ModuleContext module, ScopeHandle scope, TextId text) {
        host.moduleAccess(module);
        if (scope == null || !scope.isOpen() || text == null) return List.of();
        var node = node(module, scope);
        var parent = history.get(new Key(node, text, -1));
        if (parent == null || parent.diagnostic == null) return List.of();
        long paintedFrame = parent.diagnostic.sourceFrame();
        return history.entrySet().stream().filter(entry -> entry.getKey().node == node && entry.getKey().text.equals(text)
                        && entry.getKey().cluster >= 0 && entry.getValue().diagnostic != null
                        && entry.getValue().observedFrame == paintedFrame)
                .map(entry -> new ClusterDiagnostic(entry.getKey().cluster, exposed(node, entry.getValue().diagnostic)))
                .sorted(Comparator.comparingInt(ClusterDiagnostic::visualIndex)).toList();
    }

    private Diagnostic exposed(ScopeLifecycle.Scope node, Diagnostic diagnostic) {
        if (diagnostic == null || diagnostic.gpu() == DETAIL_OFF || debugging.stream().anyMatch(node::isWithin)) {
            return diagnostic;
        }
        return new Diagnostic(diagnostic.sourceFrame(), diagnostic.scopeGeneration(), diagnostic.renderLayerGeneration(),
                diagnostic.text(), diagnostic.result(), diagnostic.policy(), diagnostic.targetContrast(),
                diagnostic.visibleBounds(), diagnostic.decision(), diagnostic.sampling(), diagnostic.effectiveClip(), DETAIL_OFF);
    }

    @Override
    public void setGpuDiagnostics(ModuleContext module, ScopeHandle scope, boolean enabled) {
        var node = node(module, scope);
        if (enabled) {
            if (debugging.add(node)) host.onScopeClose(node, () -> debugging.remove(node));
        } else {
            debugging.remove(node);
        }
    }

    @Override
    public SamplingStatistics samplingStatistics(ModuleContext module) {
        host.moduleAccess(module);
        return statistics;
    }

    @Override
    public void retireDataItem(ModuleContext module, ScopeHandle scope, UiId dataItem) {
        var node = node(module, scope);
        Objects.requireNonNull(dataItem);
        history.keySet().removeIf(key -> key.node.isWithin(node) && dataItem.equals(key.text.dataItem()));
        consumed.replaceAll((request, pending) -> pending != null && pending.node.isWithin(node)
                && request.text() != null && dataItem.equals(request.text().dataItem()) ? null : pending);
    }

    private History history(Key key) {
        if (key == null) return new History();
        if (!history.containsKey(key) && history.size() >= MAX_STATES) history.pollFirstEntry();
        return history.computeIfAbsent(key, ignored -> new History());
    }

    private Result report(Work work, Result result) {
        var key = work.key(-1);
        if (key != null) save(work, -1, history(key), result, null, SamplingStatus.DISABLED, DETAIL_OFF);
        return result;
    }

    private void save(Work work, int cluster, History state, Result result, Decision decision,
                      SamplingStatus sampling, GpuDetails details) {
        if (work.key(cluster) == null) return;
        state.seen = host.nowNanos();
        var layer = work.layer == null ? work.node.ancestor(ScopeKind.SCREEN) : work.layer;
        state.diagnostic = new Diagnostic(samples.frame(), work.node.generation(), layer.generation(),
                work.request.text(), result, work.policy, work.contrast, work.visible(), decision, sampling, work.clip, details);
    }

    private ScopeLifecycle.Scope node(ModuleContext module, ScopeHandle scope) {
        var node = host.ownedNode(module, scope);
        if (node.ancestor(ScopeKind.SCREEN) == null) throw new IllegalArgumentException("Text requires a Screen tree");
        return node;
    }

    private void bind(ScopeLifecycle.Scope node, Configuration config) {
        if (!configurations.containsKey(node)) host.onScopeClose(node, () -> {
            configurations.remove(node);
            history.keySet().removeIf(key -> key.node.isWithin(node));
        });
        configurations.put(node, config);
    }

    private ScopeLifecycle.Scope popupOf(ScopeLifecycle.Scope node) {
        for (var current = node; current != null; current = current.parent()) {
            var config = configurations.get(current);
            if (config != null && config.popup != null) return current;
        }
        return null;
    }

    private TextReadabilityPolicy resolve(ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer) {
        var result = TextReadabilityPolicy.defaults();
        if (layer == null) return result.overlay(overrides(node, node.ancestor(ScopeKind.UI_TREE)));
        var popup = configurations.get(layer).popup;
        if (!popup.theme.isClosed()) {
            result = result.overlay(new TextReadabilityPolicy(popup.theme.resolve(ThemeTokens.TEXT_ADAPTIVE_COLOR),
                    popup.theme.resolve(ThemeTokens.TEXT_FOREGROUND_PROTECTION), null, null, null));
        }
        if (popup.page != null && popup.page.isOpen()) result = result.overlay(overrides(popup.page, popup.page));
        return result.overlay(overrides(node, layer));
    }

    private TextReadabilityPolicy overrides(ScopeLifecycle.Scope node, ScopeLifecycle.Scope stop) {
        var result = TextReadabilityPolicy.inherit();
        for (var current = node; current != null; current = current.parent()) {
            var config = configurations.get(current);
            if (config != null) result = config.policy.overlay(result);
            if (current == stop) break;
        }
        return result;
    }

    private final class Pass implements RenderService.Context {
        final ScopeLifecycle.Scope node;
        final ScopeLifecycle.Scope layer;

        Pass(ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer) { this.node = node; this.layer = layer; }
        private GuiGraphicsExtractor graphics() {
            if (context != this || graphics == null) throw new IllegalStateException("Expired text foreground context");
            return graphics;
        }
        @Override public RenderService.Frame frame() {
            var active = graphics();
            return new RenderService.Frame(RenderService.Surface.SCREEN, active.guiWidth(), active.guiHeight(),
                    samples.scale(), partialTick);
        }
        @Override public void fill(int x, int y, int right, int bottom, int argb) {
            graphics().fill(x, y, right, bottom, argb);
        }
        @Override public void clip(int x, int y, int right, int bottom, Runnable draw) {
            var active = graphics();
            active.enableScissor(x, y, right, bottom);
            try { draw.run(); } finally { active.disableScissor(); }
        }
        @Override public void transform(double x, double y, float scale, Runnable draw) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Float.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Invalid native GUI transform");
            }
            var pose = graphics().pose();
            pose.pushMatrix();
            try { pose.translate((float) x, (float) y).scale(scale); draw.run(); }
            finally { pose.popMatrix(); }
        }
        @Override public void text(Component label, int x, int y, int argb) {
            graphics().text(net.minecraft.client.Minecraft.getInstance().font, label, x, y, argb);
        }
    }

    private record Queued(int z, Runnable draw) {}
    private record Configuration(TextReadabilityPolicy policy, Popup popup) {}
    private record Popup(ThemeScope theme, ScopeLifecycle.Scope page) {}
    private record Key(ScopeLifecycle.Scope node, TextId text, int cluster) {
        boolean sameText(Work work) { return node == work.node && text.equals(work.request.text()); }
    }
    private record Work(Request request, ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer,
                        TextReadabilityPolicy.Resolved policy, Rect clip, double height, double contrast) {
        Key key(int cluster) { return request.text() == null ? null : new Key(node, request.text(), cluster); }
        Rect visible() { return request.transform().map(request.run().bounds()).intersect(clip); }
        Work withBounds(Rect bounds) {
            var run = request.run();
            var positioned = new Run(run.glyphs(), run.x(), run.y(), bounds, run.lineHeight(), run.font());
            var changed = new Request(request.scope(), request.text(), positioned, request.style(), request.transform(),
                    request.clip(), request.groupOpacity(), request.declaredBackgroundArgb(), request.foregroundZ(),
                    request.snapshot(), request.sampling(), request.policyOverride());
            return new Work(changed, node, layer, policy, clip, height, contrast);
        }
    }
    private record Choice(Decision decision, SamplingStatus sampling, GpuDetails details) {}
    private record ClusterPaint(Run run, Rect backing, int original, Decision decision) {}
    private static final class History {
        final TextReadabilityReference.State fill = new TextReadabilityReference.State();
        final ClientReadabilitySamples.Ticket capture = new ClientReadabilitySamples.Ticket();
        long observedFrame = -1;
        long seen;
        Diagnostic diagnostic;
    }
}
