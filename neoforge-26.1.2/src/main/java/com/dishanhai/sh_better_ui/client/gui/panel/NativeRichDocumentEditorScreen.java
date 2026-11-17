// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.visual.NativeRichDocumentView;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Local structured authoring. Unsupported objects are retained unchanged, never flattened from preview. */
public final class NativeRichDocumentEditorScreen extends Screen {
    private enum Tab { EDIT, PREVIEW, JSON }
    private final Screen parent;
    final NativeRichEditorModel model;
    private Tab tab = Tab.EDIT;
    private Component status = Component.translatableWithFallback("sh_better_ui.rich_text_editor.native.hint", "Preview shows applied changes. Export and copy JSON to keep your work.");
    private AbstractContainerWidget body;
    private final Map<String, AbstractWidget> focusIds = new LinkedHashMap<>();
    private String restoreFocus;
    private double restoreScroll;
    private NativeRichDocumentView preview;
    private RichDocument previewDocument;
    private net.minecraft.locale.Language previewLanguage;
    private int previewWidth, previewScroll, previewX, previewY, previewHeight;

    public NativeRichDocumentEditorScreen(Screen parent, Component title, RichDocument initial) {
        super(title); this.parent = parent; model = new NativeRichEditorModel(initial);
    }
    public RichDocument document() { return model.document.document(); }
    @Override protected void init() { rememberFocus(); build(); }
    private void rememberFocus() {
        var focused = body == null ? getFocused() : body.getFocused();
        focusIds.forEach((id, widget) -> { if (widget == focused) restoreFocus = id; });
        if (body != null) restoreScroll = body.scrollAmount();
    }
    private void rebuild() { rememberFocus(); build(); }
    private void action(Runnable work) {
        try { work.run(); status = Component.translatableWithFallback("sh_better_ui.rich_text_editor.native.ready", "Ready. Export and copy JSON to keep your document; no file is written."); }
        catch (IllegalArgumentException | IllegalStateException failure) { status = Component.translatableWithFallback("sh_better_ui.rich_text_editor.native.error", "Cannot apply: %s", failure.getMessage()); }
        rebuild();
    }
    private Button button(String id, String label, int width, Runnable work) {
        String key = switch (id) {
            case "add:text" -> "add_text"; case "add:paragraph" -> "add_paragraph"; case "add:image" -> "add_image";
            case "undo", "redo", "delete", "apply" -> id;
            case "up" -> "move_up"; case "down" -> "move_down"; case "discard" -> "reset";
            case "back" -> "close"; case "tab:EDIT" -> "properties"; case "tab:PREVIEW" -> "preview";
            default -> null;
        };
        Component caption;
        if (key != null) caption = Component.translatable("sh_better_ui.rich_text_editor." + key);
        else if (id.startsWith("flag:")) caption = Component.translatable("sh_better_ui.rich_text_editor.style." + id.substring(5).toLowerCase(Locale.ROOT))
                .append(": " + model.properties.enabled(RichTextEditorProperties.Flag.valueOf(id.substring(5))));
        else if (id.startsWith("node:")) caption = Component.literal(label);
        else caption = Component.translatableWithFallback("sh_better_ui.rich_text_editor.native." + id.replace(':', '.'), label);
        var button = Button.builder(caption, ignored -> action(work)).width(Math.max(20, width)).build();
        focusIds.put(id, button); return button;
    }
    private void build() {
        clearWidgets(); focusIds.clear(); body = null;
        int usable = Math.max(24, width - 16);
        int tabWidth = Math.max(20, Math.min(108, usable / 3));
        for (var value : Tab.values()) {
            var button = button("tab:" + value, value.name(), tabWidth, () -> { tab = value; restoreScroll = 0; });
            button.setPosition(8 + value.ordinal() * tabWidth, 26); addRenderableWidget(button);
        }
        int bodyHeight = Math.max(20, height - 108);
        boolean split = width >= 560 && tab == Tab.EDIT;
        int paneWidth = split ? Math.max(220, usable / 2 - 8) : usable;
        var content = LinearLayout.vertical().spacing(4);
        if (tab == Tab.EDIT) edit(content, paneWidth);
        else if (tab == Tab.JSON) json(content, paneWidth, bodyHeight);
        else content.addChild(new MultiLineTextWidget(Component.translatable("sh_better_ui.rich_text_editor.preview"), font).setMaxWidth(paneWidth));
        if (tab != Tab.PREVIEW) {
            var layout = new ScrollableLayout(minecraft, content, bodyHeight);
            layout.setMinWidth(paneWidth); layout.arrangeElements(); layout.setPosition(8, 50);
            layout.visitWidgets(widget -> body = (AbstractContainerWidget) addRenderableWidget(widget));
            if (body != null) body.setScrollAmount(restoreScroll);
        }
        previewX = split ? 16 + paneWidth : 8; previewY = 50; previewHeight = bodyHeight;
        if (split || tab == Tab.PREVIEW) ensurePreview(Math.max(1, width - previewX - 8));
        int footerWidth = Math.max(20, Math.min(140, (width - 24) / 2));
        var back = button("back", "Back", footerWidth, this::onClose);
        back.setPosition(8, Math.max(0, height - 26)); addRenderableWidget(back);
        var refresh = button("refresh", "Refresh preview", footerWidth, () -> previewDocument = null);
        refresh.setPosition(16 + footerWidth, Math.max(0, height - 26)); addRenderableWidget(refresh);
        var focus = focusIds.get(restoreFocus);
        if (focus != null && body != null && !restoreFocus.startsWith("tab:") && !restoreFocus.equals("back") && !restoreFocus.equals("refresh")) {
            body.setFocused(focus); setFocused(body);
        } else setInitialFocus(focus == null ? back : focus);
    }
    private void edit(LinearLayout content, int available) {
        var tools = LinearLayout.horizontal().spacing(3);
        tools.addChild(button("add:text", "+ Text", available / 3 - 2, () -> model.add("text")));
        tools.addChild(button("add:paragraph", "+ Break", available / 3 - 2, () -> model.add("paragraph")));
        tools.addChild(button("add:image", "+ PNG", available / 3 - 2, () -> model.add("image"))); content.addChild(tools);
        var history = LinearLayout.horizontal().spacing(3);
        var undo = button("undo", "Undo", available / 2 - 2, model::undo); undo.active = model.document.canUndo(); history.addChild(undo);
        var redo = button("redo", "Redo", available / 2 - 2, model::redo); redo.active = model.document.canRedo(); history.addChild(redo); content.addChild(history);
        var nodes = model.document.document().nodes();
        for (int i = 0; i < nodes.size(); i++) {
            int index = i; var node = nodes.get(i);
            String name = node instanceof TextRun text ? text.text().replace('\n', ' ') : node.getClass().getSimpleName();
            if (name.length() > 50) name = name.substring(0, 50);
            content.addChild(button("node:" + node.id(), (i == model.document.selection() ? "• " : "") + (i + 1) + ". " + name,
                    available, () -> model.select(index)));
        }
        if (model.document.selected() == null) return;
        var order = LinearLayout.horizontal().spacing(3);
        order.addChild(button("up", "Move up", available / 3 - 2, () -> model.move(-1)));
        order.addChild(button("down", "Move down", available / 3 - 2, () -> model.move(1)));
        order.addChild(button("delete", "Delete", available / 3 - 2, model::delete)); content.addChild(order);
        if (model.properties == null) {
            content.addChild(new MultiLineTextWidget(Component.translatableWithFallback("sh_better_ui.rich_text_editor.native.read_only", "Read-only node: retained exactly in exports. Preview may show alternate text; it is never saved back."), font).setMaxWidth(available)); return;
        }
        for (var field : RichTextEditorProperties.Field.values()) {
            if (model.properties.get(field) == null) continue;
            content.addChild(new MultiLineTextWidget(Component.translatable("sh_better_ui.rich_text_editor." + field.key()), font));
            if (field == RichTextEditorProperties.Field.TEXT) {
                var multiline = MultiLineEditBox.builder().setPlaceholder(Component.empty())
                        .build(font, available, 72, Component.translatable("sh_better_ui.rich_text_editor.field.text"));
                multiline.setCharacterLimit(131072); multiline.setValue(model.properties.get(field));
                multiline.setValueListener(value -> model.properties.set(field, value));
                focusIds.put("field:" + field, multiline); content.addChild(multiline); continue;
            }
            var input = new EditBox(font, available, 20, Component.translatable("sh_better_ui.rich_text_editor." + field.key()));
            input.setMaxLength(field == RichTextEditorProperties.Field.TEXT ? 131072 : 4096);
            input.setValue(model.properties.get(field));
            input.setResponder(value -> model.properties.set(field, value));
            focusIds.put("field:" + field, input); content.addChild(input);
        }
        if (model.document.selected() instanceof TextRun) {
            for (var flag : RichTextEditorProperties.Flag.values())
                content.addChild(button("flag:" + flag, flag.name() + ": " + model.properties.enabled(flag), available,
                        () -> model.properties.toggle(flag)));
        }
        var commit = LinearLayout.horizontal().spacing(3);
        commit.addChild(button("apply", "Apply properties", available / 2 - 2, model::apply));
        commit.addChild(button("discard", "Discard properties", available / 2 - 2, model::resetProperties)); content.addChild(commit);
    }
    private void json(LinearLayout content, int available, int bodyHeight) {
        var editor = MultiLineEditBox.builder().setPlaceholder(Component.literal("RichDocument JSON"))
                .build(font, available, Math.max(60, bodyHeight - 80), Component.literal("Document JSON"));
        editor.setCharacterLimit(NativeRichEditorModel.MAX_JSON_BYTES + 1); editor.setValue(model.json());
        editor.setValueListener(model::json); focusIds.put("json", editor); content.addChild(editor);
        content.addChild(button("export", "Export applied document to JSON", available, model::exportJson));
        content.addChild(button("import", "Import JSON as one undoable change", available, model::importJson));
        content.addChild(button("copy", "Copy exported JSON", available, () -> {
            // Validate before touching clipboard; mark saved only after clipboard write succeeds.
            if (model.propertiesDirty() || model.jsonDirty()
                    || !model.json().equals(RichTextEditorDocument.encode(model.document.document())))
                throw new IllegalStateException("Export the applied document first.");
            minecraft.keyboardHandler.setClipboard(model.json()); model.copiedDocument();
        }));
        content.addChild(button("paste", "Paste clipboard to JSON draft", available,
                () -> { var value = minecraft.keyboardHandler.getClipboard();
                    if (value.length() > NativeRichEditorModel.MAX_JSON_BYTES) throw new IllegalArgumentException("JSON exceeds 256 KiB.");
                    model.json(value); }));
        content.addChild(button("discardJson", "Discard JSON draft", available, model::discardJson));
    }
    private void ensurePreview(int width) {
        var document = model.document.document(); var language = net.minecraft.locale.Language.getInstance();
        if (previewDocument != document || previewWidth != width || previewLanguage != language) {
            try { preview = new NativeRichDocumentView(font, document, width); }
            catch (IllegalArgumentException invalid) { preview = null; status = Component.translatableWithFallback("sh_better_ui.rich_text_editor.native.preview_error", "Preview unavailable: %s", invalid.getMessage()); }
            previewDocument = document;
            previewWidth = width; previewLanguage = language;
        }
        previewScroll = Math.clamp(previewScroll, 0, Math.max(0, (preview == null ? 0 : preview.height()) - previewHeight));
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff151b25);
        graphics.centeredText(font, title.copy().append(model.dirty() ? " *" : ""), width / 2, 9, 0xffe7edf7);
        if (tab == Tab.PREVIEW || tab == Tab.EDIT && width >= 560) {
            ensurePreview(Math.max(1, width - previewX - 8));
            graphics.enableScissor(previewX, previewY, width - 8, previewY + previewHeight);
            try { if (preview != null) preview.render(graphics, previewX, previewY - previewScroll, previewY, previewY + previewHeight,
                    id -> minecraft.getResourceManager().getResource(id).isPresent()); }
            finally { graphics.disableScissor(); }
        }
        graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(status, Math.max(1, width - 16))), 8, Math.max(0, height - 48), 0xffb6c5d8, false);
        super.extractRenderState(graphics, x, y, partialTick);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if ((tab == Tab.PREVIEW || tab == Tab.EDIT && width >= 560) && x >= previewX && x < width - 8 && y >= previewY && y < previewY + previewHeight) {
            previewScroll = Math.clamp(previewScroll - (int) (dy * 24), 0, Math.max(0, (preview == null ? 0 : preview.height()) - previewHeight)); return dy != 0;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (tab == Tab.PREVIEW) {
            int max = Math.max(0, (preview == null ? 0 : preview.height()) - previewHeight);
            int delta = switch (event.key()) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> -18;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> 18;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> -previewHeight;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> previewHeight;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> -max;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> max;
                default -> 0;
            };
            if (delta != 0) { previewScroll = Math.clamp(previewScroll + delta, 0, max); return true; }
        }
        return super.keyPressed(event);
    }
    @Override public void onClose() {
        if (NativePort.screen(minecraft) != this) return;
        rememberFocus();
        if (!model.dirty()) { NativePort.show(minecraft, parent); return; }
        final ConfirmScreen[] dialog = new ConfirmScreen[1];
        dialog[0] = new ConfirmScreen(discard -> {
            if (NativePort.screen(minecraft) == dialog[0]) NativePort.show(minecraft, discard ? parent : this);
        }, Component.translatable("sh_better_ui.rich_text_editor.discard_title"),
                Component.translatable("sh_better_ui.rich_text_editor.native.discard_details"),
                Component.translatable("sh_better_ui.rich_text_editor.discard"),
                Component.translatable("sh_better_ui.rich_text_editor.cancel"));
        NativePort.show(minecraft, dialog[0]);
    }
    @Override public boolean isPauseScreen() { return false; }
}
