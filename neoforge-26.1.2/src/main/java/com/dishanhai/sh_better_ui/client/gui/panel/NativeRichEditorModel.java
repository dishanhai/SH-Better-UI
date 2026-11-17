// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Durable drafts are separate from validated document transactions and native widgets. */
final class NativeRichEditorModel {
    static final int MAX_JSON_BYTES = 256 * 1024;
    final RichTextEditorDocument document;
    RichTextEditorProperties properties;
    private String json = "", jsonBaseline = "";
    NativeRichEditorModel(RichDocument initial) { validate(initial); document = new RichTextEditorDocument(initial); resetProperties(); }
    static boolean editable(InlineNode node) {
        return node instanceof TextRun || node instanceof ParagraphBreak
                || node instanceof ImageNode image && image.texture().path().startsWith("textures/") && image.texture().path().endsWith(".png");
    }
    boolean propertiesDirty() { return properties != null && properties.dirty(); }
    boolean jsonDirty() { return !json.equals(jsonBaseline); }
    boolean dirty() { return document.dirty() || propertiesDirty() || jsonDirty(); }
    String json() { return json; }
    void json(String value) { json = value; }
    private void cleanProperties() { if (propertiesDirty()) throw new IllegalStateException("Apply or discard property changes first."); }
    void resetProperties() { properties = editable(document.selected()) ? new RichTextEditorProperties(document.selected()) : null; }
    void select(int index) { cleanProperties(); document.select(index); resetProperties(); }
    void apply() {
        if (properties == null) return;
        var node = properties.node();
        if (!editable(node)) throw new IllegalArgumentException("Only static resource-pack PNG images are editable.");
        if (node instanceof ImageNode image && (image.intrinsicWidth() > 4096 || image.intrinsicHeight() > 4096)
                || node instanceof ParagraphBreak paragraph && paragraph.spacing() > 4096)
            throw new IllegalArgumentException("Dimensions must not exceed 4096.");
        var nodes = new java.util.ArrayList<>(document.document().nodes());
        nodes.set(document.selection(), node); validate(new RichDocument(nodes));
        document.change(node); resetProperties();
    }
    void add(String kind) {
        cleanProperties();
        var id = UiId.parse("sh_better_ui:document/" + UUID.randomUUID());
        InlineNode node = switch (kind) {
            case "paragraph" -> new ParagraphBreak(id, 8);
            case "image" -> new ImageNode(id, UiId.parse("sh_better_ui:textures/gui/rich/material-shop.png"), 64, 64, "Material icon");
            default -> new TextRun(id, "Text");
        };
        var nodes = new java.util.ArrayList<>(document.document().nodes()); nodes.add(node); validate(new RichDocument(nodes));
        document.insert(node); resetProperties();
    }
    void delete() { cleanProperties(); document.delete(); resetProperties(); }
    void move(int step) { cleanProperties(); document.move(step); resetProperties(); }
    void undo() { cleanProperties(); document.undo(); resetProperties(); }
    void redo() { cleanProperties(); document.redo(); resetProperties(); }
    void exportJson() {
        cleanProperties();
        if (jsonDirty()) throw new IllegalStateException("Import or discard the JSON draft first.");
        json = jsonBaseline = RichTextEditorDocument.encode(document.document());
    }
    void discardJson() { json = jsonBaseline; }
    void importJson() {
        cleanProperties();
        if (json.length() > MAX_JSON_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("JSON exceeds 256 KiB.");
        var parsed = RichTextEditorDocument.decode(json);
        validate(parsed);
        document.replace(parsed); jsonBaseline = json; resetProperties();
    }
    private static void validate(RichDocument parsed) {
        // Validate native preview limits before changing either the committed document or drafts.
        long characters = 0;
        if (parsed.nodes().size() > 8192) throw new IllegalArgumentException("Too many document nodes.");
        for (var node : parsed.nodes()) {
            if (node instanceof TextRun text) characters += text.text().length();
            else if (node instanceof LocalizedText text) characters += text.fallback().length();
            else if (node instanceof ImageNode image) characters += image.altText().length();
            else if (node instanceof RichObjectNode object) characters += object.altText().length();
            else if (node instanceof RichExtensionNode extension) characters += extension.altText().length();
            if (node instanceof ImageNode image && (image.intrinsicWidth() > 4096 || image.intrinsicHeight() > 4096)
                    || node instanceof ParagraphBreak paragraph && paragraph.spacing() > 4096)
                throw new IllegalArgumentException("Dimensions must not exceed 4096.");
        }
        if (characters > 131072) throw new IllegalArgumentException("Document text exceeds 128 KiB.");
    }
    void copiedDocument() {
        cleanProperties();
        if (jsonDirty() || !json.equals(RichTextEditorDocument.encode(document.document())))
            throw new IllegalStateException("Export the current document before copying it.");
        document.markSaved();
    }
}
