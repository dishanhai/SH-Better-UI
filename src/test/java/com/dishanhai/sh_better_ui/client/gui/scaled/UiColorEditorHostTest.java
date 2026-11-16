package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real host callbacks and event ordering, not only color replacement helpers. */
class UiColorEditorHostTest {
    private record Fixture(ThemeEffectEditor host, UiColorEditor editor, ThemeEffectFields.Field field) {}

    private Fixture mount(String group, List<String> path, int entry) throws Exception {
        var host = new ThemeEffectEditor(null, ClientUiSettings.DEFAULT, group,
                new JsonObject(), new JsonObject(), ignored -> fail("Draft editing must not save"));
        var field = ThemeEffectFields.fields(group).stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow();
        set(host, "detail", field); set(host, "entry", entry); set(host, "footer", 320);
        invoke(host, "showColor", new Class<?>[]{int.class, int.class}, 10, 240);
        var editor = (UiColorEditor) get(host, "colorEditor");
        assertNotNull(editor);
        // Deliberately leave Screen width/height at zero: rebuild uses its small-window
        // cancel-only path, avoiding a font/GPU fixture while exercising the real rebuild.
        host.setFocused(editor);
        return new Fixture(host, editor, field);
    }

    private void previewTransparent(Fixture f) {
        for (int i = 0; i < 3; i++) assertTrue(f.host.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        assertTrue(f.host.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0));
        assertTrue(f.editor.hasPendingChange());
        assertEquals(0, f.editor.state().color() >>> 24);
    }

    @Test void escapeRestoresInheritedStructureAndDoesNotCloseTheDetail() throws Exception {
        var f = mount("electricLogo", List.of("palette", "coreArgb"), 0);
        var original = ((JsonObject) get(f.host, "values")).deepCopy();
        previewTransparent(f);
        assertTrue(((JsonObject) get(f.host, "local")).entrySet().isEmpty());
        assertNotEquals(original, get(f.host, "values"));
        assertTrue(f.host.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertSame(f.field, get(f.host, "detail"));
        assertEquals(original, get(f.host, "values"));
        assertTrue(((JsonObject) get(f.host, "local")).entrySet().isEmpty());
        assertFalse(f.editor.isEditing());
    }

    @Test void hostForwardsCapturedOutsideReleaseAndClearsDragging() throws Exception {
        var f = mount("electricLogo", List.of("palette", "coreArgb"), 0);
        int x = f.editor.getX() + 80, y = f.editor.getY() + 96;
        assertTrue(f.host.mouseClicked(x, y, 0));
        assertTrue(f.editor.dragging(), "Native focus reassignment must not cancel pointer press");
        assertTrue(f.host.isDragging());
        assertTrue(f.host.mouseDragged(-100, y, 0, -180, 0));
        assertTrue(f.host.mouseReleased(-100, y, 0));
        assertFalse(f.host.isDragging()); assertFalse(f.editor.isEditing());
        assertEquals(0, ThemeEffectFields.get((JsonObject) get(f.host, "local"), f.field.path()).getAsInt() >>> 24);
    }

    @Test void removingMiddlePaletteEntryAfterKeyboardPreviewDoesNotOverwriteItsSuccessor() throws Exception {
        assertRemovePreservesOthers(1);
    }

    @Test void removingLastPaletteEntryAfterKeyboardPreviewDoesNotUseAnObsoleteIndex() throws Exception {
        var defaults = ThemeEffectFields.values(ClientUiSettings.DEFAULT, "antigravity", new JsonObject(), new JsonObject());
        assertRemovePreservesOthers(defaults.getAsJsonArray("colors").size() - 1);
    }

    private void assertRemovePreservesOthers(int index) throws Exception {
        var f = mount("antigravity", List.of("colors"), index);
        JsonArray expected = ((JsonObject) get(f.host, "values")).getAsJsonArray("colors").deepCopy();
        expected.remove(index);
        previewTransparent(f);
        invoke(f.host, "removeColor", new Class<?>[0]);
        assertEquals(expected, ((JsonObject) get(f.host, "values")).getAsJsonArray("colors"));
        assertEquals(expected, ((JsonObject) get(f.host, "local")).getAsJsonArray("colors"));
        assertFalse(f.editor.isEditing());
    }

    @Test void selectingAnotherPaletteEntryCancelsBeforeChangingTarget() throws Exception {
        var f = mount("antigravity", List.of("colors"), 0);
        var original = ((JsonObject) get(f.host, "values")).deepCopy();
        previewTransparent(f);
        invoke(f.host, "selectEntry", new Class<?>[]{int.class}, 1);
        assertEquals(1, get(f.host, "entry"));
        assertEquals(original, get(f.host, "values"));
        assertTrue(((JsonObject) get(f.host, "local")).entrySet().isEmpty());
    }

    @Test void rebuildCancelsDraftWithoutCreatingAnOverride() throws Exception {
        var f = mount("electricLogo", List.of("palette", "coreArgb"), 0);
        var original = ((JsonObject) get(f.host, "values")).deepCopy();
        previewTransparent(f);
        invoke(f.host, "rebuild", new Class<?>[0]);
        assertEquals(original, get(f.host, "values"));
        assertTrue(((JsonObject) get(f.host, "local")).entrySet().isEmpty());
        assertFalse(f.editor.isEditing());
    }

    private static Object get(Object target, String name) throws Exception {
        var field = ThemeEffectEditor.class.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = ThemeEffectEditor.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = ThemeEffectEditor.class.getDeclaredMethod(name, types); method.setAccessible(true);
        try { method.invoke(target, args); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }
}
