// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.util.function.Predicate;

/** Native editable preferences, isolated until Apply; embedding owners retain their own draft. */
public final class NativePreferencesScreen extends Screen {
    private final Screen parent;
    private final boolean stagedHost;
    private final DisplayPreferenceEdits base;
    private final Predicate<DisplayPreferenceEdits> commit;
    private DisplayPreferenceEdits draft;
    private boolean failed;
    /** Standalone persisted route. Changed display fields conflict-check before writing. */
    public static NativePreferencesScreen persisted(Screen parent){
        var initial=ShBetterUiApi.settings().current();var base=DisplayPreferenceEdits.from(initial);
        return new NativePreferencesScreen(parent,initial,edited->{
            var latest=ShBetterUiApi.settings().current();var current=DisplayPreferenceEdits.from(latest);
            if(edited.conflicts(base,current))return false;
            return ShBetterUiApi.settings().save(edited.merge(base,current).apply(latest));
        });
    }
    /** For ControlPanel, pass its current draft and apply edits to that draft, not global storage. */
    public NativePreferencesScreen(Screen parent,ClientUiSettings initial,Predicate<DisplayPreferenceEdits> commit){
        this(parent,initial,commit,false);
    }
    /** stagedHost explicitly labels the action as a parent-draft update rather than persistence. */
    public NativePreferencesScreen(Screen parent,ClientUiSettings initial,Predicate<DisplayPreferenceEdits> commit,boolean stagedHost){
        super(label("title"));this.parent=parent;this.stagedHost=stagedHost;this.commit=commit;base=DisplayPreferenceEdits.from(initial);draft=base;
    }
    private static Component label(String key){return Component.translatable("sh_better_ui.native_preferences."+key);}
    private Component toggle(String key,boolean enabled){return label(key).copy().append(": ").append(Component.translatable(enabled?"options.on":"options.off"));}
    @Override protected void init(){
        int w=Math.max(40,Math.min(310,width-24)),x=(width-w)/2,step=Math.min(28,Math.max(20,(height-70)/4));
        addRenderableWidget(new UiButton(x,32,w,20,toggle("contrast",draft.highContrast()),b->{draft=new DisplayPreferenceEdits(!draft.highContrast(),draft.reducedMotion(),draft.blur());b.setMessage(toggle("contrast",draft.highContrast()));}));
        addRenderableWidget(new UiButton(x,32+step,w,20,toggle("motion",draft.reducedMotion()),b->{draft=new DisplayPreferenceEdits(draft.highContrast(),!draft.reducedMotion(),draft.blur());b.setMessage(toggle("motion",draft.reducedMotion()));}));
        addRenderableWidget(new UiButton(x,32+step*2,w,20,toggle("blur",draft.blur()),b->{draft=new DisplayPreferenceEdits(draft.highContrast(),draft.reducedMotion(),!draft.blur());b.setMessage(toggle("blur",draft.blur()));}));
        addRenderableWidget(new UiButton(x,height-28,w/2-2,20,label(stagedHost?"stage":"apply"),b->{try{if(draft.equals(base)||commit.test(draft))minecraft.setScreen(parent);else failed=true;}catch(RuntimeException error){failed=true;}}));
        addRenderableWidget(new UiButton(x+w/2+2,height-28,w/2-2,20,Component.translatable("gui.cancel"),b->onClose()));
    }
    @Override public void onClose(){
        if(draft.equals(base)){minecraft.setScreen(parent);return;}
        minecraft.setScreen(new ConfirmScreen(discard->minecraft.setScreen(discard?parent:this),label("discard"),label("discard_detail")));
    }
    /** NeoForge invokes this four-argument hook; this screen already paints its own backdrop. */
    public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,draft.highContrast()?0xFF000000:0xFF18202C);g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);
        super.render(g,mx,my,partial);
        if(failed)g.drawCenteredString(font,label("failed"),width/2,height-42,0xFFFF8888);
    }
    @Override public boolean isPauseScreen(){return false;}
}
