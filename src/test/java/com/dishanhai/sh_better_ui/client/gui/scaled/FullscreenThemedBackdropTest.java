// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
/** Source contract: both supported loaders share the fullscreen host and its native themed backdrop. */
class FullscreenThemedBackdropTest {
    @Test void fullscreenUsesOwnedThemedBackdropInsteadOfOpaqueRoot() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiFullscreenPreview.java"));
        assertTrue(source.contains("background.renderThemed(this, graphics, width, height, preferences, true)"));
        assertTrue(source.contains("background.reset();"));
        assertFalse(source.contains("DarkModeTheme.background(graphics, 0, 0, width, height)"));
        String backdrop = Files.readString(Path.of("src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiDotBackground.java"));
        assertTrue(backdrop.contains("preferences.highContrast() || client == null || client.level == null"));
    }
    @Test void previewHostsOfferEscapeCancellationBeforeVanillaClose() throws Exception {
        String base = "src/main/java/com/dishanhai/sh_better_ui/client/gui/";
        assertCancellationBeforeSuper(Path.of(base + "scaled/UiFullscreenPreview.java"), "public boolean keyPressed(");
        assertCancellationBeforeSuper(Path.of(base + "panel/DeveloperGalleryScreen.java"), "public boolean keyPressed(");
        assertCancellationBeforeSuper(Path.of("neoforge-26.1.2/" + base + "panel/DeveloperGalleryScreen.java"), "public boolean keyPressed(");
    }
    @Test void previewHostsReleaseCapturedPointerBeforeHitTestBasedSuperDispatch() throws Exception {
        for (String path : new String[]{
                "src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiFullscreenPreview.java",
                "src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/DeveloperGalleryScreen.java",
                "neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/DeveloperGalleryScreen.java"}) {
            String source = Files.readString(Path.of(path));
            int start = source.indexOf("public boolean mouseReleased(");
            int end = source.indexOf("super.mouseReleased", start);
            assertTrue(start >= 0 && end > start, path);
            String body = source.substring(start, end);
            assertTrue(body.contains("getFocused() instanceof"), path);
            assertTrue(body.contains("interaction.releasePointer("), path);
            assertTrue(body.contains("setDragging(false)"), path);
        }
    }
    private static void assertCancellationBeforeSuper(Path path, String method) throws Exception {
        String source = Files.readString(path);
        int start = source.indexOf(method);
        assertTrue(start >= 0, path.toString());
        int delegation = source.indexOf("return super.keyPressed", start);
        assertTrue(delegation > start, path.toString());
        String body = source.substring(start, delegation);
        assertTrue(body.contains("UiCancelableInteraction interaction"), path.toString());
        assertTrue(body.contains("&& interaction.cancelInteraction())"), path.toString());
        int cancel = body.indexOf("interaction.cancelInteraction()");
        assertTrue(body.indexOf("setDragging(false);", cancel) > cancel, path.toString());
        assertTrue(body.indexOf("return true;", cancel) > cancel, path.toString());
    }
}
