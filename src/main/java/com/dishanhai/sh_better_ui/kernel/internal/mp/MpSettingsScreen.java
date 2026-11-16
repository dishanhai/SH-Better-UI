// Author: dishanhai. Native optional MP settings browser; MP owns all business operations.
package com.dishanhai.sh_better_ui.kernel.internal.mp;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Full runtime model browser. Paging bounds widget count; no snapshots or network calls on opening. */
public final class MpSettingsScreen extends Screen {
    private final Screen parent;
    private final UiPreferences preferences = UiPreferences.configured();
    private final UiDotBackground background = new UiDotBackground();
    private MpSettingsBridge bridge;
    private String error = "", query = "";
    private int page, pageSize, category, totalPages=1;
    private UiButton previous,next,categoryButton,hudButton,fullButton;
    private UiTextField searchField;
    private MpSettingsBridge.Entry pendingFocusEntry;
    private String visibility="", pendingFocusSlot="";
    private final List<Row> rendered = new ArrayList<>();
    private record Row(String heading, MpSettingsBridge.Entry entry, int y, UiButton button) {}
    public static void open(Screen parent) { Minecraft.getInstance().setScreen(new MpSettingsScreen(parent)); }
    public MpSettingsScreen(Screen parent) {
        super(text(tr("title"))); this.parent = parent;
        try { bridge = new MpSettingsBridge(getClass().getClassLoader()); }
        catch (RuntimeException | LinkageError failure) { error = failure.getMessage(); }
    }
    private static String tr(String key) { return Component.translatable("sh_better_ui.mp_bridge."+key).getString(); }
    private static Component text(String text) { return Component.literal(text); }
    private UiButton button(int x, int y, int w, String label, Runnable action) {
        return addRenderableWidget(new UiButton(x,y,Math.max(20,w),20,text(label), b -> safely(action), preferences,false));
    }
    private void safely(Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError failure) { error = tr("failed") + failure.getMessage(); }
    }
    @Override protected void init() {
        String focusSlot=!pendingFocusSlot.isEmpty()?pendingFocusSlot:getFocused()==null?"":getFocused()==previous?"previous":getFocused()==next?"next":getFocused()==categoryButton?"category":getFocused()==hudButton?"hud":getFocused()==fullButton?"full":getFocused()==searchField?"search":"";
        pendingFocusSlot="";
        for(Row row:rendered)if(row.button()==getFocused())pendingFocusEntry=row.entry();
        rendered.clear(); background.reset();
        try { initBrowser();
            var target=switch(focusSlot){case "previous"->previous;case "next"->next;case "category"->categoryButton;case "hud"->hudButton;case "full"->fullButton;default->null;};
            if(target!=null&&target.active)setFocused(target);else if(!focusSlot.isEmpty()&&searchField!=null)setFocused(searchField);
        } catch(RuntimeException|LinkageError failure) { error=tr("read_failed")+failure.getMessage(); }
    }
    private void initBrowser() {
        button(12,height-28,78,tr("back"),this::onClose);
        if (bridge == null || width < 260 || height < 180) return;
        var search = addRenderableWidget(new UiTextField(font,new Bounds(12,36,Math.max(80,width-24),20),text(tr("search")),120,preferences));
        searchField=search;
        search.setValue(query); search.setHint(text(tr("search_hint")));
        search.setOnChange(v -> safely(()->{ query=v; page=0; rebuildRows(); }));
        categoryButton=button(12,64,Math.min(200,width-124),bridge.categories().isEmpty()?tr("empty"):bridge.categories().get(category).title(),()->{
            category=(category+1)%Math.max(1,bridge.categories().size()); page=0; rebuildPreservingFocus();
        });
        hudButton=button(width-106,64,94,tr("hud"),()->{
            Object nativeScreen=bridge.newHudEditor();
            if (!(nativeScreen instanceof Screen screen)) throw new IllegalStateException(tr("hud_incompatible"));
            UiConfirmation.open(this,new UiConfirmation.Prompt(text(tr("hud")),text(tr("hud_return")),text(tr("continue")),text(tr("cancel"))),preferences,null,()->null,()->minecraft.setScreen(screen));
        });
        previous=button(width-156,height-28,68,tr("previous"),()->{if(page>0){page--;rebuildPreservingFocus();}});
        next=button(width-82,height-28,70,tr("next"),()->{page++;rebuildPreservingFocus();});
        fullButton=button(12,88,width-24,tr("full_panel"),bridge::openUpstreamPanel);
        rebuildRows();
    }
    private void rebuildPreservingFocus(){
        pendingFocusSlot=getFocused()==null?"":getFocused()==previous?"previous":getFocused()==next?"next":getFocused()==categoryButton?"category":getFocused()==hudButton?"hud":getFocused()==fullButton?"full":getFocused()==searchField?"search":"";
        for(Row row:rendered)if(row.button()==getFocused())pendingFocusEntry=row.entry();
        rebuildWidgets();
    }
    private void rebuildRows() {
        MpSettingsBridge.Entry focused=pendingFocusEntry;pendingFocusEntry=null;
        boolean removedFocus=false;
        for(Row row:rendered){if(getFocused()==row.button()){focused=row.entry();removedFocus=true;setFocused(null);}row.button().active=false;row.button().visible=false;removeWidget(row.button());} rendered.clear();
        if(bridge==null) return;
        pageSize=Math.max(1,(height-154)/42);
        List<Row> matching=new ArrayList<>();
        String q=query.toLowerCase(Locale.ROOT).trim();
        for(int c=0;c<bridge.categories().size();c++) {
            var cat=bridge.categories().get(c);
            if(q.isEmpty()&&c!=category) continue;
            for(var group:cat.groups()) for(var entry:group.entries()) {
                if(!entry.visible()) continue;
                String heading=cat.title()+" / "+group.title();
                if(!q.isEmpty()&&!(heading+" "+entry.label()+" "+entry.description()).toLowerCase(Locale.ROOT).contains(q)) continue;
                matching.add(new Row(heading,entry,0,null));
            }
        }
        totalPages=Math.max(1,(matching.size()+pageSize-1)/pageSize);
        page=Math.min(page,totalPages-1);
        if(previous!=null)previous.active=page>0; if(next!=null)next.active=page+1<totalPages;
        for(int i=page*pageSize;i<Math.min(matching.size(),(page+1)*pageSize);i++) {
            Row row=matching.get(i); int y=118+(i-page*pageSize)*42;
            UiButton control=button(12,y+12,width-24,row.entry().label(),()->{if(row.entry().visible()&&!row.entry().locked())minecraft.setScreen(new Edit(this,row.entry()));});
            control.active=!row.entry().locked();
            control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text(row.entry().description())));
            rendered.add(new Row(row.heading(),row.entry(),y,control));
        }
        visibility=visibilityState();
        if(focused!=null||removedFocus){
            net.minecraft.client.gui.components.events.GuiEventListener target=null;
            for(Row row:rendered)if(row.entry()==focused&&row.button().active){target=row.button();break;}
            if(target==null&&searchField!=null&&children().contains(searchField))target=searchField;
            if(target==null)for(Row row:rendered)if(row.button().active){target=row.button();break;}
            setFocused(target);
        }
    }
    private String visibilityState() {
        if(bridge==null)return "";
        StringBuilder state=new StringBuilder();
        for(var category:bridge.categories())for(var group:category.groups())for(var entry:group.entries())state.append(entry.visible()?'1':'0');
        return state.toString();
    }
    @Override public void tick() {
        super.tick();
        // Refresh actual lock/state without rebuilding focus every frame.
        safely(()->{String state=visibilityState();if(!state.equals(visibility)){visibility=state;rebuildRows();}for(Row row:rendered){var e=row.entry(); row.button().active=e.visible()&&!e.locked();
            String value=switch(e.kind()){case TOGGLE -> e.on()?tr("on"):tr("off"); case CHOICE -> e.valueText(); case SLIDER -> String.format(Locale.ROOT,"%.2f",e.value()); case COLOR -> String.format("#%06X",e.color()&0xffffff); case THEME_GRID -> bridge.currentTheme(); case ACTION -> tr("open");};
            row.button().setMessage(text(e.label()+" · "+value+(e.locked()?tr("locked"):"")));}});
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta) {
        background.renderThemed(this,g,width,height,preferences,false);
        g.drawString(font,title,12,8,UiTheme.current().text());g.drawString(font,font.plainSubstrByWidth(tr("persistence"),width-24),12,22,UiTheme.current().text());
        for(Row row:rendered) g.drawString(font,font.plainSubstrByWidth(row.heading(),width-24),12,row.y(),UiTheme.current().text());
        if(bridge==null) g.drawWordWrap(font,text(tr("missing")),12,44,Math.max(40,width-24),UiTheme.current().text());
        if(!error.isEmpty()) g.drawWordWrap(font,text(error),12,height-62,Math.max(40,width-24),UiTheme.current().text());
        g.drawString(font,(page+1)+" / "+totalPages,96,height-22,UiTheme.current().text());
        super.render(g,x,y,delta);
    }
    public void renderBackground(GuiGraphics g,int x,int y,float delta) {}
    @Override public void removed(){ background.reset(); super.removed(); }
    @Override public void onClose(){minecraft.setScreen(parent);}

    /** Edits remain local until Apply. Escape/back never writes even after pointer release. */
    static final class Edit extends Screen {
        final MpSettingsScreen owner; final MpSettingsBridge.Entry entry;
        float draft; int color; String choice; boolean initialized;
        int optionPage; String error=""; UiColorEditor colorEditor; UiButton applyButton; boolean completed; int descriptionScroll;
        java.util.function.BiConsumer<UiConfirmation.Prompt,java.util.function.Consumer<Boolean>> confirmationPresenter;
        Edit(MpSettingsScreen owner,MpSettingsBridge.Entry entry){super(text(entry.label()));this.owner=owner;this.entry=entry;}
        UiButton button(int x,int y,int w,String label,Runnable action){return addRenderableWidget(new UiButton(x,y,Math.max(20,w),20,text(label),b->{try{action.run();}catch(RuntimeException|LinkageError ex){error=tr("failed")+ex.getMessage();}},owner.preferences,false));}
        @Override protected void init(){
            try { initEditor(); } catch(RuntimeException|LinkageError ex) { clearWidgets(); button(12,height-28,90,tr("back"),this::onClose); error=tr("read_failed")+ex.getMessage(); }
        }
        private void initEditor(){
            if(colorEditor!=null){colorEditor.cancelInteraction();colorEditor=null;}
            if(!initialized){draft=entry.kind()==MpSettingsBridge.Kind.SLIDER?entry.value():0;color=entry.kind()==MpSettingsBridge.Kind.COLOR?entry.color():0;choice=entry.kind()==MpSettingsBridge.Kind.THEME_GRID?owner.bridge.currentTheme():entry.valueText();initialized=true;}
            button(12,height-28,90,tr("cancel_back"),this::onClose);
            if(width<260||height<180)return;
            switch(entry.kind()) {
                case SLIDER -> {
                    float min=entry.min(), max=entry.max();
                    if(!(Float.isFinite(min)&&Float.isFinite(max)&&max>min)) throw new IllegalStateException("Invalid MP slider range");
                    addRenderableWidget(new UiSlider(new Bounds(12,90,width-24,24),0,10000,1,Math.round((draft-min)/(max-min)*10000),owner.preferences,v->text(String.format(Locale.ROOT,"%.3f",min+(max-min)*v/10000f)),v->draft=min+(max-min)*v/10000f));
                    apply(()->entry.value(draft));
                }
                case COLOR -> {
                    var field=addRenderableWidget(new UiTextField(font,new Bounds(12,90,width-24,22),text(tr("color")),7,owner.preferences));
                    field.setValue(String.format("#%06X",color&0xffffff));
                    field.setOnChange(s->{try{if(!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException();color=0xff000000|Integer.parseInt(s.substring(1),16);if(colorEditor!=null)colorEditor.setValue(color);error="";}catch(RuntimeException e){error=tr("hex");}});
                    if(height>=300)mountColor(12,118,width-24,rgb->field.setValue(String.format("#%06X",rgb&0xffffff)));
                    apply(this::acceptColor);
                }
                case CHOICE,THEME_GRID -> {
                    List<String> options=entry.kind()==MpSettingsBridge.Kind.CHOICE?entry.choices():owner.bridge.themes();
                    int count=Math.max(1,(height-178)/24), pages=Math.max(1,(options.size()+count-1)/count);
                    optionPage=Math.min(optionPage,pages-1);
                    for(int i=optionPage*count;i<Math.min(options.size(),(optionPage+1)*count);i++){String option=options.get(i);button(12,90+(i-optionPage*count)*24,width-24,(option.equals(choice)?"✓ ":"")+option,()->{choice=option;rebuildWidgets();});}
                    if(options.isEmpty())button(12,90,width-24,tr("cycle"),()->confirm(entry::activate));
                    if(pages>1){var prev=button(12,height-54,Math.max(65,(width-36)/2),tr("previous")+" "+(optionPage+1)+" / "+pages,()->{optionPage--;rebuildWidgets();});prev.active=optionPage>0;var next=button(width/2+4,height-54,width/2-16,tr("next"),()->{optionPage++;rebuildWidgets();});next.active=optionPage+1<pages;}
                    if(!options.isEmpty()){if(entry.kind()==MpSettingsBridge.Kind.CHOICE)mountChoiceApply(width-100,height-28,88);else apply(()->{if(entry.locked()||!entry.visible())throw new IllegalStateException("Setting locked");owner.bridge.theme(choice);});}
                }
                case TOGGLE -> button(12,90,width-24,entry.on()?tr("off"):tr("on"),()->confirm(entry::activate));
                case ACTION -> button(12,90,width-24,tr("confirm"),()->confirm(entry::activate));
            }
        }
        void mountColor(int x,int y,int width,java.util.function.IntConsumer update) {
            colorEditor=addRenderableWidget(new UiColorEditor(new Bounds(x,y,width,UiColorEditor.RGB_MIN_HEIGHT),title,color,color,false,owner.preferences,rgb->{},rgb->{color=rgb;update.accept(rgb);error="";}));
        }
        void acceptColor() {
            if(completed)return;
            if(colorEditor!=null)colorEditor.commitInteraction();
            if(!error.isEmpty())throw new IllegalArgumentException(tr("invalid_color"));
            entry.color(color); completed=true;
        }
        @Override public boolean mouseClicked(double x,double y,int button) {
            if(colorEditor!=null&&button==0&&applyButton!=null&&applyButton.active&&applyButton.isMouseOver(x,y))colorEditor.commitInteraction();
            else if(colorEditor!=null&&!colorEditor.isMouseOver(x,y))colorEditor.cancelInteraction();
            return super.mouseClicked(x,y,button);
        }
        @Override public boolean mouseReleased(double x,double y,int button) {
            if(colorEditor!=null&&colorEditor.releasePointer(x,y,button)){setDragging(false);return true;}
            return super.mouseReleased(x,y,button);
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN&&scrollDescription(3))return true;
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP&&scrollDescription(-3))return true;
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE&&colorEditor!=null&&colorEditor.isEditing()){colorEditor.cancelInteraction();setDragging(false);return true;}
            return super.keyPressed(key,scan,modifiers);
        }
        void apply(Runnable write){applyButton=button(width-100,height-28,88,tr("apply"),()->{write.run();minecraft.setScreen(owner);});}
        void mountChoiceApply(int x,int y,int w){
            applyButton=button(x,y,w,tr("apply"),this::requestChoice);
            applyButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text(entry.description())));
        }
        void requestChoice(){
            final String selected=choice;
            int index=entry.choices().indexOf(selected);
            if(index<0)throw new IllegalArgumentException("Unknown MP option");
            var hints=entry.choiceHints();
            String hint=index<hints.size()?hints.get(index):"";
            String details=entry.description()+"\n\n"+Component.translatable("sh_better_ui.mp_bridge.selection",entry.valueText(),selected).getString()+"\n"+hint+tr("warning");
            confirm(details,()->entry.choice(selected));
        }
        void confirm(Runnable action){confirm(entry.description()+tr("warning"),action);}
        void confirm(String details,Runnable action){
            var prompt=new UiConfirmation.Prompt(title,text(details),text(tr("execute")),text(tr("cancel")));
            java.util.function.Consumer<Boolean> response=new java.util.function.Consumer<>() {
                boolean resolved;
                @Override public void accept(Boolean accepted){
                    if(resolved)return;resolved=true;
                    if(!Boolean.TRUE.equals(accepted))return;
                    try{action.run();if(minecraft!=null&&minecraft.screen==Edit.this)minecraft.setScreen(owner);}
                    catch(RuntimeException|LinkageError ex){error=tr("failed")+ex.getMessage();}
                }
            };
            if(confirmationPresenter!=null)confirmationPresenter.accept(prompt,response);
            else UiConfirmation.open(this,prompt,owner.preferences,null,()->applyButton,()->response.accept(true));
        }
        private boolean scrollDescription(int delta){
            if(font==null)return false;
            int rows=Math.max(1,40/font.lineHeight),max=Math.max(0,font.split(text(entry.description()),Math.max(40,width-24)).size()-rows);
            int next=Math.max(0,Math.min(max,descriptionScroll+delta));boolean changed=next!=descriptionScroll;descriptionScroll=next;return changed;
        }
        public boolean mouseScrolled(double x,double y,double delta){return y>=35&&y<=78&&delta!=0&&scrollDescription(delta>0?-1:1);}
        public boolean mouseScrolled(double x,double y,double dx,double dy){return mouseScrolled(x,y,dy);}
        @Override public void render(GuiGraphics g,int x,int y,float delta){owner.background.renderThemed(this,g,width,height,owner.preferences,false);g.drawString(font,title,12,12,UiTheme.current().text());int lineY=35; var lines=font.split(text(entry.description()),Math.max(40,width-24));for(int i=descriptionScroll;i<lines.size()&&lineY<=74;i++){g.drawString(font,lines.get(i),12,lineY,UiTheme.current().text());lineY+=font.lineHeight;}if(lines.size()>40/font.lineHeight)g.drawString(font,text(tr("scroll_description")),12,78,UiTheme.current().text());if(!error.isEmpty())g.drawWordWrap(font,text(error),12,height-62,Math.max(40,width-24),UiTheme.current().text());super.render(g,x,y,delta);}
        public void renderBackground(GuiGraphics g,int x,int y,float delta){}
        @Override public void removed(){if(colorEditor!=null)colorEditor.cancelInteraction();owner.background.reset();super.removed();}
        @Override public void onClose(){minecraft.setScreen(owner);}
    }
}
