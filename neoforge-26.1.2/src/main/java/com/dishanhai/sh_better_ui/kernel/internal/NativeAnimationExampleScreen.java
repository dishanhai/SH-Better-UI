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
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.KeyEvent;
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
    private AbstractContainerWidget body;
    private AbstractWidget preview;
    NativeAnimationExampleScreen(Screen parent,NativeAnimationExample example){super(label("title"));this.parent=parent;this.example=example;}
    public static Component label(String key){return Component.translatable("sh_better_ui.native.animation_example."+key);}
    @Override protected void init(){
        double scroll=body==null?0:body.scrollAmount();
        clearWidgets();body=null;preview=null;
        if(scope==null || !scope.isOpen()){
            scope=example.mount(this);target=true;paused=false;
            track=example.context.engine().require(UIApis.ANIMATION).bind(example.context,scope,
                    NativeAnimationExample.PROPERTY,Spring.PANEL,0,()->UiPreferences.configured().reducedMotion(),value->{});
            fold=example.context.engine().require(UIApis.COLLAPSE).create(example.context,scope,
                    new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.SPRING,240,200,false,false));
            track.blend(1,0,3000,InterruptibleAnimation.BlendCurve.LINEAR);fold.expand();
        }
        int contentWidth=Math.max(1,Math.min(520,width-24));
        if(height>112 && width>=40){
            var content=LinearLayout.vertical().spacing(8);
            content.addChild(new MultiLineTextWidget(label("help"),font).setMaxWidth(contentWidth));
            preview=content.addChild(new AbstractWidget(0,0,contentWidth,82,label("title")){
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
                    int x=getX(),y=getY(),w=getWidth();
                    graphics.text(font,Component.translatable("sh_better_ui.native.animation_example.track",Math.round(track.value()*100),paused),x,y,0xffffffff,false);
                    graphics.fill(x,y+16,x+w,y+22,0xff304355);
                    graphics.fill(x,y+16,x+Math.round(w*Math.clamp(track.value(),0,1)),y+22,0xff80caff);
                    graphics.text(font,Component.translatable("sh_better_ui.native.animation_example.fold",fold.state().name(),Math.round(fold.progress()*100)),x,y+40,0xffffffff,false);
                    graphics.fill(x,y+56,x+w,y+68,0xff304355);
                    graphics.fill(x,y+56,x+Math.round(w*fold.progress()),y+68,0xff80d6a0);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput output){
                    output.add(NarratedElementType.TITLE,Component.translatable("sh_better_ui.native.animation_example.track",Math.round(track.value()*100),paused)
                            .append(" ").append(Component.translatable("sh_better_ui.native.animation_example.fold",fold.state().name(),Math.round(fold.progress()*100))));
                }
            });
            var viewport=new ScrollableLayout(minecraft,content,height-112);
            viewport.setMinWidth(contentWidth);viewport.arrangeElements();
            viewport.setPosition((width-viewport.getWidth())/2,28);
            viewport.visitWidgets(widget->body=(AbstractContainerWidget)addRenderableWidget(widget));
            body.setScrollAmount(scroll);
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
        super.extractRenderState(graphics,x,y,partial);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(body!=null){
            double next=switch(event.key()){
                case 266 -> body.scrollAmount()-Math.max(16,body.getHeight()-12);
                case 267 -> body.scrollAmount()+Math.max(16,body.getHeight()-12);
                case 268 -> 0;
                case 269 -> Double.MAX_VALUE;
                default -> -1;
            };
            if(next>=0 || event.key()==266){body.setScrollAmount(Math.max(0,next));return true;}
        }
        return super.keyPressed(event);
    }
    @Override public void removed(){if(scope!=null){scope.close();scope=null;}track=null;fold=null;}
    @Override public void onClose(){if(NativePort.screen(minecraft)==this)NativePort.show(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
