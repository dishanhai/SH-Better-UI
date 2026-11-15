// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingleLineEditTest {
    @Test
    void selectionReplacementUndoAndRedoPreserveEndpoints() {
        var field = new SingleLineEdit(12);
        field.setValue("0.25");
        field.move(-1, false, true);
        field.move(-1, false, true);
        assertEquals("25", field.selectedText());
        assertTrue(field.insert("75"));
        assertEquals("0.75", field.value());
        assertTrue(field.undo());
        assertEquals("25", field.selectedText());
        assertEquals(2, field.caret());
        assertTrue(field.redo());
        assertEquals("0.75", field.value());
        assertEquals(4, field.caret());
    }

    @Test
    void navigationDeletionAndLimitsKeepGraphemeClustersIntact() {
        String astronaut = "👩🏽‍🚀";
        var field = new SingleLineEdit(astronaut.length() + 1);
        field.setValue("甲" + astronaut + "乙");
        assertEquals("甲" + astronaut, field.value());
        field.move(-1, false, false);
        assertEquals(1, field.caret());
        assertTrue(field.delete(1, false));
        assertEquals("甲", field.value());
        assertTrue(field.insert("甲" + astronaut));
        assertEquals("甲甲", field.value());
    }

    @Test
    void clipboardInputStaysOnOneLineAndNewEditsClearRedo() {
        var field = new SingleLineEdit(16);
        assertTrue(field.insert("1\r\n2\t3\u0000"));
        assertEquals("1  2 3", field.value());
        field.selectWord(3);
        assertEquals("2", field.selectedText());
        assertTrue(field.deleteSelection());
        assertTrue(field.undo());
        assertTrue(field.insert("4"));
        assertEquals("1  4 3", field.value());
        assertFalse(field.redo());
        field.setValue("2");
        assertFalse(field.undo());
    }

    @Test
    void truncatesBeforeAnUnfitClusterAndRetainsExistingSelectionOnEmptyPaste() {
        var field = new SingleLineEdit(2);
        field.setValue("42");
        field.selectAll();
        assertFalse(field.insert("👩🏽‍🚀"));
        assertEquals("42", field.selectedText());
        assertTrue(field.insert("e\u0301"));
        assertEquals("e\u0301", field.value());
        assertTrue(field.delete(-1, false));
        assertEquals("", field.value());
    }
}
