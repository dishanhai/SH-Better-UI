// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.favorites.FavoriteSettingsStore;
import com.dishanhai.sh_better_ui.client.gui.scaled.MpFlatButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.MpTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.MpPageChrome;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import com.dishanhai.sh_better_ui.kernel.internal.NativeMusicOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.UnaryOperator;

/** Muonium Favorites means frequent settings, not a music-provider favorites library. */
public final class NativeFavoritesScreen extends Screen {
    private final Screen parent;
    private final FavoriteSettingsStore store;
    private boolean manage, loaded;
    private int offset, capacity = 1;
    private int windowX, windowY, windowWidth, windowHeight, left, span, bodyTop;
    private Component status;
    private Runnable undo;
    private String focusId = "";
    private final Map<String, net.minecraft.client.gui.components.AbstractWidget> controls = new LinkedHashMap<>();
    public NativeFavoritesScreen(Screen parent) {
        super(text("title")); this.parent=parent;
        store=new FavoriteSettingsStore(Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/sh_better_ui/favorite-settings.txt"));
    }
    public static void open(Screen parent) { Minecraft.getInstance().setScreen(new NativeFavoritesScreen(parent)); }
    private static Component text(String key,Object...args) { return Component.translatable("sh_better_ui.favorites."+key,args); }
    private List<String> rows() { return manage ? FavoriteSettingsStore.AVAILABLE : store.ids(); }
    @Override protected void init() {
        if(!loaded) { loaded=true; try { store.load(); } catch(Exception e) { status=text("load_failed"); } }
        controls.clear();
        if(width<300 || height<220) {
            button("back",Math.min(8,width/4),Math.max(0,height-24),Math.max(1,width-2*Math.min(8,width/4)),Component.translatable("gui.back"),this::onClose);
            return;
        }
        windowWidth=Math.min(500,width-32);windowHeight=Math.min(320,height-32);
        windowX=(width-windowWidth)/2;windowY=(height-windowHeight)/2;
        left=windowX+18;span=windowWidth-36;int w=span;bodyTop=windowY+78;
        capacity=visibleRows(windowHeight);
        offset=Math.max(0,Math.min(offset,Math.max(0,rows().size()-capacity)));
        button("manage",left+w-126,windowY+44,126, text(manage?"show_pinned":"manage"),()->{manage=!manage;offset=0;refresh("manage");});
        var settings=NativeMusicOverlay.currentSettings();
        for(int i=offset;i<Math.min(rows().size(),offset+capacity);i++) {
            String id=rows().get(i); int y=bodyTop+(i-offset)*36+6;
            if(id.equals("scale") && settings.isPresent() && w>100) {
                var slider=new com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider(
                        new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(left+Math.max(80,w/3),y,w-Math.max(80,w/3)-72,22),
                        50,200,5,Math.round(settings.get().islandScale()*100),
                        com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.configured(),
                        value->MpTypography.text(text("value",text("scale"),value+"%")), value->{});
                slider.setOnCommit(value->saveScale(value/100f),value->{});
                addRenderableWidget(slider);controls.put(id,slider);
            } else {
                var edit=button(id,left+w-154,y,84,label(id,settings.orElse(null)),()->edit(id));
                edit.active=settings.isPresent();
                if(settings.isPresent()) {
                    var value=settings.get();
                    edit.displayText(onOff(switch(id){case "hud"->value.hudEnabled();case "island"->value.islandEnabled();case "ambient"->value.ambientEnabled();default->value.taskNotices();}));
                }
            }
            boolean pinned=store.ids().contains(id);
            button("pin:"+id,left+w-64,y,64,text(pinned?"remove":"add"),()->pin(id));
        }
        int bottom=windowY+windowHeight-34;
        var up=button("up",left,bottom,36,text("previous"),()->move(-capacity)); up.active=offset>0;up.displayText(Component.literal("‹"));
        var down=button("down",left+40,bottom,36,text("next"),()->move(capacity)); down.active=offset+capacity<rows().size();down.displayText(Component.literal("›"));
        var revert=button("undo",left+80,bottom,60,text("undo"),()->{
            if(undo!=null) { Runnable action=undo; action.run(); }
        }); revert.active=undo!=null;
        button("back",left+w-72,bottom,72,Component.translatable("gui.back"),this::onClose);
        if(controls.containsKey(focusId)&&controls.get(focusId).active) setFocused(controls.get(focusId));
    }
    static int visibleRows(int windowHeight) { return Math.max(1,(windowHeight-128)/36); }
    private MpFlatButton button(String id,int x,int y,int w,Component label,Runnable action) {
        var button=addRenderableWidget(new MpFlatButton(x,y,w,Math.max(1,Math.min(22,height)),label,b->{try{action.run();}catch(Exception e){status=text("save_failed");}},UiPreferences.configured(),false));
        controls.put(id,button); return button;
    }
    private Component label(String id,MusicOverlaySettings s) {
        if(s==null) return text("unavailable",text(id));
        Object value=switch(id) {
            case "hud" -> onOff(s.hudEnabled()); case "island" -> onOff(s.islandEnabled());
            case "ambient" -> onOff(s.ambientEnabled()); case "tasks" -> onOff(s.taskNotices());
            default -> String.format(Locale.ROOT,"%.0f%%",s.islandScale()*100);
        };
        return text("value",text(id),value);
    }
    private static Component onOff(boolean enabled) { return Component.translatable(enabled?"options.on":"options.off"); }
    private void saveScale(float value) {
        try {
            var before=NativeMusicOverlay.currentSettings().orElseThrow();
            if(!NativeMusicOverlay.updateSettings(s->s.withScale(true,value))) throw new IllegalStateException();
            undo=()->{
                if(!NativeMusicOverlay.updateSettings(s->s.withScale(true,before.islandScale()))) throw new IllegalStateException();
                undo=null;status=text("undone");refresh("scale");
            };
            status=text("saved");refresh("scale");
        } catch(Exception e) { status=text("save_failed");refresh("scale"); }
    }
    private void edit(String id) {
        var before=NativeMusicOverlay.currentSettings().orElseThrow();
        UnaryOperator<MusicOverlaySettings> change=switch(id) {
            case "hud" -> s->s.withHud(!s.hudEnabled()); case "island" -> s->s.withIsland(!s.islandEnabled());
            case "ambient" -> s->s.withAmbient(!s.ambientEnabled()); case "tasks" -> s->s.withTaskNotices(!s.taskNotices());
            default -> s->s.withScale(true,s.islandScale()>=1.99f?.5f:Math.min(2f,s.islandScale()+.1f));
        };
        if(!NativeMusicOverlay.updateSettings(change)) { status=text("save_failed"); return; }
        undo=()->{
            UnaryOperator<MusicOverlaySettings> restore=switch(id) {
                case "hud" -> s->s.withHud(before.hudEnabled()); case "island" -> s->s.withIsland(before.islandEnabled());
                case "ambient" -> s->s.withAmbient(before.ambientEnabled()); case "tasks" -> s->s.withTaskNotices(before.taskNotices());
                default -> s->s.withScale(true,before.islandScale());
            };
            if(!NativeMusicOverlay.updateSettings(restore)) throw new IllegalStateException();
            undo=null;status=text("undone");refresh(id);
        };
        status=text("saved");refresh(id);
    }
    private void pin(String id) throws java.io.UncheckedIOException {
        var before=store.ids(); var next=new ArrayList<>(before);
        if(!next.remove(id))next.add(id);
        try {store.save(next);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        undo=()->{try{store.save(before);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            undo=null;status=text("undone");refresh("manage");};
        status=text("saved");refresh("pin:"+id);
    }
    private void refresh(String focus) { focusId=focus;rebuildWidgets(); if(getFocused()==null)setFocused(controls.get("manage")); }
    private void move(int amount) { offset=Math.max(0,Math.min(offset+amount,Math.max(0,rows().size()-capacity)));refresh(amount<0?"up":"down"); }
    public boolean mouseScrolled(double x,double y,double delta) {
        if(x<left||x>=left+span||y<bodyTop||y>=bodyTop+capacity*36||delta==0)return false; move(delta>0?-1:1);return true;
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==266||key==267){move(key==266?-capacity:capacity);return true;}return super.keyPressed(key,scan,mods);
    }
    public void renderBackground(GuiGraphics g,int x,int y,float partial) {}
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        var theme=UiTheme.current();var preferences=UiPreferences.configured();
        if(width<300 || height<220) {
            MpPageChrome.backdrop(g,width,height,theme,preferences);
            if(height>40) g.drawString(font,fit(MpTypography.text(text("small")),width-16),8,8,theme.text(),false);
        } else {
            MpPageChrome.window(g,width,height,windowX,windowY,windowWidth,windowHeight,theme,preferences);
            MpPageChrome.heading(g,font,title,left,windowY+17,MpPageChrome.foreground(theme,preferences));
            var scope=fit(MpTypography.text(text("scope")),Math.max(1,span-138));
            g.drawString(font,scope,left,windowY+49,MpPageChrome.muted(theme,preferences),false);
            for(int i=offset;i<Math.min(rows().size(),offset+capacity);i++) {
                int rowY=bodyTop+(i-offset)*36;
                MpPageChrome.row(g,left-4,rowY,span+8,34,theme,preferences);
                var name=fit(MpTypography.text(text(rows().get(i))),Math.max(1,span-166));
                g.drawString(font,name,left+7,rowY+11,MpPageChrome.foreground(theme,preferences),false);
            }
            if(rows().isEmpty()) MpTypography.draw(g,font,text("empty"),left,bodyTop+12,theme.text());
            if(status!=null) g.drawString(font,fit(MpTypography.text(status),span),left,windowY+windowHeight-50,MpPageChrome.muted(theme,preferences),false);
        }
        super.render(g,x,y,partial);
    }
    @Override public Component getNarrationMessage(){return title.copy().append(". ").append(text("scope")).append(status==null?Component.empty():status);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private net.minecraft.util.FormattedCharSequence fit(Component value, int available) {
        return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(value, Math.max(0, available)));
    }

}
