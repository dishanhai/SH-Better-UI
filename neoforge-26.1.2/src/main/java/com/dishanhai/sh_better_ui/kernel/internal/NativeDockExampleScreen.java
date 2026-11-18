// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.ProgressIndicator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Screen-owned example: progress is explicitly simulated, never music or a background task. */
public final class NativeDockExampleScreen extends Screen {
    private final Screen parent;
    private final NativeDockExample example;
    private ScopeHandle scope;
    private MiniWindowHandle window;
    private int ticks;
    private double progress=.4;
    private Button expand,collapse,reset,close;
    NativeDockExampleScreen(Screen parent,NativeDockExample example){super(label("title"));this.parent=parent;this.example=example;}
    public static Component label(String key){return Component.translatable("sh_better_ui.native.dock_example."+key);}
    @Override protected void init(){
        clearWidgets();
        if(scope==null || !scope.isOpen()){scope=example.mount(this);window=null;}
        if(window==null)createWindow();
        int buttonWidth=Math.max(1,Math.min(112,(width-20)/4));int left=Math.max(0,(width-buttonWidth*4-12)/2);
        int y=Math.max(0,height-52);
        expand=button(left,y,buttonWidth,"expand",()->{if(!window.isOpen())createWindow();window.expand();});
        collapse=button(left+buttonWidth+4,y,buttonWidth,"collapse",()->window.collapse());
        reset=button(left+(buttonWidth+4)*2,y,buttonWidth,"reset",()->window.resetPlacement());
        close=button(left+(buttonWidth+4)*3,y,buttonWidth,"close",()->window.close());
        button(Math.max(0,(width-buttonWidth)/2),Math.max(0,height-27),buttonWidth,"back",this::onClose);
        updateButtons();
    }
    private Button button(int x,int y,int width,String key,Runnable action){return addRenderableWidget(Button.builder(label(key),b->{
        if(NativePort.screen(minecraft)==this){action.run();updateButtons();}
    }).bounds(x,y,width,20).build());}
    private void createWindow(){
        window=example.dock.createMiniWindow(example.context,scope,MiniWindowSpec.builder("sh_better_ui:dock_example/window")
                .title(label("sample")).compactSize(28,80).size(240,160).outsideClickCloses(true)
                .compactIndicator(()->new ProgressIndicator(24,72,()->progress,0xff80caff,0xff304355))
                .content(()->(draw,view)->{
                    int x=(int)view.bounds().x()+8,y=(int)view.bounds().y()+8;
                    draw.text(label("sample"),x,y,view.foregroundArgb());
                    draw.text(Component.literal(Math.round(progress*100)+"%"),x,y+18,view.foregroundArgb());
                    draw.fill(x,y+36,x+(int)Math.max(0,(view.bounds().width()-16)*progress),y+42,0xff80caff);
                }).build());
    }
    private void updateButtons(){if(collapse!=null){boolean open=window!=null&&window.isOpen();collapse.active=open;reset.active=open;close.active=open;}}
    @Override public void tick(){ticks++;progress=(ticks%200)/200.0;updateButtons();}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float partial){}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fill(0,0,width,height,0xff151b25);graphics.centeredText(font,title,width/2,10,0xffffffff);
        int y=28;for(var line:font.split(label("help"),Math.max(1,width-40))){graphics.text(font,line,20,y,0xffd3e1ee,false);y+=font.lineHeight+2;}
        super.extractRenderState(graphics,mouseX,mouseY,partial);
    }
    @Override public void removed(){if(scope!=null){scope.close();scope=null;}window=null;}
    @Override public void onClose(){if(NativePort.screen(minecraft)==this)NativePort.show(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
