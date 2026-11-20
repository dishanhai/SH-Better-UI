package com.dishanhai.sh_better_ui.kernel.api.music;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MusicLyricsManualScrollTest {
    @Test void manualMovementReportsClampedDeltaButProgrammaticChangesStaySilent() throws Exception {
        var view=new MusicLyricsView(new Bounds(0,0,200,100),Component.empty(),UiPreferences.DEFAULT,p->{});
        var deltas=new ArrayList<Double>(); view.setOnManualScroll(deltas::add);
        var height=MusicLyricsView.class.getDeclaredField("totalHeight");height.setAccessible(true);height.setDouble(view,400);
        var move=MusicLyricsView.class.getDeclaredMethod("manualScrollTo",double.class);move.setAccessible(true);
        move.invoke(view,72d);move.invoke(view,999d);move.invoke(view,999d);move.invoke(view,-100d);
        assertEquals(List.of(72d,228d,-300d),deltas);
        view.setLines(List.of(new MusicLyricsModel.Line("a",0))); view.playback(1000,true);
        view.follow();view.bounds(new Bounds(0,0,200,50));
        assertEquals(3,deltas.size());
        assertThrows(NullPointerException.class,()->view.setOnManualScroll(null));
    }
}
