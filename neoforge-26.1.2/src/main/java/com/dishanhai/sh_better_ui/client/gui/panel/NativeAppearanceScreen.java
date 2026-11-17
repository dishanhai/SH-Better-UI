// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.NativeAppearanceBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Player appearance draft with explicit Apply, local preview, and safe return. */
public final class NativeAppearanceScreen extends Screen {
    private final Screen parent;
    private final Consumer<ThemeSettings> onApplied;
    final NativeAppearanceDraft draft;
    private ThemeSettings preview;
    private AbstractContainerWidget body;
    private final Map<String,AbstractWidget> controls=new LinkedHashMap<>();
    private Button apply,undo;
    private Component status=Component.empty();
    private boolean valid=true;
    public NativeAppearanceScreen(Screen parent){this(parent,ControlPanelSettings.current().theme(),ignored->{});}
    NativeAppearanceScreen(Screen parent,ThemeSettings initial,Consumer<ThemeSettings> onApplied){
        super(label("title"));this.parent=parent;this.onApplied=onApplied;
        draft=new NativeAppearanceDraft(initial,ControlPanelSettings.current().theme());preview=initial;
    }
    static Component label(String key,Object...arguments){return Component.translatable("sh_better_ui.native.appearance."+key,arguments);}
    private boolean current(){return NativePort.screen(minecraft)==this;}
    private Button button(String id,Component title,int width,Runnable action){
        var button=Button.builder(title,b->{if(current())action.run();}).width(width).build();controls.put(id,button);return button;
    }
    @Override protected void init(){
        var focused=body==null?getFocused():body.getFocused();
        String focus=controls.entrySet().stream().filter(e->e.getValue()==focused).map(Map.Entry::getKey).findFirst().orElse("");
        double scroll=body==null?0:body.scrollAmount();clearWidgets();controls.clear();body=null;
        int available=Math.max(9,Math.min(360,width-24));
        var content=LinearLayout.vertical().spacing(6);
        content.addChild(new MultiLineTextWidget(label("scope"),font).setMaxWidth(available));
        for(var mode:ThemeSettings.Mode.values()) content.addChild(button("mode:"+mode,
                label("mode",Component.translatable("sh_better_ui.panel.theme.mode."+mode.name().toLowerCase(Locale.ROOT)),
                        draft.state().mode()==mode?Component.literal("●"):Component.literal("○")),available,
                ()->{draft.mode(mode);validate();init();}));
        content.addChild(new MultiLineTextWidget(label("palette_hint"),font).setMaxWidth(available));
        if(draft.state().mode()==ThemeSettings.Mode.CUSTOM){
            content.addChild(button("base",label("base",Component.translatable("sh_better_ui.panel.theme.mode."+draft.state().base().name().toLowerCase(Locale.ROOT))),available,
                    ()->{draft.base(draft.state().base()==ThemeSettings.Base.DARK?ThemeSettings.Base.LIGHT:ThemeSettings.Base.DARK);validate();init();}));
            String[] names={"background","surface","border","text","accent"};
            for(int i=0;i<names.length;i++){
                int index=i;content.addChild(new MultiLineTextWidget(label("color."+names[i]),font).setMaxWidth(available));
                var input=new EditBox(font,available,20,label("color."+names[i]));input.setMaxLength(7);input.setValue(draft.state().colors().get(i));
                input.setResponder(value->{draft.color(index,value);validate();});controls.put("color:"+i,input);content.addChild(input);
            }
        }
        content.addChild(new MultiLineTextWidget(label("background_hint"),font).setMaxWidth(available));
        if(!NativeAppearanceBackground.supported(draft.state().background()))
            content.addChild(new MultiLineTextWidget(label("unsupported",draft.state().background()),font).setMaxWidth(available));
        for(String id:NativeAppearanceBackground.supportedIds()) content.addChild(button("background:"+id,
                label("choice",label("background."+id),draft.state().background().equals(id)?Component.literal("●"):Component.literal("○")),available,
                ()->{draft.background(id);validate();init();}));
        content.addChild(button("preview",label("preview"),available,()->{
            validate();if(valid)NativePort.show(minecraft,new Preview(this,preview));
        }));
        content.addChild(button("reset",label("reset"),available,()->{draft.reset();validate();init();}));
        undo=content.addChild(button("undo",label("undo"),available,()->{draft.undoReset();validate();init();}));
        if(height>90 && width>=40){
            var layout=new ScrollableLayout(minecraft,content,height-86);layout.setMinWidth(available);layout.arrangeElements();
            layout.setPosition((width-layout.getWidth())/2,28);layout.visitWidgets(widget->body=(AbstractContainerWidget)addRenderableWidget(widget));body.setScrollAmount(scroll);
        }
        int buttonWidth=Math.max(1,Math.min(100,(width-20)/2));
        var back=button("back",label("back"),buttonWidth,this::onClose);back.setPosition(6,Math.max(0,height-26));addRenderableWidget(back);
        apply=button("apply",label("apply"),buttonWidth,this::apply);apply.setPosition(Math.max(6,width-buttonWidth-6),Math.max(0,height-26));addRenderableWidget(apply);
        var restored=controls.get(focus);
        if(restored!=null && body!=null && !focus.equals("back")&&!focus.equals("apply")){body.setFocused(restored);setFocused(body);}
        else setInitialFocus(restored==null?back:restored);
        validate();
    }
    private void validate(){
        try{preview=draft.value();valid=true;status=label(draft.dirty()?"unsaved":"saved");}
        catch(IllegalArgumentException invalid){valid=false;status=label("invalid_color");}
        if(apply!=null)apply.active=valid&&draft.dirty();if(undo!=null)undo.active=draft.canUndoReset();
    }
    private void apply(){
        validate();if(!valid)return;
        var value=draft.value(ControlPanelSettings.current().theme());
        if(ControlPanelSettings.save(ControlPanelSettings.current().withTheme(value))){draft.applied(value);onApplied.accept(value);init();}
        else status=label("save_failed");
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float tick){
            if(minecraft.level==null)extractPanorama(graphics,tick);
            var preferences=UiPreferences.configured();
            com.dishanhai.sh_better_ui.client.blur.BackgroundBlur.request(graphics,
                    new UiPreferences(preferences.highContrast(),preferences.reducedMotion(),ControlPanelSettings.current().backgroundBlur()));
            graphics.fill(0,0,width,height,0x30121A26);
        }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
        NativeAppearanceBackground.draw(graphics,width,height,preview,UiPreferences.configured(),System.nanoTime()/1_000_000L);
        graphics.fill(0,0,width,height,0xc010141d);graphics.centeredText(font,title,width/2,9,0xffffffff);
        graphics.text(font,font.plainSubstrByWidth(status.getString(),Math.max(1,width-12)),6,Math.max(0,height-46),0xffe2e8f0,false);
        UiTheme.preview(UiTheme.resolve(preview),()->super.extractRenderState(graphics,x,y,tick));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        if(!current())return;if(!draft.dirty()){NativePort.show(minecraft,parent);return;}
        ConfirmScreen[] dialog=new ConfirmScreen[1];dialog[0]=new ConfirmScreen(discard->{
            if(NativePort.screen(minecraft)==dialog[0])NativePort.show(minecraft,discard?parent:this);
        },label("discard_title"),label("discard_body"),label("discard"),label("keep_editing"));NativePort.show(minecraft,dialog[0]);
    }
    private static final class Preview extends Screen {
        private final Screen parent;private final ThemeSettings theme;
        Preview(Screen parent,ThemeSettings theme){super(label("preview"));this.parent=parent;this.theme=theme;}
        @Override protected void init(){addRenderableWidget(Button.builder(label("back"),b->onClose()).bounds(Math.max(0,(width-100)/2),Math.max(0,height-26),Math.min(100,width),20).build());}
        @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float tick){
            if(minecraft.level==null)extractPanorama(graphics,tick);
            var preferences=UiPreferences.configured();
            com.dishanhai.sh_better_ui.client.blur.BackgroundBlur.request(graphics,
                    new UiPreferences(preferences.highContrast(),preferences.reducedMotion(),ControlPanelSettings.current().backgroundBlur()));
            graphics.fill(0,0,width,height,0x30121A26);
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            NativeAppearanceBackground.draw(graphics,width,height,theme,UiPreferences.configured(),System.nanoTime()/1_000_000L);
            var palette=UiTheme.resolve(theme);int w=Math.max(1,Math.min(280,width-20)),h=Math.min(90,Math.max(1,height-60));int left=(width-w)/2,top=(height-h)/2;
            graphics.fill(left,top,left+w,top+h,palette.surface());
            graphics.centeredText(font,label("preview_only"),width/2,top+12,palette.text());
            int[] colors={palette.background(),palette.surface(),palette.border(),palette.text(),palette.accent()};
            int cell=Math.max(1,(w-24)/5);
            for(int i=0;i<colors.length;i++)graphics.fill(left+12+i*cell,top+h-22,left+12+(i+1)*cell-1,top+h-9,colors[i]);
            graphics.text(font,font.plainSubstrByWidth(label("swatches").getString(),Math.max(1,w-24)),left+12,top+30,palette.text(),false);
            UiTheme.preview(palette,()->super.extractRenderState(graphics,x,y,tick));
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){if(NativePort.screen(minecraft)==this)NativePort.show(minecraft,parent);}
    }
}
