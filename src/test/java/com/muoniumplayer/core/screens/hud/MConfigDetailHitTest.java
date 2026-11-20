package com.muoniumplayer.core.screens.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import java.lang.reflect.Field;
import java.util.List;

class MConfigDetailHitTest {
    @Test
    void themeFocusMovesAcrossRowsAndIncludesLockedPositions() {
        assertEquals(1, MConfigHudScreen.moveThemeFocus(0, Keyboard.KEY_RIGHT, 3, 5));
        assertEquals(4, MConfigHudScreen.moveThemeFocus(1, Keyboard.KEY_DOWN, 3, 5));
        assertEquals(4, MConfigHudScreen.moveThemeFocus(2, Keyboard.KEY_DOWN, 3, 5));
        assertEquals(1, MConfigHudScreen.moveThemeFocus(4, Keyboard.KEY_UP, 3, 5));
        assertEquals(0, MConfigHudScreen.moveThemeFocus(0, Keyboard.KEY_LEFT, 3, 5));
    }

    @Test
    void resetDialogStartsAtCancelAndRequiresAnExplicitConfirmationChoice() {
        assertEquals(MConfigHudScreen.ResetDialogKey.CANCEL, MConfigHudScreen.resetDialogKey(Keyboard.KEY_RETURN, false));
        assertEquals(MConfigHudScreen.ResetDialogKey.CONFIRM, MConfigHudScreen.resetDialogKey(Keyboard.KEY_SPACE, true));
        assertEquals(MConfigHudScreen.ResetDialogKey.CANCEL, MConfigHudScreen.resetDialogKey(Keyboard.KEY_ESCAPE, true));
        assertEquals(MConfigHudScreen.ResetDialogKey.TOGGLE, MConfigHudScreen.resetDialogKey(Keyboard.KEY_TAB, false));
        assertEquals(MConfigHudScreen.ResetDialogKey.TOGGLE, MConfigHudScreen.resetDialogKey(Keyboard.KEY_RIGHT, false));
    }

    @Test
    void keyboardOpensResetDialogAndCancelsWithoutChangingSettings() throws Exception {
        MConfigHudScreen screen = newScreen();
        Field focus = MConfigHudScreen.class.getDeclaredField("navActionFocus");
        Field dialog = MConfigHudScreen.class.getDeclaredField("resetConfirmationVisible");
        Field confirm = MConfigHudScreen.class.getDeclaredField("resetConfirmFocused");
        focus.setAccessible(true);
        dialog.setAccessible(true);
        confirm.setAccessible(true);
        focus.setInt(screen, HudCenterSections.Section.values().length + 4);
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        assertTrue(dialog.getBoolean(screen));
        assertFalse(confirm.getBoolean(screen));
        screen.keyTyped(' ', Keyboard.KEY_SPACE);
        assertFalse(dialog.getBoolean(screen));
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        screen.keyTyped('\0', Keyboard.KEY_TAB);
        assertTrue(confirm.getBoolean(screen));
        screen.keyTyped('\0', Keyboard.KEY_ESCAPE);
        assertFalse(dialog.getBoolean(screen));
    }

    @Test
    @SuppressWarnings("unchecked")
    void arrowKeysNavigateFocusedThemeGridAndTabLeavesIt() throws Exception {
        MConfigHudScreen screen = newScreen();
        var factory = HudSettingsModel.class.getDeclaredMethod("themeGrid");
        factory.setAccessible(true);
        Object entry = factory.invoke(null);
        Class<?> cellType = Class.forName(MConfigHudScreen.class.getName() + "$Cell");
        var cellConstructor = cellType.getDeclaredConstructor(HudSettingsModel.Entry.class, int.class, int.class);
        cellConstructor.setAccessible(true);
        Object cell = cellConstructor.newInstance(entry, 0, 300);
        Class<?> rowType = Class.forName(MConfigHudScreen.class.getName() + "$MRow");
        var rowConstructor = rowType.getDeclaredConstructors()[0];
        rowConstructor.setAccessible(true);
        Object row = rowConstructor.newInstance(cell, null, null, null, 0, 0, 0, 300, 100,
                false, false, false, 0);
        Field rows = MConfigHudScreen.class.getDeclaredField("rows");
        Field focus = MConfigHudScreen.class.getDeclaredField("navActionFocus");
        Field themeFocus = MConfigHudScreen.class.getDeclaredField("themeKeyboardFocus");
        rows.setAccessible(true);
        focus.setAccessible(true);
        themeFocus.setAccessible(true);
        ((List<Object>) rows.get(screen)).add(row);
        focus.setInt(screen, HudCenterSections.Section.values().length + 5);
        themeFocus.setInt(screen, 0);
        screen.keyTyped('\0', Keyboard.KEY_RIGHT);
        assertEquals(1, themeFocus.getInt(screen));
        screen.keyTyped('\0', Keyboard.KEY_TAB);
        assertEquals(-1, themeFocus.getInt(screen));
        assertTrue(focus.getInt(screen) < HudCenterSections.Section.values().length + 5);
    }
    @Test
    void onlyExpandableCardsReserveTheArrowHitArea() {
        assertTrue(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.SLIDER, false));
        assertTrue(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.ACTION, false));
        assertFalse(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.ACTION, true));
        assertFalse(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.TOGGLE, false));
        assertFalse(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.CHOICE, false));
        assertFalse(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.COLOR, false));
        assertFalse(MConfigHudScreen.hasDetail(HudSettingsModel.Kind.THEME_GRID, false));
    }

    private static MConfigHudScreen newScreen() throws Exception {
        // Minecraft 26's Screen constructor reads the client font even in a headless test.
        Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
        Field instance = minecraft.getDeclaredField("instance");
        instance.setAccessible(true);
        Object original = instance.get(null);
        if (original != null) {
            return new MConfigHudScreen();
        }
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object client = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, minecraft);
        instance.set(null, client);
        try {
            return new MConfigHudScreen();
        } finally {
            instance.set(null, original);
        }
    }
}
