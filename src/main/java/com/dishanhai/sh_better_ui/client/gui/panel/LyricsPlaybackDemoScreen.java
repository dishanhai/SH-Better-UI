// Author: dishanhai. Original silent test content, not a music provider or playback session.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicLyricsModel;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicLyricsView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Explicitly silent, original-content harness for timing, wrapping, browsing and seek QA. */
public final class LyricsPlaybackDemoScreen extends Screen {
    private final Screen parent;
    private MusicLyricsView lyrics;
    private long position,last;
    private boolean playing=true;
    private static final List<MusicLyricsModel.Line> LINES=List.of(
        new MusicLyricsModel.Line("光落在窗边",0,4000,List.of(new MusicLyricsModel.Word("光",0,500),new MusicLyricsModel.Word("落",500,1000),new MusicLyricsModel.Word("在",1000,1400),new MusicLyricsModel.Word("窗边",1400,4000)),"",""),
        new MusicLyricsModel.Line("我们把脚步放慢",4000,8000,List.of(new MusicLyricsModel.Word("我们",4000,4800),new MusicLyricsModel.Word("把",4800,5200),new MusicLyricsModel.Word("脚步",5200,6500),new MusicLyricsModel.Word("放慢",6500,8000)),"Take a slower step",""),
        new MusicLyricsModel.Line("听见远处的风经过山川",8000,13000),
        new MusicLyricsModel.Line("每一个停顿 都有回声",13000,18000,List.of(new MusicLyricsModel.Word("每一个",13000,14000),new MusicLyricsModel.Word("停顿 ",14000,15600),new MusicLyricsModel.Word("都有",15600,16200),new MusicLyricsModel.Word("回声",16200,18000)),"Every pause has an echo",""),
        new MusicLyricsModel.Line("让此刻的旋律 留在眼前",18000,23000),
        new MusicLyricsModel.Line("A quiet moment, a little room to breathe",23000,29000),
        new MusicLyricsModel.Line("回到光亮的地方",29000,34000));
    public LyricsPlaybackDemoScreen(Screen parent){super(Component.literal("歌词扫色 · 无声原创示例"));this.parent=parent;}
    @Override protected void init(){
        int margin=Math.max(10,Math.min(36,width/16));
        lyrics=addRenderableWidget(new MusicLyricsView(new Bounds(margin,48,width-2*margin,Math.max(32,height-94)),Component.literal("无声歌词时间轴"),UiPreferences.configured(),p->{position=p;last=System.nanoTime();}));
        lyrics.setLines("original-silent-demo",LINES);lyrics.scale(width<400?1.5f:2.3f);lyrics.palette(0xfffff7f4,0xffbca9a8,0xff443339);
        int w=Math.max(40,Math.min(100,(width-40)/4));int x=10,y=height-30;
        addRenderableWidget(Button.builder(Component.literal("播放 / 暂停"),b->{playing=!playing;last=System.nanoTime();}).bounds(x,y,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("回到开头"),b->{position=0;lyrics.follow();last=System.nanoTime();}).bounds(x+w+5,y,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复跟随"),b->lyrics.follow()).bounds(x+2*(w+5),y,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x+3*(w+5),y,w,20).build());
        last=System.nanoTime();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        long now=System.nanoTime();if(playing&&last!=0)position=Math.min(34000,position+Math.max(0,(now-last)/1_000_000));last=now;
        g.fill(0,0,width,height,0xff443339);g.drawString(font,title,12,12,0xfff8e9e5,false);
        g.drawString(font,Component.literal("无音频 · "+(playing?"运行":"暂停")+" · "+position/1000+"s / 34s"),12,28,0xffcab7b4,false);
        lyrics.playback(position,true);super.render(g,mouseX,mouseY,partial);
    }
    public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
