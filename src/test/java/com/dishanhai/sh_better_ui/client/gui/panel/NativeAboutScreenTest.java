// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeAboutScreenTest {
    @Test void scrollClampsAtBothEndsAndWhenContentFits() {
        assertEquals(0, NativeAboutScreen.clampScroll(-20, 500, 100));
        assertEquals(400, NativeAboutScreen.clampScroll(999, 500, 100));
        assertEquals(25, NativeAboutScreen.clampScroll(25, 500, 100));
        assertEquals(0, NativeAboutScreen.clampScroll(10, 90, 100));
    }
    @Test void noticeLinkTargetsBundledAttribution() {
        assertNotNull(NativeAboutScreen.class.getClassLoader().getResource(NativeAboutScreen.NOTICE_RESOURCE));
    }
    @Test void linksIdentifyShbuiNotUpstreamPlayer() {
        assertEquals("https://github.com/dishanhai/SH-Better-UI", NativeAboutScreen.PROJECT);
        assertEquals("sh_better_ui.about.title", ((net.minecraft.network.chat.contents.TranslatableContents)
                NativeAboutScreen.text("title").getContents()).getKey());
    }
}
