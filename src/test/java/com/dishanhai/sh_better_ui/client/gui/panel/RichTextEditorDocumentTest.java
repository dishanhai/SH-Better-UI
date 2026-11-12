// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RichTextEditorDocumentTest {
    private static final UiId FIRST = UiId.parse("sh_better_ui:first");
    private static final UiId SECOND = UiId.parse("sh_better_ui:second");

    @Test void editMoveAndHistoryPreserveSelectedIdAndSavedBaseline() {
        var document = new RichDocument(List.of(new TextRun(FIRST, "Original"), new TextRun(SECOND, "Second")));
        var model = new RichTextEditorDocument(document);
        var draft = new RichTextEditorProperties(model.selected());
        draft.set(RichTextEditorProperties.Field.TEXT, "繁體中文\n🦊");
        draft.set(RichTextEditorProperties.Field.COLOR, "#b2ccff");
        draft.toggle(RichTextEditorProperties.Flag.BOLD);
        model.change(draft.node());
        assertEquals(FIRST, model.selected().id());
        assertEquals("繁體中文\n🦊", ((TextRun) model.selected()).text());
        assertEquals(0xb2ccff, ((TextRun) model.selected()).style().color());
        assertTrue(((TextRun) model.selected()).style().bold());
        model.move(1);
        assertEquals(1, model.selection());
        assertEquals(FIRST, model.selected().id());
        model.undo();
        assertEquals(0, model.selection());
        model.undo();
        assertEquals(document, model.document());
        assertFalse(model.dirty());
        model.redo();
        assertTrue(model.dirty());
        model.markSaved();
        assertFalse(model.dirty());
        model.undo();
        assertTrue(model.dirty());
    }

    @Test void rejectedEditsLeaveDocumentAndHistoryIntact() {
        var initial = new RichDocument(List.of(new TextRun(FIRST, "Original")));
        var model = new RichTextEditorDocument(initial);
        var invalid = new ImageNode(SECOND, UiId.parse("sh_better_ui:../bad.png"), 16, 16, "");
        assertThrows(IllegalArgumentException.class, () -> model.insert(invalid));
        assertThrows(IllegalArgumentException.class, () -> model.change(new TextRun(SECOND, "Wrong ID")));
        assertEquals(initial, model.document());
        assertEquals(FIRST, model.selected().id());
        assertFalse(model.canUndo());
        assertFalse(model.dirty());
    }

    @Test void atomicReplacementReopensPassiveExtensionAndRejectsOverBudgetFiles(@TempDir Path directory) throws IOException {
        Path saved = directory.resolve("nested").resolve("document.json");
        var first = new RichDocument(List.of(new TextRun(FIRST, "Previous")));
        var extension = new RichExtensionNode(SECOND, UiId.parse("example:unknown"), 1, 16, 16, 8,
                "Fallback", "opaque payload", false);
        var replacement = new RichDocument(List.of(new TextRun(FIRST, "Current"), extension));
        RichTextEditorDocument.write(saved, RichTextEditorDocument.encode(first));
        RichTextEditorDocument.write(saved, RichTextEditorDocument.encode(replacement));
        assertEquals(replacement, RichTextEditorDocument.load(saved));
        try (var files = Files.list(saved.getParent())) {
            assertEquals(List.of(saved), files.toList());
        }
        Files.write(saved, new byte[]{(byte) 0xC0, (byte) 0xAF});
        assertThrows(IOException.class, () -> RichTextEditorDocument.load(saved));
        Files.write(saved, new byte[256 * 1024 + 1]);
        assertThrows(IOException.class, () -> RichTextEditorDocument.load(saved));
    }
}
