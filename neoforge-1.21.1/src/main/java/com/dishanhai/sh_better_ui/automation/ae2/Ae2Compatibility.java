// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.ae2;

import java.util.Set;

/** Exact allow-list, shared by client replacement and server binding; no optional mod types in signatures. */
public final class Ae2Compatibility {
    public static final String VERSION = "19.2.18";
    private static final Set<String> PARTS = Set.of(
            "appeng.parts.reporting.ItemTerminalPart", "appeng.parts.reporting.CraftingTerminalPart");
    private static final Set<String> MENUS = Set.of(
            "appeng.menu.me.common.MEStorageMenu", "appeng.menu.me.items.CraftingTermMenu");
    private Ae2Compatibility() {}

    public static boolean supportsPart(String part) { return part != null && PARTS.contains(part); }
    public static boolean supportsMenu(String menu) { return menu != null && MENUS.contains(menu); }

    public static boolean supportsScreen(String version, String screen, String menu, String part) {
        if (!VERSION.equals(version) || !supportsPart(part)) return false;
        return ("appeng.client.gui.me.common.MEStorageScreen".equals(screen)
                && "appeng.menu.me.common.MEStorageMenu".equals(menu)
                && "appeng.parts.reporting.ItemTerminalPart".equals(part))
                || ("appeng.client.gui.me.items.CraftingTermScreen".equals(screen)
                && "appeng.menu.me.items.CraftingTermMenu".equals(menu)
                && "appeng.parts.reporting.CraftingTerminalPart".equals(part));
    }
}
