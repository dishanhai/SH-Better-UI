// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.theme.*;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Interactive samples are local to this screen. They never change player settings or playback. */
final class DisplaySuitePreviewScreen extends Screen {
    private final Screen parent;
    private final ClientUiSettings settings;
    private final List<UiButton> buttons=new ArrayList<>();
    private ThemeManager manager;
    private ThemeScope scope;
    private UiTheme palette;
    private int value=45, activations;
    private boolean selected=true;
    private int left,top,contentWidth,page;
    private final java.util.Map<String,net.minecraft.client.gui.components.AbstractWidget> controls=new java.util.LinkedHashMap<>();
    private SamplePages pages;
    /** Pure viewport arithmetic: reserve title, navigation and Back before mounting samples. */
    record SamplePages(int capacity,int count) {
        static SamplePages at(int width,int height) {
            int capacity=width<80?0:Math.min(5,Math.max(0,(height-95)/27));
            return new SamplePages(capacity,capacity==0?1:(5+capacity-1)/capacity);
        }
        int clamp(int page){return Math.max(0,Math.min(count-1,page));}
    }
    DisplaySuitePreviewScreen(Screen parent,ClientUiSettings settings) {
        super(text("title"));this.parent=parent;this.settings=settings;
    }
    private static Component text(String key,Object...args) {
        return Component.translatable("sh_better_ui.display_suite.preview."+key,args);
    }
    @Override protected void init() {
        String focus=controls.entrySet().stream().filter(e->e.getValue()==getFocused()).map(java.util.Map.Entry::getKey).findFirst().orElse("");
        release();controls.clear();
        manager=new ThemeManager();
        var prefs=UiPreferences.fromSettings(settings);
        String id=settings.theme().selectedThemeId();
        if(id==null || manager.registry().find(id).isEmpty())id=settings.theme().light()?BuiltinThemes.LIGHT_ID:BuiltinThemes.DARK;
        manager.preferences(manager.preferences().withFixedTheme(id).withReducedMotion(true).withHighContrast(prefs.highContrast()));
        var resolved=UiTheme.resolve(settings.theme());
        scope=createPreviewScope(manager,resolved,prefs);
        manager.preferences(manager.preferences().withReducedMotion(prefs.reducedMotion()));
        palette=UiTheme.fromScope(scope);
        contentWidth=Math.max(1,Math.min(280,width-24));left=Math.max(0,(width-contentWidth)/2);top=40;
        pages=SamplePages.at(width,height);page=pages.clamp(page);
        for(int i=page*pages.capacity();i<Math.min(5,(page+1)*pages.capacity());i++) {
            int y=top+(i-page*pages.capacity())*27;
            switch(i) {
                case 0 -> sample(y,"normal",false,()->activations++);
                case 1 -> sample(y,"primary",true,()->activations++);
                case 2 -> {
                    UiButton[] selection=new UiButton[1];
                    selection[0]=sample(y,"selected",false,()->{selected=!selected;selection[0].setSelected(selected);});selection[0].setSelected(selected);
                }
                case 3 -> sample(y,"disabled",false,()->{}).active=false;
                case 4 -> {
                    var slider=new UiSlider(new Bounds(left,y,contentWidth,20),0,100,1,value,prefs,n->text("slider",n),n->value=n);
                    controls.put("slider",slider);addRenderableWidget(slider);
                }
            }
        }
        if(pages.count()>1) {
            int half=(contentWidth-6)/2;
            var previous=navigation("previous",left,height-54,half,Component.translatable("sh_better_ui.native_themes.previous"),()->changePage(-1));
            previous.active=page>0;
            var next=navigation("next",left+half+6,height-54,half,Component.translatable("sh_better_ui.native_themes.next"),()->changePage(1));
            next.active=page+1<pages.count();
        }
        var back=navigation("back",left,Math.max(0,height-28),contentWidth,Component.translatable("gui.back"),this::onClose);
        var restored=controls.get(focus);
        setInitialFocus(restored!=null&&restored.active?restored:back);
    }
    /** Used by initialization and the headless scope-isolation regression test. */
    static ThemeScope createPreviewScope(ThemeManager manager,UiTheme resolved,UiPreferences prefs) {
        var previewScope=manager.global().child("display-suite-preview");
        previewScope.configure(b->{
            b.override(BACKGROUND,ColorValue.argb(resolved.background()));
            b.override(SURFACE,ColorValue.argb(resolved.surface()));
            b.override(BORDER,ColorValue.argb(resolved.border()));
            b.override(TEXT,ColorValue.argb(resolved.text()));
            b.override(ACCENT,ColorValue.argb(resolved.accent()));
            b.override(PRIMARY,ColorValue.argb(resolved.accent()));
            b.override(ON_PRIMARY,ColorValue.argb(com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy.readable(
                    manager.snapshot().resolve(ON_PRIMARY).argb(),resolved.accent()|0xff000000,
                    resolved.background()|0xff000000,prefs.highContrast()?7:4.5)));
        });
        return previewScope;
    }
    private void changePage(int delta) {
        page=pages.clamp(page+delta);rebuildWidgets();
        var focus=controls.get(delta>0?"next":"previous");
        if(focus==null||!focus.active)focus=controls.get(delta>0?"previous":"next");
        if(focus!=null)setFocused(focus);
    }
    private UiButton navigation(String id,int x,int y,int w,Component label,Runnable action) {
        var button=new UiButton(x,y,w,20,label,b->action.run(),UiPreferences.fromSettings(settings),false);
        button.bindTheme(scope);buttons.add(button);controls.put(id,button);return addRenderableWidget(button);
    }
    private UiButton sample(int y,String label,boolean primary,Runnable action) {
        var b=new UiButton(left,y,contentWidth,22,text(label),ignored->action.run(),UiPreferences.fromSettings(settings),primary);
        b.bindTheme(scope);buttons.add(b);controls.put(label,b);return addRenderableWidget(b);
    }
    public void renderBackground(GuiGraphics g,int x,int y,float tick) {}
    @Override public void render(GuiGraphics g,int x,int y,float tick) {
        g.fill(0,0,width,height,palette.background()|0xff000000);
        g.drawCenteredString(font,title.copy().append(pages.count()>1?" · "+(page+1)+"/"+pages.count():""),width/2,10,palette.text());
        var hint=pages.capacity()>0?text("hint",activations):Component.translatable("sh_better_ui.picker.too_small");
        g.drawCenteredString(font,font.plainSubstrByWidth(hint.getString(),Math.max(1,width-12)),width/2,24,palette.text());
        UiTheme.preview(palette,()->super.render(g,x,y,tick));
    }
    private void release(){buttons.forEach(UiButton::unbindTheme);buttons.clear();if(scope!=null){scope.close();scope=null;}if(manager!=null){manager.close();manager=null;}}
    @Override public void removed(){release();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
