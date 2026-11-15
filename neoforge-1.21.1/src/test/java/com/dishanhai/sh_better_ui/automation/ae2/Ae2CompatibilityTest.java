// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.ae2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Ae2CompatibilityTest {
    private static final String SCREEN = "appeng.client.gui.me.common.MEStorageScreen";
    private static final String MENU = "appeng.menu.me.common.MEStorageMenu";
    private static final String PART = "appeng.parts.reporting.ItemTerminalPart";

    @Test void physicalTerminalRequiresExactVerifiedVersion() {
        assertTrue(Ae2Compatibility.supportsScreen("19.2.18", SCREEN, MENU, PART));
        assertFalse(Ae2Compatibility.supportsScreen("19.2.19", SCREEN, MENU, PART));
        assertFalse(Ae2Compatibility.supportsScreen("19.2.18-fork", SCREEN, MENU, PART));
    }

    @Test void wirelessAndAddonScreensFallBackEvenWhenTheyReuseTheStorageMenu() {
        assertFalse(Ae2Compatibility.supportsScreen("19.2.18", SCREEN, MENU,
                "appeng.helpers.WirelessTerminalMenuHost"));
        assertFalse(Ae2Compatibility.supportsScreen("19.2.18", "addon.CustomStorageScreen", MENU, PART));
        assertFalse(Ae2Compatibility.supportsScreen("19.2.18", SCREEN, "addon.CustomMenu", PART));
    }

    @Test void craftingTerminalUsesItsOwnScreenMenuPair() {
        assertTrue(Ae2Compatibility.supportsScreen("19.2.18",
                "appeng.client.gui.me.items.CraftingTermScreen",
                "appeng.menu.me.items.CraftingTermMenu", "appeng.parts.reporting.CraftingTerminalPart"));
        assertFalse(Ae2Compatibility.supportsScreen("19.2.18", SCREEN, MENU,
                "appeng.parts.reporting.CraftingTerminalPart"));
        assertFalse(Ae2Compatibility.supportsScreen("19.2.18", SCREEN, MENU,
                "appeng.parts.encoding.PatternEncodingTerminalPart"));
    }
}
