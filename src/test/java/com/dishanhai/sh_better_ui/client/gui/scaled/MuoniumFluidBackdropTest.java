package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MuoniumFluidBackdropTest {
    @Test void followsLiveRgbButKeepsEditableAlphaAndHasManualOverride() {
        var p = MuoniumFluidProfile.DEFAULT;
        var first = p.resolve(0xff123456, 0xffabcdef, 0xff654321);
        var next = p.resolve(0xff998877, 0xff112233, 0xff445566);
        assertEquals(0xb0123456, first.primaryColor());
        assertEquals(0x90abcdef, first.secondaryColor());
        assertEquals(0x40654321, first.backgroundColor());
        assertEquals(0xb0998877, next.primaryColor());
        assertEquals(.8f, first.opacity());
        var manual = new MuoniumFluidProfile(.2f, 1, .7f, .5f, 1, 2, 3, false);
        assertEquals(1, manual.resolve(9, 8, 7).primaryColor());
        assertEquals(3, manual.resolve(9, 8, 7).backgroundColor());
    }
    @Test void registeredEditableAndIndependentRendererPerHost() {
        var spec = ShBetterUiApi.backgrounds().find(MuoniumFluidBackdrop.ID).orElseThrow();
        assertEquals(MuoniumFluidProfile.DEFAULT, spec.defaultProfile());
        assertEquals(MuoniumFluidBackdrop.ID, spec.settingsGroup());
        var a = spec.factory().create(null); var b = spec.factory().create(null);
        assertNotSame(a, b); assertInstanceOf(MuoniumFluidBackdrop.class, a);
        assertDoesNotThrow(a::close); assertDoesNotThrow(b::close);
        var fields = ThemeEffectFields.fields(MuoniumFluidBackdrop.ID);
        assertEquals(9, fields.size());
        for (var field : fields) if (List.of("speed", "scale", "opacity", "intensity").contains(field.name()))
            assertNotNull(ThemeEffectRanges.find(MuoniumFluidBackdrop.ID, field));
        var local = ThemeEffectFields.edit(ClientUiSettings.DEFAULT, MuoniumFluidBackdrop.ID,
                new JsonObject(), new JsonObject(), List.of("followTheme"), new JsonPrimitive(false));
        var persisted = ThemeOverrides.EMPTY.withGroup(MuoniumFluidBackdrop.ID, local);
        assertDoesNotThrow(() -> ThemeEffects.validate(persisted));
        assertFalse(ThemeEffects.profile(com.dishanhai.sh_better_ui.config.ThemeSettings.DEFAULT
                .withMode(com.dishanhai.sh_better_ui.config.ThemeSettings.Mode.CUSTOM).withEffects(persisted),
                MuoniumFluidBackdrop.ID, MuoniumFluidProfile.DEFAULT).followTheme());
    }
    @Test void highContrastAndEmptyAreaDoNotTouchRendererOrGraphics() {
        var renderer = new MuoniumFluidBackdrop();
        assertDoesNotThrow(() -> renderer.draw(null, null, new Bounds(0, 0, 100, 100), null, 0, 0, 0,
                new UiPreferences(true, false, true)));
        assertDoesNotThrow(() -> renderer.draw(null, null, new Bounds(0, 0, 0, 100), null, 0, 0, 10,
                UiPreferences.DEFAULT));
    }
    @Test void profileRejectsNonfiniteAndClampsAllWorkControls() {
        assertThrows(IllegalArgumentException.class, () -> new MuoniumFluidProfile(Float.NaN, 1, 1, 1, 0, 0, 0, true));
        var p = new MuoniumFluidProfile(100, 100, 100, 100, 1, 2, 3, true);
        assertEquals(2, p.speed()); assertEquals(4, p.scale()); assertEquals(1, p.opacity()); assertEquals(1, p.intensity());
        assertEquals(0, new MuoniumFluidProfile(-1, -1, -1, -1, 1, 2, 3, true).speed());
    }
}
