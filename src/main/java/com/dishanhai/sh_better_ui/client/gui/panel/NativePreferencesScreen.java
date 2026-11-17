// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import java.util.ArrayList;
import java.util.List;
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
    private final VerticalScroll scroll = new VerticalScroll();
    private final List<MpFlatButton> preferenceRows = new ArrayList<>();
    private UiVerticalScrollBar scrollBar;
    private int contentLeft, contentWidth, contentHeight, firstRow, panelX, panelY, panelW, panelH, bodyY;
    private MpFlatButton undoButton;
    private static final int ROW=52;
    private DisplayPreferenceEdits undo;
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
    private static Component label(String key){return MpTypography.text(Component.translatable("sh_better_ui.native_preferences."+key));}
    private Component toggle(String key,boolean enabled){return label(key).copy().append(": ").append(Component.translatable(enabled?"options.on":"options.off"));}
    private UiPreferences preferences(){return new UiPreferences(draft.highContrast(),draft.reducedMotion(),draft.blur());}
    private MpFlatButton button(int x,int y,int w,Component label,boolean primary,Runnable action){
        var button=addRenderableWidget(new MpFlatButton(x,y,w,22,label,b->action.run(),preferences(),primary));
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));return button;
    }
    @Override protected void init(){
        preferenceRows.clear();scrollBar=null;
        panelW=Math.min(492,Math.max(180,width-40));panelH=Math.min(276,Math.max(130,height-40));
        panelX=(width-panelW)/2;panelY=(height-panelH)/2;
        if(width<260||height<200){
            button(panelX+16,panelY+panelH-34,panelW-32,Component.translatable("gui.back"),false,this::onClose);return;
        }
        contentWidth=panelW-40;contentLeft=panelX+20;bodyY=panelY+60;
        contentHeight=Math.max(ROW,Math.min(3*ROW,((panelH-104)/ROW)*ROW));
        int visible=contentHeight/ROW;scroll.resize(contentHeight,4*ROW);
        for(int i=0;i<visible;i++){
            final int slot=i;
            preferenceRows.add(button(contentLeft+contentWidth-56,bodyY+i*ROW+9,46,Component.empty(),false,()->{
                int row=firstRow+slot;if(row>=4)return;if(row==3){minecraft.setScreen(new com.dishanhai.sh_better_ui.client.font.CustomFontScreen(this));return;}undo=draft;
                draft=switch(row){
                    case 0->new DisplayPreferenceEdits(!draft.highContrast(),draft.reducedMotion(),draft.blur());
                    case 1->new DisplayPreferenceEdits(draft.highContrast(),!draft.reducedMotion(),draft.blur());
                    default->new DisplayPreferenceEdits(draft.highContrast(),draft.reducedMotion(),!draft.blur());
                };failed=false;updateRows();
            }));
        }
        scrollBar=addRenderableWidget(new UiVerticalScrollBar(new Bounds(panelX+panelW-12,bodyY,6,contentHeight-4),label("scroll"),scroll,ROW,this::updateRows));
        int footer=panelY+panelH-34;int actionW=Math.min(86,(contentWidth-18)/4);
        var resetButton=button(contentLeft,footer,actionW,label("reset"),false,()->{undo=draft;draft=new DisplayPreferenceEdits(false,false,true);failed=false;updateRows();});
        resetButton.displayText(label("reset_short"));
        undoButton=button(contentLeft+actionW+6,footer,actionW,label("undo"),false,()->{if(undo!=null){var old=draft;draft=undo;undo=old;failed=false;updateRows();}});
        undoButton.displayText(label("undo_short"));
        button(contentLeft+contentWidth-2*actionW-6,footer,actionW,Component.translatable("gui.cancel"),false,this::onClose);
        button(contentLeft+contentWidth-actionW,footer,actionW,label(stagedHost?"stage":"apply"),true,()->{try{if(draft.equals(base)||commit.test(draft))minecraft.setScreen(parent);else failed=true;}catch(RuntimeException error){failed=true;}});
        updateRows();
    }
    private void updateRows(){
        for(var child:children())if(child instanceof MpFlatButton button)button.preferences(preferences());
        firstRow=(int)(scroll.offset()/ROW);
        boolean[] values={draft.highContrast(),draft.reducedMotion(),draft.blur()};
        String[] keys={"contrast","motion","blur"};
        for(int i=0;i<preferenceRows.size();i++){
            int row=firstRow+i;var button=preferenceRows.get(i);button.visible=row<4;button.active=button.visible;
            if(row==3){button.setMessage(Component.translatable("sh_better_ui.custom_font.title"));button.displayText(Component.translatable("sh_better_ui.custom_font.open"));button.selected(false);button.setTooltip(null);continue;}
            if(button.visible){button.setMessage(toggle(keys[row],values[row]));button.displayText(MpTypography.text(Component.translatable(values[row]?"options.on":"options.off")));button.selected(values[row]);
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(toggle(keys[row],values[row])));}
        }
        if(undoButton!=null)undoButton.active=undo!=null;
    }
    public boolean mouseScrolled(double x,double y,double delta){
        return x>=contentLeft&&x<contentLeft+contentWidth&&y>=bodyY&&y<bodyY+contentHeight&&scrollBar!=null&&scrollBar.scrollByWheel(delta);
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(scrollBar!=null&&(key==266||key==267)){scroll.scrollBy((key==266?-1:1)*Math.max(ROW,contentHeight));updateRows();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void onClose(){
        if(draft.equals(base)){minecraft.setScreen(parent);return;}
        minecraft.setScreen(new ConfirmScreen(discard->minecraft.setScreen(discard?parent:this),label("discard"),label("discard_detail")));
    }
    /** NeoForge invokes this four-argument hook; this screen already paints its own backdrop. */
    public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        var theme=UiTheme.current();var prefs=preferences();int ink=MpPageChrome.foreground(theme,prefs),muted=MpPageChrome.muted(theme,prefs);
        MpPageChrome.window(g,width,height,panelX,panelY,panelW,panelH,theme,prefs);
        MpPageChrome.heading(g,font,title,panelX+20,panelY+17,ink);
        var hint=label(stagedHost?"draft_hint":"preview_hint");
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(hint,panelW-40)),panelX+20,panelY+38,muted,false);
        if(width<260||height<200){super.render(g,mx,my,partial);return;}
        String[] titles={"contrast","motion","blur","font"};String[] keys={"contrast_detail","motion_detail","blur_detail","font_detail"};
        for(int i=0;i<preferenceRows.size();i++){
            int row=firstRow+i;if(row>=4)break;int y=bodyY+i*ROW;
            MpPageChrome.row(g,contentLeft,y,contentWidth,ROW-4,theme,prefs);
            g.drawString(font,label(titles[row]),contentLeft+12,y+8,ink,false);
            var lines=font.split(label(keys[row]),Math.max(12,contentWidth-92));
            for(int n=0;n<Math.min(2,lines.size());n++)g.drawString(font,lines.get(n),contentLeft+12,y+21+n*MpTypography.LINE_HEIGHT,muted,false);
        }
        g.fill(contentLeft,panelY+panelH-43,contentLeft+contentWidth,panelY+panelH-42,theme.light()?0x10000000:0x14ffffff);
        super.render(g,mx,my,partial);
        if(failed)g.drawString(font,label("failed"),contentLeft,panelY+panelH-58,0xFFFF8888,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
