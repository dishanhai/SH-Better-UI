package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMusicChromeLayoutTest {
    @Test void insetControlsKeepTheirFramesClearAcrossTheRequestedMatrix() {
        for(int width:new int[]{208,319,320,360,559,560,864,960})
            for(int height:new int[]{240,264,340,540}) {
                var page=OnlineMusicLayout.of(new Bounds(19,31,width,height));
                var transport=page.transport();
                var seek=page.seekBar();
                var left=DefaultMusicChromeLayout.elapsed(transport,seek);
                var right=DefaultMusicChromeLayout.remaining(transport,seek,40);
                assertTrue(left.x()>=transport.x()+8&&left.right()<seek.x());
                assertTrue(right.x()>seek.right()&&right.right()<=transport.right()-8);
                assertEquals(left.y(),right.y());
                assertTrue(right.bottom()<=transport.bottom());
                if(page.expanded()) {
                    assertTrue(page.cover().x()>=transport.x()+8);
                    assertTrue(page.cover().bottom()<seek.y());
                }
                if(!page.sidebar())continue;
                var rail=page.navigation();
                int bottom=rail.y()+30;
                for(int i=0;i<6;i++) {
                    var row=DefaultMusicChromeLayout.sidebarRow(rail,i);
                    assertTrue(row.y()>=bottom&&row.height()>=16);
                    assertTrue(row.x()>rail.x()&&row.right()<rail.right());
                    bottom=row.bottom();
                }
                assertEquals(rail.bottom()-6,bottom);
            }
    }
}
