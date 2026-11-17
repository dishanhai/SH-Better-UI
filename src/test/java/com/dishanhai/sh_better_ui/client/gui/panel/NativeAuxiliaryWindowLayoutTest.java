package com.dishanhai.sh_better_ui.client.gui.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAuxiliaryWindowLayoutTest {
    @Test void tinyAboutAndModScreensBuildAKeyboardFocusableBackButtonInsideTheViewport() {
        for (int[] size : new int[][]{{120,100},{160,80},{80,30}}) {
            var about = new NativeAboutScreen(null);
            about.width=size[0];about.height=size[1];about.init();
            assertBoundedBack(about);
            var mods = new NativeModsScreen(null,java.util.List.of());
            mods.width=size[0];mods.height=size[1];mods.init();
            assertBoundedBack(mods);
            assertFalse(about.mouseScrolled(10,10,1));
            assertFalse(mods.mouseScrolled(10,10,1));
        }
    }
    private static void assertBoundedBack(net.minecraft.client.gui.screens.Screen screen) {
        assertEquals(1,screen.children().size());
        var back=(net.minecraft.client.gui.components.Button)screen.children().get(0);
        assertTrue(back.active);assertTrue(back.visible);
        assertTrue(back.getX()>=0);assertTrue(back.getY()>=0);
        assertTrue(back.getX()+back.getWidth()<=screen.width);
        assertTrue(back.getY()+back.getHeight()<=screen.height);
        screen.setFocused(back);assertTrue(back.isFocused());
        assertEquals("gui.back",((net.minecraft.network.chat.contents.TranslatableContents)back.getMessage().getContents()).getKey());
    }

    @Test void favoriteRowsLeaveStatusAndFooterSpaceAtEverySupportedHeight() {
        for (int height=188;height<=320;height++) {
            int count=NativeFavoritesScreen.visibleRows(height);
            assertTrue(count>=1);
            assertTrue(78+count*36<=height-50, "height="+height);
        }
        assertEquals(5,NativeFavoritesScreen.visibleRows(320));
    }
    @Test void modRowsLeaveSearchAndFooterUnobscuredAtEverySupportedHeight() {
        for (int height=150;height<=360;height++) {
            int count=NativeModsScreen.visibleRows(height);
            assertTrue(count>=1);
            assertTrue(76+count*28<=height-40, "height="+height);
        }
        assertEquals(8,NativeModsScreen.visibleRows(360));
    }
}
