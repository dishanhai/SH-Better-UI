package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemeColorEditorIntegrationTest {
    @Test void scalarAndPaletteUpdatesPreserveAlphaAndUnrelatedEntries() {
        int color = 0x4064a8ff;
        assertEquals(color, ThemeEffectEditor.replaceColor(new JsonPrimitive(-1), false, 0, color).getAsInt());
        var original = JsonParser.parseString("[1,2,3]");
        var next = ThemeEffectEditor.replaceColor(original, false, 1, color).getAsJsonArray();
        assertEquals(1, next.get(0).getAsInt()); assertEquals(color, next.get(1).getAsInt());
        assertEquals(3, next.get(2).getAsInt()); assertEquals(2, original.getAsJsonArray().get(1).getAsInt());
    }
    @Test void stopEditsPreservePositionsAndCanRollbackCapturedEntry() {
        var original = JsonParser.parseString("[{\"position\":0,\"argb\":1},{\"position\":0.7,\"argb\":2}]");
        var preview = ThemeEffectEditor.replaceColor(original, true, 0, 0x99abcdef);
        var restored = ThemeEffectEditor.replaceColor(preview, true, 0, 1);
        assertEquals(original, restored);
        assertEquals(.7, preview.getAsJsonArray().get(1).getAsJsonObject().get("position").getAsDouble());
        assertThrows(IllegalArgumentException.class, () -> ThemeEffectEditor.replaceColor(original, true, 2, 3));
    }
}
