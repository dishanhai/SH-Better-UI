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
    private MpFlatButton previous, next, apply, undo;
    private int panelX,panelY,panelW,panelH;
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
    private static Component text(String key,Object... args) { return MpTypography.text(Component.translatable("sh_better_ui.native_themes."+key,args)); }
    private static Component common(String key) { return MpTypography.text(Component.translatable("sh_better_ui.panel."+key)); }
    @Override protected void init() {
        if(browser!=null) {retainedPage=browser.pageIndex();browser.close();}
        browser=new MpThemeBrowser(MinecraftThemes.manager(),
                this::effectiveTheme,
                theme -> {draft.stage(theme);status=common("theme.staged");refreshActions();}, MinecraftThemes::isConfiguredPalette);
        panelW=Math.min(620,Math.max(200,width-40));panelH=Math.min(330,Math.max(180,height-40));
        panelX=(width-panelW)/2;panelY=(height-panelH)/2;
        int left=panelX+20,w=panelW-40;
        fits=w>=MpThemeBrowser.CARD_WIDTH && panelH>=MpThemeBrowser.CARD_HEIGHT+142;
        cards=new Bounds(left,panelY+56,w,Math.max(0,panelH-142));
        previous=next=null;
        if(fits) {
            browser.layout(cards,UiPreferences.fromSettings(draft.current())); browser.page(retainedPage);
            for(var widget:browser.layout(cards,UiPreferences.fromSettings(draft.current()))) addRenderableWidget(widget);
            int item=Math.min(80,(w-62)/3);
            int toolsY=panelY+panelH-78;
            button(left,toolsY,item,text("colors"),()->edit(AppearanceService.ThemeSection.COLORS));
            button(left+item+6,toolsY,item,text("backgrounds"),()->edit(AppearanceService.ThemeSection.BACKGROUNDS));
            button(left+2*(item+6),toolsY,item,text("effects"),()->edit(AppearanceService.ThemeSection.EFFECTS));
            previous=button(left+w-50,toolsY,22,text("previous"),()->page(-1));
            previous.displayText(Component.literal("‹"));
            previous.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("previous")));
            next=button(left+w-22,toolsY,22,text("next"),()->page(1));
            next.displayText(Component.literal("›"));
            next.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("next")));
        }
        int footer=panelY+panelH-34,action=Math.min(72,(w-18)/4);
        undo=button(left,footer,action,text("undo"),()->{if(draft.undo()){status=text("undone");rebuildWidgets();}});
        button(left+action+6,footer,action,text("preview"),()->minecraft.setScreen(new DisplaySuitePreviewScreen(this,draft.current())));
        button(left+w-2*action-6,footer,action,Component.translatable("gui.cancel"),this::onClose);
        apply=button(left+w-action,footer,action,common("apply"),this::apply);
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
    private MpFlatButton button(int x,int y,int w,Component label,Runnable action) {
        var button=addRenderableWidget(new MpFlatButton(x,y,w,22,label,b->action.run(),UiPreferences.fromSettings(draft.current()),label.equals(common("apply"))));
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));return button;
    }
    private void refreshActions() {
        if(apply!=null) apply.active=draft.dirty();
        if(undo!=null) undo.active=draft.canUndo();
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
            var prefs=UiPreferences.fromSettings(settings);
            int ink=MpPageChrome.foreground(palette,prefs),muted=MpPageChrome.muted(palette,prefs);
            MpPageChrome.window(g,width,height,panelX,panelY,panelW,panelH,palette,prefs);
            MpPageChrome.heading(g,font,title,panelX+20,panelY+17,ink);
            Component message=status!=null?status:browser.error()!=null?browser.error():text("page",browser.pageIndex()+1,browser.pageCount());
            g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(MpTypography.text(message),panelW-40)),panelX+20,panelY+38,muted,false);
            if(!fits)g.drawString(font,MpTypography.text(Component.translatable("sh_better_ui.picker.too_small")),panelX+20,panelY+64,ink,false);
            g.fill(panelX+20,panelY+panelH-43,panelX+panelW-20,panelY+panelH-42,palette.light()?0x10000000:0x14ffffff);
            super.render(g,x,y,partial);
        }));
    }
    @Override public void removed() { if(browser!=null){retainedPage=browser.pageIndex();browser.close();browser=null;} }
    @Override public void onClose() {
        if (!draft.dirty()) {draft.cancel();minecraft.setScreen(parent);return;}
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(discard->{
            if(discard){draft.cancel();minecraft.setScreen(parent);}else minecraft.setScreen(this);
        },text("discard"),text("discard_detail")));
    }
    @Override public boolean isPauseScreen() {return false;}
}
