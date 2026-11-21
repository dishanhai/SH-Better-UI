package com.muoniumplayer.core.screens.ncm;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPaletteTest {
    @Test
    void rendersTheActiveHostPaletteWithoutAlteringThePlayerPreset() {
        UiTheme light = new UiTheme(true, 0xfffbfafa, 0xfff4f4f4, 0xff555555, 0xff121212, 0xff237745, 0);
        assertEquals(0xfbfafa, NCMTheme.hostColor(light, NCMScreen.ColorType.GENERIC_BACKGROUND));
        assertEquals(0x121212, NCMTheme.hostColor(light, NCMScreen.ColorType.PRIMARY_TEXT));
        assertEquals(0x237745, NCMTheme.hostColor(light, NCMScreen.ColorType.ACCENT));
        for (var role : NCMScreen.ColorType.values()) {
            assertEquals(0, NCMTheme.hostColor(light, role) >>> 24, role::name);
        }
    }
}
