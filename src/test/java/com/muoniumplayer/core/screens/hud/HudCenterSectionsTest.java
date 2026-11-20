package com.muoniumplayer.core.screens.hud;

import com.muoniumplayer.core.settings.HudSetting;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudCenterSectionsTest {
    @Test
    void modulePagesPreserveTheEntireOriginalModelAndExposeTheLaunchSettings() {
        var modules = HudSettingsModel.categories();
        var tabs = HudCenterSections.moduleTabs();
        assertEquals(modules.length + 1, tabs.length);
        for (int i = 0; i < modules.length; i++) {
            assertSame(modules[i], HudCenterSections.category(HudCenterSections.Section.MODS, i));
        }
        var launchGroup = tabs[modules.length].groups[0];
        assertTrue(Arrays.stream(launchGroup.entries).anyMatch(entry -> entry.kind == HudSettingsModel.Kind.CHOICE));
        assertTrue(Arrays.asList(HudCenterSections.category(HudCenterSections.Section.PREFERENCES, 0).groups).contains(launchGroup));
        assertSame(tabs[modules.length], HudCenterSections.category(HudCenterSections.Section.MODS, Integer.MAX_VALUE));
        assertSame(tabs[0], HudCenterSections.category(HudCenterSections.Section.MODS, -1));
    }

    @Test
    void sidebarPagesPointToLiveSettingsAndPreserveEntryIdentity() {
        for (var section : HudCenterSections.Section.values()) {
            if (section == HudCenterSections.Section.EDIT_HUD) continue;
            var page = HudCenterSections.category(section, 0);
            assertTrue(Arrays.stream(page.groups).anyMatch(group -> group.entries.length > 0), section::name);
            assertSame(page, HudCenterSections.category(section, 0));
        }
        var favorites = HudCenterSections.category(HudCenterSections.Section.FAVORITES, 0);
        assertEquals(5, favorites.groups[0].entries.length);
        assertTrue(Arrays.stream(favorites.groups[0].entries).allMatch(entry -> entry.kind == HudSettingsModel.Kind.TOGGLE));
        assertEquals(List.of(HudSetting.LYRIC_FONT_SCALE, HudSetting.DYNAMIC_ISLAND_SCALE, HudSetting.SPECTRUM_OPACITY),
                Arrays.stream(favorites.groups[1].entries).map(entry -> entry.setting).toList());
        var presets = HudCenterSections.category(HudCenterSections.Section.PRESETS, 0);
        assertTrue(Arrays.asList(HudSettingsModel.categories()).contains(presets));
    }
}
