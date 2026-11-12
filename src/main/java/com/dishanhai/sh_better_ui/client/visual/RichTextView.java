// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Anchor;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextLayout;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.opengl.GL14;

import java.net.URI;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Client-thread rich text on an existing UUK visual layout node. */
public final class RichTextView implements Subscription {
    public enum MediaStatus { UNREQUESTED, LOADING, READY, UNAVAILABLE }

    /** Captures the geometry and texture readiness of one successful presentation. */
    public record Presentation(FrameViewSnapshot frame, RichLayoutSnapshot layout, Transform localToGui,
                               long resourceVersion, Set<UiId> availableTextures) {
        public Presentation {
            availableTextures = Set.copyOf(availableTextures);
        }
    }

    private record Prepared(RichDocument source, RichDocument document, VanillaFont metrics, Language language,
                            int maxWidth, RichLayoutSnapshot layout) {}
    private record Painted(Presentation presentation, AlignmentGeometry.Rect clip,
                           Map<UiId, RichTextExtensions.Hit> extensionHits) {}
    private record ExtensionFrame(RichTextExtensions registry, RichTextExtensions.Frame snapshot,
                                  RichTextExtensions.GeometryBudget budget) {}
    private record Readability(ModuleContext module, TextReadabilityService service, TextReadabilityPolicy policy,
                               TextReadabilityService.SamplingHint sampling, UiId dataItem) {}
    private interface ReadableFragment {
        boolean draw(Fragment first, Rect bounds, FormattedCharSequence sequence, int color, boolean hovered);
    }

    private final ScopeHandle scope;
    private final Node node;
    private final RichTextImages images = new RichTextImages();
    private final RichInlineInteraction interaction = new RichInlineInteraction(this);
    private final Map<Fragment, FormattedCharSequence> sequences = new LinkedHashMap<>(64, 0.75f, true);
    private final Subscription resourceObserver;
    private RichTextExtensions extensions;
    private Readability readability;
    private final String readabilityPlacement;
    private long resourceVersion;
    private Prepared prepared;
    private Painted painted;
    private RichTextSelection selection;
    private Map<UiId, RichTextSelection.Span> selectedSpans = Map.of();

    private RichTextView(ModuleContext module, ScopeHandle scope, Node node) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.node = Objects.requireNonNull(node, "node");
        readabilityPlacement = java.util.HexFormat.of().formatHex(node.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var resources = module.engine().require(UIApis.RESOURCES);
        resourceVersion = resources.version();
        resourceObserver = resources.observe(module, scope, generation -> {
            resourceVersion = generation;
            invalidate();
            images.clear();
            interaction.clearResources();
        });
        scope.track(this);
    }

    /** Attach to the same owned Screen scope used for the visual layout binding. */
    public static RichTextView attach(ModuleContext module, ScopeHandle scope, Node node) {
        RenderSystem.assertOnRenderThread();
        return new RichTextView(Objects.requireNonNull(module, "module"), scope, node);
    }

    /** Create an image node from a full resource path such as minecraft:textures/item/diamond.png. */
    public static ImageNode image(UiId id, ResourceLocation texture, int intrinsicWidth, int intrinsicHeight,
                                  String altText) {
        Objects.requireNonNull(texture, "texture");
        return new ImageNode(id, new UiId(texture.getNamespace(), texture.getPath()),
                intrinsicWidth, intrinsicHeight, altText);
    }

    /** Opt in to a remote image for one explicit texture alias in this view. */
    public void bindRemoteImage(UiId texture, URI uri, RichTextMediaPolicy policy) {
        requireActive();
        images.bindRemote(texture, uri, policy);
        invalidate();
    }

    /** The current scope's media status. Check availableTextures for the exact presented frame. */
    public MediaStatus mediaStatus(UiId texture) {
        requireActive();
        return images.status(Objects.requireNonNull(texture, "texture"));
    }

    /** Use an explicitly supplied, scoped registry of trusted inline renderers. */
    public void bindExtensions(RichTextExtensions registry) {
        requireActive();
        extensions = Objects.requireNonNull(registry, "registry");
        painted = null;
    }

    /** Opts this view into the existing text service using its measured, indivisible native style fragments. */
    public RichTextView readability(ModuleContext module, TextReadabilityPolicy policy) {
        return readability(module, policy, TextReadabilityService.SamplingHint.DEFAULT);
    }

    /** Sets an explicit sampling hint for this view, including on an explicit-only page. */
    public RichTextView readability(ModuleContext module, TextReadabilityPolicy policy,
                                    TextReadabilityService.SamplingHint sampling) {
        requireActive();
        var service = module.engine().require(UIApis.TEXT_READABILITY);
        service.policy(module, scope); // Validate ownership before storing the binding.
        readability = new Readability(module, service, Objects.requireNonNull(policy, "policy"),
                Objects.requireNonNull(sampling, "sampling"), readability == null ? null : readability.dataItem());
        return this;
    }

    /** Assign the current data identity before reusing this view in a virtualized row. */
    public RichTextView readabilityDataItem(UiId dataItem) {
        requireActive();
        var current = Objects.requireNonNull(readability, "Enable readability before assigning a data item");
        if (Objects.equals(current.dataItem(), dataItem)) return this;
        retireReadabilityItem();
        readability = new Readability(current.module(), current.service(), current.policy(), current.sampling(), dataItem);
        return this;
    }

    private void retireReadabilityItem() {
        if (readability != null && readability.dataItem() != null && scope.isOpen()) {
            readability.service().retireDataItem(readability.module(), scope, readability.dataItem());
        }
    }

    /** Measure before the visual layout resolves. The returned size can be used to arrange the host node. */
    public RichLayoutSnapshot prepare(RichDocument document, Font font, int maxWidth) {
        return prepare(document, Map.of(), font, maxWidth);
    }

    /** Keep native styles (including trusted metadata) from the Component adapter. */
    public RichLayoutSnapshot prepare(RichTextComponents.Snapshot component, Font font, int maxWidth) {
        return prepare(component.document(), component.nativeStyles(), font, maxWidth);
    }

    private RichLayoutSnapshot prepare(RichDocument document, Map<UiId, Style> styles, Font font, int maxWidth) {
        requireActive();
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(font, "font");
        var language = Language.getInstance();
        if (prepared == null || prepared.metrics().font() != font || prepared.maxWidth() != maxWidth
                || prepared.language() != language || !prepared.source().equals(document)
                || !prepared.metrics().styles().equals(styles)) {
            boolean changed = prepared == null || prepared.language() != language || !prepared.source().equals(document);
            var resolved = changed ? resolve(document) : prepared.document();
            var metrics = new VanillaFont(font, styles, new HashMap<>());
            var layout = RichTextLayout.layout(resolved, maxWidth, metrics);
            painted = null;
            sequences.clear();
            if (changed) clearSelection();
            prepared = new Prepared(document, resolved, metrics, language, maxWidth, layout);
        }
        return prepared.layout();
    }

    /**
     * Draw at the node's local origin. The host must set an AlignmentFrame clip at that origin with
     * a width at most the prepared maxWidth, so an overwide grapheme stays within the content viewport.
     */
    public void draw(VisualLayoutBinding.LayoutPass pass, int argb) {
        draw(pass, argb, UiPreferences.configured(), RichTextEffects.NONE, 0, Double.NaN, Double.NaN);
    }

    /** Present a scrolled document under the host node's unchanged clip and visual effect geometry. */
    public void draw(VisualLayoutBinding.LayoutPass pass, int argb, UiPreferences preferences,
                     RichTextEffects effects, int scrollY, double mouseX, double mouseY) {
        draw(pass, argb, preferences, effects, scrollY, mouseX, mouseY, null);
    }

    /** The optional tooltip pass runs in Screen coordinates if effects defer the content to Screen Post. */
    public void draw(VisualLayoutBinding.LayoutPass pass, int argb, UiPreferences preferences,
                     RichTextEffects effects, int scrollY, double mouseX, double mouseY,
                     Consumer<GuiGraphics> deferredTooltip) {
        draw(pass, argb, preferences, effects, scrollY, mouseX, mouseY, deferredTooltip, -1);
    }

    /** Seek the inline media for an authoring preview while the layout keeps its normal frame clock. */
    public void drawAtMediaTime(VisualLayoutBinding.LayoutPass pass, int argb, UiPreferences preferences,
                                int scrollY, double mouseX, double mouseY, long mediaElapsedMs) {
        if (mediaElapsedMs < 0) throw new IllegalArgumentException("Negative media time");
        draw(pass, argb, preferences, RichTextEffects.NONE, scrollY, mouseX, mouseY, null, mediaElapsedMs);
    }

    /** Duration of the longest loaded inline image cycle, in milliseconds. Static images contribute zero. */
    public long mediaCycleMs() {
        requireActive();
        return images.longestCycleMs();
    }

    private void draw(VisualLayoutBinding.LayoutPass pass, int argb, UiPreferences preferences,
                      RichTextEffects effects, int scrollY, double mouseX, double mouseY,
                      Consumer<GuiGraphics> deferredTooltip, long mediaElapsedMs) {
        requireActive();
        if (scrollY < 0) throw new IllegalArgumentException("Negative scroll offset");
        var content = Objects.requireNonNull(prepared, "Prepare rich text before drawing");
        var frame = pass.snapshot();
        if (frame.scopeGeneration() != scope.generation() || !frame.scopeId().equals(scope.id())) {
            throw new IllegalArgumentException("Rich text requires the visual layout of its own Screen scope");
        }
        painted = null;
        var ready = images.resolve(content.source(), Minecraft.getInstance().getResourceManager(),
                frame.nowMs(), preferences.reducedMotion(), mediaElapsedMs);
        var activeExtensions = extensions;
        var extensionFrame = activeExtensions == null ? null : new ExtensionFrame(activeExtensions,
                activeExtensions.sample(), new RichTextExtensions.GeometryBudget());
        var binding = readability;
        var inherited = binding == null ? null : binding.service().policy(binding.module(), scope);
        boolean adaptive = binding != null && enabled(binding.policy().adaptiveColor(), inherited.adaptiveColor());
        boolean protect = binding != null && enabled(binding.policy().foregroundProtection(), inherited.foregroundProtection());
        boolean localColor = binding != null && enabled(binding.policy().localColor(), inherited.localColor());
        pass.at(node, visual -> {
            var sample = effects.drawBackdrop(visual, frame, preferences);
            var view = visual.view();
            var pose = view.transform().compose(Transform.translation(0, -scrollY));
            BiConsumer<GuiGraphics, RenderService.Context> draw = (graphics, nativeContext) -> {
                if (!isActive() || prepared != content || extensions != activeExtensions || readability != binding) return;
                var target = VisualCanvas.target();
                if (target == null) return;
                var clip = VisualCanvas.clip(frame.clip(), view).intersect(VisualCanvas.screenClip(target));
                var presentation = new Presentation(frame, content.layout(), pose, resourceVersion, ready.keySet());
                var candidate = new Painted(presentation, clip, Map.of());
                var extensionHits = new HashMap<UiId, RichTextExtensions.Hit>();
                var hover = hit(candidate, mouseX, mouseY).orElse(null);
                ReadableFragment readable = adaptive || protect ? (fragment, bounds, sequence, color, hovered) ->
                        paintReadable(binding, nativeContext, frame, pose, clip, fragment, bounds, sequence, color,
                                effects.foregroundZ(), sample.shadow(), hovered, content.metrics().font()) : null;
                sample.drawReadabilityPlate(graphics);
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(0, -scrollY, 0);
                    paint(graphics, content, ready, extensionFrame, extensionHits, visibleLines(candidate), argb,
                            sample, hover, preferences, frame.nowMs(), readable, localColor);
                } finally {
                    graphics.pose().popPose();
                }
                graphics.flush();
                if (isActive() && prepared == content && extensions == activeExtensions) {
                    painted = new Painted(presentation, clip, Map.copyOf(extensionHits));
                }
            };
            Consumer<GuiGraphics> after = deferredTooltip == null || !frame.hitTest(node, mouseX, mouseY)
                    ? null : graphics -> {
                        if (painted != null && painted.presentation().frame() == frame) deferredTooltip.accept(graphics);
                    };
            if (protect || adaptive && sample.deferred()) {
                var scheduled = binding.service().foreground(binding.module(), scope, effects.foregroundZ(),
                        visual.serviceForeground(draw, after));
                if (scheduled != TextReadabilityService.Result.SUBMITTED) {
                    visual.draw(graphics -> draw.accept(graphics, null));
                }
            } else if (sample.deferred()) {
                visual.foreground(effects.foregroundZ(), foreground -> draw.accept(foreground.graphics(), null), after);
            } else {
                visual.draw(graphics -> draw.accept(graphics, null));
            }
        });
    }

    private static boolean enabled(Boolean override, boolean inherited) {
        return override == null ? inherited : override;
    }

    private boolean paintReadable(Readability binding, RenderService.Context context, FrameViewSnapshot frame,
                                  Transform pose, AlignmentGeometry.Rect clip, Fragment fragment,
                                  Rect box, FormattedCharSequence sequence, int color, int z, boolean shadow,
                                  boolean hovered, Font font) {
        if (box.width() == 0 || box.height() == 0) return false;
        var source = fragment.node().id();
        var id = new TextId(source, binding.dataItem(), new UiId(source.namespace(),
                "rich_fragment/" + readabilityPlacement + "/" + fragment.textOffset()
                        + (box.width() == fragment.bounds().width() ? "" : "/joined")));
        var run = new TextReadabilityService.Run(sequence, box.x(), box.y(),
                new AlignmentGeometry.Rect(box.x(), box.y(), box.width(), box.height()), box.height(), font);
        var style = new TextReadabilityService.Style(color, shadow, TextReadabilityPolicy.Usage.BODY);
        var request = new TextReadabilityService.Request(scope, id, run, style, pose, clip, 1, null, z, frame)
                .withPolicy(binding.policy());
        var sampling = binding.sampling();
        if (hovered && sampling == TextReadabilityService.SamplingHint.DEFAULT) {
            sampling = TextReadabilityService.SamplingHint.AUTO_HOVERED;
        }
        request = request.withSampling(sampling);
        var service = binding.service();
        var result = context == null ? service.submit(binding.module(), request)
                : service.drawNow(binding.module(), context, request);
        return switch (result) {
            case SUBMITTED, DRAWN, CLIPPED, HIDDEN -> true;
            default -> false;
        };
    }

    public Optional<Presentation> currentSnapshot() {
        return currentPainted().map(Painted::presentation);
    }

    /** Probe rendered fragments in GUI coordinates. Geometry queries do not consume native input. */
    public Optional<Fragment> hitTest(double guiX, double guiY) {
        return currentPainted().flatMap(rendered -> hit(rendered, guiX, guiY));
    }

    private static Optional<Fragment> hit(Painted rendered, double guiX, double guiY) {
        if (!rendered.clip().contains(guiX, guiY)) return Optional.empty();
        var point = local(rendered.presentation().localToGui(), guiX, guiY);
        if (point == null) return Optional.empty();
        return rendered.presentation().layout().hitTest(point.x(), point.y()).filter(fragment -> {
            if (!(fragment.node() instanceof RichExtensionNode)) return true;
            var extensionHit = rendered.extensionHits().get(fragment.node().id());
            return extensionHit != null && extensionHit.contains(point.x(), point.y());
        });
    }

    public RichInlineInteraction interaction() { return interaction; }

    public boolean isInViewport(double guiX, double guiY) {
        return currentPainted().filter(rendered -> rendered.clip().contains(guiX, guiY)).isPresent();
    }

    /** The host decides how to handle trusted native metadata. No actions are dispatched here. */
    public Optional<Style> componentStyleAt(double guiX, double guiY) {
        return hitTest(guiX, guiY).map(hit -> prepared.metrics().styles().get(hit.node().id()));
    }

    public Optional<RichTextSelection.Position> caretAt(double guiX, double guiY) {
        return currentPainted().flatMap(frame -> hit(frame, guiX, guiY)
                .filter(fragment -> fragment.node() instanceof TextRun && !needsBidi(fragment.text()))
                .map(fragment -> RichTextSelection.caret(fragment,
                        local(frame.presentation().localToGui(), guiX, guiY).x(), prepared.metrics())));
    }

    /** Resolves a drag endpoint to the nearest text caret on its visible line, including the line margins. */
    public Optional<RichTextSelection.Position> selectionCaretAt(double guiX, double guiY) {
        return currentPainted().flatMap(rendered -> {
            if (!rendered.clip().contains(guiX, guiY)) return Optional.empty();
            var point = local(rendered.presentation().localToGui(), guiX, guiY);
            if (point == null) return Optional.empty();
            var lines = rendered.presentation().layout().visibleLines(point.y(), Math.nextUp(point.y()));
            return lines.stream().flatMap(line -> line.fragments().stream())
                    .filter(fragment -> fragment.node() instanceof TextRun)
                    .min(Comparator.comparingDouble(fragment -> distanceFromText(fragment.bounds(), point.x())))
                    .filter(fragment -> !needsBidi(fragment.text()))
                    .map(fragment -> RichTextSelection.caret(fragment, point.x(), prepared.metrics()));
        });
    }

    private static double distanceFromText(Rect bounds, double x) {
        return Math.max(0, Math.max(bounds.x() - x, x - bounds.x() - bounds.width()));
    }

    public void select(RichTextSelection range) {
        requireActive();
        selectedSpans = range.spans(Objects.requireNonNull(prepared, "Prepare before selecting").document());
        selection = range;
    }

    public String selectedText() {
        requireActive();
        return selection == null ? "" : selection.copy(prepared.document());
    }

    public void clearSelection() {
        selection = null;
        selectedSpans = Map.of();
    }

    boolean isVisible(Fragment fragment) {
        return currentPainted().filter(rendered -> {
            var box = fragment.bounds();
            if (box.x() >= rendered.presentation().layout().width()) return false;
            int width = Math.min(box.width(), rendered.presentation().layout().width() - box.x());
            return rendered.presentation().localToGui()
                    .map(new AlignmentGeometry.Rect(box.x(), box.y(), width, box.height()))
                    .overlaps(rendered.clip());
        }).isPresent();
    }

    double localX(double guiX, double guiY) {
        return currentPainted().map(rendered -> local(rendered.presentation().localToGui(), guiX, guiY))
                .map(Anchor::x).orElse(Double.NaN);
    }

    private static Anchor local(Transform pose, double x, double y) {
        double determinant = pose.xx() * pose.yy() - pose.xy() * pose.yx();
        if (determinant == 0 || !Double.isFinite(determinant)) return null;
        double dx = x - pose.tx(), dy = y - pose.ty();
        double localX = (pose.yy() * dx - pose.xy() * dy) / determinant;
        double localY = (pose.xx() * dy - pose.yx() * dx) / determinant;
        return Double.isFinite(localX) && Double.isFinite(localY) ? new Anchor(localX, localY) : null;
    }

    private static List<RichLayoutSnapshot.LineBox> visibleLines(Painted rendered) {
        var clip = rendered.clip();
        var pose = rendered.presentation().localToGui();
        var a = local(pose, clip.x(), clip.y());
        var b = local(pose, clip.right(), clip.y());
        var c = local(pose, clip.x(), clip.bottom());
        var d = local(pose, clip.right(), clip.bottom());
        if (a == null || b == null || c == null || d == null) return List.of();
        return rendered.presentation().layout().visibleLines(
                Math.min(Math.min(a.y(), b.y()), Math.min(c.y(), d.y())),
                Math.max(Math.max(a.y(), b.y()), Math.max(c.y(), d.y())));
    }

    List<RichLayoutSnapshot.LineBox> visibleLines() {
        return currentPainted().map(RichTextView::visibleLines).orElse(List.of());
    }

    private Optional<Painted> currentPainted() {
        RenderSystem.assertOnRenderThread();
        return isActive() && painted != null && painted.presentation().frame().isCurrent()
                ? Optional.of(painted) : Optional.empty();
    }

    /** Discard the old presentation before a viewport change is drawn. */
    public void invalidate() {
        RenderSystem.assertOnRenderThread();
        interaction.cancel();
        prepared = null;
        painted = null;
        sequences.clear();
        clearSelection();
    }

    @Override public boolean isActive() { return scope.isOpen() && resourceObserver.isActive(); }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        resourceObserver.close();
        invalidate();
        images.close();
        extensions = null;
        retireReadabilityItem();
        readability = null;
        interaction.clear();
    }

    private void requireActive() {
        RenderSystem.assertOnRenderThread();
        if (!isActive()) throw new IllegalStateException("Rich text scope is closed");
    }

    @SuppressWarnings("deprecation") // The two supported GuiGraphics versions expose the same managed batch.
    private void paint(GuiGraphics graphics, Prepared content, Map<UiId, RichTextImages.Image> images,
                       ExtensionFrame extensions, Map<UiId, RichTextExtensions.Hit> extensionHits,
                       List<RichLayoutSnapshot.LineBox> lines,
                       int argb, RichTextEffects.Sample effects, Fragment hover, UiPreferences preferences, long nowMs,
                       ReadableFragment readable, boolean localColor) {
        var metrics = content.metrics();
        var font = metrics.font();
        for (var line : lines) {
            var fragments = line.fragments();
            for (int i = 0; i < fragments.size(); i++) {
                var fragment = fragments.get(i);
                var box = fragment.bounds();
                if (fragment.node() instanceof TextRun run) {
                    if (readable != null && !localColor && selectedSpans.isEmpty()) {
                        int joined = paintJoinedText(metrics, fragments, i, argb, hover, readable);
                        if (joined > i) {
                            i = joined;
                            continue;
                        }
                    }
                    int end = i + 1;
                    while (readable == null && selectedSpans.isEmpty() && end < fragments.size()
                            && fragments.get(end).node() instanceof TextRun next
                            && run.style().equals(next.style())
                            && Objects.equals(metrics.styles().get(run.id()), metrics.styles().get(next.id()))) {
                        end++;
                    }
                    if (end == i + 1) {
                        paintText(graphics, metrics, fragment, effects, argb, readable, fragment == hover);
                    } else {
                        var batch = fragments.subList(i, end);
                        graphics.drawManaged(() -> {
                            for (var text : batch) paintText(graphics, metrics, text, effects, argb, null, false);
                        });
                        i = end - 1;
                    }
                } else if (fragment.node() instanceof ImageNode image && box.width() > 0 && box.height() > 0) {
                    var ready = images.get(image.texture());
                    if (ready == null) paintPlaceholder(graphics, font, box, image.altText());
                    else paintImage(graphics, box, ready);
                } else if (fragment.node() instanceof RichObjectNode object
                        && !RichInlinePainter.paint(graphics, font, fragment, images,
                        nowMs, preferences.reducedMotion(),
                        interaction.state(object, fragment == hover), interaction)) {
                    paintPlaceholder(graphics, font, box, object.altText());
                } else if (fragment.node() instanceof RichExtensionNode extension && box.width() > 0 && box.height() > 0) {
                    var result = RichTextExtensions.PaintResult.UNAVAILABLE;
                    if (extensions != null) {
                        result = extensions.registry().paint(graphics, font, extension, box, nowMs,
                                preferences.reducedMotion(), extensions.snapshot(), extensions.budget());
                    }
                    if (result.placeholder()) paintPlaceholder(graphics, font, box, extension.altText());
                    extensionHits.put(extension.id(), result.hit());
                }
            }
        }
    }

    private int paintJoinedText(VanillaFont metrics, List<Fragment> fragments, int start, int color, Fragment hover,
                                ReadableFragment readable) {
        var first = fragments.get(start);
        var run = (TextRun) first.node();
        var style = metrics.style(run);
        if (!style.getFont().equals(Style.DEFAULT_FONT) || style.getClickEvent() != null || style.getHoverEvent() != null
                || style.getInsertion() != null || style.isUnderlined() || style.isStrikethrough() || style.isObfuscated()) {
            return start;
        }
        int end = start;
        while (end + 1 < fragments.size()) {
            var current = fragments.get(end);
            var next = fragments.get(end + 1);
            if (!(next.node() instanceof TextRun nextRun) || !run.style().equals(nextRun.style())
                    || !style.equals(metrics.style(nextRun)) || !canJoin(current.bounds(), next.bounds(),
                    metrics.font().getSplitter().stringWidth(sequence(metrics, current)))) break;
            end++;
        }
        if (end == start) return start;
        var box = first.bounds();
        var last = fragments.get(end).bounds();
        var bounds = new Rect(box.x(), box.y(), last.x() + last.width() - box.x(), box.height());
        var group = fragments.subList(start, end + 1);
        var sequences = group.stream().map(fragment -> sequence(metrics, fragment)).toList();
        FormattedCharSequence joined = sink -> {
            for (var sequence : sequences) {
                if (!sequence.accept(sink)) return false;
            }
            return true;
        };
        boolean hovered = hover != null && group.contains(hover);
        return readable.draw(first, bounds, joined, originalColor(style, color), hovered) ? end : start;
    }

    static boolean canJoin(Rect first, Rect second, float advance) {
        return first.width() > 0 && second.width() > 0 && first.y() == second.y() && first.height() == second.height()
                && first.width() == second.x() - first.x() && advance == first.width();
    }

    private void paintText(GuiGraphics graphics, VanillaFont metrics, Fragment fragment, RichTextEffects.Sample effects,
                           int argb, ReadableFragment readable, boolean hovered) {
        var run = (TextRun) fragment.node();
        paintSelection(graphics, metrics, run, fragment, effects);
        var sequence = sequence(metrics, fragment);
        if (readable != null && readable.draw(fragment, fragment.bounds(), sequence,
                originalColor(metrics.style(run), argb), hovered)) return;
        effects.drawText(graphics, metrics.font(), sequence, fragment.bounds(), argb);
    }

    private static int originalColor(Style style, int argb) {
        var color = style.getColor();
        return color == null ? argb : (argb & 0xff000000) | color.getValue();
    }

    private FormattedCharSequence sequence(VanillaFont metrics, Fragment fragment) {
        var sequence = sequences.get(fragment);
        if (sequence == null) {
            sequence = metrics.sequence((TextRun) fragment.node(), fragment.text());
            if (sequences.size() >= 512) sequences.remove(sequences.keySet().iterator().next());
            sequences.put(fragment, sequence);
        }
        return sequence;
    }

    private void paintSelection(GuiGraphics graphics, VanillaFont metrics, TextRun run, Fragment fragment,
                                RichTextEffects.Sample effects) {
        var span = selectedSpans.get(run.id());
        if (span == null || needsBidi(fragment.text())) return;
        int start = Math.max(0, span.start() - fragment.textOffset());
        int end = Math.min(fragment.text().length(), span.end() - fragment.textOffset());
        if (start >= end) return;
        var box = fragment.bounds();
        int left = box.x() + metrics.width(run, fragment.text().substring(0, start));
        int right = box.x() + metrics.width(run, fragment.text().substring(0, end));
        graphics.fill(left, box.y(), right, box.y() + box.height(), effects.selectionColor(0xff224d77));
    }

    static void paintImage(GuiGraphics graphics, Rect box, RichTextImages.Image image) {
        double scale = Math.min((double) box.width() / image.width(), (double) box.height() / image.height());
        int width = Math.max(1, (int) Math.round(image.width() * scale));
        int height = Math.max(1, (int) Math.round(image.height() * scale));
        int x = box.x() + (box.width() - width) / 2, y = box.y() + (box.height() - height) / 2;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(image.location(), x, y, width, height, image.u(), image.v(), image.width(), image.height(),
                image.textureWidth(), image.textureHeight());
    }

    private static void paintPlaceholder(GuiGraphics graphics, Font font, Rect box, String altText) {
        int left = box.x(), top = box.y(), right = left + box.width(), bottom = top + box.height();
        graphics.fill(left, top, right, bottom, 0xffad526a);
        if (box.width() <= 2 || box.height() <= 2) return;
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xff472b3d);
        var label = plain(altText);
        int labelWidth = font.width(label);
        if (labelWidth <= box.width() - 2 && font.lineHeight <= box.height()) {
            graphics.drawString(font, label, left + (box.width() - labelWidth) / 2,
                    top + (box.height() - font.lineHeight) / 2, 0xfff2c9d5, false);
        }
    }

    private static FormattedCharSequence plain(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private static RichDocument resolve(RichDocument source) {
        if (source.nodes().stream().noneMatch(LocalizedText.class::isInstance)) return source;
        var nodes = new ArrayList<>(source.nodes());
        nodes.replaceAll(node -> node instanceof LocalizedText text
                ? new TextRun(text.id(), RichTextComponents.localized(text).getString(), text.style()) : node);
        return new RichDocument(nodes);
    }

    private static boolean needsBidi(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x0590) return Bidi.requiresBidi(text.toCharArray(), 0, text.length());
        }
        return false;
    }

    private record VanillaFont(Font font, Map<UiId, Style> styles, Map<ResourceLocation, Boolean> fontResources)
            implements RichTextLayout.FontMetrics {
        @Override public int ascent() { return font.lineHeight - 2; }
        @Override public int descent() { return 2; }
        @Override public int width(String text) { return font.width(plain(text)); }
        @Override public int width(TextRun run, String text) { return font.width(sequence(run, text)); }

        FormattedCharSequence sequence(TextRun run, String text) {
            var style = style(run);
            var fontId = style.getFont();
            if (!fontId.equals(Style.DEFAULT_FONT) && !fontResources.computeIfAbsent(fontId, VanillaFont::fontExists)) {
                style = style.withFont(Style.DEFAULT_FONT);
            }
            return needsBidi(text) ? Language.getInstance().getVisualOrder(FormattedText.of(text, style))
                    : FormattedCharSequence.forward(text, style);
        }

        Style style(TextRun run) {
            var nativeStyle = styles.get(run.id());
            return nativeStyle == null ? RichTextComponents.nativeStyle(run.style()) : nativeStyle;
        }

        private static boolean fontExists(ResourceLocation id) {
            var definition = ResourceLocation.tryParse(id.getNamespace() + ":font/" + id.getPath() + ".json");
            return definition != null && Minecraft.getInstance().getResourceManager().getResource(definition).isPresent();
        }
    }
}
