package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MpTypographyTest {
    @Test void scopeDoesNotMutateCallerOrReplaceSpecializedChildFont() {
        var iconFont=ResourceLocation.tryParse("sh_better_ui:hud_shortcut_keycaps");
        var source=Component.literal("设置 Preferences ").append(Component.literal("A").withStyle(s->s.withFont(iconFont)));
        var styled=MpTypography.text(source);
        assertNotEquals(MpTypography.FONT,source.getStyle().getFont());
        assertEquals(MpTypography.FONT,styled.getStyle().getFont());
        assertEquals(iconFont,styled.getSiblings().get(0).getStyle().getFont());
        assertEquals(source.getString(),styled.getString());
    }
    @Test void semiboldIsExplicitAndDoesNotMutateRegularText() throws Exception {
        var source=Component.literal("歌词 Title");var regular=MpTypography.text(source);var bold=MpTypography.bold(source);
        assertEquals(MpTypography.FONT,regular.getStyle().getFont());
        assertEquals(MpTypography.SEMIBOLD,bold.getStyle().getFont());
        assertEquals(MpTypography.SEMIBOLD,MpTypography.text(bold).getStyle().getFont());
        try(var font=getClass().getResourceAsStream("/assets/sh_better_ui/font/sh_sans_ui_semibold.ttf")) {
            assertNotNull(font);assertArrayEquals(new byte[]{0,1,0,0},font.readNBytes(4));
        }
    }
    @Test void resourcesAndLicenseArePresentWithStaticTrueTypeSignature() throws Exception {
        try(var font=getClass().getResourceAsStream("/assets/sh_better_ui/font/sh_sans_ui.ttf")){
            assertNotNull(font);assertArrayEquals(new byte[]{0,1,0,0},font.readNBytes(4));
        }
        try(var definition=getClass().getResourceAsStream("/assets/sh_better_ui/font/mp_ui.json")){
            assertNotNull(definition);var text=new String(definition.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("sh_better_ui:sh_sans_ui.ttf"));assertTrue(text.contains("minecraft:default"));
        }
        assertNotNull(getClass().getResource("/META-INF/third-party/noto-sans/OFL.txt"));
    }
}
