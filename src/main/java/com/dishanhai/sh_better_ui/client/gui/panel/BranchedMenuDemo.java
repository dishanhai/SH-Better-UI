// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBranchedMenu;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 两套图库共用的菜单条目及样例状态。 */
final class BranchedMenuDemo {
    static final String ID = "branched_menu";
    static final String TITLE = "sh_better_ui.panel.developer.gallery.sample." + ID;
    static final int ORDER = 29;
    static final int HEIGHT = 112;
    static final int MIN_HEIGHT = 90;

    private static final UiSectionBar.Section SETTINGS = section("configuration", GraphicIcon.SETTINGS);
    private static final List<UiSectionBar.Section> CHILDREN = List.of(
            section("glass", GraphicIcon.BLUR), section("display", GraphicIcon.MONITOR),
            section("appearance", GraphicIcon.CUSTOM_THEME));
    private static final List<UiSectionBar.Section> LEAVES = List.of(section("overview", GraphicIcon.INFO));
    private int selected;
    private UiBranchedMenu menu;

    UiBranchedMenu layout(Bounds viewport, UiPreferences preferences) {
        boolean expanded = menu == null || menu.expanded();
        int width = Math.min(184, viewport.width());
        int menuHeight = Math.min(HEIGHT, viewport.height());
        var bounds = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + (viewport.height() - menuHeight) / 2, width, menuHeight);
        menu = new UiBranchedMenu(bounds, preferences, SETTINGS, CHILDREN, LEAVES, expanded, selected, index -> {
            selected = index;
            menu.select(index);
        });
        return menu;
    }

    private static UiSectionBar.Section section(String key, GraphicIcon icon) {
        return new UiSectionBar.Section(Component.translatable("sh_better_ui.panel.section." + key), icon);
    }
}
