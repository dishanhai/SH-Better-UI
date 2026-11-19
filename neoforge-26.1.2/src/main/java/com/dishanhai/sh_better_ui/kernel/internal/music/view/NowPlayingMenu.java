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
    enum Layout { QUICK_ACTIONS, LIST, EFFECT_TILES }
    record Entry(Component label, MusicControlRenderer.Icon icon, boolean available, Runnable action, boolean selected) {
        Entry(Component label, MusicControlRenderer.Icon icon, boolean available, Runnable action) {
            this(label, icon, available, action, false);
        }
    }
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
    private final Layout layout;
    private final MenuContentReveal reveal=new MenuContentReveal();
    private final boolean still;
    private int focus;
    private MusicTextControl pressed;

    static int height(int entries,boolean quickHeader) {
        return height(entries,quickHeader?Layout.QUICK_ACTIONS:Layout.LIST);
    }
    static int height(int entries,Layout layout) {
        return switch(layout) {
            case QUICK_ACTIONS -> 96+30*Math.max(0,entries-3);
            case LIST -> 36+30*entries;
            case EFFECT_TILES -> 36+34*((entries+1)/2);
        };
    }
    private static Bounds effectTile(Bounds frame,int index) {
        int usable=frame.width()-22;
        int split=usable/2;
        return new Bounds(frame.x()+8+(index%2==0?0:split+6),frame.y()+28+(index/2)*34,
                index%2==0?split:usable-split,30);
    }

    NowPlayingMenu(Bounds bounds, UiPreferences preferences, Component title, List<Entry> entries,
                   BooleanSupplier valid, Runnable dismiss, Consumer<Entry> choose, Consumer<Component> narrate) {
        this(bounds,preferences,title,entries,valid,dismiss,choose,narrate,Layout.QUICK_ACTIONS);
    }

    NowPlayingMenu(Bounds bounds, UiPreferences preferences, Component title, List<Entry> entries,
                   BooleanSupplier valid, Runnable dismiss, Consumer<Entry> choose, Consumer<Component> narrate, boolean quickHeader) {
        this(bounds,preferences,title,entries,valid,dismiss,choose,narrate,quickHeader?Layout.QUICK_ACTIONS:Layout.LIST);
    }

    NowPlayingMenu(Bounds bounds, UiPreferences preferences, Component title, List<Entry> entries,
                   BooleanSupplier valid, Runnable dismiss, Consumer<Entry> choose, Consumer<Component> narrate, Layout layout) {
        this.layout=layout;
        this.still=preferences.reducedMotion()||preferences.highContrast();
        this.bounds=bounds; this.title=title; this.valid=valid; this.dismiss=dismiss; this.narrate=narrate;this.highContrast=preferences.highContrast();
        int y=bounds.y()+28;
        quickCount=layout==Layout.QUICK_ACTIONS?Math.min(3,entries.size()):0;
        int quickWidth=(bounds.width()-16)/Math.max(1,quickCount);
        int selected=-1;
        for (int i=0;i<entries.size();i++) {
            Entry entry=entries.get(i);
            Bounds box=layout==Layout.EFFECT_TILES?effectTile(bounds,i):i<quickCount
                    ?new Bounds(bounds.x()+8+i*quickWidth,y,Math.max(12,quickWidth),52)
                    :new Bounds(bounds.x()+8,bounds.y()+(quickCount>0?88:28)+(i-quickCount)*30,Math.max(12,bounds.width()-16),28);
            var button=new MusicTextControl(box,entry.label(),preferences,()->choose.accept(entry),()->{});
            button.setSelected(entry.selected());
            if(entry.selected()&&entry.available())selected=i;
            if(i<quickCount)button.setGlyphOnly(entry.icon());else button.setLeadingIcon(entry.icon()); button.setAvailable(entry.available());
            controls.add(button);
        }
        focus=-1; if(selected>=0)setFocus(selected);else move(1);
    }
    public boolean animatesContents(){return true;}
    public boolean dismissesOnOutsidePress(){return true;}
    public void render(GuiGraphicsExtractor g, int mouseX,int mouseY,float partialTick){
        if(!valid.getAsBoolean()){dismiss.run();return;}
        int shown=reveal.sample(bounds.height(),System.nanoTime()/1_000_000L,still);
        g.enableScissor(bounds.x(),bounds.y(),bounds.right(),bounds.y()+shown);
        try {
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
        } finally {g.disableScissor();}
    }
    public boolean mousePressed(double x,double y,int button,long now){
        if(!valid.getAsBoolean()){dismiss.run();return true;}
        if(!reveal.contains(y,bounds.y()))return true;
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
        if(layout==Layout.EFFECT_TILES&&(key==264||key==265)) {
            int next=focus+(key==264?2:-2);
            if(focus>=0&&next>=0&&next<controls.size()&&controls.get(next).active)setFocus(next);
            return true;
        }
        reveal.finish(); // Keyboard focus and narration must never target clipped text.
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
    public void close(){cancel(0);reveal.close();}
}
