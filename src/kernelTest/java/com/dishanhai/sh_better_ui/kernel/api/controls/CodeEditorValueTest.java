// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeEditorValueTest {
    @Test void replacementPreservesTabsNewlinesAndTheUnselectedSuffix() {
        var value = new CodeEditorValue(40);
        value.replace("left-REPLACE-right");
        assertTrue(value.replaceRange(5, 12, "{\r\n\t§key\n}"));
        assertEquals("left-{\r\n\t§key\n}-right", value.get());
        assertFalse(value.replaceRange(0, 0, "x".repeat(41)));
        assertEquals("left-{\r\n\t§key\n}-right", value.get());
    }
    @Test void preservesMultilineAndRejectsOverflowWithoutChangingPriorValue() {
        var value = new CodeEditorValue(1_048_576);
        String json = "{\n  \"name\": \"矿石\",\n  \"data\": \"" + "x".repeat(500_000) + "\"\n}";
        assertTrue(value.replace(json));
        assertEquals(json, value.get());
        assertFalse(value.replace("x".repeat(1_048_577)));
        assertEquals(json, value.get());
    }
}
