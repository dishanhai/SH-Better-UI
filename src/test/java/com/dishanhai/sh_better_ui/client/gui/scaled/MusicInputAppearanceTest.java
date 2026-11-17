// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicInputAppearance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicInputAppearanceTest {
    private UiTextField field() {
        return new UiTextField(CardNavTestFont.create(), new Bounds(0,0,140,28), Component.literal("Search"),100,UiPreferences.DEFAULT);
    }
    @Test void quietSkinRetainsEditingUndoSelectionAndSubmit() {
        var field=field();var edits=new AtomicInteger();var submits=new ArrayList<String>();
        field.setOnChange(v->edits.incrementAndGet());field.setOnSubmit(submits::add);
        field.setValue("ab");MusicInputAppearance.apply(field);
        assertEquals(0,edits.get());field.setFocused(true);
        assertTrue(field.input("中"));assertEquals("ab中",field.getValue());
        field.key(90,true,false,false);assertEquals("ab",field.getValue());
        field.key(89,true,false,false);assertEquals("ab中",field.getValue());
        field.key(65,true,false,false);field.input("new");
        field.key(257,false,false,false);assertEquals(java.util.List.of("new"),submits);
    }
    @Test void defaultSkinUnchangedQuietHasFocusErrorAndContrastEdgesAndStyledText() {
        var field=field();var canvas=new RecordingCanvas();field.setValue("Search");
        field.paint(canvas);assertEquals(1,canvas.stroke);
        MusicInputAppearance.apply(field);field.paint(canvas);
        assertEquals(0,canvas.stroke);assertEquals(8,canvas.textY);
        assertEquals(MpTypography.FONT,canvas.style.getFont());
        field.setFocused(true);field.paint(canvas);assertEquals(1,canvas.stroke);
        field.setFocused(false);field.setError(Component.literal("Invalid"));field.paint(canvas);assertEquals(1,canvas.stroke);
        field.setError(null);field.setPreferences(new UiPreferences(true,true,false));
        field.paint(canvas);assertEquals(1,canvas.stroke);
    }
    @Test void changingTextStyleCancelsOldPointerDragWithoutDroppingValue() {
        var field=field();field.setValue("abc");field.setFocused(true);field.pointerDown(6,false,false);
        MusicInputAppearance.apply(field);field.pointerDrag(130);field.pointerUp();field.input("X");
        assertEquals("Xabc",field.getValue());
    }
    private static class RecordingCanvas implements UiTextFieldPlatform.Canvas {
        int stroke,textY;Style style;
        public void box(int x,int y,int w,int h,int radius,int stroke,int edge,int fill){this.stroke=stroke;}
        public void fill(int x,int y,int right,int bottom,int color){}
        public void text(FormattedCharSequence text,int x,int y,int color){textY=y;text.accept((i,s,c)->{style=s;return true;});}
        public void clip(int x,int y,int right,int bottom){}
        public void unclip(){}
    }
}
