// Author: dishanhai. Native anchored action popover; no provider-specific fake actions.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

final class NowPlayingMenu  {
    record Entry(Component label, MusicControlRenderer.Icon icon, boolean available, Runnable action) {}
    static Bounds anchor(Bounds frame,boolean wide) {
        int inset=frame.width()>=460?24:12;
        return new Bounds(frame.right()-inset-(wide?24:84),frame.y()+6,24,24);
    }
    private final List<MusicTextControl> controls = new ArrayList<>();
    private final Bounds bounds;
    private final Component title;
    private final Runnable dismiss;
    private final BooleanSupplier valid;
    private final Consumer<Component> narrate;
    private final boolean highContrast;
    private final int quickCount;
    private int focus;
    private MusicTextControl pressed;

    static int height(int entries,boolean quickHeader){return 36+(quickHeader?60:0)+30*Math.max(0,entries-(quickHeader?3:0));}

    NowPlayingMenu(Bounds bounds, UiPreferences preferences, Component title, List<Entry> entries,
                   BooleanSupplier valid, Runnable dismiss, Consumer<Entry> choose, Consumer<Component> narrate) {
        this(bounds,preferences,title,entries,valid,dismiss,choose,narrate,true);
    }

    NowPlayingMenu(Bounds bounds, UiPreferences preferences, Component title, List<Entry> entries,
                   BooleanSupplier valid, Runnable dismiss, Consumer<Entry> choose, Consumer<Component> narrate, boolean quickHeader) {
        this.bounds=bounds; this.title=title; this.valid=valid; this.dismiss=dismiss; this.narrate=narrate;this.highContrast=preferences.highContrast();
        int y=bounds.y()+28;
        quickCount=quickHeader?Math.min(3,entries.size()):0;
        int quickWidth=(bounds.width()-16)/Math.max(1,quickCount);
        for (int i=0;i<entries.size();i++) {
            Entry entry=entries.get(i);
            var button=new MusicTextControl(i<quickCount?new Bounds(bounds.x()+8+i*quickWidth,y,Math.max(12,quickWidth),52):new Bounds(bounds.x()+8,bounds.y()+(quickCount>0?88:28)+(i-quickCount)*30,Math.max(12,bounds.width()-16),28),
                    entry.label(),preferences,()->choose.accept(entry),()->{});
            if(i<quickCount)button.setGlyphOnly(entry.icon());else button.setLeadingIcon(entry.icon()); button.setAvailable(entry.available());
            controls.add(button);
        }
        focus=-1; move(1);
    }
    public boolean animatesContents(){return true;}
    public boolean dismissesOnOutsidePress(){return true;}
    public void render(GuiGraphicsExtractor g, int mouseX,int mouseY,float partialTick){
        if(!valid.getAsBoolean()){dismiss.run();return;}
        var font=Minecraft.getInstance().font;
        g.text(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(MusicTypography.text(title),Math.max(0,bounds.width()-20))),
                bounds.x()+10,bounds.y()+9,UiTheme.current().text(),false);
        int divider=frameDividerColor();
        // A separate group for account/queue actions and search destinations.
        g.fill(bounds.x()+8,bounds.y()+25,bounds.right()-8,bounds.y()+26,divider);
        if(quickCount>0&&controls.size()>quickCount)g.fill(bounds.x()+8,bounds.y()+83,bounds.right()-8,bounds.y()+84,divider);
        for(int i=0;i<controls.size();i++){
            var control=controls.get(i);control.extractRenderState(g,mouseX,mouseY,partialTick);
            if(i<quickCount){
                var text=font.substrByWidth(control.getMessage(),Math.max(0,control.getWidth()-6));
                g.text(font,net.minecraft.locale.Language.getInstance().getVisualOrder(text),control.getX()+(control.getWidth()-font.width(text))/2,
                        control.getY()+40,control.active?UiTheme.current().text():UiTheme.current().secondaryText(0xff94a3b8,.25f),false);
            }
        }
    }
    public boolean mousePressed(double x,double y,int button,long now){
        if(!valid.getAsBoolean()){dismiss.run();return true;}
        for(int i=0;i<controls.size();i++)if(controls.get(i).active&&controls.get(i).isMouseOver(x,y)){
            setFocus(i);if(controls.get(i).mouseClicked(x,y,button))pressed=controls.get(i);return true;
        }
        return true;
    }
    public void mouseReleased(double x,double y,int button,long now){
        if(pressed!=null){var release=pressed;pressed=null;if(valid.getAsBoolean())release.mouseReleased(x,y,button);else {release.cancelInteraction();dismiss.run();}}
    }
    public boolean keyPressed(int key,int scan,int mods,long now){
        if(!valid.getAsBoolean()){dismiss.run();return true;}
        if(key==264||key==262){move(1);return true;}
        if(key==265||key==263){move(-1);return true;}
        if(key==268){focus=-1;move(1);return true;}
        if(key==269){focus=0;move(-1);return true;}
        return focus>=0&&controls.get(focus).keyPressed(key,scan,mods);
    }
    public void keyReleased(int key,int scan,int mods,long now){
        if(focus>=0){if(valid.getAsBoolean())controls.get(focus).keyReleased(key,scan,mods);else {cancel(now);dismiss.run();}}
    }
    private int frameDividerColor(){return (UiTheme.current().text()&0xffffff)|(highContrast?0xff000000:0x40000000);}
    private void move(int delta){
        for(int n=1;n<=controls.size();n++){
            int candidate=Math.floorMod(focus+delta*n,controls.size());
            if(controls.get(candidate).active){setFocus(candidate);return;}
        }
    }
    private void setFocus(int index){
        controls.forEach(c->c.setFocused(false));focus=index;controls.get(index).setFocused(true);
        narrate.accept(controls.get(index).getMessage());
    }
    public void cancel(long now){pressed=null;controls.forEach(MusicTextControl::cancelInteraction);}
    public void close(){cancel(0);}
}
