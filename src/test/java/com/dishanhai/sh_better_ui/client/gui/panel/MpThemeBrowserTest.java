package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.*;
import com.dishanhai.sh_better_ui.theme.*;
import com.dishanhai.sh_better_ui.theme.token.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class MpThemeBrowserTest {
    @Test void registeredCardsArePagedInsideViewportAndNeverChangeLiveManager() {
        try(var live=new ThemeManager()) {
            var draft=new AtomicReference<>(ThemeSettings.DEFAULT);
            var original=live.preferences();
            try(var browser=new MpThemeBrowser(live,draft::get,draft::set,id->false)) {
                var area=new Bounds(20,40,430,188);
                var cards=browser.layout(area,UiPreferences.fromSettings(ClientUiSettings.DEFAULT));
                assertEquals(6,cards.size()); assertTrue(browser.pageCount()>1);
                for(var card:cards) {
                    assertTrue(area.contains(card.getX(),card.getY()));
                    assertTrue(card.getX()+card.getWidth()<=area.right());
                    assertTrue(card.getY()+card.getHeight()<=area.bottom());
                }
                var card=(UiThemePreviewCard)cards.get(1); card.setFocused(true);
                assertTrue(card.keyPressed(GLFW.GLFW_KEY_ENTER,0,0));
                assertEquals(card.themeId(),draft.get().registeredThemeId());
                assertEquals(original,live.preferences());
                assertTrue(browser.page(1)); browser.layout(area,UiPreferences.fromSettings(ClientUiSettings.DEFAULT));
                assertFalse(card.active); assertFalse(card.visible);
            }
        }
    }
    @Test void resourceReloadAndRemovalRefreshPalettesAndRejectStaleSelection() {
        try(var live=new ThemeManager()) {
            var draft=new AtomicReference<>(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM));
            try(var browser=new MpThemeBrowser(live,draft::get,draft::set,id->id.equals("test:hidden"))) {
                var theme=Theme.builder("test:visible").parent(BuiltinThemes.DARK).token(ThemeTokens.ACCENT,ColorValue.argb(0xff123456)).build();
                live.replaceResourceThemes(List.of(theme,Theme.builder("test:hidden").build()),List.of());
                assertTrue(browser.refresh()); assertTrue(browser.themeIds().contains(theme.id()));
                assertFalse(browser.themeIds().contains("test:hidden"));
                assertEquals(0xff123456,browser.palette(theme.id()).accent());
                assertTrue(browser.select(theme.id()));
                live.replaceResourceThemes(List.of(),List.of());
                assertFalse(browser.select(theme.id())); assertTrue(browser.refresh());
                assertFalse(browser.themeIds().contains(theme.id()));
            }
        }
    }
    @Test void tinyViewportAndCloseDoNotLeaveActivatableHiddenCards() {
        try(var live=new ThemeManager()) {
            var draft=new AtomicReference<>(ThemeSettings.DEFAULT);
            var browser=new MpThemeBrowser(live,draft::get,draft::set,id->false);
            var old=browser.layout(new Bounds(0,0,140,88),UiPreferences.fromSettings(ClientUiSettings.DEFAULT));
            assertEquals(1,old.size());
            assertTrue(browser.layout(new Bounds(0,0,100,60),UiPreferences.fromSettings(ClientUiSettings.DEFAULT)).isEmpty());
            assertFalse(old.get(0).active); browser.close();
            assertFalse(browser.select(BuiltinThemes.LIGHT_ID));
        }
    }
    @Test void previewUsesPerThemeRgbOverrideAndKeepsGlassAlpha() {
        try(var live=new ThemeManager()) {
            var settings=ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.AURORA_FROST)
                    .withPalette(ThemeSettings.Base.DARK,"","ABCDEF","","","");
            var draft=new AtomicReference<>(settings);
            try(var browser=new MpThemeBrowser(live,draft::get,draft::set,id->false)) {
                var palette=browser.palette(MuoniumThemes.AURORA_FROST);
                assertEquals(0x88abcdef,palette.surface()); assertTrue(palette.liquidGlass());
                assertTrue(browser.select(MuoniumThemes.CRYSTAL));
                assertEquals(0x88abcdef,browser.palette(MuoniumThemes.AURORA_FROST).surface());
            }
        }
    }
}
