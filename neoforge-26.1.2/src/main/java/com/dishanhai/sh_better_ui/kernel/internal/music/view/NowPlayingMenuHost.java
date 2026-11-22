package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.internal.PlayerMenuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 26.x counterpart of the local-modal host: one first-registered input shield and foreground pass. */
final class NowPlayingMenuHost {
    private final PreviewContext host;
    private final Shield shield;
    private UiButton source;
    private NowPlayingMenu content;
    private UiExpansionController expansion;
    private BooleanSupplier valid=()->false;
    private boolean outside, dismissAtRender;
    NowPlayingMenuHost(PreviewContext host){this.host=host;shield=host.add(new Shield(host.viewport()));shield.visible=false;}
    boolean supported(){return OnlineMusicScreen.supportsMenuForeground(host);}
    void interrupt(){
        if(content!=null)content.close();content=null;expansion=null;outside=false;dismissAtRender=false;shield.visible=false;
        if(host.screen().getFocused()==shield){host.screen().setDragging(false);host.screen().setFocused(source!=null&&source.visible?source:null);}
    }
    void open(UiButton source,Bounds viewport,Component title,List<NowPlayingMenu.Entry> entries,NowPlayingMenu.Layout menuLayout,BooleanSupplier valid){
        if(!supported())return;
        interrupt();this.source=source;this.valid=valid;
        int width=Math.min(248,viewport.width()),height=NowPlayingMenu.height(entries.size(),menuLayout);
        if(viewport.height()<height)return;
        var start=new Bounds(source.getX(),source.getY(),source.getWidth(),source.getHeight());
        var target=new Bounds(Math.max(viewport.x(),Math.min(viewport.right()-width,start.right()-width)),
                Math.max(viewport.y(),Math.min(viewport.bottom()-height,start.bottom()+6)),width,height);
        content=new NowPlayingMenu(target,host.preferences(),title,entries,valid,this::interrupt,entry->{
            boolean allowed=valid.getAsBoolean();interrupt();if(allowed)entry.action().run();
        },text->Minecraft.getInstance().getNarrator().saySystemNow(text),menuLayout);
        expansion=new UiExpansionController(start,target,UiExpansionController.Config.CENTERED_MENU,host.preferences().reducedMotion(),now());
        shield.visible=true;host.screen().setFocused(shield);
    }
    private static long now(){return System.nanoTime()/1_000_000L;}
    void render(UiFrame frame){
        if(content==null)return;
        if(dismissAtRender){interrupt();return;}
        if(!valid.getAsBoolean()||!source.visible||!Minecraft.getInstance().isWindowActive()){interrupt();return;}
        if(host.screen().getFocused()!=shield){
            if(host.screen().getFocused()!=source){interrupt();return;}
            host.screen().setFocused(shield);
        }
        expansion.advance(now());var box=expansion.frame();var target=expansion.target();
        var g=frame.graphics();g.nextStratum();
        boolean highContrast = host.preferences().highContrast();
        RoundedCorners.draw(g, box.x(), box.y(), box.width(), box.height(), PlayerMenuStyle.RADIUS,
                PlayerMenuStyle.borderWidth(highContrast), PlayerMenuStyle.border(1, highContrast),
                PlayerMenuStyle.surface(1, highContrast));
        g.nextStratum();g.enableScissor(box.x(),box.y(),box.right(),box.bottom());
        g.pose().pushMatrix();
        try{
            g.pose().translate(box.x(),box.y());g.pose().scale((float)box.width()/target.width(),(float)box.height()/target.height());g.pose().translate(-target.x(),-target.y());
            content.render(g,frame.mouseX(),frame.mouseY(),frame.partialTick());
        }finally{g.pose().popMatrix();g.disableScissor();}
    }
    private final class Shield extends AbstractWidget implements UiCancelableInteraction {
        Shield(Bounds b){super(b.x(),b.y(),b.width(),b.height(),Component.translatable("sh_better_ui.online_music.menu.title"));}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int x,int y,float partial){} // foreground only
        @Override protected void updateWidgetNarration(NarrationElementOutput out){out.add(NarratedElementType.TITLE,getMessage());}
        @Override public boolean isMouseOver(double x,double y){return visible&&super.isMouseOver(x,y);}
        @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
            if(!visible||content==null)return false;
            if(!valid.getAsBoolean()){outside=true;return true;}
            outside=!expansion.target().contains(e.x(),e.y());
            if(outside&&e.button()!=0)dismissAtRender=true;
            if(!outside&&expansion.state()==UiExpansionController.State.ACTIVE)content.mousePressed(e.x(),e.y(),e.button(),now());
            return true;
        }
        @Override public boolean mouseReleased(MouseButtonEvent e){
            if(!visible||content==null)return false;
            if(outside)interrupt();else content.mouseReleased(e.x(),e.y(),e.button(),now());return true;
        }
        @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return visible;}
        @Override public boolean keyPressed(KeyEvent e){
            if(!visible||content==null)return false;
            if(e.isEscape()||e.key()==258){interrupt();return true;}
            if(expansion.state()==UiExpansionController.State.ACTIVE)content.keyPressed(e.key(),e.scancode(),e.modifiers(),now());return true;
        }
        @Override public boolean keyReleased(KeyEvent e){if(!visible||content==null)return false;content.keyReleased(e.key(),e.scancode(),e.modifiers(),now());return true;}
        @Override public boolean cancelInteraction(){if(!visible)return false;interrupt();return true;}
    }
}
