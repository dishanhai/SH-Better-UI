package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FullscreenLyricLayoutTest {
    private static void inside(Bounds child,Bounds parent) {
        assertTrue(child.width()>0&&child.height()>0,child.toString());
        assertTrue(child.x()>=parent.x()&&child.y()>=parent.y(),child.toString());
        assertTrue(child.right()<=parent.right()&&child.bottom()<=parent.bottom(),child.toString());
    }
    @Test void stageContentAndControlsFitAcrossGuiScalesAndAspectRatios() {
        for(int[] size:new int[][]{{208,240},{300,240},{360,340},{459,240},{460,320},{800,600},{1280,720}})
            for(boolean ownBack:new boolean[]{true,false}) for(boolean failed:new boolean[]{true,false}) for(boolean lyrics:new boolean[]{true,false}) {
                var frame=new Bounds(19,31,size[0],size[1]);var stage=FullscreenLyricLayout.of(frame,ownBack,failed,lyrics);
                for(var child:List.of(stage.back(),stage.cover(),stage.info(),stage.lyrics(),stage.transport(),stage.seek(),stage.previous(),stage.play(),stage.next(),stage.volume(),stage.mode(),stage.queue()))inside(child,frame);
                assertTrue(stage.previous().right()<stage.play().x());assertTrue(stage.play().right()<stage.next().x());
                assertTrue(stage.seek().bottom()<stage.play().y());assertTrue(stage.play().bottom()<stage.volume().y());
                if(failed){inside(stage.message(),frame);assertTrue(stage.lyrics().bottom()<=stage.message().y());
                    if(!stage.wide())assertTrue(stage.message().bottom()<stage.seek().y());}
            }
    }
    @Test void coverAndLyricModesNeverMovePlaybackOrVolumeControls() {
        for(var frame:List.of(new Bounds(0,0,208,240),new Bounds(0,0,590,372),new Bounds(0,0,380,500))) {
            var cover=FullscreenLyricLayout.of(frame,false,false,false);var lyrics=FullscreenLyricLayout.of(frame,false,false,true);
            assertEquals(cover.transport(),lyrics.transport());assertEquals(cover.seek(),lyrics.seek());assertEquals(cover.play(),lyrics.play());
            assertEquals(cover.volume(),lyrics.volume());assertEquals(cover.mode(),lyrics.mode());
        }
    }
    @Test void wideStagesKeepLyricsAndTransportInSeparateColumns() {
        var stage=FullscreenLyricLayout.of(new Bounds(0,0,590,372),false,false,true);
        assertTrue(stage.wide());assertTrue(stage.cover().right()<stage.lyrics().x());assertTrue(stage.info().right()<stage.lyrics().x());
        assertTrue(stage.transport().right()<stage.lyrics().x());
    }
}
