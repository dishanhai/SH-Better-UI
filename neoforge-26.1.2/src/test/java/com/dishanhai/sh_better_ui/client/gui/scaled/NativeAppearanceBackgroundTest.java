// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeAppearanceBackgroundTest {
    private static ThemeSettings custom(String effect) {
        return ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM)
                .withPalette(ThemeSettings.Base.DARK,"#102030","#203040","#304050","#eeeeee","#abcdef")
                .withBackgroundEffect(effect);
    }
    @Test void supportedChoicesMatchRealDrawModesAndPreserveUnknownId() {
        assertEquals(java.util.List.of("nativeBackdrop","nativeSolid","nativeGradient","dotMatrix"),NativeAppearanceBackground.supportedIds());
        var old = custom("example:black_hole");
        assertFalse(NativeAppearanceBackground.supported(old.backgroundEffect()));
        var sample = NativeAppearanceBackground.sample(800,450,old,new UiPreferences(false,false,false),123);
        assertEquals("nativeSolid",sample.effect()); assertEquals(0xff102030,sample.base());
        assertEquals("example:black_hole",old.backgroundEffect());
    }
    @Test void originalBackdropDoesNotFillOrDecorateTheWorld() {
        var settings=custom("nativeBackdrop");
        var sampled=NativeAppearanceBackground.sample(800,450,settings,new UiPreferences(false,false,false),5000);
        assertEquals("nativeBackdrop",sampled.effect()); assertEquals(0,sampled.countLimit());
        assertDoesNotThrow(() -> NativeAppearanceBackground.draw(null,800,450,settings,new UiPreferences(false,false,false),5000));
    }
    @Test void importedPlainBackdropLeavesTheOriginalSceneUnchanged() {
        var settings = custom("sh_better_ui:plain");
        assertTrue(NativeAppearanceBackground.supported(settings.backgroundEffect()));
        assertTrue(NativeAppearanceBackground.isBackdrop(settings.backgroundEffect()));
        assertEquals("nativeBackdrop", NativeAppearanceBackground.sample(800, 450, settings,
                new UiPreferences(false, false, false), 0).effect());
        assertEquals("sh_better_ui:plain", settings.backgroundEffect());
    }
    @Test void reducedMotionIsDeterministicButNormalDotsAnimate() {
        var settings=custom("dotMatrix");
        var reduced=new UiPreferences(false,true,false);
        assertEquals(NativeAppearanceBackground.sample(800,450,settings,reduced,1),
                NativeAppearanceBackground.sample(800,450,settings,reduced,9000));
        var moving=new UiPreferences(false,false,false);
        assertNotEquals(NativeAppearanceBackground.sample(800,450,settings,moving,1).phase(),
                NativeAppearanceBackground.sample(800,450,settings,moving,9000).phase());
    }
    @Test void highContrastRemovesMotionAndDecorations() {
        var settings=custom("dotMatrix"); var preferences=new UiPreferences(true,false,false);
        var dark=NativeAppearanceBackground.sample(800,450,settings,preferences,9000);
        assertEquals("nativeSolid",dark.effect()); assertEquals(0xff000000,dark.base()); assertEquals(0,dark.countLimit());
        var light=NativeAppearanceBackground.sample(800,450,settings.withMode(ThemeSettings.Mode.LIGHT),preferences,9000);
        assertEquals(0xffffffff,light.base()); assertEquals(0,light.phase());
    }
    @Test void proceduralWorkIsBoundedAndDoesNotNeedTextures() {
        var settings=custom("dotMatrix"); var preferences=new UiPreferences(false,false,false);
        var small=NativeAppearanceBackground.sample(800,450,settings,preferences,0);
        var large=NativeAppearanceBackground.sample(16000,9000,settings,preferences,0);
        assertTrue(large.spacing()>small.spacing()); assertEquals(1024,large.countLimit());
        assertEquals(0xffabcdef,large.accent());
    }
}
