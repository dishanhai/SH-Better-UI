// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.RichTextComponents;
import com.dishanhai.sh_better_ui.client.visual.RichTextEffects;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Runnable native example for both loaders. Open in-game with /shbetterrichtext. */
public final class RichTextExampleScreen extends Screen {
    private final UiDotBackground backdrop = new UiDotBackground();
    private static final String TRANSLATION_PREFIX = "sh_better_ui.rich_text_example.";
    private static final int MAX_CONTENT_WIDTH = 464;
    private static final int MARGIN = 12;
    private static final int CONTENT_TOP = 32;
    private static final int ADVANCED_TOP = 56;

    static { ShBetterUiApi.blur().allowScreen(RichTextExampleScreen.class::isInstance); }

    private final ModuleContext module;
    private final Screen parent;
    private final RichDocument imported;
    private final GuiEventListener returnFocus;
    private ScopeHandle tree;
    private VisualLayoutBinding binding;
    private RichTextView view;
    private AlignmentLayout.Node content;
    private Language language;
    private RichDocument document;
    private RichTextComponents.Snapshot advancedDocument;
    private boolean advanced;
    private int effectMode;
    private int scroll;
    private int maxScroll;
    private int activations;
    private int level = 40;
    private boolean dropped;
    private String edited;
    private String undoText;
    private Button undo;
    private RichTextWorkbench.Viewport inline;

    public RichTextExampleScreen(ModuleContext module, Screen parent) {
        this(module, parent, null);
    }

    public RichTextExampleScreen(ModuleContext module, Screen parent, RichDocument imported) {
        super(label("title"));
        this.module = module;
        this.parent = parent;
        this.imported = imported;
        returnFocus = parent == null ? null : parent.getFocused();
    }

    @Override protected void init() {
        if (tree == null || !tree.isOpen()) {
            var engine = module.engine();
            tree = engine.require(UIApis.SCREENS).mount(module, this, id("screen"));
            var layout = ShBetterUiApi.layout().create(viewport());
            content = layout.node("rich_text", AlignmentFrame.box(new Rect(0, 0, 0, 0)));
            view = RichTextView.attach(module, tree, content);
            view.interaction().onButton(RichTextWorkbench.PRESS, () -> activations++);
            view.interaction().onSlider(RichTextWorkbench.LEVEL, value -> level = value);
            view.interaction().draggable(RichTextWorkbench.MOVABLE);
            view.interaction().onDrop(RichTextWorkbench.PRESS, event -> dropped = true);
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, tree, layout, time -> arrange());
        } else {
            view.invalidate();
        }
        int buttonWidth = Math.min(96, Math.max(0, (width - 3 * MARGIN) / 2));
        int buttonHeight = Math.min(20, height);
        int buttonY = Math.max(0, height - buttonHeight - 6);
        var back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(Math.min(MARGIN, Math.max(0, width - buttonWidth)), buttonY, buttonWidth, buttonHeight)
                .build());
        if (imported != null) {
            inline = addWidget(new RichTextWorkbench.Viewport(this, view, this::scroll, this::scrollPage));
            setInitialFocus(back);
            return;
        }
        int switchWidth = Math.min(128, Math.max(0, width - 2 * MARGIN - (advanced ? 0 : buttonWidth + 6)));
        addRenderableWidget(Button.builder(label(advanced ? "simple" : "advanced"), button -> {
            advanced = !advanced;
            scroll = 0;
            view.interaction().clearFocus();
            minecraft.tell(() -> {
                if (minecraft.screen == this) rebuildWidgets();
            });
        }).bounds(MARGIN, advanced ? 24 : buttonY, switchWidth, 20).build()).setX(
                advanced ? MARGIN : Math.max(MARGIN, width - switchWidth - MARGIN));
        inline = null;
        if (advanced) {
            inline = addWidget(new RichTextWorkbench.Viewport(this, view, this::scroll, this::scrollPage));
            int effectX = MARGIN + switchWidth + 6;
            if (width - effectX - MARGIN >= 64) {
                int available = width - effectX - MARGIN;
                boolean showPipeline = available >= 160;
                int effectWidth = Math.min(132, available - (showPipeline ? 96 : 0));
                addRenderableWidget(Button.builder(effectLabel(), button -> {
                    effectMode = (effectMode + 1) % 3;
                    button.setMessage(effectLabel());
                }).bounds(effectX, 24, effectWidth, 20).build());
                if (showPipeline) {
                    int x = effectX + effectWidth + 6;
                    addRenderableWidget(Button.builder(label("pipeline"), button -> minecraft.tell(() -> {
                        if (minecraft.screen == this) minecraft.setScreen(new RichTextPipelineScreen(module, this));
                    })).bounds(x, 24, Math.min(132, width - MARGIN - x), 20).build());
                }
            }
            addEditor(buttonY, MARGIN + buttonWidth + 6);
        }
        setInitialFocus(back);
    }

    private void addEditor(int y, int x) {
        int available = width - x - MARGIN - 64;
        if (available < 54) return;
        if (edited == null) edited = label("editable_default").getString();
        var field = new EditBox(font, x, y, available, 20, label("edit"));
        field.setMaxLength(256);
        field.setValue(edited);
        field.setResponder(value -> {
            if (value.equals(edited) || value.codePoints().anyMatch(point -> point >= 0xd800 && point <= 0xdfff)) return;
            if (undoText == null) undoText = edited;
            updateText(value);
            undo.active = true;
        });
        addRenderableWidget(field);
        undo = addRenderableWidget(Button.builder(label("undo"), button -> {
            String original = undoText;
            if (original == null) return;
            updateText(original);
            field.setValue(original);
            undoText = null;
            button.active = false;
        }).bounds(x + available + 4, y, 60, 20).build());
        undo.active = undoText != null;
    }

    private void updateText(String value) {
        if (advancedDocument != null) advancedDocument = RichTextWorkbench.edit(advancedDocument, edited, value);
        edited = value;
    }

    private Component effectLabel() {
        return label(switch (effectMode) {
            case 1 -> "glass";
            case 2 -> "reveal";
            default -> "flat";
        });
    }

    private RichTextEffects effects() {
        return switch (effectMode) {
            case 1 -> RichTextEffects.GLASS;
            case 2 -> RichTextEffects.REVEAL;
            default -> RichTextEffects.NONE;
        };
    }

    private boolean scroll(double delta) {
        if (!Double.isFinite(delta)) return false;
        return scrollPixels(-((double) Math.round(32 * delta)));
    }

    private boolean scrollPage(int direction) {
        int visibleHeight = viewport().height() - (advanced ? ADVANCED_TOP : CONTENT_TOP);
        return scrollPixels(direction * (double) Math.max(1, visibleHeight));
    }

    private boolean scrollPixels(double delta) {
        int next = (int) Math.max(0, Math.min(maxScroll, scroll + delta));
        if (next == scroll) return false;
        scroll = next;
        return true;
    }

    private Bounds viewport() {
        return new Bounds(0, 0, width, Math.max(0, height - (advanced ? 47 : 28)));
    }

    private void arrange() {
        var currentLanguage = Language.getInstance();
        if (imported != null) {
            document = imported;
        } else if (currentLanguage != language || document == null) {
            document = new RichDocument(List.of(
                    text("intro"), image("diamond", "minecraft:textures/item/diamond.png", 16, 16), text("tail"),
                    new TextRun(id("missing_label"), "\n\n" + label("missing_label").getString()),
                    image("missing", "sh_better_ui:textures/gui/rich_text/missing.png", 24, 12), text("wide_label"),
                    image("edition", "minecraft:textures/gui/title/edition.png", 128, 16)));
            language = currentLanguage;
            advancedDocument = null;
        }
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, Math.max(0, width - 2 * MARGIN));
        int top = advanced ? ADVANCED_TOP : CONTENT_TOP;
        int visibleHeight = Math.max(0, viewport().height() - top);
        if (advanced && advancedDocument == null) {
            if (edited == null) edited = label("editable_default").getString();
            advancedDocument = RichTextWorkbench.document(edited);
        }
        var text = advanced ? view.prepare(advancedDocument, font, contentWidth) : view.prepare(document, font, contentWidth);
        maxScroll = advanced || imported != null ? Math.max(0, text.height() - visibleHeight) : 0;
        scroll = Math.min(scroll, maxScroll);
        int contentHeight = Math.min(text.height(), visibleHeight);
        content.update(AlignmentFrame.box(new Rect((width - contentWidth) / 2, top, contentWidth, contentHeight))
                .withClip(new Rect(0, 0, contentWidth, contentHeight)));
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop.render(this, graphics, width, height, UiPreferences.configured());
        if (advanced && effectMode != 0) {
            graphics.fillGradient(0, ADVANCED_TOP, width, Math.max(ADVANCED_TOP, height - 46), 0xff23496a, 0xff392940);
        }
        var viewport = viewport();
        if (font.width(title) <= width - 2 * MARGIN && viewport.height() >= font.lineHeight + MARGIN) {
            graphics.drawCenteredString(font, title, width / 2, MARGIN, DarkModeTheme.textColor());
        }
        // LoadingOverlay renders its backdrop directly, outside the native Screen render event.
        boolean behindOverlay = minecraft.getOverlay() != null;
        if (behindOverlay && view != null) view.invalidate();
        if (!behindOverlay && binding != null && binding.isActive()) {
            var frame = new UiFrame(graphics, viewport, UiMouseState.read(), UiPreferences.configured(),
                    UiFrameScheduler.nowMs(), partialTick);
            binding.renderScreen(frame, pass -> view.draw(pass, DarkModeTheme.textColor(), frame.preferences(),
                    advanced ? effects() : RichTextEffects.NONE, scroll, mouseX, mouseY,
                    advanced ? tooltip -> renderInlineTooltip(tooltip, mouseX, mouseY) : null));
        }
        if (advanced && height >= ADVANCED_TOP + 48) {
            var status = Component.translatable(TRANSLATION_PREFIX + "status", activations, level,
                    label(dropped ? "dropped" : "drop_hint"));
            if (font.width(status) <= width - 2 * MARGIN) {
                graphics.drawString(font, status, MARGIN, height - 42, DarkModeTheme.textColor(), false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!behindOverlay && view != null && advanced) {
            renderInlineTooltip(graphics, mouseX, mouseY);
        } else if (!behindOverlay && view != null) {
            view.hitTest(mouseX, mouseY).filter(hit -> hit.node() instanceof ImageNode).ifPresent(hit -> {
                var alt = ((ImageNode) hit.node()).altText();
                if (!alt.isEmpty()) graphics.renderTooltip(font, Component.literal(alt), mouseX, mouseY);
            });
        }
    }

    private void renderInlineTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!view.interaction().renderTooltip(graphics, mouseX, mouseY)) {
            view.componentStyleAt(mouseX, mouseY).filter(style -> style.getHoverEvent() != null)
                    .ifPresent(style -> graphics.renderComponentHoverEffect(font, style, mouseX, mouseY));
        }
    }

    @Override public void setFocused(GuiEventListener child) {
        // Native click dispatch sets focus after press, even when the viewport already owns it.
        if (child != null && child == inline && child == getFocused()) return;
        super.setFocused(child);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        // Forge dispatches release to the child currently under the pointer, even during a drag.
        if (inline != null && inline.mouseReleased(x, y, button)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (inline != null && button != 0 && inline.mouseDragged(x, y, button, dx, dy)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    public boolean mouseScrolled(double x, double y, double dy) {
        return inline != null && inline.mouseScrolled(x, y, dy);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return mouseScrolled(x, y, dy);
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
        if (parent != null && returnFocus != null && parent.children().contains(returnFocus)) {
            parent.setFocused(returnFocus);
        }
    }

    @Override public void removed() {
        if (tree != null) tree.close();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (advanced && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && view.interaction().cancel()) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    private static UiId id(String path) { return new UiId("sh_better_ui", "rich_text_example/" + path); }

    static MutableComponent label(String key) { return Component.translatable(TRANSLATION_PREFIX + key); }

    private static TextRun text(String key) { return new TextRun(id(key), label(key).getString()); }

    private static ImageNode image(String key, String path, int width, int height) {
        return RichTextView.image(id(key), ResourceLocation.tryParse(path), width, height, label(key + "_alt").getString());
    }
}
