package com.dishanhai.sh_better_ui.client.gui.panel;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ControlCenterNavigationTest {
    private static ControlCenterNavigation.Location at(String section, String query, int scroll) {
        return new ControlCenterNavigation.Location("CONFIGURATION", section, query, scroll);
    }
    @Test void dynamicChoicesExcludeOnlyOwnedRuntimeThemesAndPersistRestartResolvableIds() {
        try (var live = new com.dishanhai.sh_better_ui.theme.ThemeManager();
             var restarted = new com.dishanhai.sh_better_ui.theme.ThemeManager()) {
            String baseId = com.dishanhai.sh_better_ui.client.theme.MinecraftThemes.CONFIGURED_PALETTE_ID;
            // An external theme owns the usual spelling; SHBUI therefore owns only the collision suffix.
            var external = com.dishanhai.sh_better_ui.theme.Theme.builder(baseId)
                    .parent(com.dishanhai.sh_better_ui.theme.BuiltinThemes.DARK).build();
            var owned = com.dishanhai.sh_better_ui.theme.Theme.builder(baseId + "_1").parent(baseId).build();
            live.register(external); live.register(owned); restarted.register(external);
            java.util.function.Predicate<String> internal = id -> live.registry().find(id).orElse(null) == owned;
            var choices = ControlPanelScreen.dynamicThemeChoices(live.registry().themes().keySet(), internal);
            assertTrue(choices.contains(baseId)); assertFalse(choices.contains(owned.id()));
            assertFalse(ControlPanelScreen.canStageDynamicTheme(owned.id(), internal));
            assertTrue(ControlPanelScreen.canStageDynamicTheme(baseId, internal));
            assertTrue(ControlPanelScreen.canStageDynamicTheme("", internal));
            assertTrue(ControlPanelScreen.canStageDynamicTheme("user:temporarily_unavailable", internal));
            var json = new com.google.gson.Gson();
            for (String id : choices) {
                var saved = new com.dishanhai.sh_better_ui.config.DynamicThemeSettings(id, 100, false);
                var restored = json.fromJson(json.toJson(saved), com.dishanhai.sh_better_ui.config.DynamicThemeSettings.class);
                assertTrue(restarted.registry().find(restored.fixedTheme()).isPresent(), restored.fixedTheme());
                assertDoesNotThrow(() -> new com.dishanhai.sh_better_ui.theme.ThemeResolver().resolve(
                        restarted.registry(), restarted.tokenCatalog(), List.of(restored.fixedTheme()), null));
            }
            // Reusing the same spelling for an external replacement must not permanently blacklist it.
            live.register(com.dishanhai.sh_better_ui.theme.Theme.builder(owned.id()).build(),
                    com.dishanhai.sh_better_ui.theme.ThemeRegistry.DuplicatePolicy.REPLACE_CODE);
            assertTrue(ControlPanelScreen.canStageDynamicTheme(owned.id(), internal));
            assertTrue(ControlPanelScreen.dynamicThemeChoices(live.registry().themes().keySet(), internal).contains(owned.id()));
        }
    }

    @Test void backForwardRetainQueryAndScrollWithoutOwningSettings() {
        var first = at("glass", "glass blur", 38);
        var model = new ControlCenterNavigation(first);
        model.visit(at("display", "", 70));
        assertEquals(first, model.back());
        assertEquals(70, model.forward().scroll());
        assertFalse(model.canForward());
    }
    @Test void newBranchDropsForwardAndRepeatedDestinationDoesNotDuplicate() {
        var model = new ControlCenterNavigation(at("glass", "", 0));
        model.visit(at("display", "", 0)); model.back();
        model.visit(at("feedback", "", 0)); model.visit(model.current());
        assertFalse(model.canForward()); model.back(); assertFalse(model.canBack());
    }
    @Test void journalBoundedAndScrollClamped() {
        var model = new ControlCenterNavigation(at("glass", "", -1));
        assertEquals(0, model.current().scroll());
        for (int i=0;i<200;i++) model.visit(at("s"+i, "", i));
        int n=0; while(model.canBack()) { model.back(); n++; }
        assertEquals(63,n);
    }
    @Test void searchHandlesUnicodeCaseWhitespaceAndMultipleTerms() {
        var entries = List.of(new ControlCenterNavigation.Entry("a","b","玻璃 Glass","radius blur"),
                new ControlCenterNavigation.Entry("a","c","Music","volume"));
        assertEquals(entries.subList(0,1),ControlCenterNavigation.search(entries,"  ＧＬＡＳＳ   blur "));
        assertEquals(entries.subList(0,1),ControlCenterNavigation.search(entries,"玻璃"));
        assertTrue(ControlCenterNavigation.search(entries,"blur volume").isEmpty());
        assertEquals(entries,ControlCenterNavigation.search(entries,""));
    }
}
