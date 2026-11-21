package com.muoniumplayer.core.screens.hud;

import com.muoniumplayer.core.management.FontManager;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MuoniumFontBridgeTest {
    @Test
    void installsRealCjkFacesIntoAnAlreadyInitializedHost() throws Exception {
        byte[] regular = FontManager.builtinUiFallbackBytes(false);
        byte[] bold = FontManager.builtinUiFallbackBytes(true);
        assertNotNull(regular);
        assertNotNull(bold);
        String chinese = "\u4e2d\u6587\u8bbe\u7f6e\u97f3\u4e50\u64ad\u653e";
        assertEquals(-1, Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(regular)).canDisplayUpTo(chinese));
        assertEquals(-1, Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bold)).canDisplayUpTo(chinese));

        FakeFontManager.INSTANCE.fonts.clear();
        MuoniumFontBridge.installHostFonts(FakeFontManager.class, regular, bold);
        assertArrayEquals(regular, FakeFontManager.INSTANCE.fonts.get("ui"));
        assertArrayEquals(bold, FakeFontManager.INSTANCE.fonts.get("ui-bold"));
        assertArrayEquals(regular, FakeFontManager.INSTANCE.fonts.get("unifont"));
        assertEquals("ui", FakeFontManager.INSTANCE.defaultFont);
    }

    public static final class FakeFontManager {
        public static final FakeFontManager INSTANCE = new FakeFontManager();
        final Map<String, byte[]> fonts = new HashMap<>();
        String defaultFont;

        public void replace(String name, byte[] bytes, boolean pixelAligned) {
            assertFalse(pixelAligned);
            fonts.put(name, bytes);
        }

        public void setDefault(String name) { defaultFont = name; }
    }
}
