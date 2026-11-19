package com.dishanhai.sh_better_ui.client.lyrics.render;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LyricScopedTypographyTest {
    @Test void oldCallersKeepDefaultFontWhileScopedTextUsesRequestedFont() {
        assertEquals(Style.EMPTY.getFont(),LyricView.scopedText("歌词",null).getStyle().getFont());
        assertEquals(MusicTypography.FONT,LyricView.scopedText("歌词",MusicTypography.FONT).getStyle().getFont());
        var custom=ResourceLocation.tryParse("test:custom");
        assertEquals(custom,LyricView.scopedText("歌词",custom).getStyle().getFont());
        assertEquals("歌词",LyricView.scopedText("歌词",custom).getString());
    }
}
