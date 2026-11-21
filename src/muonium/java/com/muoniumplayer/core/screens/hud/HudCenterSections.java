// Author: dishanhai
package com.muoniumplayer.core.screens.hud;

import com.muoniumplayer.MuoniumPlayerClient;
import com.muoniumplayer.core.rendering.HudSettingsIcons;
import com.muoniumplayer.core.settings.HudSetting;
import com.muoniumplayer.core.settings.PlayerAppearance;
import com.muoniumplayer.core.utils.other.BrowserUtils;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import static com.muoniumplayer.core.screens.hud.HudSettingsModel.*;

/** Sections shown by the embedded settings center. All editable rows use the player's settings. */
final class HudCenterSections {
    enum Section {
        EDIT_HUD("edit_hud", HudSettingsIcons.CLASSIC_UI),
        FAVORITES("favorites", HudSettingsIcons.SPARKLE),
        MODS("mods", HudSettingsIcons.TUNE),
        PRESETS("presets", HudSettingsIcons.RESTORE),
        THEMES("themes", HudSettingsIcons.PALETTE),
        PREFERENCES("preferences", HudSettingsIcons.MODERN_UI),
        ABOUT("about", HudSettingsIcons.INFO);

        private final String key;
        private final String glyph;

        Section(String key, String glyph) {
            this.key = key;
            this.glyph = glyph;
        }

        String title() { return text(key); }
        String icon() { return glyph; }
    }

    private static final int ACCENT = 0xff405aff;
    private static final String SOURCE = "https://github.com/zihe114514/MuoniumPlayer-multiversion";
    private static final EnumMap<Section, Category> pages = new EnumMap<>(Section.class);
    private static Category[] tabs;
    private static Language language;
    private static Entry hostControls;
    private static Entry hostTheme;

    private HudCenterSections() {}

    static String text(String suffix, Object... args) {
        return Component.translatable("sh_better_ui.muonium.center." + suffix, args).getString();
    }

    static Entry controlsOption() {
        hostControls = toggle(text("host_controls"), text("host_controls.desc"), PlayerAppearance::useShbuiControls,
                () -> PlayerAppearance.setShbuiControls(!PlayerAppearance.useShbuiControls()));
        return hostControls;
    }

    static Entry themeOption() {
        hostTheme = toggle(text("host_theme"), text("host_theme.desc"), PlayerAppearance::useShbuiTheme,
                () -> PlayerAppearance.setShbuiTheme(!PlayerAppearance.useShbuiTheme()));
        return hostTheme;
    }

    static String hostOptionId(Entry entry) {
        if (entry != null && entry == hostControls) return "shbui_controls";
        if (entry != null && entry == hostTheme) return "shbui_theme";
        return null;
    }

    static Category[] moduleTabs() {
        refresh();
        return tabs;
    }

    static Category category(Section section, int moduleTab) {
        refresh();
        return section == Section.MODS ? tabs[Math.max(0, Math.min(moduleTab, tabs.length - 1))] : pages.get(section);
    }

    private static void refresh() {
        Language current = Language.getInstance();
        if (tabs != null && language == current) return;
        Category[] modules = HudSettingsModel.categories();
        List<Group> themes = new ArrayList<>();
        List<Group> preferences = new ArrayList<>();
        List<Group> interfaceGroups = new ArrayList<>();
        List<Group> about = new ArrayList<>();
        about.add(new Group(text("project"), HudSettingsIcons.INFO,
                button(text("player_version", MuoniumPlayerClient.MOD_VERSION), text("player_credit"), () -> BrowserUtils.open(SOURCE))));
        for (Category module : modules) {
            if ("presets".equals(module.id)) pages.put(Section.PRESETS, module);
            for (Group group : module.groups) {
                if (group.centerSection == Section.THEMES) themes.add(group);
                if (group.centerSection == Section.PREFERENCES) preferences.add(group);
                if (group.centerSection == Section.ABOUT) about.add(group);
                if (group.centerSection == Section.MODS) {
                    interfaceGroups.add(group);
                    preferences.add(group);
                }
            }
        }

        preferences.add(new Group(text("host"), HudSettingsIcons.TUNE,
                button(Component.translatable("sh_better_ui.panel.title").getString(), text("host_desc"), HudSettingsModel::openHostPreferences)));
        pages.put(Section.PREFERENCES, page(Section.PREFERENCES, preferences));
        pages.put(Section.THEMES, page(Section.THEMES, themes));
        pages.put(Section.ABOUT, page(Section.ABOUT, about));
        pages.put(Section.FAVORITES, favorites());
        pages.put(Section.EDIT_HUD, page(Section.EDIT_HUD, List.of()));
        tabs = Arrays.copyOf(modules, modules.length + 1);
        tabs[modules.length] = new Category(text("interface"), "", ACCENT, HudSettingsIcons.MODERN_UI,
                interfaceGroups.toArray(Group[]::new));
        language = current;
    }

    private static Category page(Section section, List<Group> groups) {
        return new Category(section.title(), "", ACCENT, section.icon(), groups.toArray(Group[]::new));
    }

    private static Category favorites() {
        Entry[] switches = Arrays.stream(MuoniumFavorites.QuickToggle.values())
                .map(control -> toggle(control.title(), control.description(), control.read::get, control::toggle))
                .toArray(Entry[]::new);
        return page(Section.FAVORITES, List.of(
                new Group(text("overlays"), HudSettingsIcons.VISIBILITY, switches),
                new Group(text("adjustments"), HudSettingsIcons.TUNE,
                        slider(HudSetting.LYRIC_FONT_SCALE, text("fav.lyric_size")),
                        slider(HudSetting.DYNAMIC_ISLAND_SCALE, text("fav.island_size")),
                        slider(HudSetting.SPECTRUM_OPACITY, text("fav.spectrum_opacity")))));
    }
}
