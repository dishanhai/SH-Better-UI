// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Preview-only draft until Apply. No edit writes global preferences or resource files. */
public final class NativeFeedbackScreen extends Screen {
    private final Screen parent;
    private final Consumer<ControlFeedbackSettings> applied;
    private final Consumer<Screen> navigate;
    private ControlFeedbackSettings draft,saved,beforeReset;
    private AbstractContainerWidget body;
    private UiButton preview;
    private UiSlider delay;
    private Button hover,press,pulse,apply,undo;
    private Component status=Component.empty();
    private boolean selected;
    public NativeFeedbackScreen(Screen parent){this(parent,ControlPanelSettings.current().feedback(),value->{});}
    NativeFeedbackScreen(Screen parent,ControlFeedbackSettings initial,Consumer<ControlFeedbackSettings> applied){
        this(parent,initial,applied,null);
    }
    NativeFeedbackScreen(Screen parent,ControlFeedbackSettings initial,Consumer<ControlFeedbackSettings> applied,Consumer<Screen> navigate){
        super(label("title"));this.parent=parent;this.applied=applied;
        this.navigate=navigate==null?next->NativePort.show(minecraft,next):navigate;
        draft=initial;saved=ControlPanelSettings.current().feedback();
    }
    static Component label(String key,Object... args){return Component.translatable("sh_better_ui.native.feedback."+key,args);}
    private boolean current(){return NativePort.screen(minecraft)==this;}
    boolean dirty(){return !draft.equals(saved);}
    private UiPreferences previewPreferences(){return ControlPanelSettings.current().withFeedback(draft).preferences(minecraft.options!=null&&minecraft.options.highContrast().get());}
    @Override protected void init(){
        if(delay!=null)delay.cancelEdit();
        double scroll=body==null?0:body.scrollAmount();boolean delayFocused=delay!=null&&delay.isFocused();
        clearWidgets();body=null;delay=null;preview=null;hover=null;press=null;pulse=null;undo=null;
        int w=Math.max(9,Math.min(400,width-24));
        if(width>=40 && height>84){
            var content=LinearLayout.vertical().spacing(7);
            content.addChild(new MultiLineTextWidget(label("hint"),font).setMaxWidth(w));
            hover=content.addChild(control(w,"hover",()->edit(new ControlFeedbackSettings(!draft.hoverAnimation(),draft.pressAnimation(),draft.selectionPulse(),draft.tooltipDelayMs()))));
            press=content.addChild(control(w,"press",()->edit(new ControlFeedbackSettings(draft.hoverAnimation(),!draft.pressAnimation(),draft.selectionPulse(),draft.tooltipDelayMs()))));
            pulse=content.addChild(control(w,"pulse",()->edit(new ControlFeedbackSettings(draft.hoverAnimation(),draft.pressAnimation(),!draft.selectionPulse(),draft.tooltipDelayMs()))));
            delay=new UiSlider(new Bounds(0,0,w,20),0,ControlFeedbackSettings.MAX_TOOLTIP_DELAY_MS,1,draft.tooltipDelayMs(),previewPreferences(),
                    value->label("delay",value),value->editDelay(value));
            delay.setOnCommit(value->{},this::editDelay);content.addChild(delay);
            preview=new UiButton(0,0,w,24,label("preview"),b->{if(current()){selected=!selected;preview.setSelected(selected);}},previewPreferences(),true);
            preview.setSelected(selected);preview.setSampleBackground(false);preview.setTooltip(Tooltip.create(label("tooltip")));content.addChild(preview);
            content.addChild(control(w,"reset",()->{beforeReset=draft;draft=ControlFeedbackSettings.DEFAULT;status=label("unsaved");sync();}));
            undo=content.addChild(control(w,"undo",()->{if(beforeReset!=null){draft=beforeReset;beforeReset=null;sync();}}));
            var viewport=new ScrollableLayout(minecraft,content,height-72);viewport.setMinWidth(w);viewport.arrangeElements();
            viewport.setPosition((width-viewport.getWidth())/2,28);viewport.visitWidgets(widget->body=(AbstractContainerWidget)addRenderableWidget(widget));
            body.setScrollAmount(scroll);if(delayFocused){body.setFocused(delay);setFocused(body);}
        }
        int footerWidth=Math.max(1,Math.min(170,(width-20)/2)),left=Math.max(0,(width-footerWidth*2-4)/2);
        apply=addRenderableWidget(Button.builder(label("apply"),b->{if(current())apply();}).bounds(left,Math.max(0,height-26),footerWidth,20).build());
        addRenderableWidget(Button.builder(label("cancel"),b->{if(current()){cancelSlider();navigate.accept(parent);}})
                .bounds(left+footerWidth+4,Math.max(0,height-26),footerWidth,20).build());
        sync();
    }
    private Button control(int width,String key,Runnable run){return Button.builder(label(key),b->{if(current()){cancelSlider();run.run();}}).width(width).build();}
    private void cancelSlider(){if(delay!=null)delay.cancelEdit();}
    private void edit(ControlFeedbackSettings value){draft=value;beforeReset=null;status=label("unsaved");sync();}
    private void editDelay(int value){draft=new ControlFeedbackSettings(draft.hoverAnimation(),draft.pressAnimation(),draft.selectionPulse(),value);beforeReset=null;status=label("unsaved");sync();}
    private void sync(){
        if(hover!=null)hover.setMessage(label("toggle",label("hover"),Component.translatable(draft.hoverAnimation()?"options.on":"options.off")));
        if(press!=null)press.setMessage(label("toggle",label("press"),Component.translatable(draft.pressAnimation()?"options.on":"options.off")));
        if(pulse!=null)pulse.setMessage(label("toggle",label("pulse"),Component.translatable(draft.selectionPulse()?"options.on":"options.off")));
        if(delay!=null && !delay.isPointerEditing())delay.setValue(draft.tooltipDelayMs());
        if(preview!=null)preview.setPreferences(previewPreferences());
        if(apply!=null)apply.active=dirty();if(undo!=null)undo.active=beforeReset!=null;
    }
    void apply(){
        cancelSlider();
        if(!ControlPanelSettings.save(ControlPanelSettings.current().withFeedback(draft))){status=label("failed");return;}
        saved=draft;beforeReset=null;applied.accept(draft);status=label("saved");sync();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float partial){
        super.extractRenderState(graphics,x,y,partial);graphics.centeredText(font,title,width/2,10,0xffffffff);
        var bounds=statusBounds();
        graphics.enableScissor(bounds.x(),bounds.y(),bounds.x()+bounds.width(),bounds.y()+bounds.height());
        try { graphics.centeredText(font,status,width/2,bounds.y(),0xffd3e1ee); }
        finally { graphics.disableScissor(); }
    }
    Bounds statusBounds(){
        int inset=Math.min(10,Math.max(0,width/2));
        return new Bounds(inset,Math.max(0,height-39),Math.max(0,width-inset*2),Math.min(font.lineHeight,Math.max(0,height)));
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isEscape() && delay!=null && delay.isPointerEditing()){delay.cancelEdit();return true;}
        if(body!=null && (delay==null || !delay.isFocused())){
            double next=switch(event.key()){case 266->body.scrollAmount()-body.getHeight();case 267->body.scrollAmount()+body.getHeight();case 268->0;case 269->Double.MAX_VALUE;default->-1;};
            if(next>=0 || event.key()==266){body.setScrollAmount(Math.max(0,next));return true;}
        }
        return super.keyPressed(event);
    }
    @Override public void onClose(){
        if(!current())return;cancelSlider();
        if(!dirty()){navigate.accept(parent);return;}
        var dialog=new ConfirmScreen[1];dialog[0]=new ConfirmScreen(yes->{
            if(NativePort.screen(minecraft)==dialog[0])navigate.accept(yes?parent:this);
        },label("discard"),label("discard_detail"));navigate.accept(dialog[0]);
    }
    @Override public void removed(){cancelSlider();}
    @Override public boolean isPauseScreen(){return false;}
}
