package com.dishanhai.sh_better_ui.kernel.api.music.control;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
class MusicTextLayoutTest {
    @Test void wideNestedTreesRemainBoundedWithoutCopyingOrMutatingSource(){
        var source=Component.literal("root");var cursor=source;
        for(int depth=0;depth<32;depth++){
            var next=Component.literal("child");cursor.append(next);
            for(int i=0;i<256;i++)cursor.append(Component.literal("x"));
            cursor=next;
        }
        var result=MusicTextLayout.bound(source);
        assertTrue(result.truncated());
        assertTrue(result.text().getString().length()<=MusicTextLayout.MAX_CODE_POINTS);
        assertEquals(257,source.getSiblings().size());
    }
    @Test void budgetDoesNotSplitSupplementaryCharactersAndKeepsFontStyle(){
        var font=ResourceLocation.tryParse("sh_better_ui:mp_ui");
        var source=Component.literal("😀".repeat(5000)).withStyle(Style.EMPTY.withFont(font).withBold(true));
        var bounded=MusicTextLayout.bound(source);
        assertTrue(bounded.truncated());assertEquals(4096,bounded.text().getString().codePointCount(0,bounded.text().getString().length()));
        bounded.text().visit((style,text)->{assertEquals(font,style.getFont());assertTrue(style.isBold());return Optional.empty();},Style.EMPTY);
    }
    @Test void styledChildrenKeepTheirOwnFontAndLiteralSourceIsNotMutated(){
        var a=ResourceLocation.tryParse("sh_better_ui:mp_ui");var b=ResourceLocation.tryParse("minecraft:uniform");
        var source=Component.literal("Title").withStyle(Style.EMPTY.withFont(a)).append(Component.literal("子").withStyle(Style.EMPTY.withFont(b)));
        var bounded=MusicTextLayout.bound(source);assertFalse(bounded.truncated());assertEquals("Title子",bounded.text().getString());
        bounded.text().visit((style,text)->{if(text.equals("子"))assertEquals(b,style.getFont());return Optional.empty();},Style.EMPTY);
        assertEquals("Title子",source.getString());
    }
    @Test void emptyComponentSiblingsCannotBypassTheWorkBudget(){
        var source=Component.empty();for(int i=0;i<1000;i++)source.append(Component.empty());
        assertTrue(MusicTextLayout.bound(source).truncated());
    }
    @Test void excessiveStyledRunsAreBoundedEvenWhenTextIsShort(){
        var source=Component.empty();for(int i=0;i<400;i++)source.append(Component.literal("x"));
        var bounded=MusicTextLayout.bound(source);assertTrue(bounded.truncated());assertTrue(bounded.text().getString().length()<=MusicTextLayout.MAX_RUNS);
    }
}
