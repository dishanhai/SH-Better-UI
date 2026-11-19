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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import java.util.Set;
import com.dishanhai.sh_better_ui.kernel.internal.music.IslandPlaybackShortcut;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationSource;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNotices;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService;

/** Scoped native HUD owner. Never captures gameplay input or starts media capture. */
public final class NativeMusicOverlay implements UIModule {
    private static NativeMusicOverlay active;
    private final boolean persistent;
    private ModuleContext context;
    private LocalMusicIsland island;
    private LocalMusicHud card;
    private boolean restoreIsland = true;
    private record PresentedPlayback(ClientLevel world, LocalPlayer player, OnlineMusicService.BackgroundTarget target) {}
    private PresentedPlayback presentedPlayback;
    private final MusicIslandTaskAdapter tasks = new MusicIslandTaskAdapter();
    private long ambientAt = Long.MIN_VALUE;
    private final MusicOperationNotices operationTasks = new MusicOperationNotices();
    private final com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationAdmission operationAdmission = new com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationAdmission();
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
    @Override public void stop(ModuleContext context) { if (active == this) active = null; suspend(); operationTasks.reset(); }
    private void suspend() { operationAdmission.clear(id -> { if(island!=null)island.cancelNotice(id); }); tasks.reset(); restoreIsland = true; presentedPlayback = null; if (island != null) island.suspend(); if (card != null) card.suspend(); }
    private MusicOverlaySettings settings() { return context.engine().configs().get(MusicOverlaySettings.KEY); }
    private void save(MusicOverlaySettings value) {
        var configs = context.engine().configs();
        configs.beginTransaction().set(configs.userGlobal(), MusicOverlaySettings.KEY, value).commit();
        restoreIsland = true;
    }
    private void draw(HudService.Frame frame) {
        var mc = Minecraft.getInstance();
        presentedPlayback = null;
        var value = settings();
        var source=context.engine().find(OnlineMusicService.KEY).orElse(null);
        boolean operationEnabled=value.islandEnabled() && value.taskNotices() && mc.level!=null && mc.player!=null;
        var operationUpdates=operationTasks.observe(source instanceof MusicOperationSource operations ? operations.operationsSnapshot() : java.util.List.of(),operationEnabled);
        operationAdmission.update(operationUpdates,id -> { if(island!=null)island.cancelNotice(id); });
        if(!operationEnabled)operationAdmission.clear(id -> { if(island!=null)island.cancelNotice(id); });
        if ((!value.hudEnabled() && !value.islandEnabled()) || mc.level == null || mc.player == null) { suspend(); return; }
        boolean hidden = mc.options.hideGui || mc.screen != null || mc.getOverlay() != null || mc.options.keyPlayerList.isDown();
        if (card == null) card = new LocalMusicHud(ShBetterUiApi.music());
        if (island == null) island = new LocalMusicIsland(ShBetterUiApi.music());
        var viewport = new Bounds(0, 0, frame.width(), frame.height());
        var ui = new UiFrame(frame.graphics(), viewport, new UiMouseState(-1,-1,-1,-1,false,true,false,0),
                frame.preferences(), PageTransition.nowMs(), frame.partialTick());
        if (value.hudEnabled() && !hidden) card.render(ui, mc.font,
                MusicOverlayLayout.panel(frame.width(),frame.height(),false,value.hudScale(),value.x(),value.y()));
        if (value.islandEnabled()) {
            island.setScene(true,mc.options.keyPlayerList.isDown(),hidden,mc.screen != null || mc.getOverlay() != null);
            island.setStyle(value.islandStyle());
            island.setAmbient(value.ambientEnabled() ? IslandAmbientState.Settings.DEFAULT : IslandAmbientState.Settings.DISABLED);
            if (value.ambientEnabled() && (ambientAt == Long.MIN_VALUE || ui.nowMs() < ambientAt || ui.nowMs()-ambientAt>=250)) {
                ambientAt=ui.nowMs();
                var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID());
                var time=java.time.LocalTime.now();
                island.updateAmbient(mc.getFps(), info==null?-1:info.getLatency(), time.getHour(), time.getMinute(),ui.nowMs());
            }
            if (restoreIsland) { island.setExpanded(value.expanded(), ui.nowMs(), frame.preferences().reducedMotion()); restoreIsland = false; }
            if (value.taskNotices()) {
                for (var update : tasks.observe(ShBetterUiApi.music().getFrame())) {
                    if (update.cancel()) island.cancelNotice(update.id());
                    else island.notify(new IslandNoticeQueue.Notice(update.id(),text("task."+update.messageKey()).getString(),
                            update.detail(),-1,update.ttlMs(),(update.messageKey().equals("provider_unavailable") || update.messageKey().equals("playback_failed"))?IslandNoticeQueue.Priority.HIGH:IslandNoticeQueue.Priority.NORMAL,ui.nowMs()+update.ttlMs()));
                }
            } else {
                tasks.reset(); island.cancelNotice(MusicIslandTaskAdapter.LYRICS); island.cancelNotice(MusicIslandTaskAdapter.PROVIDER); island.cancelNotice(MusicIslandTaskAdapter.PLAYBACK);
            }
            if (hidden) return;
            operationAdmission.flush(update -> island.tryNotify(new IslandNoticeQueue.Notice(update.id(),
                    text("task."+update.key()).getString(),"",-1,update.ttlMs(),
                    update.error()?IslandNoticeQueue.Priority.HIGH:IslandNoticeQueue.Priority.NORMAL,ui.nowMs()+update.ttlMs())));
            var full = MusicOverlayLayout.panel(frame.width(),frame.height(),true,value.islandScale(),value.islandX(),value.islandY());
            island.render(ui,mc.font,MusicOverlayLayout.compact(full,value.islandScale()),full,false);
            var target = island.backgroundControlTarget();
            if (target != null) presentedPlayback = new PresentedPlayback(mc.level, mc.player, target);
        } else { tasks.reset(); island.suspend(); restoreIsland=true; }
    }
    /** Explicit gameplay shortcut; never changes the saved default expansion preference. */
    public static void toggleIsland() {
        var mc = Minecraft.getInstance();
        if (!available() || mc.level == null || mc.player == null || mc.screen != null
                || mc.getOverlay() != null || mc.options.hideGui || mc.options.keyPlayerList.isDown()) return;
        if (!active.settings().islandEnabled() || active.island == null) return;
        active.presentedPlayback = null;
        active.island.setExpanded(!active.island.expanded(), PageTransition.nowMs(), UiPreferences.configured().reducedMotion());
        active.restoreIsland = false;
    }

    /** Runs synchronously for an explicitly bound keyboard press; never captures gameplay input. */
    public static void controlIslandPlayback(IslandPlaybackShortcut shortcut) {
        var mc = Minecraft.getInstance();
        if (!available() || mc.level == null || mc.player == null || !mc.isWindowActive()
                || mc.screen != null || mc.getOverlay() != null || mc.options.hideGui || mc.options.keyPlayerList.isDown()) return;
        var owner = active;
        var shown = owner.presentedPlayback;
        if (!owner.settings().islandEnabled() || owner.restoreIsland || shown == null
                || shown.world() != mc.level || shown.player() != mc.player) return;
        var service = owner.context.engine().find(OnlineMusicService.KEY).orElse(null);
        if (service != null && shortcut.dispatch(service, shown.target(),
                ShBetterUiApi.music().getFrame().snapshot(), System.nanoTime())) owner.presentedPlayback = null;
    }

    /** Reserve the expansion envelope only while a real island surface is visible. */
    static java.util.Optional<Bounds> occupiedIslandBounds(int width, int height) {
        var mc = Minecraft.getInstance();
        if (!available() || mc.level == null || mc.player == null || mc.screen != null
                || mc.getOverlay() != null || mc.options.hideGui || mc.options.keyPlayerList.isDown())
            return java.util.Optional.empty();
        var value = active.settings();
        if (!value.islandEnabled()) return java.util.Optional.empty();
        if (active.island == null) active.island = new LocalMusicIsland(ShBetterUiApi.music());
        if (active.restoreIsland) {
            active.island.setExpanded(value.expanded(), PageTransition.nowMs(), UiPreferences.configured().reducedMotion());
            active.restoreIsland = false;
        }
        active.island.setScene(true, false, false, false);
        active.island.setStyle(value.islandStyle());
        active.island.setAmbient(value.ambientEnabled() ? IslandAmbientState.Settings.DEFAULT : IslandAmbientState.Settings.DISABLED);
        if (!active.island.visible(PageTransition.nowMs())) return java.util.Optional.empty();
        return java.util.Optional.of(MusicOverlayLayout.panel(width, height, true,
                value.islandScale(), value.islandX(), value.islandY()));
    }

    public static boolean available() { return active != null && active.context.scope().isOpen(); }
    public static void open(Screen parent) { if (available()) Minecraft.getInstance().setScreen(new SettingsScreen(active,parent)); }
    /** Immutable current configuration, only while the scoped native module is active. */
    public static java.util.Optional<MusicOverlaySettings> currentSettings() {
        return available() ? java.util.Optional.of(active.settings()) : java.util.Optional.empty();
    }
    /** Applies an explicit UI edit against the latest value; does not silently save while unavailable. */
    public static boolean updateSettings(java.util.function.UnaryOperator<MusicOverlaySettings> update) {
        java.util.Objects.requireNonNull(update, "update");
        if (!available()) return false;
        active.save(java.util.Objects.requireNonNull(update.apply(active.settings()), "updated settings"));
        return true;
    }
    private static Component text(String key, Object... args) { return Component.translatable("sh_better_ui.music_overlay."+key,args); }
    private static final class SettingsScreen extends Screen {
        private final NativeMusicOverlay owner;
        private final Screen parent;
        private final MusicOverlaySettings initial;
        private MusicOverlaySettings draft;
        private UiButton apply;
        private Component error;
        SettingsScreen(NativeMusicOverlay owner, Screen parent) {
            super(text("title"));
            this.owner=owner;
            this.parent=parent;
            this.initial=owner.settings();
            this.draft=initial;
        }
        private void edit(MusicOverlaySettings value) {
            draft=value;
            error=null;
            apply.active=!draft.equals(initial);
        }
        @Override protected void init() {
            int w=Math.min(240,Math.max(40,width-16)), x=(width-w)/2, step=Math.min(24,Math.max(14,(height-66)/8)), y=26;
            addRenderableWidget(new UiButton(x,y,w,step-2,toggle("hud",draft.hudEnabled()), b->{edit(draft.withHud(!draft.hudEnabled()));b.setMessage(toggle("hud",draft.hudEnabled()));}));
            var islandButton = addRenderableWidget(new UiButton(x,y+step,w,step-2,toggle("island",draft.islandEnabled()), b->{edit(draft.withIsland(!draft.islandEnabled()));b.setMessage(toggle("island",draft.islandEnabled()));}));
            islandButton.setPreview(text("island_keys_hint"));
            addRenderableWidget(new UiButton(x,y+2*step,w,step-2,toggle("expand",draft.expanded()),b->{edit(draft.withExpanded(!draft.expanded()));b.setMessage(toggle("expand",draft.expanded()));}));
            addRenderableWidget(new UiButton(x,y+3*step,w,step-2,text("edit_hud"),b->minecraft.setScreen(new PositionScreen(this,false))));
            addRenderableWidget(new UiButton(x,y+4*step,w,step-2,text("edit_island"),b->minecraft.setScreen(new PositionScreen(this,true))));
            addRenderableWidget(new UiButton(x,y+5*step,w,step-2,toggle("ambient",draft.ambientEnabled()),b->{edit(draft.withAmbient(!draft.ambientEnabled()));b.setMessage(toggle("ambient",draft.ambientEnabled()));}));
            addRenderableWidget(new UiButton(x,y+6*step,w,step-2,toggle("task_notices",draft.taskNotices()),b->{edit(draft.withTaskNotices(!draft.taskNotices()));b.setMessage(toggle("task_notices",draft.taskNotices()));}));
            addRenderableWidget(new UiButton(x,y+7*step,w,step-2,styleLabel(),b->{edit(draft.withIslandStyle(IslandShellStyle.fromId(draft.islandStyle()).next().id()));b.setMessage(styleLabel());}));
            apply=addRenderableWidget(new UiButton(x,height-26,w/2-2,20,text("apply"), b->{
                if(draft.equals(initial))return;
                try{owner.save(draft);}catch(RuntimeException e){error=text("save_failed");return;}
                onClose();
            }));
            apply.active=!draft.equals(initial);
            addRenderableWidget(new UiButton(x+w/2+2,height-26,w/2-2,20,text("cancel"),b->onClose()));
        }
        private Component styleLabel(){return text("style").copy().append(": ").append(text("style."+draft.islandStyle()));}
        private Component toggle(String key,boolean on){return text(key).copy().append(": ").append(Component.translatable(on?"options.on":"options.off"));}
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xF018202C);g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);super.render(g,x,y,partial);
            var status=error!=null?error:text(draft.equals(initial)?"no_pending_changes":"pending_changes");
            footerStatus(g,status,error!=null?0xFFFF9999:0xFFE1E7F0);
        }
        private void footerStatus(GuiGraphics graphics, Component status, int color) {
            if(width<=8 || height<40)return;
            graphics.enableScissor(4,height-39,width-4,height-27);
            try { graphics.drawCenteredString(font,status,width/2,height-39,color); }
            finally { graphics.disableScissor(); }
        }
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
            addRenderableWidget(new UiButton(x+w+4,height-26,w,20,text("apply"),b->{
                var next=editIsland?parent.draft.withIslandPosition(xFraction,yFraction):parent.draft.withPosition(xFraction,yFraction);
                parent.edit(next.withScale(editIsland,scale));
                onClose();
            }));
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
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF18202C);g.drawCenteredString(font,text("drag"),width/2,10,0xFFFFFFFF);var frame=new UiFrame(g,new Bounds(0,0,width,height),new UiMouseState(x,y,x,y,true,false,true,0),UiPreferences.configured(),PageTransition.nowMs(),partial);if(editIsland){island.setStyle(parent.draft.islandStyle());island.setScene(true,false,false,false);island.setExpanded(true,frame.nowMs(),frame.preferences().reducedMotion());island.setHovered(bounds.contains(x,y),frame.nowMs());island.render(frame,font,MusicOverlayLayout.compact(bounds,scale),bounds,bounds.contains(x,y));}else card.render(frame,font,bounds);if(dragging||getFocused()==handle)g.renderOutline(bounds.x(),bounds.y(),bounds.width(),bounds.height(),0xFFFFFFFF);super.render(g,x,y,partial);}
        @Override public void onClose(){minecraft.setScreen(parent);}
        // NeoForge Screen.render invokes this after our opaque custom background.
        // Do not blur the already-rendered HUD preview; this signature is harmless on Forge 1.20.1.
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public boolean isPauseScreen(){return false;}
    }
}
