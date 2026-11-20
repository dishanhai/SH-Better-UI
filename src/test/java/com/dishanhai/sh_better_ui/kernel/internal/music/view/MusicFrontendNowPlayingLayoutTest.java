// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendNowPlayingLayoutTest {
    @Test void activeArtworkAndControlBoundsNeverOverlap() {
        for(int w=96;w<900;w+=7) for(int h=58;h<540;h+=5) {
            var body=new Bounds(12,17,w,h);
            var p=MusicFrontendNowPlayingLayout.fit(body);
            assertTrue(p.usable());
            var blocks=List.of(p.cover(),p.title(),p.subtitle(),p.lyrics(),p.queue());
            for(int i=0;i<blocks.size();i++) {
                var a=blocks.get(i);
                assertTrue(a.x()>=body.x()&&a.y()>=body.y()&&a.right()<=body.right()&&a.bottom()<=body.bottom());
                for(int j=0;j<i;j++) {
                    var b=blocks.get(j);
                    assertTrue(a.right()<=b.x()||a.x()>=b.right()||a.bottom()<=b.y()||a.y()>=b.bottom(),
                            () -> "Overlap at " + body + ": " + a + ", " + b);
                }
            }
        }
    }
    @Test void compactPortraitStacksWhileWideUsesRoomForTheSong() {
        var compact=MusicFrontendNowPlayingLayout.fit(new Bounds(0,0,240,260));
        var wide=MusicFrontendNowPlayingLayout.fit(new Bounds(0,0,590,260));
        assertTrue(compact.title().y()>compact.cover().bottom());
        assertTrue(wide.title().x()>wide.cover().right());
        assertFalse(MusicFrontendNowPlayingLayout.fit(new Bounds(0,0,160,35)).usable());
    }
}
