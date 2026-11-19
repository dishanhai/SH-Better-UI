// Author: dishanhai. Original vector primitives, no Apple artwork or font assets.
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Reusable immediate renderer, independent of widget, player, provider and global theme ownership. */
public final class MusicControlRenderer {
    /** Original outline vocabulary. These are geometry, never atlas sprites or font characters. */
    public enum Icon { SEARCH, BACK, FAVORITE, USER, HOME, LIBRARY, HISTORY, SETTINGS, PLAYLIST, ADD, CLOSE, REMOVE, REFRESH }
    /** Draws only the icon; its owner supplies hover/focus chrome and the full accessible label. */
    public static void icon(GuiGraphicsExtractor g, Bounds b, Icon icon, boolean pressed, boolean enabled,
                            int foreground, UiPreferences preferences) {
        if(icon==null||b.width()<1||b.height()<1)return;
        float size=Math.min(20,Math.min(b.width(),b.height())*.72f);
        if(pressed&&!preferences.reducedMotion())size*=.96f;
        int ink=preferences.highContrast()?foreground|0xff000000:foreground;
        if(!enabled)ink=alpha(ink,.35f);
        float cx=b.x()+b.width()/2f,cy=b.y()+b.height()/2f;
        g.pose().pushMatrix();
        g.pose().translate(cx-size/2,cy-size/2);g.pose().scale(size/20,size/20);
        float t=preferences.highContrast()?1.9f:1.5f;
        switch(icon){
            case SEARCH -> {ring(g,7.8f,7.8f,5.5f,t,ink);line(g,12,12,17.4f,17.4f,t,ink);}
            case BACK -> {line(g,12.5f,3.5f,6,10,t,ink);line(g,6,10,12.5f,16.5f,t,ink);}
            case CLOSE -> {line(g,4,4,16,16,t,ink);line(g,4,16,16,4,t,ink);}
            case ADD -> {line(g,3,10,17,10,t,ink);line(g,10,3,10,17,t,ink);}
            case REMOVE -> line(g,3,10,17,10,t,ink);
            case USER -> {ring(g,10,6,3.3f,t,ink);curve(g,3,17,3,10.5f,17,10.5f,17,17,t,ink);}
            case FAVORITE -> {
                curve(g,10,17,6,14,2,11,2,6.5f,t,ink);
                curve(g,2,6.5f,2,1.8f,7.3f,1.8f,10,6,t,ink);
                curve(g,10,6,12.7f,1.8f,18,1.8f,18,6.5f,t,ink);
                curve(g,18,6.5f,18,11,14,14,10,17,t,ink);
            }
            case HOME -> {
                line(g,2,9,10,2,t,ink);line(g,10,2,18,9,t,ink);
                line(g,4,8,4,17,t,ink);line(g,4,17,8,17,t,ink);line(g,8,17,8,12,t,ink);
                line(g,8,12,12,12,t,ink);line(g,12,12,12,17,t,ink);line(g,12,17,16,17,t,ink);line(g,16,17,16,8,t,ink);
            }
            case LIBRARY -> {
                RoundedCorners.draw(g,2.5f,3,4,14,1, t,ink,0);
                RoundedCorners.draw(g,8.5f,3,4,14,1,t,ink,0);
                line(g,15,3.5f,18,16.5f,t,ink);
            }
            case REFRESH -> {
                // Open sweep and tangent arrowhead distinguish refresh from a clock/history symbol.
                float previousX=15.5f,previousY=5.5f;
                for(int i=1;i<=24;i++){
                    double angle=Math.toRadians(-40-i*285f/24);
                    float nextX=10+(float)Math.cos(angle)*7,nextY=10+(float)Math.sin(angle)*7;
                    line(g,previousX,previousY,nextX,nextY,t,ink);previousX=nextX;previousY=nextY;
                }
                line(g,15.5f,5.5f,11.3f,5.1f,t,ink);
                line(g,15.5f,5.5f,15.9f,1.4f,t,ink);
            }
            case HISTORY -> {ring(g,10,10,7.2f,t,ink);line(g,10,5.5f,10,10,t,ink);line(g,10,10,13.5f,12,t,ink);}
            case SETTINGS -> {
                for(int row=0;row<3;row++){
                    float y=4+row*6,x=row==1?13:7;
                    line(g,2,y,x-2,y,t,ink);line(g,x+2,y,18,y,t,ink);ring(g,x,y,2,t,ink);
                }
            }
            case PLAYLIST -> {
                for(int row=0;row<3;row++){float y=4+row*6;line(g,7,y,18,y,t,ink);ring(g,2.5f,y,.65f,t,ink);}
            }
        }
        g.pose().popMatrix();
    }
    private static void ring(GuiGraphicsExtractor g,float x,float y,float radius,float t,int ink){
        RoundedCorners.draw(g,x-radius,y-radius,radius*2,radius*2,radius,t,ink,0);
    }
    /** SDF rounded capsules share the existing physical-pixel antialiasing, including rotated poses. */
    private static void line(GuiGraphicsExtractor g,float x,float y,float x2,float y2,float t,int ink){
        float length=(float)Math.hypot(x2-x,y2-y);
        g.pose().pushMatrix();g.pose().translate(x,y);
        g.pose().rotate((float)Math.atan2(y2-y,x2-x));
        RoundedCorners.draw(g,-t/2,-t/2,length+t,t,t/2,0,0,ink);g.pose().popMatrix();
    }
    private static void curve(GuiGraphicsExtractor g,float x,float y,float a,float b,float c,float d,float endX,float endY,float t,int ink){
        float previousX=x,previousY=y;
        for(int i=1;i<=10;i++){
            float u=i/10f,v=1-u;
            float nextX=v*v*v*x+3*v*v*u*a+3*v*u*u*c+u*u*u*endX;
            float nextY=v*v*v*y+3*v*v*u*b+3*v*u*u*d+u*u*u*endY;
            line(g,previousX,previousY,nextX,nextY,t,ink);previousX=nextX;previousY=nextY;
        }
    }
    private MusicControlRenderer(){}
    public static void seek(GuiGraphicsExtractor g,Bounds b,double fraction,boolean engaged,boolean focused,
                            boolean enabled,int foreground,UiPreferences preferences){
        seek(g,b,fraction,engaged?1f:0f,focused,enabled,foreground,preferences);
    }
    public static void seek(GuiGraphicsExtractor g,Bounds b,double fraction,float engagement,boolean focused,
                            boolean enabled,int foreground,UiPreferences preferences){
        if(b.width()<8||b.height()<1)return;
        float engaged=Math.max(0,Math.min(1,engagement));
        float x=b.x()+4,w=b.width()-8,cy=b.y()+b.height()/2f;
        boolean contrast=preferences.highContrast();
        int ink=contrast?(foreground|0xff000000):foreground;
        float thickness=2+engaged;
        RoundedCorners.draw(g,x,cy-thickness/2,w,thickness,thickness/2,0,0,alpha(ink,contrast?.5f:enabled?.24f:.12f));
        float fill=(float)(w*(Double.isFinite(fraction)?Math.max(0,Math.min(1,fraction)):0));
        if(fill>0)RoundedCorners.draw(g,x,cy-thickness/2,fill,thickness,thickness/2,0,0,alpha(ink,enabled?.8f:.3f));
        if(enabled&&engaged>0){
            float radius=2+1.5f*engaged;
            RoundedCorners.draw(g,x+fill-radius,cy-radius,radius*2,radius*2,radius,0,0,alpha(ink,engaged));
        }
        if(focused)RoundedCorners.draw(g,b.x(),b.y()+2,b.width(),Math.max(1,b.height()-4),5,1,alpha(ink,.7f),0);
    }
    public static void transport(GuiGraphicsExtractor g,Bounds b,MusicTransportControl.Glyph glyph,boolean hover,
                                 boolean focus,boolean pressed,boolean enabled,int foreground,UiPreferences preferences){
        transport(g,b,glyph,1f,hover,focus,pressed,enabled,foreground,preferences);
    }
    static void transport(GuiGraphicsExtractor g,Bounds b,MusicTransportControl.Glyph glyph,float glyphWeight,boolean hover,
                          boolean focus,boolean pressed,boolean enabled,int foreground,UiPreferences preferences){
        if(b.width()<1||b.height()<1)return;
        int ink=preferences.highContrast()?(foreground|0xff000000):foreground;
        if(!enabled)ink=alpha(ink,.3f);
        float side=Math.min(b.width(),b.height());float cx=b.x()+b.width()/2f,cy=b.y()+b.height()/2f;
        if((hover||focus||pressed)&&enabled)
            RoundedCorners.draw(g,cx-side/2,cy-side/2,side,side,side/2,focus?1:0,
                    alpha(ink,.65f),alpha(ink,pressed?.14f:.07f));
        if(glyphWeight<1f) drawTransportGlyph(g,TransportGlyphMotion.pairedWith(glyph),
                cx,cy,side,pressed,alpha(ink,1f-glyphWeight),preferences);
        if(glyphWeight>0f) drawTransportGlyph(g,glyph,cx,cy,side,pressed,alpha(ink,glyphWeight),preferences);
    }
    private static void drawTransportGlyph(GuiGraphicsExtractor g,MusicTransportControl.Glyph glyph,float cx,float cy,
                                           float side,boolean pressed,int ink,UiPreferences preferences){
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
                g.pose().pushMatrix();g.pose().translate(cx,cy+size*.04f);g.pose().rotate((float)Math.toRadians(45));
                bar(g,-size*.23f,-size*.23f,size*.46f,size*.46f,ink);
                RoundedCorners.draw(g,-size*.46f,-size*.23f,size*.46f,size*.46f,size*.23f,0,0,ink);
                RoundedCorners.draw(g,-size*.23f,-size*.46f,size*.46f,size*.46f,size*.23f,0,0,ink);
                g.pose().popMatrix();
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
    private static void bar(GuiGraphicsExtractor g,float x,float y,float w,float h,int color){RoundedCorners.draw(g,x,y,w,h,Math.min(w,h)/3,0,0,color);}
    private static void stroke(GuiGraphicsExtractor g,float x,float y,float x2,float y2,float thickness,int color){
        float length=(float)Math.hypot(x2-x,y2-y);
        g.pose().pushMatrix();g.pose().translate(x,y);
        g.pose().rotate((float)Math.atan2(y2-y,x2-x));
        bar(g,0,-thickness/2,length,thickness,color);g.pose().popMatrix();
    }
    /** Quarter-GUI-unit analytic scan conversion keeps the common renderer independent of loader vertex APIs. */
    private static void triangle(GuiGraphicsExtractor g,float x,float y,float w,float h,boolean right,int color){
        if(w<=0||h<=0)return;
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(.25f,.25f);
        int rows=Math.max(1,Math.round(h*4));
        for(int row=0;row<rows;row++){
            float half=Math.abs((row+.5f)/rows-.5f)*2;
            float extent=w*4*(1-half);int whole=(int)extent;
            if(right){if(whole>0)g.fill(0,row,whole,row+1,color);g.fill(whole,row,whole+1,row+1,alpha(color,extent-whole));}
            else {int end=Math.round(w*4);if(whole>0)g.fill(end-whole,row,end,row+1,color);g.fill(end-whole-1,row,end-whole,row+1,alpha(color,extent-whole));}
        }
        g.pose().popMatrix();
    }
    private static int alpha(int color,float amount){return (color&0xffffff)|(Math.round((color>>>24)*amount)<<24);}
}
