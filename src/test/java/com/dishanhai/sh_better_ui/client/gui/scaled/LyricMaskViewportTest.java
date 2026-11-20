package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LyricMaskViewportTest {
    @Test void keepsTheLyricBlurAtTheRequestedLogicalWidthsOnBothScaleThreeAndFour() {
        for(int guiWidth:new int[]{864,960})for(int scale:new int[]{3,4}) {
            var clip=new Bounds(312,40,guiWidth-336,420);
            var mask=LyricMaskViewport.of(clip,guiWidth,540,guiWidth*scale,540*scale);
            assertNotNull(mask);
            assertTrue(mask.width()<=2048&&mask.height()<=2048);
            if(scale==3)assertEquals(clip.width()*3,mask.width());
            else assertTrue(mask.width()>=2046);
            assertEquals(clip.x()*mask.fullWidth()/(double)guiWidth,-mask.originX(),.51);
            assertEquals((540-clip.bottom())*mask.fullHeight()/540d,-mask.originY(),.51);
            assertEquals(clip.height()*mask.fullHeight()/540d,mask.height(),1.01);
        }
    }

    @Test void tallMasksAreBoundedAndFractionalGuiProjectionStaysAligned() {
        var clip=new Bounds(6,91,350,1500);
        var mask=LyricMaskViewport.of(clip,864,1750,3457,6989);
        assertNotNull(mask);
        assertTrue(mask.height()>=2046&&mask.height()<=2048);
        assertEquals(clip.width()*mask.fullWidth()/864d,mask.width(),1.01);
        assertEquals((1750-clip.bottom())*mask.fullHeight()/1750d,-mask.originY(),.51);
    }
}
