// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRichDocumentMediaTest {
    @Test void resizeRetainsOwnerButRemovalClosesAndReopeningGetsFreshOwner() throws Exception {
        try(var client=NativeTestClient.install()) {
            var screen=new NativeRichDocumentScreen(null,Component.literal("Media"),new RichDocument(java.util.List.of()));
            client.screen(screen);screen.width=320;screen.height=220;screen.init();
            var field=NativeRichDocumentScreen.class.getDeclaredField("media");field.setAccessible(true);
            var first=field.get(screen);assertNotNull(first);
            screen.width=200;screen.init();assertSame(first,field.get(screen));
            screen.removed();assertNull(field.get(screen));
            screen.removed();assertNull(field.get(screen));
            screen.init();assertNotSame(first,field.get(screen));
            screen.removed();
        }
    }
}
