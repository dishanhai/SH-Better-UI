package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicTypographyTest {
    @Test void scopeDoesNotMutateCallerOrReplaceSpecializedChildFont() {
        var iconFont=ResourceLocation.tryParse("sh_better_ui:hud_shortcut_keycaps");
        var source=Component.literal("设置 Preferences ").append(Component.literal("A").withStyle(s->s.withFont(iconFont)));
        var styled=MusicTypography.text(source);
        assertNotEquals(MusicTypography.FONT,source.getStyle().getFont());
        assertEquals(MusicTypography.FONT,styled.getStyle().getFont());
        assertEquals(iconFont,styled.getSiblings().get(0).getStyle().getFont());
        assertEquals(source.getString(),styled.getString());
    }
    @Test void explicitRootFontWinsForRegularBoldAndMeasurementStyle() {
        var custom = ResourceLocation.tryParse("example:music_font");
        var source = Component.literal("Custom 中文").withStyle(style -> style.withFont(custom));
        assertEquals(custom, MusicTypography.text(source).getStyle().getFont());
        assertEquals(custom, MusicTypography.bold(source).getStyle().getFont());
        assertEquals(custom, source.getStyle().getFont());
    }
    @Test void semiboldIsExplicitAndDoesNotMutateRegularText() throws Exception {
        var source=Component.literal("歌词 Title");var regular=MusicTypography.text(source);var bold=MusicTypography.bold(source);
        assertEquals(MusicTypography.FONT,regular.getStyle().getFont());
        assertEquals(MusicTypography.SEMIBOLD,bold.getStyle().getFont());
        assertEquals(MusicTypography.SEMIBOLD,MusicTypography.text(bold).getStyle().getFont());
        try(var font=getClass().getResourceAsStream("/assets/sh_better_ui/font/sh_music_sans_semibold.ttf")) {
            assertNotNull(font);assertArrayEquals(new byte[]{0,1,0,0},font.readNBytes(4));
        }
    }
    @Test void selectionCacheRechecksOnPackManagerOrReloadButNotPerSpan() {
        var cache=new MusicTypography.SelectionCache();var manager=new Object();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BooleanSupplier lookup=()->{calls.incrementAndGet();return true;};
        assertTrue(cache.resolve(manager,"old.zip",lookup));
        for(int i=0;i<100;i++)assertTrue(cache.resolve(manager,"old.zip",lookup));
        assertEquals(1,calls.get());
        cache.invalidate(); cache.resolve(manager,"old.zip",lookup);assertEquals(2,calls.get());
        cache.resolve(manager,"new.zip",lookup);assertEquals(3,calls.get());
        cache.resolve(new Object(),"new.zip",lookup);assertEquals(4,calls.get());
    }
    @Test void oldUserPackCompatibilityDoesNotBypassNewMusicDefinitions() {
        assertTrue(MusicTypography.usesLegacyDefinition("mod/sh_better_ui"));
        assertTrue(MusicTypography.usesLegacyDefinition(""));
        assertFalse(MusicTypography.usesLegacyDefinition("file/shbui-userfont-sample.zip"));
        assertFalse(MusicTypography.usesLegacyDefinition("file/custom-music-pack.zip"));
    }
    @Test void selectedLegacyUserPackKeepsPriorityWithoutRewritingIt() {
        assertEquals(MpTypography.FONT,MusicTypography.selectedFont(false,true));
        assertEquals(MpTypography.SEMIBOLD,MusicTypography.selectedFont(true,true));
        assertEquals(MusicTypography.FONT,MusicTypography.selectedFont(false,false));
        assertEquals(MusicTypography.SEMIBOLD,MusicTypography.selectedFont(true,false));
    }
    @Test void originalPanelFontRemainsSeparate() throws Exception {
        assertNotEquals(MpTypography.FONT,MusicTypography.FONT);
        try(var original=getClass().getResourceAsStream("/assets/sh_better_ui/font/mp_ui.json")) {
            assertNotNull(original);
            var text=new String(original.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("sh_sans_ui.ttf")); assertFalse(text.contains("sh_music_sans"));
        }
    }
    @Test void resourcesAndLicenseArePresentWithStaticTrueTypeSignature() throws Exception {
        try(var font=getClass().getResourceAsStream("/assets/sh_better_ui/font/sh_music_sans_medium.ttf")){
            assertNotNull(font);assertArrayEquals(new byte[]{0,1,0,0},font.readNBytes(4));
        }
        try(var definition=getClass().getResourceAsStream("/assets/sh_better_ui/font/music_ui.json")){
            assertNotNull(definition);var text=new String(definition.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("sh_better_ui:sh_music_sans_medium.ttf"));
            assertTrue(text.contains("sh_better_ui:sh_sans_ui.ttf"), "Rare CJK glyphs retain Noto fallback");
            assertTrue(text.contains("minecraft:default"));
            var providers=com.google.gson.JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("providers");
            for(int i=0;i<2;i++)assertEquals(3,providers.get(i).getAsJsonObject().getAsJsonArray("shift").get(1).getAsInt());
        }
        assertNotNull(getClass().getResource("/META-INF/third-party/ibm-plex/OFL.txt"));
    }
}
