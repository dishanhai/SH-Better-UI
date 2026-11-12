// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Optional, on-demand inspection of one frozen publication. Opening this screen does not run analysis. */
public final class CorrectnessInspectorScreen extends Screen {
    private enum Tab { LAYOUT, MOTION, VISUAL, CORRECTNESS, PAGE_TREE, PAGE_LAYOUT, PAGE_PROTOCOL }
    private static final List<Tab> CORRECTNESS_TABS = List.of(Tab.LAYOUT, Tab.MOTION, Tab.VISUAL, Tab.CORRECTNESS);
    private static final List<Tab> PAGE_TABS = List.of(Tab.PAGE_TREE, Tab.PAGE_LAYOUT, Tab.PAGE_PROTOCOL);
    private static final String PREFIX = "sh_better_ui.correctness.inspector.";
    private static final int GAP = 5;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TRACK_WIDTH = 5;
    private final Screen parent;
    private final GuiEventListener returnFocus;
    private final UISnapshot snapshot;
    private final String exportedJson;
    private final Supplier<PageStudioReport> studioSource;
    private final List<Tab> availableTabs;
    private PageStudioReport studio;
    private final UiPreferences preferences = UiPreferences.configured();
    private final UiDotBackground backdrop = new UiDotBackground();
    private final List<UISnapshot.Node> nodes;
    private final int[] scrolls = new int[Tab.values().length];
    private final UiButton[] tabs;
    private List<FormattedCharSequence> lines = List.of();
    private Tab selected = Tab.LAYOUT;
    private UiButton copy;
    private UiButton previous;
    private UiButton next;
    private int nodeIndex;
    private int contentTop, contentBottom, textTop, textRight, previewHeight;
    private int dragOffset = -1;
    private int[] samplePixels = new int[0];
    private boolean copied;
    private boolean tooSmall;

    public CorrectnessInspectorScreen(Screen parent, UISnapshot snapshot, String exportedJson) {
        this(parent, snapshot, exportedJson, null);
    }

    CorrectnessInspectorScreen(Screen parent, Supplier<PageStudioReport> source) {
        this(parent, null, null, Objects.requireNonNull(source));
    }

    private CorrectnessInspectorScreen(Screen parent, UISnapshot snapshot, String exportedJson,
                                       Supplier<PageStudioReport> source) {
        super(source == null ? label("title") : PageStudioReport.label("title"));
        this.parent = parent;
        returnFocus = parent == null ? null : parent.getFocused();
        studioSource = source;
        if (source == null) {
            this.snapshot = Objects.requireNonNull(snapshot);
            this.exportedJson = Objects.requireNonNull(exportedJson);
            nodes = snapshot.nodes().values().stream().sorted(Comparator.comparing(UISnapshot.Node::id)).toList();
            availableTabs = CORRECTNESS_TABS;
        } else {
            this.snapshot = null;
            this.exportedJson = null;
            nodes = List.of();
            availableTabs = PAGE_TABS;
            selected = Tab.PAGE_TREE;
            studio = source.get();
        }
        tabs = new UiButton[availableTabs.size()];
    }

    @Override protected void init() {
        previous = next = null;
        int inset = Math.min(8, Math.max(1, width / 12));
        int innerWidth = Math.max(1, width - inset * 2);
        tooSmall = width < 120 || height < 100;
        if (tooSmall) {
            contentTop = textTop = 26;
            contentBottom = Math.max(contentTop, height - 30);
            textRight = Math.max(9, width - inset - TRACK_WIDTH - GAP);
            previewHeight = 0;
            int half = Math.max(1, (innerWidth - GAP) / 2);
            var back = button(inset, Math.max(0, height - 24), half, Component.translatable("gui.back"), this::onClose);
            copy = button(inset + half + GAP, Math.max(0, height - 24), half, auxiliaryLabel(), this::auxiliary);
            setInitialFocus(back);
            rebuild();
            return;
        }
        int columns = innerWidth >= 340 || height < 160 ? tabs.length : 2;
        int cellWidth = Math.max(1, (innerWidth - GAP * (columns - 1)) / columns);
        int tabY = height < 124 ? 26 : 42;
        for (int index = 0; index < tabs.length; index++) {
            var tab = availableTabs.get(index);
            var button = button(inset + index % columns * (cellWidth + GAP), tabY + index / columns * 24,
                    cellWidth, label("tab." + tab.name().toLowerCase(Locale.ROOT)), () -> select(tab));
            button.setPreview(label("tab_hint." + tab.name().toLowerCase(Locale.ROOT)));
            button.setSelected(tab == selected);
            tabs[index] = button;
        }
        contentTop = tabY + ((tabs.length + columns - 1) / columns) * 24 + 4;
        contentBottom = Math.max(contentTop, height - 56);
        int availableHeight = contentBottom - contentTop;
        previewHeight = availableHeight >= (studio == null ? 130 : 80) ? Math.min(136, availableHeight / 3) : 0;
        updateTextTop();
        textRight = Math.max(inset + 1, width - inset - TRACK_WIDTH - GAP);

        int half = Math.max(1, (innerWidth - GAP) / 2);
        previous = button(inset, height - 50, half,
                studio == null ? label("previous") : PageStudioReport.label("previous"), () -> selectNode(-1));
        next = button(inset + half + GAP, height - 50, half,
                studio == null ? label("next") : PageStudioReport.label("next"), () -> selectNode(1));
        previous.active = next.active = itemCount() > 1;
        var back = button(inset, height - 26, half,
                Component.translatable("gui.back"), this::onClose);
        copy = button(inset + half + GAP, height - 26, half, auxiliaryLabel(), this::auxiliary);
        if (studio == null) copy.setPreview(label("copy_hint"));
        setInitialFocus(back);
        rebuild();
    }

    private UiButton button(int x, int y, int width, Component text, Runnable press) {
        var button = new UiButton(x, y, width, BUTTON_HEIGHT, text, ignored -> press.run(),
                preferences, false, UiStyle.DEFAULT);
        button.setSampleBackground(false);
        return addRenderableWidget(button);
    }

    private void select(Tab tab) {
        selected = tab;
        for (int index = 0; index < tabs.length; index++) if (tabs[index] != null) tabs[index].setSelected(availableTabs.get(index) == tab);
        dragOffset = -1;
        updateTextTop();
        rebuild();
    }

    private void updateTextTop() {
        boolean preview = previewHeight > 0 && (studio == null || selected == Tab.PAGE_LAYOUT);
        textTop = contentTop + (preview ? previewHeight + GAP : 0);
    }

    private void selectNode(int offset) {
        if (itemCount() == 0) return;
        nodeIndex = Math.floorMod(nodeIndex + offset, itemCount());
        scrolls[selected.ordinal()] = 0;
        rebuild();
    }

    private UISnapshot.Node node() { return nodes.isEmpty() ? null : nodes.get(nodeIndex); }
    private int itemCount() { return studio == null ? nodes.size() : studio.pages().size(); }
    private PageStudioReport.Page page() { return studio.pages().isEmpty() ? null : studio.pages().get(nodeIndex); }

    private void rebuild() {
        var content = new ArrayList<Component>();
        if (tooSmall) content.add(label("too_small"));
        content.add(label("frozen"));
        if (studio != null) {
            content.addAll(switch (selected) {
                case PAGE_TREE -> studio.tree(page());
                case PAGE_LAYOUT -> studio.layout(page());
                case PAGE_PROTOCOL -> studio.protocol();
                default -> throw new IllegalStateException("Invalid page Studio tab");
            });
            wrap(content);
            return;
        }
        content.add(label("versions", snapshot.version(), snapshot.declarationVersion(), snapshot.resourceVersion(), snapshot.themeVersion()));
        content.add(label("status", snapshot.status().name(), snapshot.report().unresolvedHard().size()));
        var node = node();
        if (node == null) {
            content.add(label("empty"));
        } else {
            content.add(label("node", nodeIndex + 1, nodes.size(), node.id().toString()));
            content.add(label("parent", node.declaration().parent() == null ? "—" : node.declaration().parent().toString()));
            content.add(label("node_version", node.version(), node.revision(), node.scopeGeneration()));
            switch (selected) {
                case LAYOUT -> layoutLines(content, node);
                case MOTION -> motionLines(content, node);
                case VISUAL -> visualLines(content, node);
                case CORRECTNESS -> correctnessLines(content, node);
            }
        }
        if (selected == Tab.CORRECTNESS || node == null) {
            for (String limitation : snapshot.report().limitations()) content.add(label("limitation", Component.translatable(limitation)));
        }
        wrap(content);
        samplePixels = node == null ? new int[0] : displaySample(node).pixels();
    }

    private void wrap(List<Component> content) {
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (var paragraph : content) {
            wrapped.addAll(font.split(paragraph, Math.max(1, textRight - 8)));
            wrapped.add(FormattedCharSequence.EMPTY);
        }
        lines = List.copyOf(wrapped);
        scrollTo(scrolls[selected.ordinal()]);
    }

    private void layoutLines(List<Component> content, UISnapshot.Node node) {
        content.add(label("layout_legend"));
        content.add(label("bounds.preferred", rect(node.preferred())));
        content.add(label("bounds.resolved", rect(node.resolved())));
        content.add(label("bounds.clipped", rect(node.clipped())));
        content.add(label("bounds.visible", rect(node.visible())));
        content.add(label("bounds.interactive", rect(node.interactive())));
        content.add(label("bounds.shadow", rect(node.shadow())));
        content.add(label("bounds.occlusion", rect(node.occlusion())));
        var c = node.declaration().constraints();
        content.add(label("constraints", c.anchor().name(), c.priority(), number(c.margin()),
                number(c.minW()), number(c.minH()), number(c.preferredW()), number(c.preferredH()), number(c.maxW()), number(c.maxH())));
        content.add(label("constraint_levels", node.declaration().contract().hard().toString(), node.declaration().contract().soft().toString()));
        for (var area : snapshot.safeAreas()) {
            content.add(label("safe_area", area.id().toString(), rect(area.bounds()), area.evidence().name(), area.frameId()));
        }
        content.add(label("tree"));
        for (var item : nodes) content.add(Component.literal("  ".repeat(depth(item)) + item.id()));
    }

    private int depth(UISnapshot.Node node) {
        int result = 0;
        UiId parentId = node.declaration().parent();
        while (parentId != null && result < 16) {
            result++;
            var parentNode = snapshot.nodes().get(parentId);
            parentId = parentNode == null ? null : parentNode.declaration().parent();
        }
        return result;
    }

    private void motionLines(List<Component> content, UISnapshot.Node node) {
        content.add(label("prediction_notice"));
        content.add(label("bounds.predicted", rect(node.predicted())));
        content.add(label("bounds.swept", rect(node.swept())));
        if (node.motion() == null) {
            content.add(label("no_motion"));
        } else {
            var motion = node.motion();
            content.add(label("motion_details", motion.path().size(), motion.conservative(), number(motion.spatialError()), motion.budgetExceeded()));
            content.add(label("condition", motion.condition()));
            int count = 0;
            for (var frame : motion.path()) {
                if (count++ == 64) { content.add(label("path_limit")); break; }
                var t = frame.state().transform();
                content.add(label("keyframe", number(frame.seconds()), number(t.x()), number(t.y()),
                        number(t.scaleX()), number(t.scaleY()), number(t.radians())));
            }
        }
        for (var collision : snapshot.collisions()) {
            if (!collision.first().equals(node.id()) && !collision.second().equals(node.id())) continue;
            content.add(label("collision", collision.first().toString(), collision.second().toString(),
                    collision.conservative(), number(collision.timeError())));
            content.add(label("condition", collision.condition()));
            for (var interval : collision.intervals()) content.add(label("interval", number(interval.fromSeconds()),
                    number(interval.toSeconds()), rect(interval.overlap())));
        }
        for (var correction : snapshot.report().corrections()) if (correction.nodeId().equals(node.id())) {
            content.add(label("correction", correction.strategy().name(), correction.reason(), correction.verified()));
        }
    }

    private void visualLines(List<Component> content, UISnapshot.Node node) {
        var background = node.background();
        var observation = node.materialObservation();
        var statistics = displaySample(node).statistics();
        var l = node.legibility();
        content.add(label("sample_notice"));
        content.add(label("sample", background.validity().name(), background.source().name(), background.stage().name(),
                background.frameId(), background.stateVersion(), background.capturedNanos()));
        content.add(label("effective_sample", observation.validity().name(), observation.source().name(), observation.stage().name(),
                observation.frameId(), observation.stateVersion(), observation.capturedNanos()));
        content.add(label("luminance", number(statistics.minimum()), number(statistics.p10()), number(statistics.median()),
                number(statistics.p90()), number(statistics.maximum()), number(statistics.textureComplexity())));
        content.add(label("legibility", number(l.computedRatio()), number(l.worstRatio()), number(l.heuristicScore()),
                number(l.edgeClarity()), number(l.textureInterference()), number(l.dynamicStability())));
        content.add(label("pixel_evidence", l.pixelObserved(), l.finalCompositeObserved(), l.valid()));
        content.add(label("text_before", color(node.declaration().visualPolicy().text().argb()),
                number(node.declaration().visualPolicy().text().outlineWidth()), number(node.declaration().visualPolicy().text().shadowRadius())));
        content.add(label("text_after", color(node.text().argb()), number(node.text().outlineWidth()), number(node.text().shadowRadius())));
        content.add(label("material_before", node.declaration().visualPolicy().glass().toString()));
        content.add(label("material_after", node.material().glass().toString(), color(node.material().tintArgb()),
                number(node.material().opacity()), number(node.material().contrastMask()), node.material().highReadability()));
        content.add(label("measurement", number(node.measurement().width()), number(node.measurement().height()),
                node.measurement().resourceVersion(), node.measurement().measured()));
        for (String limitation : l.limitations()) content.add(label("limitation", Component.translatable(limitation)));
        for (var correction : snapshot.report().corrections()) if (correction.nodeId().equals(node.id())) {
            content.add(label("correction", correction.strategy().name(), correction.reason(), correction.verified()));
        }
    }

    private void correctnessLines(List<Component> content, UISnapshot.Node node) {
        content.add(label("revalidation", snapshot.report().revalidationCount()));
        for (var issue : snapshot.report().issues()) {
            if (!issue.nodes().isEmpty() && !issue.nodes().contains(node.id())) continue;
            content.add(label("issue", issue.id(), issue.code(), issue.severity().name(), issue.evidence().name(), issue.resolved()));
            content.add(Component.translatable(issue.explanationKey()));
            content.add(label("affected", issue.nodes().toString()));
            content.add(label("measurements", issue.measurements().toString()));
            content.add(label("alternatives", issue.alternatives().toString()));
        }
        for (var correction : snapshot.report().corrections()) if (correction.nodeId().equals(node.id())) {
            content.add(label("correction", correction.strategy().name(), correction.reason(), correction.verified()));
            content.add(label("before_after", correction.before().toString(), correction.after().toString()));
        }
    }

    // Both GUI versions draw the background once from render; their background override signatures differ.
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop.render(this, graphics, width, height, UiPreferences.configured());
        draw(graphics, getTitle().getVisualOrderText(), 8, 8, LiquidGlassTheme.textColor());
        if (height >= 124 && !tooSmall) {
            var subtitle = studio == null ? label("frame", snapshot.frameId(), snapshot.version())
                    : PageStudioReport.label("position", itemCount() == 0 ? 0 : nodeIndex + 1, itemCount());
            draw(graphics, subtitle.getVisualOrderText(), 8, 24, LiquidGlassTheme.textColor());
        }
        if (previewHeight > 0) {
            if (studio == null) drawPreview(graphics);
            else if (selected == Tab.PAGE_LAYOUT) drawPagePreview(graphics);
        }
        graphics.enableScissor(8, textTop, textRight, contentBottom);
        try {
            int scroll = scrolls[selected.ordinal()];
            int first = Math.max(0, scroll / lineStep());
            int end = Math.min(lines.size(), (scroll + viewHeight() + lineStep() - 1) / lineStep());
            for (int index = first; index < end; index++) draw(graphics, lines.get(index), 8,
                    textTop + index * lineStep() - scroll, LiquidGlassTheme.textColor());
        } finally { graphics.disableScissor(); }
        if (maxScroll() > 0 && viewHeight() > 12) {
            graphics.fill(textRight + GAP, textTop, textRight + GAP + TRACK_WIDTH, contentBottom, 0xff394657);
            int thumb = thumbTop();
            graphics.fill(textRight + GAP, thumb, textRight + GAP + TRACK_WIDTH, thumb + thumbHeight(),
                    dragOffset >= 0 ? 0xffffffff : LiquidGlassTheme.accentColor());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPreview(GuiGraphics graphics) {
        var node = node();
        graphics.fill(8, contentTop, Math.max(9, width - 8), contentTop + previewHeight, 0xff17202b);
        if (node == null) return;
        if (selected == Tab.VISUAL && samplePixels.length > 0) {
            drawSamples(graphics, node);
            return;
        }
        var viewport = snapshot.viewport();
        double scale = Math.min(Math.max(1, width - 24) / Math.max(1, viewport.width()),
                Math.max(1, previewHeight - 20) / Math.max(1, viewport.height()));
        for (var area : snapshot.safeAreas()) outline(graphics, area.bounds(), viewport, scale, 0xffffb454);
        for (var item : nodes) if (item.renderable()) outline(graphics, item.resolved(), viewport, scale, 0xff536779);
        outline(graphics, node.preferred(), viewport, scale, 0xffd2a8ff);
        outline(graphics, node.resolved(), viewport, scale, 0xff58c4fa);
        outline(graphics, node.interactive(), viewport, scale, 0xff89d69f);
        if (selected == Tab.MOTION) {
            outline(graphics, node.swept(), viewport, scale, 0xffffb454);
            if (node.motion() != null) {
                int count = 0;
                for (var keyframe : node.motion().path()) {
                    if (count++ >= 128) break;
                    outline(graphics, frameBounds(keyframe), viewport, scale, 0xff70573a);
                }
            }
        }
        draw(graphics, label(selected == Tab.MOTION ? "prediction_notice" : "layout_legend").getVisualOrderText(),
                12, contentTop + previewHeight - font.lineHeight - 2, 0xfff0f3f7);
    }

    private void drawPagePreview(GuiGraphics graphics) {
        var page = page();
        if (page == null || page.layout() == null || page.viewport() == null) return;
        var area = page.viewport();
        if (area.width() <= 0 || area.height() <= 0) return;
        var viewport = new Rect(area.x(), area.y(), area.width(), area.height(), Space.GUI);
        double scale = Math.min((width - 24.0) / viewport.width(), (previewHeight - 16.0) / viewport.height());
        graphics.fill(8, contentTop, width - 8, contentTop + previewHeight, 0xff17202b);
        outline(graphics, viewport, viewport, scale, 0xff536779);
        for (var box : page.layout().widgets()) {
            if (!box.visible()) continue;
            boolean focused = page.layout().focused().filter(box.id()::equals).isPresent();
            outline(graphics, new Rect(box.x(), box.y(), box.width(), box.height(), Space.GUI), viewport, scale,
                    focused ? 0xffffb454 : box.enabled() ? 0xff58c4fa : 0xff536779);
        }
    }

    private Rect frameBounds(SpatialIntelligence.Keyframe keyframe) {
        var state = keyframe.state();
        var matrix = state.transform().matrix();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            var point = matrix.point(i % 2 == 0 ? 0 : state.shape().width(), i < 2 ? 0 : state.shape().height());
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y());
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY, Space.GUI);
    }

    private void outline(GuiGraphics graphics, Rect rect, Rect viewport, double scale, int color) {
        if (rect.space() != Space.GUI || rect.empty()) return;
        int x = 12 + (int) Math.round((rect.x() - viewport.x()) * scale);
        int y = contentTop + 3 + (int) Math.round((rect.y() - viewport.y()) * scale);
        int right = x + Math.max(1, (int) Math.round(rect.width() * scale));
        int bottom = y + Math.max(1, (int) Math.round(rect.height() * scale));
        graphics.enableScissor(8, contentTop, width - 8, contentTop + previewHeight - 12);
        try {
            graphics.fill(x, y, right, y + 1, color); graphics.fill(x, bottom - 1, right, bottom, color);
            graphics.fill(x, y, x + 1, bottom, color); graphics.fill(right - 1, y, right, bottom, color);
        } finally { graphics.disableScissor(); }
    }

    private void drawSamples(GuiGraphics graphics, UISnapshot.Node node) {
        var sample = displaySample(node);
        boolean contrast = sample.validity() == VisualIntelligence.Validity.VALID
                && sample.source() != VisualIntelligence.Source.PREDICTED
                && sample.stage() == VisualIntelligence.Stage.POST_MATERIAL_PRE_TEXT;
        int columns = Math.min(32, sample.sampleWidth()), rows = Math.min(32, sample.sampleHeight());
        int gridWidth = Math.max(1, (width - 36) / 2), gridHeight = Math.max(1, previewHeight - 18);
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            int pixel = samplePixels[(row * sample.sampleHeight() / rows) * sample.sampleWidth()
                    + column * sample.sampleWidth() / columns];
            int left = 12 + column * gridWidth / columns, right = 12 + (column + 1) * gridWidth / columns;
            int top = contentTop + 3 + row * gridHeight / rows, bottom = contentTop + 3 + (row + 1) * gridHeight / rows;
            graphics.fill(left, top, right, bottom, pixel | 0xff000000);
            if (contrast) {
                double ratio = ContrastPolicy.ratio(node.text().argb(), 0, pixel | 0xff000000);
                int color = ratio >= node.requiredContrast() ? 0xff498a62
                        : ratio >= 3 ? 0xffd4a54a : 0xffb54f54;
                graphics.fill(left + gridWidth + 8, top, right + gridWidth + 8, bottom, color);
            }
        }
        draw(graphics, label(contrast ? "heatmap" : "sample_notice").getVisualOrderText(), 12,
                contentTop + previewHeight - font.lineHeight - 1, 0xfff0f3f7);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (hasControlDown() && key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + tabs.length) {
            select(availableTabs.get(key - GLFW.GLFW_KEY_1)); return true;
        }
        if (hasControlDown() && key == (studio == null ? GLFW.GLFW_KEY_C : GLFW.GLFW_KEY_R)) {
            auxiliary(); return true;
        }
        if (hasControlDown() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            selectNode(key == GLFW.GLFW_KEY_LEFT ? -1 : 1); return true;
        }
        int scroll = scrolls[selected.ordinal()];
        int target = switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll - lineStep();
            case GLFW.GLFW_KEY_DOWN -> scroll + lineStep();
            case GLFW.GLFW_KEY_PAGE_UP -> scroll - Math.max(lineStep(), viewHeight() - lineStep());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll + Math.max(lineStep(), viewHeight() - lineStep());
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> maxScroll();
            default -> scroll;
        };
        return scrollTo(target) || super.keyPressed(key, scanCode, modifiers);
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        return x >= 8 && x < width - 8 && y >= textTop && y < contentBottom
                && scrollTo(scrolls[selected.ordinal()] - (int) Math.round(delta * lineStep() * 3));
    }
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return mouseScrolled(x, y, dy); }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && maxScroll() > 0 && viewHeight() > 12
                && x >= textRight + GAP && x < textRight + GAP + TRACK_WIDTH && y >= textTop && y < contentBottom) {
            dragOffset = y >= thumbTop() && y < thumbTop() + thumbHeight() ? (int) y - thumbTop() : thumbHeight() / 2;
            drag(y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragOffset >= 0) { drag(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragOffset >= 0) { dragOffset = -1; return true; }
        return super.mouseReleased(x, y, button);
    }
    private void drag(double y) {
        double ratio = (y - textTop - dragOffset) / Math.max(1, viewHeight() - thumbHeight());
        scrollTo((int) Math.round(ratio * maxScroll()));
    }
    private boolean scrollTo(int value) {
        int next = Math.max(0, Math.min(value, maxScroll()));
        if (next == scrolls[selected.ordinal()]) return false;
        scrolls[selected.ordinal()] = next; return true;
    }
    private int lineStep() { return font.lineHeight + 2; }
    private int viewHeight() { return Math.max(0, contentBottom - textTop); }
    private int maxScroll() { return Math.max(0, lines.size() * lineStep() - viewHeight()); }
    private int thumbHeight() { return Math.max(8, viewHeight() * viewHeight() / Math.max(1, lines.size() * lineStep())); }
    private int thumbTop() { return textTop + (int) ((long) scrolls[selected.ordinal()] * (viewHeight() - thumbHeight()) / Math.max(1, maxScroll())); }

    private Component auxiliaryLabel() {
        return studio == null ? label(copied ? "copied" : "copy") : PageStudioReport.label("refresh");
    }

    private void auxiliary() {
        if (studio != null) {
            studio = studioSource.get();
            nodeIndex = Math.min(nodeIndex, Math.max(0, itemCount() - 1));
            if (previous != null) previous.active = next.active = itemCount() > 1;
            rebuild();
            return;
        }
        minecraft.keyboardHandler.setClipboard(exportedJson);
        copied = true;
        if (copy != null) copy.setMessage(label("copied"));
    }
    @Override public void onClose() {
        dragOffset = -1;
        minecraft.setScreen(parent);
        if (parent != null && minecraft.screen == parent && returnFocus != null && parent.children().contains(returnFocus)) {
            parent.setFocused(returnFocus);
        } else if (parent instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn.Target target) {
            target.prepareReturnLayer();
        }
    }
    @Override public void removed() { dragOffset = -1; super.removed(); }
    @Override public void tick() { if (!minecraft.isWindowActive()) dragOffset = -1; super.tick(); }
    @Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
    @Override public Component getNarrationMessage() {
        if (studio != null) {
            return CommonComponents.joinForNarration(getTitle(),
                    label("tab." + selected.name().toLowerCase(Locale.ROOT)),
                    page() == null ? PageStudioReport.label("empty") : Component.literal(page().id().toString()));
        }
        var node = node();
        return CommonComponents.joinForNarration(getTitle(), label("frozen"), label("frame", snapshot.frameId(), snapshot.version()),
                label("active_tab", label("tab." + selected.name().toLowerCase(Locale.ROOT))),
                node == null ? label("empty") : label("node", nodeIndex + 1, nodes.size(), node.id().toString()));
    }
    private void draw(GuiGraphics graphics, FormattedCharSequence text, int x, int y, int color) {
        UiFormattedText.draw(graphics, font, text, x, y, color, false, preferences);
    }
    private static String rect(Rect rect) {
        return String.format(Locale.ROOT, "(%.1f, %.1f; %.1f × %.1f) %s", rect.x(), rect.y(), rect.width(), rect.height(), rect.space().name());
    }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "—"; }
    private static String color(int argb) { return String.format(Locale.ROOT, "#%08X", argb); }
    private static VisualIntelligence.BackgroundSample displaySample(UISnapshot.Node node) {
        var observation = node.materialObservation();
        return observation.validity() == VisualIntelligence.Validity.VALID ? observation : node.background();
    }
    private static Component label(String key, Object... arguments) { return Component.translatable(PREFIX + key, arguments); }
}
