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
    private final List<UiButton> rows=new ArrayList<>();
    private List<InstalledModData> filtered=List.of();
    private List<net.minecraft.util.FormattedCharSequence> details=List.of();
    private InstalledModData selected;
    private String query="";
    private double listOffset;
    private UiVerticalScrollBar bar;
    private int top=58,body,left,span;
    public NativeModsScreen(Screen parent){this(parent,PlatformUiApi.loadedMods());}
    NativeModsScreen(Screen parent,List<InstalledModData> mods){super(label("title"));this.parent=parent;this.mods=List.copyOf(mods);}
    private static Component label(String key,Object... args){return Component.translatable("sh_better_ui.native_mods."+key,args);}
    @Override protected void init(){
        rows.clear();span=Math.max(40,Math.min(600,width-24));left=(width-span)/2;
        body=Math.max(22,((height-top-38)/22)*22);
        if(selected==null){
            var search=addRenderableWidget(new UiTextField(font,new Bounds(left,30,span,20),label("search"),120,UiPreferences.configured()));
            search.setValue(query);search.setOnChange(value->{query=value;scroll.scrollTo(0);refresh();});
            for(int i=0;i<body/22;i++){
                final int slot=i;
                rows.add(addRenderableWidget(new UiButton(left,top+i*22,span-14,20,Component.empty(),b->{
                    int index=(int)(scroll.offset()/22)+slot;
                    if(index<filtered.size()){selected=filtered.get(index);listOffset=scroll.offset();scroll.scrollTo(0);rebuildWidgets();}
                })));
            }
        } else {
            details=font.split(Component.literal(selected.name()+"\n"+selected.id()+"  "+selected.version()+"\n\n"+selected.description()),Math.max(20,span-18));
        }
        bar=addRenderableWidget(new UiVerticalScrollBar(new Bounds(left+span-10,top,10,body),label("scroll"),scroll,22,this::updateRows));
        addRenderableWidget(new UiButton(left,height-28,Math.min(span,140),20,Component.translatable("gui.back"),b->onClose()));
        refresh();
    }
    private void refresh(){
        filtered=InstalledModData.filter(mods,query);
        scroll.resize(body,selected==null?filtered.size()*22:details.size()*(font.lineHeight+2));
        updateRows();
    }
    private void updateRows(){
        int first=(int)(scroll.offset()/22);
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int index=first+i;row.visible=index<filtered.size();row.active=row.visible;
            if(row.visible){var m=filtered.get(index);row.setMessage(Component.literal(m.name()+"  "+m.version()+"  ["+m.id()+"]"));}
        }
    }
    public boolean mouseScrolled(double x,double y,double delta){return y>=top&&y<top+body&&bar.scrollByWheel(delta);}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==266||key==267){scroll.scrollBy((key==266?-1:1)*body);updateRows();return true;}
        return super.keyPressed(key,scan,mods);
    }
    /** Shared by list and detail mode; prevent NeoForge's implicit second background blur. */
    public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xFF18202C);g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);
        if(selected!=null){g.enableScissor(left,top,left+span-12,top+body);int y=top-(int)scroll.offset();for(var line:details){if(y+font.lineHeight>=top&&y<top+body)g.drawString(font,line,left+2,y,0xFFFFFFFF,false);y+=font.lineHeight+2;}g.disableScissor();}
        else if(filtered.isEmpty())g.drawString(font,label("empty"),left,top,0xFFCCCCCC,false);
        if(selected!=null)g.drawString(font,label("readonly"),left,32,0xFFBBBBBB,false);
        super.render(g,mx,my,partial);
    }
    @Override public void onClose(){if(selected!=null){selected=null;scroll.resize(body,filtered.size()*22);scroll.scrollTo(listOffset);rebuildWidgets();}else minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
