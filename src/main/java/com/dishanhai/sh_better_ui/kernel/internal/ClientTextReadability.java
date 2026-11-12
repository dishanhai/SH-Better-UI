// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Insets;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator;
import com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator.Frame;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class ClientTextReadability implements TextReadabilityService {
    static final int MAX_STATES = 2048;
    static final long IDLE_NANOS = Duration.ofSeconds(5).toNanos();
    private static final int DETAIL_AGE_FRAMES = 12;
    private static final TextReadabilityReference.Sampling SAMPLING = new TextReadabilityReference.Sampling(
            TextReadabilityBudget.bounded("minimumSamples", 4, 1, 8),
            TextReadabilityBudget.bounded("interiorCoveragePercent", 75, 1, 100) / 100.0,
            TextReadabilityBudget.bounded("outlinePixels", 16, 0, 128));
    private static final GpuDetails DETAIL_OFF = new GpuDetails(DetailStatus.OFF, -1, -1, 0, null, List.of());

    private final KernelHost host;
    private final ClientScreens screens;
    private final Map<ScopeLifecycle.Scope, Configuration> configurations = new HashMap<>();
    private final Map<Key, Entry> history = new LinkedHashMap<>();
    private final Map<Request, Prepared> consumed = new IdentityHashMap<>();
    private final TextReadabilityBudget budget = TextReadabilityBudget.fromProperties();
    private final Map<Key, Slot> slots = new HashMap<>();
    private final BitSet occupiedSlots = new BitSet(MAX_STATES);
    private final Map<ScopeLifecycle.Scope, Subscription> debugScopes = new HashMap<>();
    private final Map<Long, Ticket> tickets = new HashMap<>();
    private final Map<Key, GpuDetails> completed = new HashMap<>();
    private final Map<Key, Long> pending = new HashMap<>();
    private final Map<Long, GpuTiming> timings = new HashMap<>();
    private final Counters counters = new Counters();
    private SamplingStatistics statistics = counters.snapshot(-1, -1, GpuTiming.EMPTY, 0, 0, 0);
    private int generation;
    private long nextTicket;
    private long peakGpuBytes;
    private boolean overlayEnabled;
    private Diagnostic hovered;
    private Key hoveredKey;
    private UiMouseState pointer;
    private long consumptionFrame = -1;
    private long targetEpoch;
    private Target target;
    private PopupPass activePopup;
    private ForegroundPass activeForeground;

    ClientTextReadability(KernelHost host, ClientScreens screens) {
        this.host = host;
        this.screens = screens;
    }

    @Override
    public void configure(ModuleContext module, ScopeHandle scope, TextReadabilityPolicy policy) {
        var node = node(module, scope);
        var previous = configurations.get(node);
        bind(node, new Configuration(Objects.requireNonNull(policy, "policy"), previous == null ? null : previous.popup));
    }

    @Override
    public void configurePopup(ModuleContext module, ScopeHandle scope, ThemeScope theme, ScopeHandle owningPage,
                               TextReadabilityPolicy policy) {
        var popup = node(module, scope);
        Objects.requireNonNull(theme, "theme");
        if (theme.isClosed()) throw new IllegalArgumentException("Popup theme is closed");
        var page = owningPage == null ? null : node(module, owningPage);
        if (page != null && (page.kind() != ScopeKind.UI_TREE || page == popup
                || popup.ancestor(ScopeKind.SCREEN) != page.ancestor(ScopeKind.SCREEN)
                || popupLayer(page) != null)) {
            throw new IllegalArgumentException("Popup policy must inherit from its owning page tree on the same Screen");
        }
        if (popup.kind() == ScopeKind.UI_TREE && configurations.values().stream()
                .anyMatch(config -> config.popup != null && config.popup.page == popup)) {
            throw new IllegalArgumentException("A page tree supplying popup policy cannot become a popup");
        }
        bind(popup, new Configuration(Objects.requireNonNull(policy, "policy"), new Popup(theme, page)));
    }

    @Override
    public TextReadabilityPolicy.Resolved policy(ModuleContext module, ScopeHandle scope) {
        var node = node(module, scope);
        return resolvePolicy(node, popupLayer(node)).resolve();
    }

    @Override
    public Result submit(ModuleContext module, Request request) {
        return render(module, null, request, false);
    }

    @Override
    public Result drawNow(ModuleContext module, RenderService.Context context, Request request) {
        return render(module, context, request, true);
    }

    @Override
    public Result foreground(ModuleContext module, ScopeHandle scope, int z, Consumer<RenderService.Context> draw) {
        host.moduleAccess(module);
        if (scope == null || draw == null) return Result.INVALID_REQUEST;
        if (!scope.isOpen()) return Result.SCOPE_CLOSED;
        var node = node(module, scope);
        var frame = screenFrame(node);
        var popup = popupLayer(node);
        var stage = submissionStage(frame, popup);
        if (stage != null) return stage;
        enqueue(frame, popup, z, graphics -> runForeground(node, popup, frame, graphics, draw));
        return Result.SUBMITTED;
    }

    @Override
    public void renderPopup(ModuleContext module, ScopeHandle scope, GuiGraphics graphics, Runnable content) {
        var popup = node(module, scope);
        Objects.requireNonNull(content, "content");
        var frame = screenFrame(popup);
        if (popupLayer(popup) != popup || frame == null || !frame.overlays()
                || graphics != frame.graphics() || activePopup != null) {
            throw new IllegalStateException("Popup readability requires its active Screen overlay pass");
        }
        var pass = new PopupPass(popup);
        activePopup = pass;
        try {
            GuiStateGuard.draw(() -> {
                try {
                    content.run();
                    pass.sealed = true;
                    pass.draws.sort(Comparator.comparingInt(QueuedDraw::z));
                    for (var draw : pass.draws) {
                        if (!frame.isCurrent() || !popup.isOpen()) break;
                        draw.draw.accept(graphics);
                    }
                } finally {
                    graphics.flush();
                }
            });
        } finally {
            activePopup = null;
        }
    }

    @Override
    public Optional<Diagnostic> diagnostics(ModuleContext module, ScopeHandle scope, TextId text) {
        host.moduleAccess(module);
        if (scope == null || !scope.isOpen() || text == null) return Optional.empty();
        var node = node(module, scope);
        var key = new Key(node, text);
        var entry = history.get(key);
        return entry == null || host.nowNanos() - entry.seen >= IDLE_NANOS
                ? Optional.empty() : Optional.of(withDetails(key, entry.diagnostic));
    }

    @Override
    public void setGpuDiagnostics(ModuleContext module, ScopeHandle scope, boolean enabled) {
        var node = node(module, scope);
        if (enabled) {
            debugScopes.computeIfAbsent(node, watched -> host.onScopeClose(watched, () -> {
                debugScopes.remove(watched);
                removeDebugData();
            }));
        } else {
            var registration = debugScopes.remove(node);
            if (registration != null) {
                registration.close();
                removeDebugData();
            }
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
        Objects.requireNonNull(dataItem, "dataItem");
        discardKeys(key -> key.scope.isWithin(node) && dataItem.equals(key.text.dataItem()));
        consumed.replaceAll((request, pending) -> pending != null && pending.node.isWithin(node)
                && request.text() != null && dataItem.equals(request.text().dataItem()) ? null : pending);
    }

    void advance() {
        long now = host.nowNanos();
        discardKeys(key -> !key.scope.isOpen() || now - history.get(key).seen >= IDLE_NANOS);
        TextReadabilityPainter.pollActualDiagnostics(this::receiveDetail);
        TextReadabilityPainter.pollActualCosts(this::receiveCost);
        long frame = host.frameNumber();
        tickets.entrySet().removeIf(entry -> frame - entry.getValue().frame > DETAIL_AGE_FRAMES * 2);
        timings.keySet().removeIf(source -> frame - source > DETAIL_AGE_FRAMES);
        budget.reset();
        counters.reset();
        hovered = null;
        hoveredKey = null;
        pointer = null;
    }

    void endFrame() {
        consumed.clear();
        consumptionFrame = -1;
        long gpuFrame = timings.keySet().stream().mapToLong(Long::longValue).max().orElse(-1);
        long bytes = TextReadabilityPainter.actualAllocatedBytes();
        peakGpuBytes = Math.max(peakGpuBytes, bytes);
        statistics = counters.snapshot(host.frameNumber(), gpuFrame,
                timings.getOrDefault(gpuFrame, GpuTiming.EMPTY), bytes, peakGpuBytes, occupiedSlots.cardinality());
    }

    void targetChanged(Object owner, int texture, int width, int height, double guiScale) {
        var next = new Target(owner, texture, width, height, guiScale);
        if (next.equals(target)) return;
        target = next;
        invalidateTarget();
    }

    void invalidateTarget() {
        targetEpoch++;
        TextReadabilityPainter.clearActualDiagnostics();
        history.clear();
        slots.clear();
        occupiedSlots.clear();
        tickets.clear();
        pending.clear();
        completed.clear();
        hovered = null;
        hoveredKey = null;
        consumed.replaceAll((request, pending) -> null);
    }

    private Result render(ModuleContext module, RenderService.Context context, Request request, boolean immediate) {
        host.moduleAccess(module);
        if (request == null) return Result.INVALID_REQUEST;
        if (!request.scope().isOpen()) return Result.SCOPE_CLOSED;
        var node = node(module, request.scope());
        var popup = popupLayer(node);
        var frame = screenFrame(node);
        Prepared item;
        try {
            item = prepare(request, node, popup, frame);
        } catch (IllegalArgumentException failure) {
            return Result.INVALID_REQUEST;
        }
        if (immediate && (context == null || context != activeForeground || activeForeground.node != node
                || activeForeground.frame != frame || activeForeground.popup != popup || !frame.isCurrent())) {
            return report(item, Result.INVALID_STAGE);
        }
        if (request.snapshot() != null && !currentSnapshot(item)) return report(item, Result.STALE_LAYOUT);
        if (request.groupOpacity() == 0 || (request.style().argb() >>> 24) == 0) return report(item, Result.HIDDEN);
        if (item.visible.empty() || request.run().bounds().empty()) {
            var sampling = item.policy.adaptiveColor() ? fallback(SamplingStatus.INVALID_REGION) : SamplingStatus.DISABLED;
            return report(item, Result.CLIPPED, null, null, -1, sampling);
        }
        if (!immediate) {
            var stage = submissionStage(frame, popup);
            if (stage != null) return report(item, stage);
        }
        var painted = item.policy.adaptiveColor() && !item.policy.localColor() ? withAuthoredColor(item) : item;
        if (painted == null) return report(item, Result.INVALID_REQUEST);
        if (!claim(frame, request)) return report(item, Result.ALREADY_CONSUMED);
        if (!immediate && painted.policy.foregroundProtection()) {
            consumed.put(request, painted);
            enqueue(frame, popup, request.foregroundZ(), graphics -> {
                if (consumed.replace(request, painted, null)) paint(painted, graphics);
            });
            return report(painted, Result.SUBMITTED);
        }
        return paint(painted, immediate ? activeForeground.graphics : frame.graphics());
    }

    private static Prepared withAuthoredColor(Prepared item) {
        var request = item.request;
        var style = request.style();
        int base = style.argb() & 0xFFFFFF;
        int[] authored = {-1};
        boolean uniform = request.run().glyphs().accept((index, glyphStyle, codePoint) -> {
            int rgb = glyphStyle.getColor() == null ? base : glyphStyle.getColor().getValue();
            if (authored[0] < 0) authored[0] = rgb;
            return authored[0] == rgb;
        });
        if (!uniform) return null;
        if (authored[0] < 0 || authored[0] == base) return item;
        int argb = (style.argb() & 0xFF000000) | authored[0];
        return item.withRequest(request.withStyle(new Style(argb, style.shadow(), style.usage(), style.lockHue(),
                style.allowOutline(), style.allowBacking())));
    }

    private Prepared prepare(Request request, ScopeLifecycle.Scope node, ScopeLifecycle.Scope popup, Frame frame) {
        var policy = resolvePolicy(node, popup).overlay(request.policyOverride());
        var layer = popup == null ? node.ancestor(ScopeKind.UI_TREE) : popup;
        var layerConfig = configurations.get(layer);
        if (layerConfig != null && layerConfig.policy.samplingMode() == TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY) {
            policy = policy.withSamplingMode(TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY);
        }
        var resolved = policy.resolve();
        if (resolved.lockHue() != null && resolved.lockHue() != request.style().lockHue()) {
            var style = request.style();
            request = request.withStyle(new Style(style.argb(), style.shadow(), style.usage(), resolved.lockHue(),
                    style.allowOutline(), style.allowBacking()));
        }
        var transform = request.transform();
        var visible = transform.map(request.run().bounds()).intersect(request.clip());
        double guiHeight = request.run().lineHeight() * Math.hypot(transform.xy(), transform.yy());
        double height = TextReadabilityPolicy.framebufferHeight(guiHeight, target == null ? 1 : target.guiScale);
        var screen = node.ancestor(ScopeKind.SCREEN);
        return new Prepared(request, node, popup, screen, frame, visible, height, resolved,
                resolved.targetContrast(request.style().usage(), height), targetEpoch, -1);
    }

    private Frame screenFrame(ScopeLifecycle.Scope node) {
        return VisualFrameCoordinator.screenFrame(screens.ownerOf(node));
    }

    private Result submissionStage(Frame frame, ScopeLifecycle.Scope popup) {
        if (frame == null) return Result.RENDER_UNAVAILABLE;
        if (popup == null) return frame.acceptsForeground() ? null : Result.QUEUE_CLOSED;
        if (activePopup == null || activePopup.scope != popup) return Result.INVALID_STAGE;
        return activePopup.sealed ? Result.QUEUE_CLOSED : null;
    }

    private void enqueue(Frame frame, ScopeLifecycle.Scope popup, int z, Consumer<GuiGraphics> draw) {
        if (popup == null) frame.deferForeground(z, draw);
        else activePopup.draws.add(new QueuedDraw(z, draw));
    }

    private boolean claim(Frame frame, Request request) {
        if (consumptionFrame != frame.id()) {
            consumed.clear();
            consumptionFrame = frame.id();
        }
        if (consumed.containsKey(request)) return false;
        consumed.put(request, null);
        return true;
    }

    private boolean currentSnapshot(Prepared item) {
        var snapshot = item.request.snapshot();
        if (snapshot == null) return true;
        if (item.frame == null || !snapshot.isCurrent() || snapshot.frameId() != item.frame.id()
                || snapshot.mountGeneration() != item.screen.generation()) return false;
        for (var scope = item.node; scope != null; scope = scope.parent()) {
            if (scope.generation() == snapshot.scopeGeneration()) return true;
        }
        return false;
    }

    private void runForeground(ScopeLifecycle.Scope node, ScopeLifecycle.Scope popup, Frame frame, GuiGraphics graphics,
                               Consumer<RenderService.Context> draw) {
        if (!node.isOpen() || !frame.isCurrent() || popupLayer(node) != popup) return;
        var viewport = screens.viewport();
        var context = new ForegroundPass(node, popup, frame, graphics,
                new RenderService.Frame(RenderService.Surface.SCREEN, viewport.width(), viewport.height(),
                        viewport.guiScale(), frame.partialTick()));
        activeForeground = context;
        try {
            GuiStateGuard.draw(() -> {
                try {
                    draw.accept(context);
                } finally {
                    graphics.flush();
                }
            });
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            host.report("Text foreground " + node.id(), failure);
        } finally {
            activeForeground = null;
            context.graphics = null;
        }
    }

    private Result paint(Prepared item, GuiGraphics graphics) {
        if (!item.node.isOpen()) return Result.SCOPE_CLOSED;
        if (!item.frame.isCurrent() || targetEpoch != item.epoch || popupLayer(item.node) != item.popup
                || !currentSnapshot(item)) return report(item, Result.STALE_LAYOUT);
        if (item.policy.adaptiveColor() && item.policy.localColor()) {
            try {
                var localized = paintLocal(item, graphics);
                if (localized != null) return localized;
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                host.report("Local text readability " + item.node.id(), failure);
                return report(item, Result.RENDER_FAILED);
            }
            var authored = withAuthoredColor(item);
            if (authored != null) return paintRun(authored, graphics, null);
            var preserved = item.request.withPolicy(item.request.policyOverride().overlay(
                    new TextReadabilityPolicy(false, null, null, null, null)));
            return paintRun(prepare(preserved, item.node, item.popup, item.frame), graphics, null);
        }
        return paintRun(item, graphics, null);
    }

    private Result paintLocal(Prepared item, GuiGraphics graphics) {
        var request = item.request;
        var run = request.run();
        var hint = request.sampling();
        if (target == null || request.groupOpacity() < 1 || hint == SamplingHint.DECLARED_ONLY
                || item.policy.samplingMode() == TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY
                && !hint.explicitlyMarked()) return null;
        var font = run.font() == null ? Minecraft.getInstance().font : run.font();
        if (font.getClass() != Font.class) return null;
        var clusters = NativeTextClusters.measure(run.glyphs(), (float) run.x(), request.style().argb(),
                font.getSplitter()::stringWidth);
        if (clusters.size() < 2) return null;
        int priority = priority(item);
        var grant = budget.offer(priority, 1);
        if (grant == null) return null;
        long sourceFrame = host.frameNumber();
        long started = System.nanoTime();
        TextReadabilityPainter.Scissor backdrop;
        try {
            backdrop = TextReadabilityPainter.captureLocalBackdrop(graphics, request, sourceFrame, grant.pixelAllowance());
        } finally {
            counters.cpuNanos += System.nanoTime() - started;
        }
        if (backdrop == null) return null;
        int copied = backdrop.width() * backdrop.height();
        budget.spendPixels(grant, copied);
        counters.pixels += copied;

        int previousSamples = counters.runs;
        Result result = Result.CLIPPED;
        Result failure = null;
        for (int i = 0; i < clusters.size(); i++) {
            var cluster = clusters.get(i);
            var bounds = new Rect(cluster.left(), run.bounds().y(), cluster.right() - (double) cluster.left(),
                    run.bounds().height()).intersect(run.bounds());
            var visible = request.transform().map(bounds).intersect(request.clip());
            if (bounds.empty() || visible.empty() && request.transform().map(bounds.outset(Insets.all(4)))
                    .intersect(request.clip()).empty()) continue;
            // Backing can extend into surrounding whitespace without covering adjacent clusters.
            double left = i == 0 ? 2 : Math.min(2, (cluster.left() - (double) clusters.get(i - 1).right()) / 2);
            double right = i + 1 == clusters.size() ? 2
                    : Math.min(2, (clusters.get(i + 1).left() - (double) cluster.right()) / 2);
            var local = new Local(cluster, backdrop, bounds.outset(new Insets(left, 1, right, 1)), priority);
            var style = request.style();
            var child = new Request(request.scope(), request.text(),
                    new Run(cluster.sequence(), cluster.left(), run.y(), bounds, run.lineHeight(), run.font()),
                    new Style(cluster.color(), style.shadow(), style.usage(), style.lockHue(), style.allowOutline(),
                            style.allowBacking()), request.transform(), request.clip(), request.groupOpacity(),
                    request.declaredBackgroundArgb(), request.foregroundZ(), request.snapshot(), hint,
                    request.policyOverride());
            var painted = paintRun(item.withRun(child, visible, cluster.index()), graphics, local);
            if (painted == Result.DRAWN) result = painted;
            else if (painted != Result.CLIPPED && failure == null) failure = painted;
        }
        var key = item.key();
        if (key != null) releaseSlot(key);
        var sampling = counters.runs > previousSamples ? SamplingStatus.LOCAL_GPU : SamplingStatus.BUDGET_EXHAUSTED;
        return report(item, failure == null ? result : failure, null, null, item.frame.id(), sampling);
    }

    private Result paintRun(Prepared item, GuiGraphics graphics, Local local) {
        var request = item.request;
        var key = item.key();
        var previous = key == null ? null : history.get(key);
        var frameNumber = item.frame.id();
        TextReadabilityReference.State state = null;
        TextReadabilityReference.Decision decision = null;
        Integer fill = null;
        Integer outline = null;
        Integer backing = null;
        SamplingStatus sampling = SamplingStatus.DISABLED;
        Result result;
        long started = System.nanoTime();
        try {
            if (item.policy.adaptiveColor()) {
                sampling = sample(item, graphics, local);
                if (sampling == SamplingStatus.GPU) {
                    return report(item, Result.DRAWN, null, null, frameNumber, sampling);
                }
                if (key != null) {
                    releaseSlot(key);
                    state = previous != null && previous.state != null
                            && frameNumber - previous.lastPaintFrame <= 1
                            ? previous.state : new TextReadabilityReference.State();
                }
                var style = request.style();
                var color = request.declaredBackgroundArgb();
                var background = color == null ? TextReadabilityReference.Background.unknown()
                        : TextReadabilityReference.Background.declared(color);
                var input = new TextReadabilityReference.Input(style.argb(), style.lockHue(), style.allowOutline(),
                        style.allowBacking(), item.contrast, request.groupOpacity(), item.height,
                        item.policy.stabilityNanos(), background, SAMPLING, List.of());
                decision = TextReadabilityReference.evaluate(input, state, host.nowNanos());
                if (decision.fillArgb() != style.argb()) fill = decision.fillArgb();
                if (decision.protection() == TextReadabilityReference.Protection.OUTLINE) outline = decision.protectionArgb();
                if (decision.protection() == TextReadabilityReference.Protection.BACKING) backing = decision.protectionArgb();
            } else if (key != null) releaseSlot(key);
            result = decision != null && decision.hidden() ? Result.HIDDEN
                    : TextReadabilityPainter.draw(graphics, request, fill, outline, backing,
                    local == null ? null : local.backing());
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            host.report("Text readability " + item.node.id(), failure);
            result = Result.RENDER_FAILED;
            if (key != null) releaseSlot(key);
        } finally {
            if (item.policy.adaptiveColor()) counters.cpuNanos += System.nanoTime() - started;
        }
        return report(item, result, decision, result == Result.DRAWN ? state : null, frameNumber, sampling);
    }

    private int priority(Prepared item) {
        var hint = item.request.sampling();
        return switch (hint) {
            case FOCUSED, AUTO_FOCUSED -> 3;
            case HOVERED, AUTO_HOVERED -> 2;
            default -> isHovered(item.visible) ? 2 : hint == SamplingHint.IMPORTANT ? 1 : 0;
        };
    }

    private SamplingStatus sample(Prepared item, GuiGraphics graphics, Local local) {
        var request = item.request;
        var hint = request.sampling();
        if (hint == SamplingHint.DECLARED_ONLY) return SamplingStatus.DECLARED_ONLY;
        if (item.policy.samplingMode() == TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY && !hint.explicitlyMarked()) {
            return SamplingStatus.EXPLICIT_ONLY;
        }
        if (target == null) return fallback(SamplingStatus.UNAVAILABLE);
        int priority = local == null ? priority(item) : local.priority();
        var visible = item.visible.intersect(new Rect(0, 0, target.width / target.guiScale, target.height / target.guiScale));
        if (visible.empty()) return fallback(SamplingStatus.INVALID_REGION);
        var grant = budget.offer(priority, 1);
        if (grant == null) return fallback(SamplingStatus.BUDGET_EXHAUSTED);

        var key = item.key();
        long now = host.nowNanos();
        var slot = key == null ? null : slot(key);
        var signature = new Signature(request.style(), item.policy.stabilityNanos(), item.contrast,
                local == null ? List.of() : local.cluster().glyphs());
        long previousFrame = slot == null ? -1 : slot.frame;
        long frame = item.frame.id();
        long elapsed = 0;
        if (slot != null) {
            if (slot.signature != null && (!signature.equals(slot.signature) || frame - previousFrame > 1)) {
                slot.generation = nextGeneration();
                invalidateDetail(key);
            } else if (slot.signature != null && previousFrame != frame) {
                elapsed = Math.max(0, now - slot.nanos);
            }
        }

        long sourceFrame = host.frameNumber();
        boolean observe = key != null && debugActive(key, item.visible);
        long ticket = observe ? ++nextTicket : 0;
        TextReadabilityPainter.ActualResult result;
        while (true) {
            result = TextReadabilityPainter.drawActual(graphics, request,
                    new TextReadabilityPainter.ActualInput(grant.samples(), slot == null ? -1 : slot.index,
                            slot == null ? 0 : slot.generation, elapsed, item.policy.stabilityNanos(), item.contrast,
                            item.height, SAMPLING, sourceFrame, ticket, grant.pixelAllowance(),
                            local == null ? null : local.backdrop(), local == null ? null : local.backing()));
            if (!result.pixelBudgetExhausted()) break;
            grant = budget.offer(grant.lane() - 1, 1);
            if (grant == null) return fallback(SamplingStatus.BUDGET_EXHAUSTED);
        }
        if (result.result() != Result.DRAWN) {
            return fallback(result.regionInvalid() || result.result() == Result.CLIPPED
                    ? SamplingStatus.INVALID_REGION : SamplingStatus.UNAVAILABLE);
        }
        budget.spend(grant, result.copiedPixels());
        counters.runs++;
        counters.samples += result.attemptedSamples();
        counters.pixels += result.copiedPixels();
        if (slot != null) {
            slot.signature = signature;
            slot.frame = frame;
            slot.nanos = now;
        }
        if (observe && result.telemetryQueued()) {
            tickets.put(ticket, new Ticket(key, sourceFrame, targetEpoch, slot.generation));
            pending.put(key, sourceFrame);
        }
        return SamplingStatus.GPU;
    }

    private SamplingStatus fallback(SamplingStatus status) {
        switch (status) {
            case BUDGET_EXHAUSTED -> counters.budgetFallbacks++;
            case INVALID_REGION -> counters.invalidRegions++;
            case UNAVAILABLE -> counters.unavailableFallbacks++;
            default -> throw new IllegalArgumentException("Unexpected sampling fallback " + status);
        }
        return status;
    }

    private Slot slot(Key key) {
        var current = slots.get(key);
        if (current != null) return current;
        int index = occupiedSlots.nextClearBit(0);
        if (index >= MAX_STATES) {
            for (var older : history.keySet()) {
                var evicted = slots.get(older);
                if (evicted == null) continue;
                index = evicted.index;
                releaseSlot(older);
                break;
            }
        }
        occupiedSlots.set(index);
        var next = new Slot(index, nextGeneration());
        slots.put(key, next);
        return next;
    }

    private int nextGeneration() {
        if (generation == Integer.MAX_VALUE) {
            TextReadabilityPainter.releaseGpuResources();
            generation = 0;
        }
        return ++generation;
    }

    private void releaseSlot(Key key) {
        var removed = slots.remove(key);
        if (removed == null) return;
        occupiedSlots.clear(removed.index);
        invalidateDetail(key);
    }

    private void invalidateDetail(Key key) {
        var last = completed.get(key);
        var awaiting = pending.remove(key);
        if (last != null || awaiting != null) {
            long sourceFrame = last == null ? awaiting : last.sourceFrame();
            completed.put(key, new GpuDetails(DetailStatus.INVALIDATED, sourceFrame, -1, 0, null, List.of()));
        }
        tickets.values().removeIf(ticket -> ticket.key.equals(key));
    }

    private void discardKeys(Predicate<Key> discard) {
        history.keySet().removeIf(key -> {
            if (!discard.test(key)) return false;
            releaseKey(key);
            return true;
        });
    }

    private void releaseKey(Key key) {
        releaseSlot(key);
        completed.remove(key);
        pending.remove(key);
        tickets.values().removeIf(ticket -> ticket.key.equals(key));
    }

    private boolean isHovered(Rect bounds) {
        if (pointer == null) pointer = UiMouseState.read();
        return pointer.available() && bounds.contains(pointer.x(), pointer.y());
    }

    private boolean scopeDebug(Key key) {
        for (var scope = key.scope; scope != null; scope = scope.parent()) {
            if (debugScopes.containsKey(scope)) return true;
        }
        return false;
    }

    private boolean debugActive(Key key, Rect bounds) {
        return scopeDebug(key) || overlayEnabled && isHovered(bounds);
    }

    private void removeDebugData() {
        if (overlayEnabled) return;
        if (debugScopes.isEmpty()) TextReadabilityPainter.clearActualDiagnostics();
        completed.keySet().removeIf(key -> !scopeDebug(key));
        pending.keySet().removeIf(key -> !scopeDebug(key));
        tickets.values().removeIf(ticket -> !scopeDebug(ticket.key));
    }

    private void receiveDetail(TextReadabilityPainter.ActualDiagnostic value) {
        var stamp = tickets.remove(value.diagnosticId());
        if (stamp == null || stamp.epoch != targetEpoch || stamp.frame != value.frame()
                || !stamp.key.scope.isOpen() || !history.containsKey(stamp.key)) return;
        var slot = slots.get(stamp.key);
        if (slot == null || slot.generation != stamp.generation) return;
        pending.remove(stamp.key, stamp.frame);
        var previous = completed.get(stamp.key);
        if (previous != null && previous.sourceFrame() > stamp.frame) return;
        var samples = value.samples().stream()
                .map(p -> new SamplePoint(p.x(), p.y(), p.luminance(), p.outlineCoverage())).toList();
        completed.put(stamp.key, new GpuDetails(DetailStatus.READY, stamp.frame, -1,
                value.attemptedSamples(), value.decision(), samples));
    }

    private void receiveCost(TextReadabilityPainter.ActualCost value) {
        if (host.frameNumber() - value.frame() > DETAIL_AGE_FRAMES) return;
        var timing = timings.computeIfAbsent(value.frame(), ignored -> new GpuTiming());
        timing.runs++;
        timing.nanos += value.gpuNanos();
    }

    private Diagnostic withDetails(Key key, Diagnostic diagnostic) {
        if (key == null || !debugActive(key, diagnostic.visibleBounds())) return diagnostic;
        long awaiting = pending.getOrDefault(key, -1L);
        var last = completed.get(key);
        long now = host.frameNumber();
        boolean pendingExpired = awaiting >= 0 && now - awaiting > DETAIL_AGE_FRAMES;
        long pendingFrame = pendingExpired ? -1 : awaiting;
        GpuDetails detail;
        if (diagnostic.sampling() != SamplingStatus.GPU) {
            if (last == null) return diagnostic;
            detail = new GpuDetails(DetailStatus.INVALIDATED, last.sourceFrame(), -1, 0, null, List.of());
        } else if (last != null && last.decision() != null && now - last.sourceFrame() <= DETAIL_AGE_FRAMES) {
            detail = new GpuDetails(DetailStatus.READY, last.sourceFrame(), pendingFrame, last.attemptedSamples(),
                    last.decision(), last.samples());
        } else {
            DetailStatus status;
            if (pendingFrame >= 0) status = DetailStatus.PENDING;
            else if (pendingExpired) status = DetailStatus.EXPIRED;
            else if (last != null) status = last.status() == DetailStatus.INVALIDATED
                    ? DetailStatus.INVALIDATED : DetailStatus.EXPIRED;
            else status = DetailStatus.BUSY;
            detail = new GpuDetails(status, last == null ? awaiting : last.sourceFrame(), pendingFrame, 0, null, List.of());
        }
        return new Diagnostic(diagnostic.sourceFrame(), diagnostic.scopeGeneration(), diagnostic.renderLayerGeneration(),
                diagnostic.text(), diagnostic.result(), diagnostic.policy(), diagnostic.targetContrast(),
                diagnostic.visibleBounds(), diagnostic.decision(), diagnostic.sampling(), diagnostic.effectiveClip(), detail);
    }

    boolean toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        if (!overlayEnabled) {
            hovered = null;
            hoveredKey = null;
            removeDebugData();
        }
        return overlayEnabled;
    }

    void renderOverlay(GuiGraphics graphics) {
        if (!overlayEnabled || hovered == null) return;
        TextReadabilityOverlay.render(graphics, withDetails(hoveredKey, hovered));
    }

    private Result report(Prepared item, Result result) {
        var key = item.key();
        var previous = key == null ? null : history.get(key);
        return report(item, result, null, previous == null ? null : previous.state,
                previous == null ? -1 : previous.lastPaintFrame, SamplingStatus.DISABLED);
    }

    private Result report(Prepared item, Result result, TextReadabilityReference.Decision decision,
                          TextReadabilityReference.State state, long lastPaintFrame, SamplingStatus sampling) {
        var key = item.key();
        if (!item.node.isOpen()) return result;
        var layer = item.popup == null ? item.screen : item.popup;
        var diagnostic = new Diagnostic(host.frameNumber(), item.node.generation(), layer.generation(),
                item.request.text(), result, item.policy, item.contrast, item.visible, decision, sampling,
                item.request.clip(), DETAIL_OFF);
        if (overlayEnabled && sampling != SamplingStatus.LOCAL_GPU && result != Result.SUBMITTED
                && isHovered(item.visible)) {
            hovered = diagnostic;
            hoveredKey = key;
        }
        if (key == null) return result;
        history.remove(key);
        if (history.size() == MAX_STATES) {
            var entries = history.keySet().iterator();
            var discarded = entries.next();
            entries.remove();
            releaseKey(discarded);
        }
        history.put(key, new Entry(host.nowNanos(), diagnostic, state, lastPaintFrame));
        return result;
    }

    private ScopeLifecycle.Scope node(ModuleContext module, ScopeHandle scope) {
        var node = host.ownedNode(module, scope);
        if (node.ancestor(ScopeKind.SCREEN) == null) throw new IllegalArgumentException("Text requires a Screen tree");
        return node;
    }

    private void bind(ScopeLifecycle.Scope node, Configuration config) {
        if (!configurations.containsKey(node)) {
            host.onScopeClose(node, () -> {
                configurations.remove(node);
                discardKeys(key -> key.scope.isWithin(node));
            });
        }
        configurations.put(node, config);
    }

    private ScopeLifecycle.Scope popupLayer(ScopeLifecycle.Scope node) {
        for (var current = node; current != null; current = current.parent()) {
            var config = configurations.get(current);
            if (config != null && config.popup != null) return current;
        }
        return null;
    }

    private TextReadabilityPolicy resolvePolicy(ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer) {
        var defaults = TextReadabilityPolicy.defaults();
        if (layer == null) return defaults.overlay(overrides(node, node.ancestor(ScopeKind.UI_TREE)));
        var popup = configurations.get(layer).popup;
        if (!popup.theme.isClosed()) {
            defaults = defaults.overlay(new TextReadabilityPolicy(popup.theme.resolve(ThemeTokens.TEXT_ADAPTIVE_COLOR),
                    popup.theme.resolve(ThemeTokens.TEXT_FOREGROUND_PROTECTION), null, null, null));
        }
        if (popup.page != null && popup.page.isOpen()) defaults = defaults.overlay(overrides(popup.page, popup.page));
        return defaults.overlay(overrides(node, layer));
    }

    private TextReadabilityPolicy overrides(ScopeLifecycle.Scope node, ScopeLifecycle.Scope layer) {
        var result = TextReadabilityPolicy.inherit();
        for (var current = node; ; current = current.parent()) {
            var config = configurations.get(current);
            if (config != null) result = config.policy.overlay(result);
            if (current == layer) return result;
        }
    }

    private record Popup(ThemeScope theme, ScopeLifecycle.Scope page) {}
    private record Configuration(TextReadabilityPolicy policy, Popup popup) {}
    private record Key(ScopeLifecycle.Scope scope, TextId text, int clusterIndex) {
        Key(ScopeLifecycle.Scope scope, TextId text) { this(scope, text, -1); }
    }
    private record Entry(long seen, Diagnostic diagnostic, TextReadabilityReference.State state, long lastPaintFrame) {}
    private record Target(Object owner, int texture, int width, int height, double guiScale) {}
    private record Ticket(Key key, long frame, long epoch, int generation) {}
    private record Signature(Style style, long stabilityNanos, double target, List<NativeTextClusters.Glyph> glyphs) {}
    private record Local(NativeTextClusters.Cluster cluster, TextReadabilityPainter.Scissor backdrop,
                         Rect backing, int priority) {}

    private static final class Slot {
        private final int index;
        private int generation;
        private Signature signature;
        private long frame = -1;
        private long nanos;

        private Slot(int index, int generation) {
            this.index = index;
            this.generation = generation;
        }
    }

    private static final class GpuTiming {
        private static final GpuTiming EMPTY = new GpuTiming();
        private int runs;
        private long nanos;
    }

    private static final class Counters {
        private int runs;
        private int samples;
        private int pixels;
        private int budgetFallbacks;
        private int unavailableFallbacks;
        private int invalidRegions;
        private long cpuNanos;

        private void reset() {
            runs = samples = pixels = budgetFallbacks = unavailableFallbacks = invalidRegions = 0;
            cpuNanos = 0;
        }

        private SamplingStatistics snapshot(long frame, long gpuFrame, GpuTiming timing, long bytes, long peak,
                                            int slots) {
            return new SamplingStatistics(frame, runs, samples, pixels, budgetFallbacks, unavailableFallbacks,
                    invalidRegions, cpuNanos, gpuFrame, timing.runs, timing.nanos, bytes, peak, slots);
        }
    }

    private record Prepared(Request request, ScopeLifecycle.Scope node, ScopeLifecycle.Scope popup,
                            ScopeLifecycle.Scope screen, Frame frame, Rect visible, double height,
                            TextReadabilityPolicy.Resolved policy, double contrast, long epoch, int clusterIndex) {
        Key key() { return request.text() == null ? null : new Key(node, request.text(), clusterIndex); }
        Prepared withRequest(Request replacement) {
            return new Prepared(replacement, node, popup, screen, frame, visible, height, policy, contrast, epoch,
                    clusterIndex);
        }
        Prepared withRun(Request replacement, Rect clusterBounds, int clusterIndex) {
            return new Prepared(replacement, node, popup, screen, frame, clusterBounds, height, policy, contrast,
                    epoch, clusterIndex);
        }
    }

    private record QueuedDraw(int z, Consumer<GuiGraphics> draw) {}

    private static final class PopupPass {
        private final ScopeLifecycle.Scope scope;
        private final ArrayList<QueuedDraw> draws = new ArrayList<>();
        private boolean sealed;

        private PopupPass(ScopeLifecycle.Scope scope) { this.scope = scope; }
    }

    private final class ForegroundPass implements RenderService.Context {
        private final ScopeLifecycle.Scope node;
        private final ScopeLifecycle.Scope popup;
        private final Frame frame;
        private final RenderService.Frame viewport;
        private GuiGraphics graphics;

        private ForegroundPass(ScopeLifecycle.Scope node, ScopeLifecycle.Scope popup, Frame frame,
                               GuiGraphics graphics, RenderService.Frame viewport) {
            this.node = node;
            this.popup = popup;
            this.frame = frame;
            this.graphics = graphics;
            this.viewport = viewport;
        }

        private void requireLive() {
            host.assertOwner();
            if (activeForeground != this || !frame.isCurrent() || !node.isOpen()) {
                throw new IllegalStateException("Text foreground context has expired");
            }
        }

        @Override public RenderService.Frame frame() {
            requireLive();
            return viewport;
        }

        @Override public void fill(int x, int y, int right, int bottom, int argb) {
            requireLive();
            graphics.fill(x, y, right, bottom, argb);
        }

        @Override public void text(Component label, int x, int y, int argb) {
            requireLive();
            graphics.drawString(Minecraft.getInstance().font, Objects.requireNonNull(label), x, y, argb, false);
        }

        @Override public void clip(int x, int y, int right, int bottom, Runnable draw) {
            requireLive();
            graphics.enableScissor(x, y, right, bottom);
            try {
                draw.run();
            } finally {
                graphics.disableScissor();
            }
        }

        @Override public void transform(double x, double y, float scale, Runnable draw) {
            requireLive();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Float.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Invalid render transform");
            }
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(scale, scale, 1);
                draw.run();
            } finally {
                graphics.pose().popPose();
            }
        }
    }
}
