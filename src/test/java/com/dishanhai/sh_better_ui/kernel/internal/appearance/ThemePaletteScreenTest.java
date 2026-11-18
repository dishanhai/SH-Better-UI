package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiColorEditor;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ThemePaletteScreenTest {
    private record Fixture(ThemePaletteScreen screen, UiColorEditor editor, ArrayList<String> results) {}
    private Fixture mount() throws Exception {
        var results = new ArrayList<String>();
        var screen = new ThemePaletteScreen(null, ClientUiSettings.DEFAULT.withTheme(
                ClientUiSettings.DEFAULT.theme().withMode(com.dishanhai.sh_better_ui.config.ThemeSettings.Mode.CUSTOM)), 4, Component.literal("Accent"), results::add);
        var mount = ThemePaletteScreen.class.getDeclaredMethod("mountEditor", int.class, int.class, int.class);
        mount.setAccessible(true); mount.invoke(screen, 10, 70, 240);
        var field = ThemePaletteScreen.class.getDeclaredField("editor"); field.setAccessible(true);
        var editor = (UiColorEditor) field.get(screen); screen.setFocused(editor);
        return new Fixture(screen, editor, results);
    }
    private ThemePaletteEdit edit(Fixture f) throws Exception {
        var field = ThemePaletteScreen.class.getDeclaredField("edit"); field.setAccessible(true);
        return (ThemePaletteEdit) field.get(f.screen);
    }
    @Test void escapeCancelsPendingRgbWithoutConvertingInheritanceToExplicitColor() throws Exception {
        var f = mount();
        assertFalse(f.editor.alphaEditable());
        assertTrue(f.screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertTrue(f.editor.isEditing());
        assertEquals("", edit(f).value());
        assertTrue(f.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(f.editor.isEditing()); assertEquals("", edit(f).value()); assertTrue(f.results.isEmpty());
    }
    @Test void outsidePointerReleaseOnlyCommitsLocalCandidateAndExplicitConfirmDeliversOnce() throws Exception {
        var f = mount(); int y = f.editor.getY() + 30;
        assertTrue(f.screen.mouseClicked(f.editor.getX() + 80, y, 0));
        assertTrue(f.editor.dragging());
        assertTrue(f.screen.mouseDragged(-100, y, 0, -180, 0));
        assertTrue(f.screen.mouseReleased(-100, y, 0));
        assertFalse(f.screen.isDragging()); assertFalse(f.editor.isEditing());
        assertFalse(edit(f).value().isEmpty()); assertTrue(f.results.isEmpty());
        var accept = ThemePaletteScreen.class.getDeclaredMethod("acceptDraft"); accept.setAccessible(true);
        assertEquals(true, accept.invoke(f.screen)); assertEquals(false, accept.invoke(f.screen));
        assertEquals(1, f.results.size()); assertEquals(edit(f).value(), f.results.get(0));
    }
    @Test void removalAndFocusLossNeverPublishChildDraft() throws Exception {
        var f = mount();
        f.editor.state().preview(2, 0.2f);
        f.screen.setFocused(null);
        assertFalse(f.editor.isEditing()); assertEquals("", edit(f).value());
        f.editor.state().preview(2, 0.1f);
        f.screen.removed();
        assertFalse(f.editor.isEditing()); assertEquals("", edit(f).value()); assertTrue(f.results.isEmpty());
    }
    @Test void parentResetClearsOnlyCurrentPaletteAndUndoRestoresWholeDraft() throws Exception {
        var custom = ClientUiSettings.DEFAULT.theme()
                .withMode(com.dishanhai.sh_better_ui.config.ThemeSettings.Mode.CUSTOM)
                .withBackgroundEffect("blackHole");
        var a = com.dishanhai.sh_better_ui.theme.MuoniumThemes.VELVET_ROSE;
        var b = com.dishanhai.sh_better_ui.theme.MuoniumThemes.MIDNIGHT_GRAPHITE;
        var selected = custom.withRegisteredTheme(a)
                .withPalette(custom.customBase(), "112233", "", "", "", "")
                .withRegisteredTheme(b).withPalette(custom.customBase(), "445566", "", "", "", "")
                .withRegisteredTheme(a);
        var host = new ThemeCustomizationScreen(null, null, ClientUiSettings.DEFAULT.withTheme(selected),
                com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection.COLORS,
                ignored -> fail("Reset must not publish"));
        var reset = ThemeCustomizationScreen.class.getDeclaredMethod("reset"); reset.setAccessible(true);
        var field = ThemeCustomizationScreen.class.getDeclaredField("draft"); field.setAccessible(true);
        reset.invoke(host);
        var cleared = (com.dishanhai.sh_better_ui.config.ThemeSettings) field.get(host);
        assertEquals(a, cleared.registeredThemeId()); assertEquals("", cleared.background());
        assertEquals("blackHole", cleared.backgroundEffect()); assertEquals(selected.effects(), cleared.effects());
        assertEquals("#445566", cleared.withRegisteredTheme(b).background());
        reset.invoke(host); assertEquals(selected, field.get(host));
    }

    @Test void fullPaletteArchiveKeepsDraftAndShowsRecoverableErrorInsteadOfThrowing() throws Exception {
        var theme = ClientUiSettings.DEFAULT.theme().withMode(com.dishanhai.sh_better_ui.config.ThemeSettings.Mode.CUSTOM);
        for (int i = 0; i <= com.dishanhai.sh_better_ui.config.ThemePaletteArchive.MAX_PALETTES; i++) {
            theme = theme.withRegisteredTheme("test:theme_" + i)
                    .withPalette(theme.customBase(), "112233", "", "", "", "");
        }
        var host = new ThemeCustomizationScreen(null, null, ClientUiSettings.DEFAULT.withTheme(theme),
                com.dishanhai.sh_better_ui.kernel.api.AppearanceService.ThemeSection.COLORS,
                ignored -> fail("Selection must not publish"));
        var select = ThemeCustomizationScreen.class.getDeclaredMethod("selectRegisteredTheme", String.class); select.setAccessible(true);
        var draft = ThemeCustomizationScreen.class.getDeclaredField("draft"); draft.setAccessible(true);
        var error = ThemeCustomizationScreen.class.getDeclaredField("selectionError"); error.setAccessible(true);
        assertEquals(false, select.invoke(host, "test:next"));
        assertEquals(theme, draft.get(host)); assertNotNull(error.get(host));
        var reset = ThemeCustomizationScreen.class.getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(host);
        assertNull(error.get(host));
        assertEquals(true, select.invoke(host, "test:next"));
        assertNull(error.get(host));
    }

    @Test void ownedBackdropReleasesItsActiveRendererOnRemovalAndRepeatedRemovalIsSafe() throws Exception {
        var f = mount();
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new com.dishanhai.sh_better_ui.client.api.BackgroundRegistry();
        registry.register(new com.dishanhai.sh_better_ui.client.api.BackgroundSpec("test:palette", "test.palette", null,
                ignored -> new com.dishanhai.sh_better_ui.client.api.BackgroundSpec.Renderer() {
                    @Override public void draw(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                            com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds area,
                            com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState mouse, double x, double y, long now,
                            com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences preferences) {}
                    @Override public void close() { closed.incrementAndGet(); }
                }));
        var layer = registry.layer(null);
        // This is a lifecycle fixture; real drawing now requires the native GL state scope.
        var renderer = layer.getClass().getDeclaredField("renderer"); renderer.setAccessible(true);
        renderer.set(layer, registry.find("test:palette").orElseThrow().factory().create(null));
        var owner = ThemePaletteScreen.class.getDeclaredField("background"); owner.setAccessible(true);
        var effect = com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground.class.getDeclaredField("effectLayer");
        effect.setAccessible(true); effect.set(owner.get(f.screen), layer);
        f.screen.removed(); f.screen.removed();
        assertEquals(1, closed.get()); assertNull(effect.get(owner.get(f.screen)));
    }

    @Test void backdropRepaintPrecedesWidgetsAndIsNotAnAccumulatingAlphaFill() throws Exception {
        var path = java.nio.file.Path.of("src/main/java/com/dishanhai/sh_better_ui/kernel/internal/appearance/ThemePaletteScreen.java");
        String source = java.nio.file.Files.readString(path);
        int render = source.indexOf("public void render(GuiGraphics");
        int backdrop = source.indexOf("background.renderThemed(this, g, width, height, preferences, false)", render);
        int widgets = source.indexOf("super.render(g, mouseX, mouseY, partialTick)", render);
        assertTrue(backdrop > render && widgets > backdrop);
        assertFalse(source.substring(render, widgets).contains("g.fill(0, 0, width, height, palette.background())"));
    }

}
