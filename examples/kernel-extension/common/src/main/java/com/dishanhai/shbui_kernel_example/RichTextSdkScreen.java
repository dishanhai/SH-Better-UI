// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.RichTextEffects;
import com.dishanhai.sh_better_ui.client.visual.RichTextExtensions;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Transform;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits;
import com.dishanhai.sh_better_ui.kernel.api.render.RenderGraph;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextDataSource;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.dishanhai.shbui_kernel_example.ExampleUiModule.id;
import static com.dishanhai.shbui_kernel_example.ExampleUiModule.label;

/** A third-party native Screen using only exported SH-Better-UI APIs. */
final class RichTextSdkScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MARGIN = 12;
    private static final int TOP = 32;
    private static final int MAX_DOCUMENT_BYTES = 256 * 1024;
    private static final ResourceLocation GUIDE =
            ResourceLocation.tryParse(ExampleUiModule.MOD_ID + ":rich_text/guide.json");
    static final UiId DIAMOND_TEXTURE = UiId.parse("minecraft:textures/item/diamond.png");
    static final UiId ITEM = id("guide/item");
    static final UiId METER = id("guide/meter");
    static final UiId VECTOR = id("guide/vector");
    private static final UiId METER_TYPE = id("energy_meter");
    static final UiId VECTOR_TYPE = id("vector_badge");
    private static final UiId ENERGY_DATA = id("energy");
    private static final UiId PROJECT = id("guide_project");
    private static final UiId GUIDE_DOCUMENT = id("guide_document");
    private static final UiId METER_BACKGROUND = id("meter/background");
    private static final UiId METER_PROGRESS = id("meter/progress");
    private static final RenderGraph.Plan METER_PLAN = RenderGraph.compile(List.of(
            new RenderGraph.Pass(METER_PROGRESS, RenderGraph.Stage.CONTENT, Set.of(METER_BACKGROUND),
                    Set.of(), Set.of(), Set.of(), 1),
            new RenderGraph.Pass(METER_BACKGROUND, RenderGraph.Stage.CONTENT, Set.of(),
                    Set.of(), Set.of(), Set.of(), 1)), Set.of(), Set.of(), 2).plan().orElseThrow();
    static final int METER_FILL = 0xff38c7a0;
    static final int METER_TRACK = 0xff30445a;
    static final int VECTOR_FILL = 0xffc5a0ff;
    private static final FilledGeometry<Local> BADGE = FilledGeometry.polygon(List.of(
            new Point<>(0, 0), new Point<>(1, .5), new Point<>(0, 1), new Point<>(.3, .5)), 64);

    private final ModuleContext module;
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private ScopeHandle tree;
    private AlignmentLayout.Node content;
    private RichTextView view;
    private RichTextExtensions extensions;
    private Subscription vectorRenderer;
    private VisualLayoutBinding binding;
    private ProjectEdits edits;
    private ProjectEdits.Session project;
    private Button charge;
    private int energy = 25;
    private long revision;
    private int scroll;
    private int maxScroll;

    RichTextSdkScreen(ModuleContext module, Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        super(label("rich_text.title"));
        this.module = module;
        this.parent = parent;
        this.returnFocus = returnFocus;
    }

    @Override protected void init() {
        if (tree == null || !tree.isOpen()) {
            var engine = module.engine();
            tree = engine.require(UIApis.SCREENS).mount(module, this, id("rich_text_screen"));
            var layout = ShBetterUiApi.layout().create(viewport());
            content = layout.node("guide", AlignmentFrame.box(new Rect(0, 0, 0, 0)));
            view = RichTextView.attach(module, tree, content);
            extensions = RichTextExtensions.attach(tree);
            extensions.registerData(ENERGY_DATA, () -> new RichTextDataSource.Snapshot(revision, Integer.toString(energy)));
            extensions.register(METER_TYPE, 1, RichTextSdkScreen::drawMeter);
            vectorRenderer = extensions.register(VECTOR_TYPE, 1, RichTextSdkScreen::drawVector);
            view.bindExtensions(extensions);
            edits = engine.require(ProjectEdits.KEY);
            project = edits.open(module, tree, PROJECT, Map.of());
            var imported = commitGuide(id("guide_initial"), loadGuide());
            if (imported.status() != ProjectEdits.Status.APPLIED) {
                LOGGER.warn("Could not import guide project: {}", imported.status());
                var fallback = commitGuide(id("guide_fallback"), guideWithFixtures(List.of(localized("load_error"))));
                if (fallback.status() != ProjectEdits.Status.APPLIED) {
                    throw new IllegalStateException("Could not open the built-in guide project: " + fallback.status());
                }
            }
            engine.require(UIApis.RESOURCES).observe(module, tree, this::reloadGuide);
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, tree, layout, time -> arrange());
        } else {
            view.invalidate();
        }
        int buttonWidth = Math.min(140, Math.max(0, (width - 3 * MARGIN) / 2));
        int buttonY = Math.max(0, height - 26);
        var back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(MARGIN, buttonY, buttonWidth, 20).build());
        charge = addRenderableWidget(Button.builder(chargeLabel(), button -> {
            energy = energy == 100 ? 0 : energy + 25;
            revision++;
            button.setMessage(chargeLabel());
        }).bounds(Math.max(MARGIN, width - MARGIN - buttonWidth), buttonY, buttonWidth, 20).build());
        setInitialFocus(back);
    }

    private Component chargeLabel() {
        return label("rich_text.charge", energy);
    }

    private RichDocument loadGuide() {
        var nodes = new ArrayList<InlineNode>();
        try (var input = minecraft.getResourceManager().open(GUIDE)) {
            byte[] data = input.readNBytes(MAX_DOCUMENT_BYTES + 1);
            if (data.length > MAX_DOCUMENT_BYTES) throw new IOException("Rich text guide exceeds the v1 byte limit");
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
            var decoded = RichDocumentCodec.decode(json);
            // The passive resource may not reuse IDs reserved for host-created nodes.
            if (decoded.nodes().stream().anyMatch(node -> node.id().namespace().equals(ExampleUiModule.MOD_ID)
                    && node.id().path().startsWith("guide/"))) {
                throw new IllegalArgumentException("Rich text guide uses a reserved host node ID");
            }
            nodes.addAll(decoded.nodes());
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.warn("Could not read example document {}", GUIDE, failure);
            nodes.add(localized("load_error"));
        }
        return guideWithFixtures(nodes);
    }

    private static RichDocument guideWithFixtures(List<InlineNode> content) {
        var nodes = new ArrayList<>(content);
        nodes.add(new ParagraphBreak(id("guide/item_break"), 6));
        nodes.add(localized("item_label"));
        nodes.add(new RichObjectNode(ITEM, new RichObjectNode.Item(UiId.parse("minecraft:diamond"), 1),
                16, 16, 16, Component.translatable("item.minecraft.diamond").getString()));
        nodes.add(new ParagraphBreak(id("guide/meter_break"), 6));
        nodes.add(localized("meter_label"));
        nodes.add(new RichExtensionNode(METER, METER_TYPE, 1, 96, 12, 12,
                label("rich_text.meter_alt").getString(), "0"));
        nodes.add(new RichExtensionNode(VECTOR, VECTOR_TYPE, 1, 20, 12, 12,
                label("rich_text.vector_alt").getString(), ""));
        return new RichDocument(nodes);
    }

    private static LocalizedText localized(String name) {
        String key = ExampleUiModule.MOD_ID + ".rich_text." + name;
        return new LocalizedText(id("guide/" + name), key, label("rich_text." + name).getString(),
                List.of(), RichStyle.EMPTY);
    }

    private static void drawMeter(RichTextExtensions.Context context) {
        var box = context.bounds();
        int percent = context.data(ENERGY_DATA).map(sample -> Integer.parseInt(sample.value()))
                .orElseGet(() -> Integer.parseInt(context.node().payload()));
        int filled = Math.max(0, box.width() - 2) * Math.max(0, Math.min(100, percent)) / 100;
        var graphics = context.graphics();
        METER_PLAN.execute(pass -> {
            if (pass.id().equals(METER_BACKGROUND)) {
                graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), METER_TRACK);
            } else {
                graphics.fill(box.x() + 1, box.y() + 1, box.x() + 1 + filled, box.y() + box.height() - 1, METER_FILL);
            }
            return true;
        });
    }

    private void reloadGuide(long generation) {
        var result = commitGuide(id("guide_reload/" + generation), loadGuide());
        if (result.status() != ProjectEdits.Status.APPLIED) {
            LOGGER.warn("Could not reload guide project: {}", result.status());
        }
    }

    private ProjectEdits.Result commitGuide(UiId operation, RichDocument replacement) {
        var stamp = edits.current(module, project).orElseThrow().document().stamp();
        var patch = new ProjectEdits.Patch(stamp, operation,
                List.of(new ProjectEdits.PutDocument(GUIDE_DOCUMENT, replacement)), Optional.empty());
        return edits.commit(module, project, patch);
    }

    private static void drawVector(RichTextExtensions.Context context) {
        var box = context.bounds();
        var toDocument = new Transform<Local, Local>(new Matrix(box.width() - 2, 0, 0, box.height() - 2,
                box.x() + 2, box.y() + 1));
        context.graphics().fillGeometry(BADGE.transform(toDocument), VECTOR_FILL);
    }

    private Bounds viewport() {
        return new Bounds(0, 0, width, Math.max(0, height - 32));
    }

    private void arrange() {
        var document = edits.current(module, project).orElseThrow().document().richText(GUIDE_DOCUMENT).orElseThrow();
        int contentWidth = Math.min(330, Math.max(0, width - 2 * MARGIN));
        int available = Math.max(0, viewport().height() - TOP);
        int documentHeight = view.prepare(document, font, contentWidth).height();
        int visibleHeight = Math.min(available, documentHeight);
        maxScroll = documentHeight - visibleHeight;
        scroll = Math.min(scroll, maxScroll);
        content.update(AlignmentFrame.box(new Rect((width - contentWidth) / 2, TOP, contentWidth, visibleHeight))
                .withClip(new Rect(0, 0, contentWidth, visibleHeight)));
    }

    // The Screen supplies its own background on both native method signatures.
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff111a28);
        if (font.width(title) <= width - 2 * MARGIN && viewport().height() >= TOP) {
            graphics.drawCenteredString(font, title, width / 2, MARGIN, 0xffedf6ff);
        }
        boolean covered = minecraft.getOverlay() != null;
        if (covered && view != null) view.invalidate();
        if (!covered && binding != null && binding.isActive()) {
            var frame = new UiFrame(graphics, viewport(), UiMouseState.read(), UiPreferences.configured(),
                    UiFrameScheduler.nowMs(), partialTick);
            binding.renderScreen(frame, pass -> view.draw(pass, 0xffedf6ff, frame.preferences(),
                    RichTextEffects.NONE, scroll, mouseX, mouseY));
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!covered && view != null) view.interaction().renderTooltip(graphics, mouseX, mouseY);
    }

    private boolean scrollBy(int offset) {
        int next = Math.max(0, Math.min(maxScroll, scroll + offset));
        if (next == scroll) return false;
        scroll = next;
        return true;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        return view != null && view.isInViewport(x, y) && scrollBy((int) Math.round(-24 * delta));
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return mouseScrolled(x, y, vertical);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int page = Math.max(1, viewport().height() - TOP - font.lineHeight);
        if ((key == GLFW.GLFW_KEY_PAGE_UP && scrollBy(-page)) || (key == GLFW.GLFW_KEY_PAGE_DOWN && scrollBy(page))) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
        var focus = returnFocus.get();
        if (parent.children().contains(focus)) parent.setFocused(focus);
    }

    @Override public void removed() {
        if (tree != null) tree.close();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    Button chargeButton() { return charge; }
    RichTextView view() { return view; }
    RichTextExtensions extensions() { return extensions; }
    Subscription vectorRenderer() { return vectorRenderer; }
    Optional<ProjectEdits.View> projectView() { return edits.current(module, project); }
}
