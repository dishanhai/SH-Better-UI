// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.MuoniumThemes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MpPageChromeTest {
    @Test void glassHasTransparentChromeButNoBlurFallbackIsOpaque() {
        try (var manager = new ThemeManager()) {
            for (String id : java.util.List.of(MuoniumThemes.LIQUID_GLASS, MuoniumThemes.CRYSTAL, MuoniumThemes.AURORA_FROST)) {
                manager.preferences(manager.preferences().withFixedTheme(id).withReducedMotion(true));
                var theme = UiTheme.fromScope(manager.global());
                assertEquals(216, MpPageChrome.background(theme, new UiPreferences(false,true,true)) >>> 24);
                assertEquals(255, MpPageChrome.background(theme, new UiPreferences(false,true,false)) >>> 24);
                assertEquals(0xFF000000, MpPageChrome.background(theme, new UiPreferences(true,true,true)));
                assertEquals(0xFFFFFFFF, MpPageChrome.foreground(theme, new UiPreferences(true,true,true)));
            }
        }
    }
    @Test void opaqueThemesRetainTheirOwnSemanticColors() {
        try (var manager = new ThemeManager()) {
            for (String id : java.util.List.of(MuoniumThemes.PORCELAIN_INK, MuoniumThemes.SAGE_MIST)) {
                manager.preferences(manager.preferences().withFixedTheme(id).withReducedMotion(true));
                var theme=UiTheme.fromScope(manager.global());
                assertEquals(theme.background(), MpPageChrome.background(theme, UiPreferences.DEFAULT));
                assertEquals(theme.text(), MpPageChrome.foreground(theme, UiPreferences.DEFAULT));
            }
        }
    }
}
