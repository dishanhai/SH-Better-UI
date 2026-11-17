package com.dishanhai.sh_better_ui.kernel.api.music.status;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MusicStatusViewTest {
    private static final class TestFont extends Font {
        int wraps;
        final StringSplitter splitter=new StringSplitter((codePoint,style)->6f);
        TestFont(){super(null);}
        @Override public List<FormattedCharSequence> split(FormattedText text,int width){
            wraps++;return splitter.splitLines(text,width,Style.EMPTY).stream().map(Language.getInstance()::getVisualOrder).toList();
        }
    }
    @Test void narrowerStatusWrapsAndRepeatedFramesReuseMeasurements(){
        var view=new MusicStatusView();var font=new TestFont();
        view.setContent(Component.literal("Search unavailable"),Component.literal("Check your connection and try again. No songs were loaded."),false,null);
        int wide=view.requiredHeight(font,320),calls=font.wraps;
        assertEquals(wide,view.requiredHeight(font,320));assertEquals(calls,font.wraps);
        assertTrue(view.requiredHeight(font,110)>wide);
        calls=font.wraps;MusicStatusView.invalidateFontMetrics();view.requiredHeight(font,110);assertTrue(font.wraps>calls);
    }
    @Test void busyIsExplicitAndNarrationContainsActualCopy(){
        var view=new MusicStatusView();
        view.setContent(Component.literal("Loading"),Component.literal("Provider rejected the request"),false,null);
        assertFalse(view.busy());assertEquals("Loading. Provider rejected the request",view.narration().getString());
        view.setContent(Component.literal("Searching"),Component.literal("You can cancel"),true,null);assertTrue(view.busy());
    }
}
