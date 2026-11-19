// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicViewPaintTypographyTest {
    @Test void textWrappedForMeasurementAndDrawingInheritsMusicFontAndRetainsStyledRuns() {
        var title = Component.literal("Status ")
                .append(MusicTypography.bold("中文字").withStyle(style -> style.withColor(0x00ff88)))
                .append(Component.literal(" العربية"));
        var runs = new LinkedHashMap<String, Style>();
        var wrapped = OnlineMusicViewPaint.wrapContent(title);
        wrapped.visit((style, text) -> {
            runs.put(text, style);
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals(title.getString(), wrapped.getString());
        assertEquals(MusicTypography.FONT, runs.get("Status ").getFont());
        assertEquals(MusicTypography.SEMIBOLD, runs.get("中文字").getFont());
        assertEquals(0x00ff88, runs.get("中文字").getColor().getValue());
        assertEquals(MusicTypography.FONT, runs.get(" العربية").getFont());
        assertEquals(Style.EMPTY, title.getStyle());
    }
}
