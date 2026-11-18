// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.DisplayStyleSuites;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplaySuiteButtonPaintTest {
    private static final UiTheme PALETTE=new UiTheme(false,0xff10121e,0xff1c2032,0xff7a84ab,
            0xfff2f3ff,0xffa5b4ff,31);
    @Test void onlyUnboundDefaultSuiteMaterialIsEligible(){
        var settings=ThemeSettings.DEFAULT.withRegisteredTheme(DisplayStyleSuites.MIDNIGHT);
        assertNull(sample(ThemeSettings.DEFAULT,UiStyle.DEFAULT,false));
        assertNull(sample(settings.withMode(ThemeSettings.Mode.DARK),UiStyle.DEFAULT,false));
        assertNull(sample(settings,UiStyle.NETWORK,false));
        assertNull(sample(settings,UiStyle.LIQUID_GLASS,false));
        assertNull(sample(settings,UiStyle.DEFAULT,true));
        assertNotNull(sample(settings,UiStyle.DEFAULT,false));
    }
    private ThemeButtonStyle.Paint sample(ThemeSettings settings,UiStyle style,boolean preview){
        return DisplaySuiteButtonPaint.sample(settings,PALETTE,style,preview,false,false,true,false,0,0);
    }
    @Test void geometryAndStateTokensReachNativeFallback(){
        var midnight=sample(ThemeSettings.DEFAULT.withRegisteredTheme(DisplayStyleSuites.MIDNIGHT),UiStyle.DEFAULT,false);
        var paper=sample(ThemeSettings.DEFAULT.withRegisteredTheme(DisplayStyleSuites.WARM_PAPER),UiStyle.DEFAULT,false);
        assertEquals(5f,midnight.radius());assertEquals(2f,paper.radius());
        var pressed=DisplaySuiteButtonPaint.sample(ThemeSettings.DEFAULT.withRegisteredTheme(DisplayStyleSuites.MIDNIGHT),PALETTE,
                UiStyle.DEFAULT,false,false,true,true,false,1,1);
        assertNotEquals(midnight.fill(),pressed.fill());
        assertTrue(ContrastPolicy.ratio(pressed.text(),pressed.fill(),pressed.background())>=4.5);
        assertFalse(pressed.glass());assertFalse(pressed.backdrop());
    }
    @Test void customColorsAndHighContrastRemainAuthoritative(){
        var settings=ThemeSettings.DEFAULT.withRegisteredTheme(DisplayStyleSuites.MIDNIGHT)
                .withPalette(ThemeSettings.Base.DARK,"","","","#AABBCC","#123456");
        var custom=new UiTheme(false,0xff000000,0xff202020,0xff777777,0xffaabbcc,0xff123456,31);
        var paint=DisplaySuiteButtonPaint.sample(settings,custom,UiStyle.DEFAULT,false,true,false,true,false,0,0);
        assertEquals(custom.accent(),paint.fill());assertEquals(custom.accent(),paint.accent());
        var contrast=DisplaySuiteButtonPaint.sample(settings,custom,UiStyle.DEFAULT,false,false,false,true,true,0,0);
        assertEquals(2,contrast.borderWidth());assertEquals(0xff000000,contrast.fill());
        assertTrue(ContrastPolicy.ratio(contrast.text(),contrast.fill(),contrast.background())>=7);
    }
}
