// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Set;

/** Scoped native HUD owner. Never captures gameplay input or starts media capture. */
public final class NativeMusicOverlay implements UIModule {
    private static NativeMusicOverlay active;
    private final boolean persistent;
    private ModuleContext context;
    private LocalMusicIsland island;
    private LocalMusicHud card;
    private boolean restoreIsland = true;
    private long ambientAt = Long.MIN_VALUE;
    private NativeMusicOverlay(boolean persistent) { this.persistent = persistent; }
    static ModuleDiscovery.Candidate candidate(boolean persistent) {
        return new ModuleDiscovery.Candidate("sh_better_ui", NativeMusicOverlay.class.getName(),
                new NativeMusicOverlay(persistent), Set.of(UIPermission.REGISTER_RENDER_PASS, UIPermission.MODIFY_GLOBAL_CONFIG), false, true);
    }
    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(UiId.parse("sh_better_ui:music_overlay"))
                .version(new UiVersion(1,0,0)).initializationPhase(ModuleDescriptor.InitializationPhase.EXTENSION)
                .requiredCapability(UIApis.NATIVE_HUD, VersionRange.ANY)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS).requestPermission(UIPermission.MODIFY_GLOBAL_CONFIG).build();
    }
    @Override public void register(ModuleContext context) {}
    @Override public void start(ModuleContext context) {
        this.context = context;
        var configs = context.engine().configs();
        configs.define(MusicOverlaySettings.KEY);
        if (persistent) {
            var gson = new Gson();
            context.scope().track(configs.persist(MusicOverlaySettings.KEY,
                    ConfigCodec.of(gson::toJson, s -> gson.fromJson(s, MusicOverlaySettings.class))));
        }
        context.scope().track(context.engine().require(UIApis.HUD).mount(context, this::draw));
        active = this;
    }
    @Override public void stop(ModuleContext context) { if (active == this) active = null; suspend(); }
    private void suspend() { restoreIsland = true; if (island != null) island.suspend(); if (card != null) card.suspend(); }
    private MusicOverlaySettings settings() { return context.engine().configs().get(MusicOverlaySettings.KEY); }
    private void save(MusicOverlaySettings value) {
        var configs = context.engine().configs();
        configs.beginTransaction().set(configs.userGlobal(), MusicOverlaySettings.KEY, value).commit();
        restoreIsland = true;
    }
    private void draw(HudService.Frame frame) {
        var mc = Minecraft.getInstance();
        var value = settings();
        if ((!value.hudEnabled() && !value.islandEnabled()) || mc.level == null || mc.player == null
                || mc.options.hideGui || mc.screen != null || mc.options.keyPlayerList.isDown()) { suspend(); return; }
        if (card == null) card = new LocalMusicHud(ShBetterUiApi.music());
        if (island == null) island = new LocalMusicIsland(ShBetterUiApi.music());
        var viewport = new Bounds(0, 0, frame.width(), frame.height());
        var ui = new UiFrame(frame.graphics(), viewport, new UiMouseState(-1,-1,-1,-1,false,true,false,0),
                frame.preferences(), PageTransition.nowMs(), frame.partialTick());
        if (value.hudEnabled()) card.render(ui, mc.font,
                MusicOverlayLayout.panel(frame.width(),frame.height(),false,value.hudScale(),value.x(),value.y()));
        if (value.islandEnabled()) {
            island.setScene(true,false,false,false);
            island.setAmbient(value.ambientEnabled() ? IslandAmbientState.Settings.DEFAULT : IslandAmbientState.Settings.DISABLED);
            if (value.ambientEnabled() && (ambientAt == Long.MIN_VALUE || ui.nowMs() < ambientAt || ui.nowMs()-ambientAt>=250)) {
                ambientAt=ui.nowMs();
                var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID());
                var time=java.time.LocalTime.now();
                island.updateAmbient(mc.getFps(), info==null?-1:info.getLatency(), time.getHour(), time.getMinute(),ui.nowMs());
            }
            if (restoreIsland) { island.setExpanded(value.expanded(), ui.nowMs(), frame.preferences().reducedMotion()); restoreIsland = false; }
            var full = MusicOverlayLayout.panel(frame.width(),frame.height(),true,value.islandScale(),value.islandX(),value.islandY());
            island.render(ui,mc.font,MusicOverlayLayout.compact(full,value.islandScale()),full,false);
        }
    }
    public static boolean available() { return active != null && active.context.scope().isOpen(); }
    public static void open(Screen parent) { if (available()) Minecraft.getInstance().setScreen(new SettingsScreen(active,parent)); }
    private static Component text(String key, Object... args) { return Component.translatable("sh_better_ui.music_overlay."+key,args); }
    private static final class SettingsScreen extends Screen {
        private final NativeMusicOverlay owner;
        private final Screen parent;
        private MusicOverlaySettings draft;
        private Component error;
        SettingsScreen(NativeMusicOverlay owner, Screen parent) { super(text("title")); this.owner=owner;this.parent=parent;draft=owner.settings(); }
        @Override protected void init() {
            int w=Math.min(240,Math.max(40,width-16)), x=(width-w)/2, step=Math.min(24,Math.max(14,(height-60)/6)), y=26;
            addRenderableWidget(new UiButton(x,y,w,step-2,toggle("hud",draft.hudEnabled()), b->{draft=draft.withHud(!draft.hudEnabled());b.setMessage(toggle("hud",draft.hudEnabled()));}));
            addRenderableWidget(new UiButton(x,y+step,w,step-2,toggle("island",draft.islandEnabled()), b->{draft=draft.withIsland(!draft.islandEnabled());b.setMessage(toggle("island",draft.islandEnabled()));}));
            addRenderableWidget(new UiButton(x,y+2*step,w,step-2,toggle("expand",draft.expanded()),b->{draft=draft.withExpanded(!draft.expanded());b.setMessage(toggle("expand",draft.expanded()));}));
            addRenderableWidget(new UiButton(x,y+3*step,w,step-2,text("edit_hud"),b->minecraft.setScreen(new PositionScreen(this,false))));
            addRenderableWidget(new UiButton(x,y+4*step,w,step-2,text("edit_island"),b->minecraft.setScreen(new PositionScreen(this,true))));
            addRenderableWidget(new UiButton(x,y+5*step,w,step-2,toggle("ambient",draft.ambientEnabled()),b->{draft=draft.withAmbient(!draft.ambientEnabled());b.setMessage(toggle("ambient",draft.ambientEnabled()));}));
            addRenderableWidget(new UiButton(x,height-26,w/2-2,20,text("apply"), b->{try{owner.save(draft);onClose();}catch(RuntimeException e){error=text("save_failed");}}));
            addRenderableWidget(new UiButton(x+w/2+2,height-26,w/2-2,20,text("cancel"),b->onClose()));
        }
        private Component toggle(String key,boolean on){return text(key).copy().append(": ").append(Component.translatable(on?"options.on":"options.off"));}
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xF018202C);g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);super.render(g,x,y,partial);if(error!=null)g.drawCenteredString(font,error,width/2,height-39,0xFFFF7777);}
        @Override public void onClose(){minecraft.setScreen(parent);}
        // NeoForge Screen.render invokes this after our opaque custom background.
        // Do not blur the already-rendered HUD preview; this signature is harmless on Forge 1.20.1.
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class PositionScreen extends Screen {
        private final SettingsScreen parent;
        private final boolean editIsland;
        private float xFraction,yFraction,scale;
        private boolean dragging;
        private double dragX,dragY;
        private Bounds area,bounds;
        private UiButton handle;
        private final LocalMusicHud card = new LocalMusicHud(ShBetterUiApi.music());
        private final LocalMusicIsland island = new LocalMusicIsland(ShBetterUiApi.music());
        PositionScreen(SettingsScreen parent,boolean editIsland){super(text(editIsland?"edit_island":"edit_hud"));this.parent=parent;this.editIsland=editIsland;xFraction=editIsland?parent.draft.islandX():parent.draft.x();yFraction=editIsland?parent.draft.islandY():parent.draft.y();scale=editIsland?parent.draft.islandScale():parent.draft.hudScale();}
        @Override protected void init(){
            dragging=false;area=new Bounds(0,0,Math.max(1,width),Math.max(1,height));place();
            int w=Math.min(90,(width-16)/3),x=(width-3*w-8)/2;
            addRenderableWidget(new UiButton(x,height-26,w,20,text("reset"),b->{xFraction=.5f;yFraction=editIsland?.05f:.75f;scale=1f;place();}));
            addRenderableWidget(new UiButton(x+w+4,height-26,w,20,text("apply"),b->{parent.draft=editIsland?parent.draft.withIslandPosition(xFraction,yFraction):parent.draft.withPosition(xFraction,yFraction);parent.draft=parent.draft.withScale(editIsland,scale);onClose();}));
            addRenderableWidget(new UiButton(x+2*w+8,height-26,w,20,text("cancel"),b->onClose()));
            // Footer controls must win hit testing when the preview extends behind them.
            handle=addWidget(new UiButton(bounds.x(),bounds.y(),bounds.width(),bounds.height(),text("drag"),b->{}));
        }
        private void place(){var local=MusicOverlayLayout.panel(area.width(),area.height(),editIsland,scale,xFraction,yFraction);bounds=new Bounds(area.x()+local.x(),area.y()+local.y(),local.width(),local.height());if(handle!=null){handle.setX(bounds.x());handle.setY(bounds.y());handle.setWidth(bounds.width());handle.setHeight(bounds.height());}}
        @Override public boolean mouseClicked(double x,double y,int button){if(y>=height-30)return super.mouseClicked(x,y,button);if(button==0&&x>=bounds.x()&&x<bounds.right()&&y>=bounds.y()&&y<bounds.bottom()){dragging=true;dragX=x-bounds.x();dragY=y-bounds.y();setFocused(handle);return true;}return super.mouseClicked(x,y,button);}
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragging){xFraction=clamp((float)(x-dragX-area.x())/Math.max(1,area.width()-bounds.width()));yFraction=clamp((float)(y-dragY-area.y())/Math.max(1,area.height()-bounds.height()));place();return true;}return super.mouseDragged(x,y,button,dx,dy);}
        @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&dragging){dragging=false;return true;}return super.mouseReleased(x,y,button);}
        private void resize(float delta) {
            int left=bounds.x()-area.x(), top=bounds.y()-area.y();
            scale=Math.max(.5f,Math.min(2f,scale+delta));
            var resized=MusicOverlayLayout.panel(area.width(),area.height(),editIsland,scale,xFraction,yFraction);
            int w=resized.width(),h=resized.height();
            xFraction=clamp(left/(float)Math.max(1,area.width()-w));yFraction=clamp(top/(float)Math.max(1,area.height()-h));place();
        }
        public boolean mouseScrolled(double x,double y,double delta){if(delta==0)return false;if(x>=bounds.x()&&x<bounds.right()&&y>=bounds.y()&&y<bounds.bottom()){resize(delta>0?.1f:-.1f);return true;}return false;}
        public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return mouseScrolled(x,y,vertical);}
        @Override public boolean keyPressed(int key,int scan,int mods){if(getFocused()==handle&&(key==61||key==45||key==334||key==333)){resize(key==61||key==334?.1f:-.1f);return true;}if(getFocused()==handle&&key>=262&&key<=265){xFraction=clamp(xFraction+(key==262?.02f:key==263?-.02f:0));yFraction=clamp(yFraction+(key==264?.02f:key==265?-.02f:0));place();return true;}return super.keyPressed(key,scan,mods);}
        private static float clamp(float value){return Math.max(0,Math.min(1,value));}
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF18202C);g.drawCenteredString(font,text("drag"),width/2,10,0xFFFFFFFF);var frame=new UiFrame(g,new Bounds(0,0,width,height),new UiMouseState(x,y,x,y,true,false,true,0),UiPreferences.configured(),PageTransition.nowMs(),partial);if(editIsland){island.setScene(true,false,false,false);island.setExpanded(true,frame.nowMs(),frame.preferences().reducedMotion());island.render(frame,font,MusicOverlayLayout.compact(bounds,scale),bounds,false);}else card.render(frame,font,bounds);if(dragging||getFocused()==handle)g.renderOutline(bounds.x(),bounds.y(),bounds.width(),bounds.height(),0xFFFFFFFF);super.render(g,x,y,partial);}
        @Override public void onClose(){minecraft.setScreen(parent);}
        // NeoForge Screen.render invokes this after our opaque custom background.
        // Do not blur the already-rendered HUD preview; this signature is harmless on Forge 1.20.1.
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public boolean isPauseScreen(){return false;}
    }
}
