// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeAppearanceScreenTest {
    @TempDir Path directory;
    @Test void invalidRawDraftAndResetUndoNeverChangeSavedSettings() {
        ControlPanelSettings.load(directory);
        var saved=ControlPanelSettings.current();
        var draft=new NativeAppearanceDraft(saved.theme().withMode(ThemeSettings.Mode.CUSTOM),saved.theme());
        draft.color(0,"#12");assertTrue(draft.dirty());
        assertThrows(IllegalArgumentException.class,draft::value);
        draft.reset();assertTrue(draft.canUndoReset());draft.undoReset();
        assertEquals("#12",draft.state().colors().get(0));
        assertEquals(saved,ControlPanelSettings.current());
    }
    @Test void nativeApplyPersistsThemeAndMergesLatestOtherSettings() throws Exception {
        ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var screen=new NativeAppearanceScreen(null);client.client().screen=screen;screen.width=300;screen.height=220;screen.init();
            screen.draft.mode(ThemeSettings.Mode.CUSTOM);screen.draft.color(4,"#45ABCD");screen.draft.background("nativeGradient");screen.init();
            var other=ControlPanelSettings.current().withDisplay(true,true,false).withGlass(false,true,70,90,110);
            assertTrue(ControlPanelSettings.save(other));
            press(screen,"apply");
            assertFalse(screen.draft.dirty());
            assertEquals(other.withTheme(other.theme().withMode(ThemeSettings.Mode.CUSTOM)
                    .withPalette(ThemeSettings.Base.DARK,"","","","","#45ABCD").withBackgroundEffect("nativeGradient")),ControlPanelSettings.current());
            ControlPanelSettings.load(directory);assertEquals("#45ABCD",ControlPanelSettings.current().theme().accent());
            var reopened=new NativeAppearanceScreen(null);assertEquals("nativeGradient",reopened.draft.state().background());
        }
    }
    @Test void rawFieldsAndScrollSurviveResizeWithoutSaving() throws Exception {
        ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var screen=new NativeAppearanceScreen(null);client.client().screen=screen;screen.draft.mode(ThemeSettings.Mode.CUSTOM);
            screen.width=120;screen.height=150;screen.init();
            var input=(EditBox)controls(screen).get("color:0");input.setValue("bad!");
            var body=(AbstractContainerWidget)field(screen,"body");body.setScrollAmount(30);
            assertTrue(body.getX()>=0 && body.getX()+body.getWidth()<=screen.width);
            screen.width=160;screen.init();
            assertEquals("bad!",((EditBox)controls(screen).get("color:0")).getValue());
            assertEquals(30,((AbstractContainerWidget)field(screen,"body")).scrollAmount());
            assertFalse(controls(screen).get("apply").active);
            assertEquals(ControlPanelSettings.DEFAULT,ControlPanelSettings.current());
        }
    }
    @Test void failedSaveRetainsDraftAndParentCallbackIsNotCalled() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("sh_better_ui"),"blocked");ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            var screen=new NativeAppearanceScreen(null,ControlPanelSettings.current().theme(),theme->calls.incrementAndGet());
            client.client().screen=screen;screen.width=300;screen.height=220;screen.draft.background("nativeSolid");screen.init();
            press(screen,"apply");assertTrue(screen.draft.dirty());assertEquals(0,calls.get());
            assertEquals(ControlPanelSettings.DEFAULT,ControlPanelSettings.current());
        }
    }
    @Test void childApplyPreservesUnappliedParentChangesWithoutShowingSaved() throws Exception {
        ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var parent=new ControlPanelScreen(null);
            var draftField=ControlPanelScreen.class.getDeclaredField("draft");draftField.setAccessible(true);
            var dirty=ControlPanelSettings.current().withDisplay(true,true,false);draftField.set(parent,dirty);
            var appearance=ControlPanelSettings.current().theme().withMode(ThemeSettings.Mode.LIGHT);
            parent.appearanceApplied(appearance);
            assertEquals(dirty.withTheme(appearance),draftField.get(parent));
            assertNull(field(parent,"status"));
            assertNotEquals(field(parent,"saved"),field(parent,"draft"));
        }
    }
    @Test void parentAppearanceModeIsRegisteredForRenderingInputAndNarration() throws Exception {
        ControlPanelSettings.load(directory);
        try (var client = NativeTestClient.install()) {
            var screen = new ControlPanelScreen(null);
            client.client().screen = screen;
            screen.width = 500;
            screen.height = 300;
            screen.init();
            var selectTab = ControlPanelScreen.class.getDeclaredMethod("selectTab", int.class);
            selectTab.setAccessible(true);
            selectTab.invoke(screen, 2);
            var segment = screen.children().stream()
                    .filter(com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment.class::isInstance)
                    .map(com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment.class::cast)
                    .findFirst().orElseThrow();
            for (String registry : new String[]{"renderables", "narratables"}) {
                var list = net.minecraft.client.gui.screens.Screen.class.getDeclaredField(registry);
                list.setAccessible(true);
                assertTrue(((java.util.List<?>) list.get(screen)).contains(segment), registry);
            }
            assertTrue(segment.visible);
            screen.setFocused(segment);
            assertTrue(screen.keyPressed(new KeyEvent(262, 0, 0)));
            assertEquals(ThemeSettings.Mode.LIGHT, ((ControlPanelSettings) field(screen, "draft")).theme().mode());
            assertEquals(ThemeSettings.Mode.DARK, ControlPanelSettings.current().theme().mode());
            selectTab.invoke(screen, 1);
            assertFalse(screen.children().contains(segment));
            var renderables = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("renderables");
            renderables.setAccessible(true);
            assertFalse(((java.util.List<?>) renderables.get(screen)).contains(segment));
        }
    }
    @Test void suitesRestoreTheirOwnOverridesAndPersistWithoutChangingOtherSettings() throws Exception {
        ControlPanelSettings.load(directory);
        var suites=com.dishanhai.sh_better_ui.theme.DisplayStyleSuites.IDS;
        try(var client=NativeTestClient.install()) {
            var screen=new NativeAppearanceScreen(null);client.client().screen=screen;screen.width=300;screen.height=220;screen.init();
            press(screen,"suite:"+suites.get(0));
            assertEquals(suites.get(0),screen.draft.value().registeredThemeId());
            screen.draft.color(4,"#123456");
            screen.draft.suite(suites.get(1));assertEquals("",screen.draft.value().accent());
            screen.draft.suite(suites.get(0));assertEquals("#123456",screen.draft.value().accent());
            screen.init();press(screen,"apply");ControlPanelSettings.load(directory);
            assertEquals(suites.get(0),ControlPanelSettings.current().theme().registeredThemeId());
            assertEquals("#123456",ControlPanelSettings.current().theme().accent());
            screen.draft.reset();screen.draft.undoReset();assertEquals(suites.get(0),screen.draft.value().registeredThemeId());
        }
    }
    @Test void resetApplyPreservesOtherSuitesArchivedColors() {
        ControlPanelSettings.load(directory);
        var first=com.dishanhai.sh_better_ui.theme.DisplayStyleSuites.PORCELAIN;
        var second=com.dishanhai.sh_better_ui.theme.DisplayStyleSuites.JADE;
        var settings=ThemeSettings.DEFAULT.withRegisteredTheme(first)
                .withPalette(ThemeSettings.Base.DARK,"","","","","#123456")
                .withRegisteredTheme(second).withPalette(ThemeSettings.Base.DARK,"","","","","#ABCDEF");
        var draft=new NativeAppearanceDraft(settings,settings);
        draft.reset();
        assertEquals(ThemeSettings.Mode.DARK,draft.value().mode());
        assertEquals("",draft.value().accent());
        assertTrue(ControlPanelSettings.save(ControlPanelSettings.current().withTheme(draft.value())));
        ControlPanelSettings.load(directory);
        var reopened=new NativeAppearanceDraft(ControlPanelSettings.current().theme(),ControlPanelSettings.current().theme());
        reopened.suite(first);assertEquals("#123456",reopened.value().accent());
        reopened.suite(second);assertEquals("",reopened.value().accent());
    }
    @Test void registeredSuiteResolvesActualNativePaletteAndCustomOverrides() {
        for(String id:com.dishanhai.sh_better_ui.theme.DisplayStyleSuites.IDS) {
            var settings=ThemeSettings.DEFAULT.withRegisteredTheme(id);
            var palette=com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme.resolve(settings);
            try(var manager=new com.dishanhai.sh_better_ui.theme.ThemeManager()) {
                manager.preferences(manager.preferences().withReducedMotion(true).withFixedTheme(id));
                assertEquals(manager.snapshot().resolve(com.dishanhai.sh_better_ui.theme.token.ThemeTokens.ACCENT).argb(),palette.accent());
                assertEquals(manager.snapshot().resolve(com.dishanhai.sh_better_ui.theme.token.ThemeTokens.LIGHT),palette.light());
            }
            assertEquals(0xff123456,com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme.resolve(
                    settings.withPalette(ThemeSettings.Base.DARK,"","","","","#123456")).accent());
        }
    }
    @Test void fullPaletteArchiveKeepsDraftAndShowsCapacityInsteadOfCrashing() throws Exception {
        ControlPanelSettings.load(directory);
        var archive=com.dishanhai.sh_better_ui.config.ThemePaletteArchive.EMPTY;
        var color=new com.dishanhai.sh_better_ui.config.ThemePaletteArchive.Palette(ThemeSettings.Base.DARK,"","","","","#123456");
        for(int i=0;i<com.dishanhai.sh_better_ui.config.ThemePaletteArchive.MAX_PALETTES;i++)archive=archive.with("test:p"+i,color);
        var initial=new ThemeSettings(ThemeSettings.Mode.CUSTOM,ThemeSettings.Base.DARK,"","","","","#ABCDEF",
                ThemeSettings.DEFAULT.effects(),ThemeSettings.DEFAULT.backgroundEffect(),"",4,"",archive);
        try(var client=NativeTestClient.install()) {
            var screen=new NativeAppearanceScreen(null,initial,ignored->{});client.client().screen=screen;screen.width=300;screen.height=220;screen.init();
            assertDoesNotThrow(()->press(screen,"suite:"+com.dishanhai.sh_better_ui.theme.DisplayStyleSuites.PORCELAIN));
            assertEquals(initial,screen.draft.value());
            assertTrue(field(screen,"status").toString().contains("palette_capacity"));
        }
    }
    private static void press(Object screen,String key)throws Exception{((Button)controls(screen).get(key)).onPress(new KeyEvent(257,0,0));}
    @SuppressWarnings("unchecked") private static Map<String,AbstractWidget> controls(Object screen)throws Exception{return (Map<String,AbstractWidget>)field(screen,"controls");}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
}
