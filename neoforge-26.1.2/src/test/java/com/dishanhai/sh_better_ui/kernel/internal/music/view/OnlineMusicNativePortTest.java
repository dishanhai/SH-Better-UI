// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.scaled.MpTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class OnlineMusicNativePortTest {
    @Test
    void samplesNewNativeArgbWithoutSwappingRedAndBlue() {
        int argb = 0xffe02050;
        var expected = ArtworkPalette.fromArgb(1, 1, new int[]{argb});
        var actual = OnlineArtworkTexture.samplePalette(1, 1, (x, y) -> argb);
        assertEquals(expected, actual);
        var texture = new OnlineArtworkTexture.Texture(Identifier.parse("test:cover"), 1, 1, actual);
        assertEquals(actual, texture.palette());
    }

    @Test
    void coverPaletteSamplingIsBoundedAndInImageBounds() {
        var calls = new AtomicInteger();
        OnlineArtworkTexture.samplePalette(512, 80, (x, y) -> {
            assertTrue(x >= 0 && x < 512 && y >= 0 && y < 80);
            calls.incrementAndGet();
            return 0xffe02050;
        });
        assertEquals(64 * 64, calls.get());
    }

    @Test
    void scopedTypographyUsesNewFontDescriptionsWithoutChangingSource() {
        var original = Component.literal("Song");
        var bold = MpTypography.bold(original);
        assertEquals(MpTypography.SEMIBOLD, bold.getStyle().getFont());
        assertEquals(MpTypography.SEMIBOLD, MpTypography.text(bold).getStyle().getFont());
        assertEquals(MpTypography.FONT, MpTypography.text(original).getStyle().getFont());
        assertNotEquals(MpTypography.FONT, original.getStyle().getFont());
    }

    @Test
    void lyricFocusScalePreservesHighContrastAndBounds() {
        assertEquals(1f, OnlineLyricButton.lyricScale(true, 0, true));
        assertEquals(1.12f, OnlineLyricButton.lyricScale(true, 0, false));
        assertEquals(.925f, OnlineLyricButton.lyricScale(false, Float.NaN, false), .0001f);
        assertEquals(OnlineLyricButton.lyricScale(false, 2, false),
                OnlineLyricButton.lyricScale(false, -2, false));
    }
}
