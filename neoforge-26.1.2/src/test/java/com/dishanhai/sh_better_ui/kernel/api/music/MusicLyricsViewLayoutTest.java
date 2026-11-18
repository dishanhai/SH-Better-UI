package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MusicLyricsViewLayoutTest {
    private static final Font FONT = new Font(null) {
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            return List.of(FormattedCharSequence.EMPTY);
        }
    };
    @Test void finalLineCannotDisappearPastTrailingWhitespaceInTinyOrNormalViewports() throws Exception {
        for(int height:new int[]{12,18,24,40,42,54,72,120,280}) {
            var view=new MusicLyricsView(new Bounds(0,0,240,height),net.minecraft.network.chat.Component.empty(),UiPreferences.DEFAULT,p->{});
            view.scale(1.5f);
            view.setLines(List.of(new MusicLyricsModel.Line("作曲",0),new MusicLyricsModel.Line("纯音乐，请欣赏",1000)));
            var layout=MusicLyricsView.class.getDeclaredMethod("layout",Font.class);layout.setAccessible(true);layout.invoke(view,FONT);
            double[] tops=(double[])field(view,"tops"),heights=(double[])field(view,"heights");
            double maxOffset=Math.max(0,(double)field(view,"totalHeight")-height);
            assertTrue(tops[1]+heights[1]>maxOffset,"last line above viewport at height "+height);
            assertTrue(tops[1]<maxOffset+height,"last line below viewport at height "+height);
            assertTrue(tops[1]-tops[0]-heights[0]<=height*.25+.001);
        }
    }
    @Test void followWithoutPlayableTimestampReturnsToStartWithoutFontAccess() throws Exception {
        var view=new MusicLyricsView(new Bounds(0,0,240,42),net.minecraft.network.chat.Component.empty(),UiPreferences.DEFAULT,p->{});
        view.setLines(List.of(new MusicLyricsModel.Line("作曲",0),new MusicLyricsModel.Line("纯音乐，请欣赏",1000)));
        view.playback(-1,false);view.model().browse();
        var offset=MusicLyricsView.class.getDeclaredField("offset");offset.setAccessible(true);offset.setDouble(view,80);
        view.follow();assertFalse(view.browsing());assertEquals(0d,offset.getDouble(view));
    }
    private static Object field(Object instance,String name)throws Exception {
        var field=MusicLyricsView.class.getDeclaredField(name);field.setAccessible(true);return field.get(instance);
    }
}
