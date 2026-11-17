// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Searchable real installed-mod metadata, with native focusable rows and draggable scrolling. */
public final class NativeModsScreen extends Screen {
    private final Screen parent;
    private final List<InstalledModData> mods;
    private final VerticalScroll scroll=new VerticalScroll();
    private final List<MpFlatButton> rows=new ArrayList<>();
    private List<InstalledModData> filtered=List.of();
    private List<net.minecraft.util.FormattedCharSequence> details=List.of();
    private InstalledModData selected;
    private String query="";
    private boolean compact;
    private double listOffset;
    private UiVerticalScrollBar bar;
    private static final int ROW = 28;
    private int top,body,left,span,windowX,windowY,windowWidth,windowHeight;
    public NativeModsScreen(Screen parent){this(parent,PlatformUiApi.loadedMods());}
    NativeModsScreen(Screen parent,List<InstalledModData> mods){super(label("title"));this.parent=parent;this.mods=List.copyOf(mods);}
    private static Component label(String key,Object... args){return Component.translatable("sh_better_ui.native_mods."+key,args);}
    @Override protected void init(){
        rows.clear();bar=null;compact=width<220||height<190;
        if(compact){
            addRenderableWidget(new MpFlatButton(Math.min(8,width/4),Math.max(0,height-24),Math.max(1,width-2*Math.min(8,width/4)),Math.max(1,Math.min(22,height)),Component.translatable("gui.back"),b->onClose(),UiPreferences.configured(),false));
            return;
        }
        windowWidth=Math.max(120,Math.min(540,width-32));windowHeight=Math.max(150,Math.min(360,height-32));
        windowX=(width-windowWidth)/2;windowY=(height-windowHeight)/2;
        span=windowWidth-36;left=windowX+18;top=windowY+76;
        body=visibleRows(windowHeight)*ROW;
        if(selected==null){
            var search=addRenderableWidget(new UiTextField(font,new Bounds(left,windowY+44,span,22),label("search"),120,UiPreferences.configured()));
            search.setValue(query);search.setOnChange(value->{query=value;scroll.scrollTo(0);refresh();});
            for(int i=0;i<body/ROW;i++){
                final int slot=i;
                rows.add(addRenderableWidget(new MpFlatButton(left,top+i*ROW,span-14,24,Component.empty(),b->{
                    int index=(int)(scroll.offset()/ROW)+slot;
                    if(index<filtered.size()){selected=filtered.get(index);listOffset=scroll.offset();scroll.scrollTo(0);rebuildWidgets();}
                },UiPreferences.configured(),false)));
            }
        } else {
            details=font.split(MpTypography.text(selected.name()+"\n"+selected.id()+"  "+selected.version()+"\n\n"+selected.description()),Math.max(20,span-18));
        }
        bar=addRenderableWidget(new UiVerticalScrollBar(new Bounds(left+span-10,top,10,body),label("scroll"),scroll,ROW,this::updateRows));
        addRenderableWidget(new MpFlatButton(windowX+windowWidth-90,windowY+windowHeight-32,72,22,Component.translatable("gui.back"),b->onClose(),UiPreferences.configured(),false));
        refresh();
    }
    static int visibleRows(int windowHeight){return Math.max(1,(windowHeight-116)/ROW);}
    private void refresh(){
        filtered=InstalledModData.filter(mods,query);
        scroll.resize(body,selected==null?filtered.size()*ROW:details.size()*(MpTypography.LINE_HEIGHT+2));
        updateRows();
    }
    private void updateRows(){
        int first=(int)(scroll.offset()/ROW);
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int index=first+i;row.visible=index<filtered.size();row.active=row.visible;
            if(row.visible){var m=filtered.get(index);row.setMessage(Component.literal(m.name()+"  "+m.version()+"  ["+m.id()+"]"));row.displayText(Component.empty());}
        }
    }
    public boolean mouseScrolled(double x,double y,double delta){return x>=left&&x<left+span&&y>=top&&y<top+body&&bar!=null&&bar.scrollByWheel(delta);}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!compact&&(key==266||key==267)){scroll.scrollBy((key==266?-1:1)*body);updateRows();return true;}
        return super.keyPressed(key,scan,mods);
    }
    /** Shared by list and detail mode; prevent NeoForge's implicit second background blur. */
    public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        var theme=UiTheme.current();var preferences=UiPreferences.configured();
        if(compact){
            g.fill(0,0,width,height,theme.background()|0xff000000);
            if(height>40)g.drawString(font,fit(MpTypography.text(title),width-16),8,8,theme.text(),false);
            super.render(g,mx,my,partial);return;
        }
        MpPageChrome.window(g,width,height,windowX,windowY,windowWidth,windowHeight,theme,preferences);
        MpPageChrome.heading(g,font,title,left,windowY+17,MpPageChrome.foreground(theme,preferences));
        if(selected!=null){g.enableScissor(left,top,left+span-12,top+body);int y=top-(int)scroll.offset();for(var line:details){if(y+MpTypography.LINE_HEIGHT>=top&&y<top+body)g.drawString(font,line,left+2,y,MpPageChrome.foreground(theme,preferences),false);y+=MpTypography.LINE_HEIGHT+2;}g.disableScissor();}
        else if(filtered.isEmpty())g.drawString(font,MpTypography.text(label("empty")),left,top,MpPageChrome.foreground(theme,preferences),false);
        if(selected!=null)g.drawString(font,MpTypography.text(label("readonly")),left,windowY+48,MpPageChrome.foreground(theme,preferences),false);
        super.render(g,mx,my,partial);
        if(selected==null) {
            int first=(int)(scroll.offset()/ROW);
            for(int i=0;i<rows.size();i++) {
                int index=first+i;if(index>=filtered.size())break;
                var mod=filtered.get(index);var row=rows.get(i);
                int versionWidth=Math.min(86,Math.max(30,span/3));
                var name=fit(MpTypography.text(mod.name()),Math.max(1,span-versionWidth-36));
                var version=fit(MpTypography.text(mod.version()),versionWidth);
                g.drawString(font,name,left+9,row.getY()+6,MpPageChrome.foreground(theme,preferences),false);
                g.drawString(font,version,left+span-24-font.width(version),row.getY()+6,MpPageChrome.muted(theme,preferences),false);
            }
            MpTypography.draw(g,font,Component.literal(filtered.size()+" / "+mods.size()),left,windowY+windowHeight-26,MpPageChrome.muted(theme,preferences));
        }
    }
    @Override public void onClose(){if(selected!=null){selected=null;scroll.resize(body,filtered.size()*ROW);scroll.scrollTo(listOffset);rebuildWidgets();}else minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private net.minecraft.util.FormattedCharSequence fit(Component value, int available) {
        return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(value, Math.max(0, available)));
    }

}
