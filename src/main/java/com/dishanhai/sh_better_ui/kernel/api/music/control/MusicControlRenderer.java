// Author: dishanhai. Original vector primitives, no Apple artwork or font assets.
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphics;

/** Reusable immediate renderer, independent of widget, player, provider and global theme ownership. */
public final class MusicControlRenderer {
    private MusicControlRenderer(){}
    public static void seek(GuiGraphics g,Bounds b,double fraction,boolean engaged,boolean focused,
                            boolean enabled,int foreground,UiPreferences preferences){
        if(b.width()<8||b.height()<1)return;
        float x=b.x()+4,w=b.width()-8,cy=b.y()+b.height()/2f;
        boolean contrast=preferences.highContrast();
        int ink=contrast?(foreground|0xff000000):foreground;
        float thickness=engaged?3:2;
        RoundedCorners.draw(g,x,cy-thickness/2,w,thickness,thickness/2,0,0,alpha(ink,contrast?.5f:enabled?.24f:.12f));
        float fill=(float)(w*(Double.isFinite(fraction)?Math.max(0,Math.min(1,fraction)):0));
        if(fill>0)RoundedCorners.draw(g,x,cy-thickness/2,fill,thickness,thickness/2,0,0,alpha(ink,enabled?.8f:.3f));
        if(engaged&&enabled)RoundedCorners.draw(g,x+fill-3.5f,cy-3.5f,7,7,3.5f,0,0,ink);
        if(focused)RoundedCorners.draw(g,b.x(),b.y()+2,b.width(),Math.max(1,b.height()-4),5,1,alpha(ink,.7f),0);
    }
    public static void transport(GuiGraphics g,Bounds b,MusicTransportControl.Glyph glyph,boolean hover,
                                 boolean focus,boolean pressed,boolean enabled,int foreground,UiPreferences preferences){
        if(b.width()<1||b.height()<1)return;
        int ink=preferences.highContrast()?(foreground|0xff000000):foreground;
        if(!enabled)ink=alpha(ink,.3f);
        float side=Math.min(b.width(),b.height());float cx=b.x()+b.width()/2f,cy=b.y()+b.height()/2f;
        if((hover||focus||pressed)&&enabled)
            RoundedCorners.draw(g,cx-side/2,cy-side/2,side,side,side/2,focus?1:0,
                    alpha(ink,.65f),alpha(ink,pressed?.14f:.07f));
        float size=Math.min(glyph==MusicTransportControl.Glyph.PLAY||glyph==MusicTransportControl.Glyph.PAUSE?26:18,side*.55f);
        if(pressed&&!preferences.reducedMotion())size*=.94f;
        switch(glyph){
            case STOP -> bar(g,cx-size*.38f,cy-size*.38f,size*.76f,size*.76f,ink);
            case CANCEL -> {stroke(g,cx-size*.32f,cy-size*.32f,cx+size*.32f,cy+size*.32f,2,ink);stroke(g,cx-size*.32f,cy+size*.32f,cx+size*.32f,cy-size*.32f,2,ink);}
            case REMOVE -> bar(g,cx-size*.4f,cy-1,size*.8f,2,ink);
            case SEARCH -> {
                RoundedCorners.draw(g,cx-size*.48f,cy-size*.48f,size*.68f,size*.68f,size*.34f,1.5f,ink,0);
                stroke(g,cx+size*.09f,cy+size*.09f,cx+size*.45f,cy+size*.45f,2,ink);
            }
            case USER -> {
                RoundedCorners.draw(g,cx-size*.19f,cy-size*.47f,size*.38f,size*.38f,size*.19f,0,0,ink);
                RoundedCorners.draw(g,cx-size*.4f,cy+size*.02f,size*.8f,size*.42f,size*.2f,0,0,ink);
            }
            case FAVORITE -> {
                g.pose().pushPose();g.pose().translate(cx,cy+size*.04f,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
                bar(g,-size*.23f,-size*.23f,size*.46f,size*.46f,ink);
                RoundedCorners.draw(g,-size*.46f,-size*.23f,size*.46f,size*.46f,size*.23f,0,0,ink);
                RoundedCorners.draw(g,-size*.23f,-size*.46f,size*.46f,size*.46f,size*.23f,0,0,ink);
                g.pose().popPose();
            }
            case PLAY -> triangle(g,cx-size*.38f,cy-size*.5f,size*.85f,size,true,ink);
            case PAUSE -> {bar(g,cx-size*.36f,cy-size/2,size*.23f,size,ink);bar(g,cx+size*.13f,cy-size/2,size*.23f,size,ink);}
            case PREVIOUS,NEXT -> {
                boolean right=glyph==MusicTransportControl.Glyph.NEXT;
                triangle(g,cx-size*.64f,cy-size*.43f,size*.64f,size*.86f,right,ink);
                triangle(g,cx,cy-size*.43f,size*.64f,size*.86f,right,ink);
            }
            case BACK -> {stroke(g,cx-size*.27f,cy,cx+size*.22f,cy-size*.38f,2,ink);stroke(g,cx-size*.27f,cy,cx+size*.22f,cy+size*.38f,2,ink);}
            case LYRICS -> {
                bar(g,cx-size*.45f,cy-size*.4f,size*.9f,2,ink);
                bar(g,cx-size*.45f,cy-1,size*.7f,2,ink);
                bar(g,cx-size*.45f,cy+size*.3f,size*.82f,2,ink);
            }
            case QUEUE -> {
                for(int i=0;i<3;i++){float y=cy+(i-1)*size*.35f;bar(g,cx-size*.48f,y-1,2,2,ink);bar(g,cx-size*.2f,y-1,size*.68f,2,ink);}
            }
        }
    }
    private static void bar(GuiGraphics g,float x,float y,float w,float h,int color){RoundedCorners.draw(g,x,y,w,h,Math.min(w,h)/3,0,0,color);}
    private static void stroke(GuiGraphics g,float x,float y,float x2,float y2,float thickness,int color){
        float length=(float)Math.hypot(x2-x,y2-y);
        g.pose().pushPose();g.pose().translate(x,y,0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)Math.atan2(y2-y,x2-x)));
        bar(g,0,-thickness/2,length,thickness,color);g.pose().popPose();
    }
    /** Quarter-GUI-unit analytic scan conversion keeps the common renderer independent of loader vertex APIs. */
    private static void triangle(GuiGraphics g,float x,float y,float w,float h,boolean right,int color){
        if(w<=0||h<=0)return;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.25f,.25f,1);
        int rows=Math.max(1,Math.round(h*4));
        for(int row=0;row<rows;row++){
            float half=Math.abs((row+.5f)/rows-.5f)*2;
            float extent=w*4*(1-half);int whole=(int)extent;
            if(right){if(whole>0)g.fill(0,row,whole,row+1,color);g.fill(whole,row,whole+1,row+1,alpha(color,extent-whole));}
            else {int end=Math.round(w*4);if(whole>0)g.fill(end-whole,row,end,row+1,color);g.fill(end-whole-1,row,end-whole,row+1,alpha(color,extent-whole));}
        }
        g.pose().popPose();
    }
    private static int alpha(int color,float amount){return (color&0xffffff)|(Math.round((color>>>24)*amount)<<24);}
}
