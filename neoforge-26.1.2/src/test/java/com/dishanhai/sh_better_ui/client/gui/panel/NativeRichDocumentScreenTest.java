// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRichDocumentScreenTest {
    @Test void screenScrollsWithinBoundsAndRetainsPositionAcrossReflow() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var screen = new NativeRichDocumentScreen(null, Component.literal("Consumer document"), new RichDocument(java.util.List.of(new TextRun(UiId.parse("test:body"), "Long reader content\n".repeat(100)))));
            screen.width = 320; screen.height = 200; screen.init();
            assertDoesNotThrow(() -> screen.extractBackground(null,0,0,0));
            assertFalse(screen.mouseScrolled(-1,100,0,-1));
            assertTrue(screen.mouseScrolled(40,80,0,-1));
            assertEquals(24, field(screen,"scroll"));
            screen.width = 208; screen.init();
            assertEquals(24, field(screen,"scroll"));
            assertTrue(screen.keyPressed(new KeyEvent(269,0,0)));
            assertEquals(field(screen,"maxScroll"),field(screen,"scroll"));
            assertTrue(screen.keyPressed(new KeyEvent(268,0,0)));
            assertEquals(0,field(screen,"scroll"));
        }
    }

    private static int field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(owner);
    }
}
