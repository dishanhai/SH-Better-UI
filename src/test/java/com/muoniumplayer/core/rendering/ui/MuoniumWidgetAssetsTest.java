package com.muoniumplayer.core.rendering.ui;


import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuoniumWidgetAssetsTest {
    @Test
    void everyMappedIconCanBeRendered() throws Exception {
        verifyFont("fontello", "\ue001\ue002\ue003\ue004\ue800\ue801\ue802\uf127\uf234");
        verifyFont("hud-settings-icons", range(0xe200, 0xe221) + "\ue2ff");
        verifyFont("party-icons", range(0xe400, 0xe432) + "\ue4ff");
        verifyFont("island-status-icons", "\ue803\ue807\ue808\ue901\ue902\ue903\ue904");
        verifyFont("island-volume-icons", range(0xe902, 0xe906));
        verifyFont("music-brand-icons", "\ue800\ue801\ue802");
        verifyFont("player-action-icons", "\ue113\ue144");
        verifyFont("player-queue-icons", "\ue309");
        verifyFont("qq-music-icons", "\ue888");
        verifyFont("youtube-icons", "\ue803");
        verifyFont("music", "ABCDEFGHIJK");
        verifyFont("icomoon", "ABCDEFGHIJKLMNOPQRSabcdefghijklmnopqrstuvwxyz");
        verifyFont("player-icons", "ABCDEabcdefghijklmnopqrstuvwxyz");
    }

    private static void verifyFont(String name, String characters) throws Exception {
        try (InputStream resource = MuoniumWidgetAssetsTest.class.getResourceAsStream("/muonium/fonts/" + name + ".ttf")) {
            assertNotNull(resource, name);
            Font font = Font.createFont(Font.TRUETYPE_FONT, resource).deriveFont(48f);
            FontRenderContext context = new FontRenderContext(null, true, true);
            for (int character : characters.codePoints().toArray()) {
                assertTrue(font.canDisplay(character), name + " U+" + Integer.toHexString(character));
                assertFalse(font.createGlyphVector(context, new String(Character.toChars(character)))
                        .getVisualBounds().isEmpty(), name + " U+" + Integer.toHexString(character));
            }
        }
    }

    private static String range(int first, int last) {
        StringBuilder result = new StringBuilder();
        for (int codePoint = first; codePoint <= last; codePoint++) {
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }
}
