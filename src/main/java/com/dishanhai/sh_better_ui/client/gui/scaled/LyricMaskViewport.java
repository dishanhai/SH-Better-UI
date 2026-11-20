// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Maps top-left GUI coordinates to a bounded, bottom-left mask framebuffer. */
record LyricMaskViewport(int width,int height,int originX,int originY,int fullWidth,int fullHeight) {
    static final int MAX_EDGE=2048;
    static LyricMaskViewport of(Bounds clip,int guiWidth,int guiHeight,int framebufferWidth,int framebufferHeight) {
        if(clip.width()<=0||clip.height()<=0||guiWidth<=0||guiHeight<=0||framebufferWidth<=0||framebufferHeight<=0)return null;
        double xScale=framebufferWidth/(double)guiWidth,yScale=framebufferHeight/(double)guiHeight;
        double reduction=Math.min(1,Math.min(MAX_EDGE/(clip.width()*xScale),MAX_EDGE/(clip.height()*yScale)));
        int fullWidth=Math.max(1,(int)Math.floor(framebufferWidth*reduction));
        int fullHeight=Math.max(1,(int)Math.floor(framebufferHeight*reduction));
        return crop(clip,guiWidth,guiHeight,fullWidth,fullHeight);
    }

    private static LyricMaskViewport crop(Bounds clip,int guiWidth,int guiHeight,int fullWidth,int fullHeight) {
        // Rounding absolute edges keeps both ends aligned when the projection is fractional.
        int left=map(clip.x(),guiWidth,fullWidth);
        int right=map(clip.right(),guiWidth,fullWidth);
        int bottom=fullHeight-map(clip.bottom(),guiHeight,fullHeight);
        int top=fullHeight-map(clip.y(),guiHeight,fullHeight);
        int width=right-left,height=top-bottom;
        if(width<=0||height<=0||width>MAX_EDGE||height>MAX_EDGE)return null;
        return new LyricMaskViewport(width,height,-left,-bottom,fullWidth,fullHeight);
    }

    private static int map(int coord,int guiSize,int pixels) {
        return (int)Math.round((double)coord*pixels/guiSize);
    }
}
