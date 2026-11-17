// Author: dishanhai. Procedural, resource-independent turntable; no QQ assets are bundled.
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;

/** Borrowed artwork and host-owned playback state. Pausing freezes rotation without a jump. */
public final class MusicVinylStage {
    private final MusicArtworkStage cover = new MusicArtworkStage();
    private final VinylMotion motion=new VinylMotion();
    public void render(UiFrame frame, Bounds area, ResourceLocation artwork, int width, int height, boolean playing) {
        float angle=motion.sample(frame.nowMs(),playing,frame.preferences().reducedMotion());
        int size=Math.min(area.width(),area.height()); if(size<24)return;
        int x=area.x()+(area.width()-size)/2,y=area.y()+(area.height()-size)/2;
        var g=frame.graphics();
        RoundedCorners.draw(g,x,y,size,size,8,1,0xffae9d7c,0xff72634f);
        int d=Math.max(20,size-12),cx=x+size/2,cy=y+size/2;
        RoundedCorners.draw(g,cx-d/2,cy-d/2,d,d,d/2f,2,0xffc3bca3,0xff16181a);
        for(int i=0;i<9;i++) {
            int groove=d-8-i*Math.max(2,d/24);if(groove<d/3)break;
            RoundedCorners.draw(g,cx-groove/2,cy-groove/2,groove,groove,groove/2f,0.65f,0xff383a3b,0);
        }
        int label=Math.max(6,d/4);
        g.pose().pushPose();
        try {
            g.pose().translate(cx,cy,0);g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            RoundedCorners.draw(g,-label/2,-label/2,label,label,label/2f,0,0,0xffb38b50);
            if(artwork!=null&&width>0&&height>0) {
                int a=Math.max(4,label*2/3);
                cover.renderCover(frame,new Bounds(-a/2,-a/2,a,a),artwork,width,height);
            }
            RoundedCorners.draw(g,-1,-1,2,2,1,0,0,0xffded8c6);
        } finally { g.pose().popPose(); }
        // Stylus rests clear of the playable artwork; it is decoration, not a fake button.
        int armX=x+size-8;
        RoundedCorners.draw(g,armX-2,y+8,4,size/2,2,0,0,0xffd4d0c1);
        RoundedCorners.draw(g,armX-9,y+size/2,11,5,2,0,0,0xff444a47);
    }
    public void reset(){motion.reset();}
}
