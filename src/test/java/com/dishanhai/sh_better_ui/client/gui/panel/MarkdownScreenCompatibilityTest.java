package com.dishanhai.sh_better_ui.client.gui.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** The NeoForge super.render background hook must never repaint or blur already-rendered text. */
class MarkdownScreenCompatibilityTest {
    @Test void nativeBackgroundHookIsIntentionallyEmpty() {
        assertDoesNotThrow(() -> new MarkdownExampleScreen(null, "# Sample")
                .renderBackground(null, 0, 0, 0));
    }
}
