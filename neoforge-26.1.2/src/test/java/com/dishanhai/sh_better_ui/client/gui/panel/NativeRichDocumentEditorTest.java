// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class NativeRichDocumentEditorTest {
    private static final UiId FIRST = UiId.parse("test:first"), SECOND = UiId.parse("test:second");
    private static RichDocument initial() { return new RichDocument(List.of(new TextRun(FIRST, "Original\n第二行"), new TextRun(SECOND, "Second"))); }
    @Test void propertyDraftCannotBeLostBySelectionOrUndo() {
        var model = new NativeRichEditorModel(initial());
        model.properties.set(RichTextEditorProperties.Field.TEXT, "Draft");
        assertTrue(model.dirty());
        assertThrows(IllegalStateException.class, () -> model.select(1));
        assertThrows(IllegalStateException.class, model::undo);
        assertEquals(0, model.document.selection());
        assertEquals(initial(), model.document.document());
        model.resetProperties(); model.select(1); assertEquals(1, model.document.selection());
    }
    @Test void applyUndoRedoPreserveIdentityStyleAndSelection() {
        var model = new NativeRichEditorModel(initial());
        model.properties.set(RichTextEditorProperties.Field.TEXT, "繁體\n😀");
        model.properties.set(RichTextEditorProperties.Field.COLOR, "#33aaff");
        model.properties.toggle(RichTextEditorProperties.Flag.BOLD); model.apply(); model.move(1);
        assertEquals(FIRST, model.document.selected().id()); model.undo(); assertEquals(0, model.document.selection());
        var text = (TextRun) model.document.selected(); assertTrue(text.style().bold()); assertEquals("繁體\n😀", text.text());
        model.undo(); assertEquals(initial(), model.document.document()); model.redo(); assertTrue(model.document.dirty());
    }
    @Test void invalidPropertiesDoNotCommitAndRemainEditable() {
        var model = new NativeRichEditorModel(initial()); model.properties.set(RichTextEditorProperties.Field.COLOR, "bad rgb");
        assertThrows(IllegalArgumentException.class, model::apply);
        assertEquals(initial(), model.document.document()); assertTrue(model.propertiesDirty()); assertFalse(model.document.canUndo());
    }
    @Test void staticImageParagraphAndDeleteRoundTripThroughUndo() {
        var model = new NativeRichEditorModel(initial()); model.add("image");
        assertInstanceOf(ImageNode.class, model.document.selected());
        model.properties.set(RichTextEditorProperties.Field.TEXTURE, "test:textures/bad.gif");
        assertThrows(IllegalArgumentException.class, model::apply); model.resetProperties();
        model.add("paragraph"); assertInstanceOf(ParagraphBreak.class, model.document.selected());
        var selected = model.document.selected(); model.delete(); model.undo(); assertEquals(selected, model.document.selected());
    }
    @Test void malformedAndOverBudgetJsonKeepOriginalAndDraft() {
        var model = new NativeRichEditorModel(initial()); model.json("{oops");
        assertThrows(RuntimeException.class, model::importJson); assertEquals("{oops", model.json());
        assertEquals(initial(), model.document.document());
        model.json("汉".repeat(NativeRichEditorModel.MAX_JSON_BYTES / 2));
        assertThrows(IllegalArgumentException.class, model::importJson); assertEquals(initial(), model.document.document());
    }
    @Test void aggregateTextBudgetRejectsApplyWithoutChangingUndoOrDraft() {
        var initial = new RichDocument(List.of(new TextRun(FIRST, "a".repeat(70000)), new TextRun(SECOND, "b")));
        var model = new NativeRichEditorModel(initial); model.select(1);
        model.properties.set(RichTextEditorProperties.Field.TEXT, "b".repeat(70000));
        assertThrows(IllegalArgumentException.class, model::apply);
        assertEquals(initial, model.document.document()); assertFalse(model.document.canUndo());
        assertEquals(70000, model.properties.get(RichTextEditorProperties.Field.TEXT).length());
    }
    @Test void imageAltBudgetRejectsImportBeforeTransaction() {
        var model = new NativeRichEditorModel(initial());
        var oversized = new RichDocument(List.of(new ImageNode(FIRST, UiId.parse("test:textures/image.png"), 16, 16, "x".repeat(131073))));
        model.json(RichTextEditorDocument.encode(oversized));
        assertThrows(IllegalArgumentException.class, model::importJson);
        assertEquals(initial(), model.document.document()); assertFalse(model.document.canUndo()); assertTrue(model.jsonDirty());
    }
    @Test void unsupportedNodeRemainsExactAndReadOnlyThroughExportImport() {
        var passive = new RichExtensionNode(SECOND, UiId.parse("test:unknown"), 1, 16, 16, 8, "Fallback", "opaque payload", false);
        var initial = new RichDocument(List.of(new TextRun(FIRST, "Text"), passive));
        var model = new NativeRichEditorModel(initial); model.select(1); assertNull(model.properties);
        model.exportJson(); model.importJson(); assertEquals(initial, model.document.document());
        assertEquals(passive, model.document.document().nodes().get(1));
    }
    @Test void exportDoesNotSaveAndSuccessfulCopyMarksOnlyExactDocumentSaved() {
        var model = new NativeRichEditorModel(initial()); model.add("text"); model.exportJson();
        assertTrue(model.dirty()); model.copiedDocument(); assertFalse(model.dirty());
        model.json(model.json() + " "); assertThrows(IllegalStateException.class, model::copiedDocument);
        assertTrue(model.dirty()); model.discardJson(); assertFalse(model.dirty());
    }
    @Test void importIsSingleUndoableChangeAndJsonDraftPreventsOverwrite() {
        var model = new NativeRichEditorModel(initial());
        var replacement = new RichDocument(List.of(new TextRun(FIRST, "New")));
        model.json(RichTextEditorDocument.encode(replacement)); assertThrows(IllegalStateException.class, model::exportJson);
        model.importJson(); assertEquals(replacement, model.document.document());
        model.undo(); assertEquals(initial(), model.document.document()); model.redo(); assertEquals(replacement, model.document.document());
    }
    @Test void nativeScreenResizeKeepsPropertyDraftWithoutCommitting() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var screen = new NativeRichDocumentEditorScreen(null, Component.literal("Editor"), initial());
            screen.width = 800; screen.height = 460; screen.init();
            screen.model.properties.set(RichTextEditorProperties.Field.TEXT, "Draft\nnever lost");
            screen.width = 320; screen.height = 240; screen.init();
            assertEquals("Draft\nnever lost", screen.model.properties.get(RichTextEditorProperties.Field.TEXT));
            assertEquals(initial(), screen.document()); assertTrue(screen.model.dirty());
            assertFalse(screen.isPauseScreen()); assertDoesNotThrow(() -> screen.extractBackground(null, 0, 0, 0));
        }
    }
    @Test void focusAndMultilineDraftSurviveNarrowReflow() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            fixture.client().setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            var screen = new NativeRichDocumentEditorScreen(null, Component.literal("Editor"), initial());
            screen.width = 800; screen.height = 460; screen.init();
            var ids = (java.util.Map<?, ?>) field(screen, "focusIds");
            var editor = (net.minecraft.client.gui.components.MultiLineEditBox) ids.get("field:TEXT");
            assertEquals("Original\n第二行", editor.getValue());
            var body = (net.minecraft.client.gui.components.AbstractContainerWidget) field(screen, "body");
            body.setFocused(editor); screen.setFocused(body); editor.setValue("Pending\n😀");
            screen.width = 320; screen.init();
            ids = (java.util.Map<?, ?>) field(screen, "focusIds");
            var next = (net.minecraft.client.gui.components.MultiLineEditBox) ids.get("field:TEXT");
            body = (net.minecraft.client.gui.components.AbstractContainerWidget) field(screen, "body");
            assertEquals("Pending\n😀", next.getValue()); assertSame(next, body.getFocused());
            assertEquals(initial(), screen.document());
        }
    }
    @Test void previewReusesLayoutUntilDocumentOrWidthChanges() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var screen = new NativeRichDocumentEditorScreen(null, Component.literal("Editor"), initial());
            screen.width = 800; screen.height = 460; screen.init();
            var preview = field(screen, "preview"); screen.init(); assertSame(preview, field(screen, "preview"));
            screen.model.properties.set(RichTextEditorProperties.Field.TEXT, "Committed"); screen.model.apply(); screen.init();
            assertNotSame(preview, field(screen, "preview"));
        }
    }
    @Test void jsonDraftAndKeyboardPreviewSurviveTabAndResize() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var initial = new RichDocument(List.of(new TextRun(FIRST, "Long text\n".repeat(100))));
            var screen = new NativeRichDocumentEditorScreen(null, Component.literal("Editor"), initial);
            screen.width = 320; screen.height = 240;
            var tabField = screen.getClass().getDeclaredField("tab"); tabField.setAccessible(true);
            var tabs = tabField.getType().getEnumConstants();
            tabField.set(screen, tabs[2]); screen.model.json("{draft"); screen.init();
            assertEquals("{draft", ((net.minecraft.client.gui.components.MultiLineEditBox)
                    ((java.util.Map<?, ?>) field(screen, "focusIds")).get("json")).getValue());
            tabField.set(screen, tabs[1]); screen.init();
            assertTrue(screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0)));
            assertTrue((int) field(screen, "previewScroll") > 0);
            screen.width = 240; screen.init(); assertEquals("{draft", screen.model.json());
            assertTrue(screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0)));
            assertEquals(0, field(screen, "previewScroll")); assertEquals(initial, screen.document());
        }
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    @Test void staleCloseCannotNavigateOrDiscardEdits() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var screen = new NativeRichDocumentEditorScreen(null, Component.literal("Editor"), initial());
            screen.model.add("text"); fixture.client().screen = null;
            screen.onClose(); assertTrue(screen.model.dirty()); assertNull(fixture.client().screen);
        }
    }
}
