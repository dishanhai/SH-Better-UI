// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;

import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Discovered from Preferences; local files never leave the player's computer. */
public final class CustomFontScreen extends Screen {
    private final Screen parent;
    private EditBox path;
    private String selectedPath="";
    private Component status=label("hint");
    private boolean busy, applying;
    private AtomicBoolean cancelled=new AtomicBoolean();
    private int x,y,w;
    public CustomFontScreen(Screen parent) { super(label("title")); this.parent=parent; }
    private static Component label(String key) { return Component.translatable("sh_better_ui.custom_font."+key); }
    @Override protected void init() {
        w=Math.min(440, Math.max(180,width-32));x=(width-w)/2;y=Math.max(8,(height-244)/2);
        if(width<280||height<256){button(Math.max(40,height-32),busy?label("cancel"):Component.translatable("gui.back"),this::onClose).active=!applying;return;}
        path=addRenderableWidget(new EditBox(font,x+12,y+51,w-24,20,label("path")));
        path.setMaxLength(2048);path.setValue(selectedPath);path.setResponder(value->selectedPath=value);path.setEditable(!busy);
        button(y+77,label("import"),()->confirm(false,-1));
        for(int i=0;i<LocalFontPack.SOURCES.size();i++) { int source=i;
            button(y+105+i*25,Component.literal(LocalFontPack.SOURCES.get(i).name()),()->confirm(true,source));
        }
        button(y+159,label("restore"),()->{if(!busy)apply(null);});
        button(y+211,busy?label("cancel"):Component.translatable("gui.back"),()->{
            if(busy&&!applying){cancelled.set(true);status=label("cancelling");} else onClose();
        }).active=!applying;
        // Reading font paths, network and zip compression never run on the render thread.
    }
    private MpFlatButton button(int top,Component text,Runnable action) {
        var button=addRenderableWidget(new MpFlatButton(x+12,top,w-24,22,text,b->action.run(),UiPreferences.configured(),false));
        button.active=!busy || top==y+211;return button;
    }
    private void confirm(boolean remote,int source) {
        if(busy)return;
        Component detail=remote?Component.literal(LocalFontPack.SOURCES.get(source).name()+" · SIL OFL 1.1 · raw.githubusercontent.com\n").append(label("download_detail")):label("license_detail");
        minecraft.setScreen(new ConfirmScreen(accepted->{minecraft.setScreen(this);if(accepted)start(remote,source);},label(remote?"download":"import"),detail));
    }
    private void start(boolean remote,int source) {
        if(busy)return;busy=true;cancelled=new AtomicBoolean();var token=cancelled;status=label("working");rebuildWidgets();
        Path directory=minecraft.gameDirectory.toPath().resolve("resourcepacks");
        int format=SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        String local=selectedPath;
        CompletableFuture.supplyAsync(()->{
            try {
                if(remote)return LocalFontPack.fetch(LocalFontPack.SOURCES.get(source),directory,format,token::get);
                return LocalFontPack.create(LocalFontPack.read(Path.of(local)),directory,format,
                    "User-provided font. No redistribution permission is granted by SHBUI.".getBytes(StandardCharsets.UTF_8),token::get);
            } catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
        }).whenComplete((pack,error)->minecraft.execute(()->{
            if(token.get()){busy=false;status=label("cancelled");rebuildWidgets();return;}
            if(error!=null){busy=false;status=label("failed");rebuildWidgets();return;}
            apply(pack);
        }));
    }
    private void apply(Path pack) {
        busy=true;applying=true;status=label("reloading");rebuildWidgets();
        var repo=minecraft.getResourcePackRepository();
        var previous=new ArrayList<>(repo.getSelectedIds());
        try {
            repo.reload();var selected=new ArrayList<>(previous);
            selected.removeIf(id->id.startsWith("file/"+LocalFontPack.PREFIX));
            if(pack!=null) {
                String id="file/"+pack.getFileName();
                if(repo.getPack(id)==null)throw new IllegalStateException("Generated pack not discovered");
                selected.add(id);
            }
            repo.setSelected(selected);
            minecraft.reloadResourcePacks().whenComplete((unused,error)->minecraft.execute(()->{
                if(error==null){savePackSelection();finish(label(pack==null?"restored":"applied"));}
                else rollback(previous);
            }));
        } catch(RuntimeException error) { rollback(previous); }
    }
    private void rollback(List<String> previous) {
        var repo=minecraft.getResourcePackRepository();repo.setSelected(previous);
        minecraft.reloadResourcePacks().whenComplete((unused,error)->minecraft.execute(()->{
            savePackSelection();finish(label(error==null?"failed":"recovery"));
        }));
    }
    /** Persist the completed reload without Options.updateResourcePacks starting a second one. */
    private void savePackSelection() {
        var options=minecraft.options;
        options.resourcePacks.clear();
        options.incompatibleResourcePacks.clear();
        for(var pack:minecraft.getResourcePackRepository().getSelectedPacks()) {
            if(!pack.isFixedPosition()&&!pack.isHidden()) {
                options.resourcePacks.add(pack.getId());
                if(!pack.getCompatibility().isCompatible())options.incompatibleResourcePacks.add(pack.getId());
            }
        }
        options.save();
    }
    private void finish(Component message){busy=false;applying=false;status=message;rebuildWidgets();}
    @Override public void onFilesDrop(List<Path> paths) {
        if(!busy&&path!=null&&paths.size()==1){selectedPath=paths.get(0).toString();path.setValue(selectedPath);status=label("confirm_file");}
    }
    @Override public void removed(){if(busy&&!applying)cancelled.set(true);}
    @Override public void onClose(){if(!busy)minecraft.setScreen(parent);else if(!applying){cancelled.set(true);status=label("cancelling");}}
    public void renderBackground(GuiGraphics g,int mx,int my,float partial) {}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        var theme=UiTheme.current();var prefs=UiPreferences.configured();
        MpPageChrome.window(g,width,height,x,y,w,240,theme,prefs);
        int ink=MpPageChrome.foreground(theme,prefs);
        MpPageChrome.heading(g,font,title,x+12,y+12,ink);
        if(width<280||height<256){g.drawString(font,label("small"),x+12,y+32,ink,false);super.render(g,mx,my,partial);return;}
        g.drawString(font,label("path"),x+12,y+35,ink,false);
        var lines=font.split(MpTypography.text(status),w-24);
        for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x+12,y+185+i*10,ink,false);
        super.render(g,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
}
