// Author: dishanhai. Original responsive music stage; cover and lyric modes share transport geometry.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

record FullscreenLyricLayout(Bounds frame, Bounds back, Bounds cover, Bounds info, Bounds lyrics,
                             Bounds transport, Bounds seek, Bounds previous, Bounds play, Bounds next,
                             Bounds volume, Bounds mode, Bounds queue, Bounds message, boolean wide) {
    static FullscreenLyricLayout of(Bounds frame) { return of(frame,true,false,true); }
    static FullscreenLyricLayout of(Bounds frame, boolean ownReturn, boolean messageVisible) {
        return of(frame,ownReturn,messageVisible,true);
    }
    static FullscreenLyricLayout of(Bounds frame, boolean ownReturn, boolean messageVisible, boolean lyricMode) {
        int inset=frame.width()>=460?24:12;
        int x=frame.x()+inset,right=frame.right()-inset;
        boolean wide=frame.width()>=460&&frame.height()>=320;
        int column=wide?Math.min(260,(right-x-28)*44/100):right-x;
        int rightX=x+column+28;
        var back=new Bounds(x,frame.y()+8,Math.min(100,right-x),22);
        var transport=new Bounds(x,frame.bottom()-105,column,97);
        var seek=new Bounds(x,transport.y(),column,16);
        int mid=x+column/2;
        var play=new Bounds(mid-18,transport.y()+35,36,36);
        var previous=new Bounds(mid-63,transport.y()+39,28,28);
        var next=new Bounds(mid+35,transport.y()+39,28,28);
        var volume=new Bounds(x,transport.y()+79,column,16);
        int modeY=wide?frame.bottom()-32:frame.y()+6;
        var mode=new Bounds(right-60,modeY,24,24);
        var queue=new Bounds(right-24,modeY,24,24);
        int messageHeight=messageVisible?28:0;
        var message=new Bounds(wide?rightX:x,wide?frame.bottom()-76:transport.y()-32,wide?right-rightX:column,messageHeight);
        int top=frame.y()+(ownReturn||!wide?40:16);
        Bounds cover,info,lyrics;
        if(wide) {
            int infoY=transport.y()-42;
            int size=Math.max(32,Math.min(column,infoY-top-12));
            cover=new Bounds(x+(column-size)/2,top,size,size);
            info=new Bounds(x,infoY,column,34);
            int bottom=messageVisible?message.y()-10:frame.bottom()-46;
            lyrics=new Bounds(rightX,top,right-rightX,Math.max(24,bottom-top));
        } else {
            boolean compact=lyricMode||frame.height()<300||messageVisible;
            if(compact) {
                int size=32;
                cover=new Bounds(x,top,size,size);
                info=new Bounds(x+44,top,Math.max(1,column-44),34);
            } else {
                int infoY=transport.y()-42;
                int size=Math.max(32,Math.min(column,infoY-top-12));
                cover=new Bounds(x+(column-size)/2,top,size,size);
                info=new Bounds(x,infoY,column,34);
            }
            int lyricY=top+44;
            int bottom=messageVisible?message.y()-6:transport.y()-8;
            lyrics=new Bounds(x,lyricY,column,Math.max(12,bottom-lyricY));
        }
        return new FullscreenLyricLayout(frame,back,cover,info,lyrics,transport,seek,previous,play,next,volume,mode,queue,message,wide);
    }
    int lyricRowHeight(){return wide?42:34;}
    float lyricScale(){return wide?1.9f:frame.width()>=300?1.5f:1.2f;}
}
