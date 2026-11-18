// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.internal.NativeMusicActivity;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import java.util.function.BiFunction;

/** Controls only the native music activity features currently wired to the live runtime. */
public final class NativeMusicSettingsScreen extends Screen {
    private final Screen parent;
    private final NativeMusicActivity music;
    private AbstractContainerWidget body;
    private Button island, notices, activity, hud, resetHud, undoReset;
    private UiSlider hudX, hudY, hudScale;
    private record HudLayout(float x,float y,float scale) {
        static HudLayout of(MusicOverlaySettings value) { return new HudLayout(value.x(),value.y(),value.hudScale()); }
        MusicOverlaySettings apply(MusicOverlaySettings value) { return value.withPosition(x,y).withScale(false,scale); }
    }
    private HudLayout beforeReset;
    private void cancelSliders() {
        if(hudX!=null)hudX.cancelEdit(); if(hudY!=null)hudY.cancelEdit(); if(hudScale!=null)hudScale.cancelEdit();
    }
    private boolean removed;

    public NativeMusicSettingsScreen(Screen parent, NativeMusicActivity music) {
        super(label("title"));
        this.parent = parent;
        this.music = Objects.requireNonNull(music);
    }
    private static Component label(String suffix) {
        return Component.translatable("sh_better_ui.native.music_settings." + suffix);
    }
    @Override protected void init() {
        var focused=body==null?null:body.getFocused();
        String focus=focused==null?"":focused==hudX?"x":focused==hudY?"y":focused==hudScale?"scale":
                focused==hud?"hud":focused==island?"island":focused==notices?"notices":"";
        cancelSliders();
        clearWidgets();
        removed = false;
        double scroll = body == null ? 0 : body.scrollAmount();
        // ScrollableLayout reserves 20px around its content when a scrollbar is needed.
        int available = Math.max(9, Math.min(320, width - 24));
        var content = LinearLayout.vertical().spacing(8);
        content.addChild(new MultiLineTextWidget(label("description"), font).setMaxWidth(available));
        hud = content.addChild(Button.builder(Component.empty(), button -> {
            if(!current())return;
            var settings=music.settings();music.settings(settings.withHud(!settings.hudEnabled()));sync();
        }).width(available).build());
        var initial=music.settings();
        hudX=content.addChild(slider("hud_x",available,0,100,Math.round(initial.x()*100),
                (value,percent)->value.withPosition(percent/100f,value.y())));
        hudY=content.addChild(slider("hud_y",available,0,100,Math.round(initial.y()*100),
                (value,percent)->value.withPosition(value.x(),percent/100f)));
        hudScale=content.addChild(slider("hud_scale",available,50,200,Math.round(initial.hudScale()*100),
                (value,percent)->value.withScale(false,percent/100f)));
        content.addChild(new MultiLineTextWidget(label("hud_layout_hint"),font).setMaxWidth(available));
        resetHud=content.addChild(Button.builder(label("reset_hud_layout"),button->{
            if(!current())return;
            cancelSliders();var current=music.settings();beforeReset=HudLayout.of(current);
            music.settings(HudLayout.of(MusicOverlaySettings.DEFAULT).apply(current));sync();
        }).width(available).build());
        undoReset=content.addChild(Button.builder(label("undo_hud_reset"),button->{
            if(!current()||beforeReset==null)return;
            cancelSliders();var current=music.settings();
            if(HudLayout.of(current).equals(HudLayout.of(MusicOverlaySettings.DEFAULT)))
                music.settings(beforeReset.apply(current));
            beforeReset=null;sync();
        }).width(available).build());
        content.addChild(Button.builder(label("preview_hud"),button->{
            if(current()) NativePort.show(minecraft,new LayoutPreview(this,music));
        }).width(available).build());
        island = content.addChild(Button.builder(Component.empty(), button -> {
            if (!current()) return;
            var settings = music.settings();
            music.settings(settings.withIsland(!settings.islandEnabled()));
            sync();
        }).width(available).build());
        notices = content.addChild(Button.builder(Component.empty(), button -> {
            if (!current()) return;
            var settings = music.settings();
            music.settings(settings.withTaskNotices(!settings.taskNotices()));
            sync();
        }).width(available).build());
        content.addChild(new MultiLineTextWidget(label("notice_hint"), font).setMaxWidth(available));
        content.addChild(Button.builder(label("player"), button -> {
            if (current()) NativePort.show(minecraft, ShBetterUiApi.settings().onlineMusicScreen(this));
        }).width(available).build());
        activity = content.addChild(Button.builder(label("activity"), button -> {
            if (current() && ShBetterUiApi.settings().activitiesAvailable())
                NativePort.show(minecraft, ShBetterUiApi.settings().activityScreen(this));
        }).width(available).build());
        body = null;
        if (height > 64 && width >= 40) {
            var layout = new ScrollableLayout(minecraft, content, height - 64);
            layout.setMinWidth(available);
            layout.arrangeElements();
            layout.setPosition((width - layout.getWidth()) / 2, 28);
            layout.visitWidgets(widget -> body = (AbstractContainerWidget) addRenderableWidget(widget));
            body.setScrollAmount(scroll);
        }
        var back = addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(Math.max(0,(width - Math.min(100,width))/2), Math.max(0,height-26),
                        Math.max(1,Math.min(100,width)),20).build());
        AbstractWidget restore=switch(focus){case "x"->hudX;case "y"->hudY;case "scale"->hudScale;
            case "hud"->hud;case "island"->island;case "notices"->notices;default->null;};
        if(restore!=null && body!=null){body.setFocused(restore);setFocused(body);}else setInitialFocus(back);
        sync();
    }
    private UiSlider slider(String key,int width,int min,int max,int value,
                            BiFunction<MusicOverlaySettings,Integer,MusicOverlaySettings> change) {
        var slider=new UiSlider(new Bounds(0,0,width,20),min,max,1,value,UiPreferences.configured(),
                percent->Component.translatable("sh_better_ui.native.music_settings."+key,percent),ignored->{});
        slider.setOnCommit(percent->{
            if(!current())return;
            beforeReset=null;music.settings(change.apply(music.settings(),percent));sync();
        },ignored->{});
        return slider;
    }
    private void sync() {
        var settings = music.settings();
        hud.setMessage(Component.translatable("sh_better_ui.native.music_settings.hud",
                settings.hudEnabled()?CommonComponents.OPTION_ON:CommonComponents.OPTION_OFF));
        if(!hudX.isPointerEditing())hudX.setValue(Math.round(settings.x()*100));
        if(!hudY.isPointerEditing())hudY.setValue(Math.round(settings.y()*100));
        if(!hudScale.isPointerEditing())hudScale.setValue(Math.round(settings.hudScale()*100));
        if(beforeReset!=null && !HudLayout.of(settings).equals(HudLayout.of(MusicOverlaySettings.DEFAULT)))beforeReset=null;
        undoReset.active=beforeReset!=null;
        resetHud.active=!HudLayout.of(settings).equals(HudLayout.of(MusicOverlaySettings.DEFAULT));
        island.setMessage(Component.translatable("sh_better_ui.native.music_settings.island",
                settings.islandEnabled() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
        notices.setMessage(Component.translatable("sh_better_ui.native.music_settings.notices",
                settings.taskNotices() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
        activity.active = ShBetterUiApi.settings().activitiesAvailable();
    }
    private boolean current() { return !removed && NativePort.screen(minecraft) == this; }
    @Override public void tick() { super.tick(); sync(); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float tick) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick) {
        graphics.fill(0,0,width,height,0xff10141d);
        graphics.centeredText(font,title,width/2,9,0xfff4f7fc);
        super.extractRenderState(graphics,x,y,tick);
    }
    private static final class LayoutPreview extends Screen {
        private final Screen parent;
        private final NativeMusicActivity music;
        LayoutPreview(Screen parent,NativeMusicActivity music) { super(label("preview_hud"));this.parent=parent;this.music=music; }
        @Override protected void init() {
            var back=addRenderableWidget(Button.builder(CommonComponents.GUI_BACK,button->onClose())
                    .bounds(Math.max(0,(width-Math.min(100,width))/2),Math.max(0,height-26),Math.max(1,Math.min(100,width)),20).build());
            setInitialFocus(back);
        }
        @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float tick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick) {
            graphics.fill(0,0,width,height,0xff10141d);
            graphics.centeredText(font,label("preview_hint"),width/2,9,0xfff4f7fc);
            com.dishanhai.sh_better_ui.kernel.internal.NativeMusicHud.drawPreview(graphics,width,height,music.settings());
            super.extractRenderState(graphics,x,y,tick);
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){if(NativePort.screen(minecraft)==this)NativePort.show(minecraft,parent);}
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (current()) {cancelSliders();NativePort.show(minecraft,parent);} }
    @Override public void removed() { cancelSliders();removed = true; }
}
