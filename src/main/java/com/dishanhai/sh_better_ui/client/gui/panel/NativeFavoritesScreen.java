// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.favorites.FavoriteSettingsStore;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
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
        if(width<220 || height<185) {
            button("back",8,Math.max(30,height-26),Math.max(1,width-16),Component.translatable("gui.back"),this::onClose);
            return;
        }
        int left=Math.max(8,(width-430)/2), w=Math.max(1,width-2*left);
        capacity=Math.max(1,(height-142)/34);
        offset=Math.max(0,Math.min(offset,Math.max(0,rows().size()-capacity)));
        button("manage",left,42,w, text(manage?"show_pinned":"manage"),()->{manage=!manage;offset=0;refresh("manage");});
        var settings=NativeMusicOverlay.currentSettings();
        for(int i=offset;i<Math.min(rows().size(),offset+capacity);i++) {
            String id=rows().get(i); int y=70+(i-offset)*34;
            if(id.equals("scale") && settings.isPresent() && w>100) {
                var slider=new com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider(
                        new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(left,y,w-80,20),
                        50,200,5,Math.round(settings.get().islandScale()*100),
                        com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.configured(),
                        value->text("value",text("scale"),value+"%"), value->{});
                slider.setOnCommit(value->saveScale(value/100f),value->{});
                addRenderableWidget(slider);controls.put(id,slider);
            } else {
                var edit=button(id,left,y,Math.max(1,w-80),label(id,settings.orElse(null)),()->edit(id));
                edit.active=settings.isPresent();
            }
            boolean pinned=store.ids().contains(id);
            button("pin:"+id,left+w-76,y,76,text(pinned?"remove":"add"),()->pin(id));
        }
        int bottom=height-62;
        var up=button("up",left,bottom,Math.max(1,(w-8)/3),text("previous"),()->move(-capacity)); up.active=offset>0;
        var down=button("down",left+(w+4)/3,bottom,Math.max(1,(w-8)/3),text("next"),()->move(capacity)); down.active=offset+capacity<rows().size();
        var revert=button("undo",left+2*(w+4)/3,bottom,Math.max(1,(w-8)/3),text("undo"),()->{
            if(undo!=null) { Runnable action=undo; action.run(); }
        }); revert.active=undo!=null;
        button("back",left,height-26,w,Component.translatable("gui.back"),this::onClose);
        if(controls.containsKey(focusId)&&controls.get(focusId).active) setFocused(controls.get(focusId));
    }
    private UiButton button(String id,int x,int y,int w,Component label,Runnable action) {
        var button=addRenderableWidget(new UiButton(x,y,w,20,label,b->{try{action.run();}catch(Exception e){status=text("save_failed");}}));
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
        if(y<70||y>=height-66||delta==0)return false; move(delta>0?-1:1);return true;
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==266||key==267){move(key==266?-capacity:capacity);return true;}return super.keyPressed(key,scan,mods);
    }
    public void renderBackground(GuiGraphics g,int x,int y,float partial) {}
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        var theme=UiTheme.current(); g.fill(0,0,width,height,theme.background());
        g.drawCenteredString(font,title,width/2,10,theme.text());
        g.drawCenteredString(font,text("scope"),width/2,25,theme.text());
        if(width<220 || height<185) g.drawCenteredString(font,text("small"),width/2,66,theme.text());
        else if(rows().isEmpty())g.drawCenteredString(font,text("empty"),width/2,76,theme.text());
        super.render(g,x,y,partial);
        if(status!=null)g.drawCenteredString(font,status,width/2,height-38,theme.text());
    }
    @Override public Component getNarrationMessage(){return title.copy().append(". ").append(text("scope")).append(status==null?Component.empty():status);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
