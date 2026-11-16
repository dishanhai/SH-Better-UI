// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.function.Predicate;

/** Registry-backed native Themes page. Previews are draw-scoped; the host owns commit semantics. */
public final class NativeThemesScreen extends Screen {
    private final Screen parent;
    private final Predicate<ClientUiSettings> save;
    private final NativeThemeDraft draft;
    private final boolean stagedHost;
    private MpThemeBrowser browser;
    private UiButton previous, next, apply;
    private Component status;
    private Bounds cards;
    private int retainedPage;
    private boolean fits;

    public NativeThemesScreen(Screen parent, ClientUiSettings initial, Predicate<ClientUiSettings> save) {
        this(parent,initial,save,false);
    }
    /** In staged mode Apply updates only the parent draft; its own Apply persists later. */
    public NativeThemesScreen(Screen parent, ClientUiSettings initial, Predicate<ClientUiSettings> save, boolean stagedHost) {
        super(text("title")); this.parent=parent; this.save=Objects.requireNonNull(save);
        this.stagedHost=stagedHost;
        draft=new NativeThemeDraft(initial);
        if(stagedHost)status=text("staged_hint");
    }
    private static Component text(String key,Object... args) { return Component.translatable("sh_better_ui.native_themes."+key,args); }
    private static Component common(String key) { return Component.translatable("sh_better_ui.panel."+key); }
    @Override protected void init() {
        if(browser!=null) {retainedPage=browser.pageIndex();browser.close();}
        browser=new MpThemeBrowser(MinecraftThemes.manager(),
                this::effectiveTheme,
                theme -> {draft.stage(theme);status=common("theme.staged");refreshActions();}, MinecraftThemes::isConfiguredPalette);
        int left=Math.max(8,(width-600)/2), w=Math.max(1,width-left*2);
        fits=w>=MpThemeBrowser.CARD_WIDTH && height>=226;
        cards=new Bounds(left,30,w,Math.max(0,height-128));
        if(fits) {
            // Establish page capacity before restoring the retained page after resize/editor return.
            browser.layout(cards,UiPreferences.fromSettings(draft.current())); browser.page(retainedPage);
            for(var widget:browser.layout(cards,UiPreferences.fromSettings(draft.current()))) addRenderableWidget(widget);
            int third=(w-8)/3;
            button(left,height-94,third,text("colors"),()->edit(AppearanceService.ThemeSection.COLORS));
            button(left+third+4,height-94,third,text("backgrounds"),()->edit(AppearanceService.ThemeSection.BACKGROUNDS));
            button(left+2*(third+4),height-94,w-2*(third+4),text("effects"),()->edit(AppearanceService.ThemeSection.EFFECTS));
            previous=button(left,height-68,(w-4)/2,text("previous"),()->page(-1));
            next=button(left+(w-4)/2+4,height-68,w-(w-4)/2-4,text("next"),()->page(1));
        }
        apply=button(left,height-26,(w-4)/2,common("apply"),this::apply);
        button(left+(w-4)/2+4,height-26,w-(w-4)/2-4,Component.translatable("gui.cancel"),this::onClose);
        refreshActions();
    }
    private com.dishanhai.sh_better_ui.config.ThemeSettings effectiveTheme() {
        try {
            return ControlPanelScreen.effectiveBrowserTheme(draft.current(), id -> !MinecraftThemes.isConfiguredPalette(id)
                    && MinecraftThemes.manager().registry().find(id).isPresent());
        } catch(IllegalArgumentException fullArchive) {
            status=Component.translatable("sh_better_ui.custom_theme.palette_capacity");
            return draft.current().theme();
        }
    }
    private UiButton button(int x,int y,int w,Component label,Runnable action) {
        return addRenderableWidget(new UiButton(x,y,w,20,label,b->action.run()));
    }
    private void refreshActions() {
        if(apply!=null) apply.active=fits&&draft.dirty();
        if(previous!=null)previous.active=browser.pageIndex()>0;
        if(next!=null)next.active=browser.pageIndex()+1<browser.pageCount();
    }
    private void page(int direction) {
        if(!fits||!browser.page(direction))return;
        retainedPage=browser.pageIndex(); rebuildWidgets();
        setFocused(direction>0?(next.active?next:previous):(previous.active?previous:next));
    }
    private void edit(AppearanceService.ThemeSection section) {
        retainedPage=browser.pageIndex();
        try {
            minecraft.setScreen(UnifiedUI.get().require(UIApis.APPEARANCE).editTheme(this,draft.current(),section,
                    theme->{draft.stage(theme);status=common("theme.staged");}));
        } catch(RuntimeException unavailable) { status=text("editor_unavailable"); }
    }
    private void apply() {
        try {
            boolean accepted=draft.apply(save);
            status=accepted?(stagedHost?text("staged"):common("saved")):common("save_failed");
        }
        catch(RuntimeException failure) {status=common("save_failed");}
        refreshActions();
    }
    public boolean mouseScrolled(double x,double y,double delta) {
        if(!fits||delta==0||x<cards.x()||x>=cards.right()||y<cards.y()||y>=cards.bottom())return false;
        page(delta>0?-1:1);return true;
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical) { return mouseScrolled(x,y,vertical); }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(fits&&(key==266||key==267)){page(key==266?-1:1);return true;}
        return super.keyPressed(key,scan,mods);
    }
    public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        if(browser.refresh())rebuildWidgets();
        var settings=draft.current();
        var palette=MinecraftThemes.previewPalette(settings,UiTheme.resolve(settings.theme()));
        ThemeEffects.preview(settings,()->UiTheme.preview(palette,()->{
            g.fill(0,0,width,height,palette.background()|0xFF000000);
            g.drawCenteredString(font,title,width/2,10,palette.text());
            if(!fits)g.drawCenteredString(font,Component.translatable("sh_better_ui.picker.too_small"),width/2,40,palette.text());
            super.render(g,x,y,partial);
            Component message=status!=null?status:browser.error()!=null?browser.error():text("page",browser.pageIndex()+1,browser.pageCount());
            g.drawCenteredString(font,message,width/2,height-42,palette.text());
        }));
    }
    @Override public void removed() { if(browser!=null){retainedPage=browser.pageIndex();browser.close();browser=null;} }
    @Override public void onClose() {draft.cancel();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
