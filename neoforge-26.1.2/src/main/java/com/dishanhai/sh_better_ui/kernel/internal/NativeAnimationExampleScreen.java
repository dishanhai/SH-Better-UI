// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Drives actual public scoped services. Settings navigation recreates the closed demo tree on return. */
public final class NativeAnimationExampleScreen extends Screen {
    private final Screen parent;
    private final NativeAnimationExample example;
    private ScopeHandle scope;
    private AnimationService.Track track;
    private CollapseHandle fold;
    private boolean target=true,paused;
    NativeAnimationExampleScreen(Screen parent,NativeAnimationExample example){super(label("title"));this.parent=parent;this.example=example;}
    public static Component label(String key){return Component.translatable("sh_better_ui.native.animation_example."+key);}
    @Override protected void init(){
        clearWidgets();
        if(scope==null || !scope.isOpen()){
            scope=example.mount(this);target=true;paused=false;
            track=example.context.engine().require(UIApis.ANIMATION).bind(example.context,scope,
                    NativeAnimationExample.PROPERTY,Spring.PANEL,0,()->UiPreferences.configured().reducedMotion(),value->{});
            fold=example.context.engine().require(UIApis.COLLAPSE).create(example.context,scope,
                    new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.SPRING,240,200,false,false));
            track.blend(1,0,3000,InterruptibleAnimation.BlendCurve.LINEAR);fold.expand();
        }
        int w=Math.max(1,Math.min(170,(width-24)/2)),left=Math.max(0,(width-w*2-4)/2),y=Math.max(0,height-76);
        button(left,y,w,"collapse",fold::toggle);
        button(left+w+4,y,w,"reverse",()->{target=!target;track.play(target?1:0,InterruptibleAnimation.Policy.RETARGET,0);});
        button(left,y+24,w,"pause",()->{track.pause();paused=true;});
        button(left+w+4,y+24,w,"resume",()->{track.resume();paused=false;});
        button(left,y+48,w,"settings",()->NativePort.show(minecraft,ShBetterUiApi.settings().controlPanel(this)));
        button(left+w+4,y+48,w,"back",this::onClose);
    }
    private void button(int x,int y,int width,String key,Runnable run){addRenderableWidget(Button.builder(label(key),b->{
        if(NativePort.screen(minecraft)==this && scope!=null && scope.isOpen())run.run();
    }).bounds(x,y,width,20).build());}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float partial){}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float partial){
        graphics.fill(0,0,width,height,0xff151b25);graphics.centeredText(font,title,width/2,10,0xffffffff);
        int top=28;for(var line:font.split(label("help"),Math.max(1,width-24))){graphics.text(font,line,12,top,0xffd3e1ee,false);top+=font.lineHeight+2;}
        int availableBottom=Math.max(top,height-84),barWidth=Math.max(1,width-32);
        if(top+36<availableBottom){
            graphics.text(font,Component.translatable("sh_better_ui.native.animation_example.track",Math.round(track.value()*100),paused),16,top+4,0xffffffff,false);
            graphics.fill(16,top+18,16+barWidth,top+24,0xff304355);
            graphics.fill(16,top+18,16+Math.round(barWidth*Math.clamp(track.value(),0,1)),top+24,0xff80caff);
        }
        if(top+72<availableBottom){
            graphics.text(font,Component.translatable("sh_better_ui.native.animation_example.fold",fold.state().name(),Math.round(fold.progress()*100)),16,top+38,0xffffffff,false);
            graphics.fill(16,top+52,16+Math.round(barWidth*fold.progress()),top+64,0xff80d6a0);
        }
        super.extractRenderState(graphics,x,y,partial);
    }
    @Override public void removed(){if(scope!=null){scope.close();scope=null;}track=null;fold=null;}
    @Override public void onClose(){if(NativePort.screen(minecraft)==this)NativePort.show(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
