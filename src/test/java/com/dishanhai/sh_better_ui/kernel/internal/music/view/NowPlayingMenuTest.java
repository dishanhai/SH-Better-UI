package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicControlRenderer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class NowPlayingMenuTest {
    private NowPlayingMenu menu(AtomicInteger chosen, AtomicBoolean valid, AtomicInteger dismissed) {
        return new NowPlayingMenu(new Bounds(0,0,248,156),UiPreferences.DEFAULT,Component.literal("More"),
            List.of(entry("Disabled",false),entry("Queue",true),entry("Library",true),entry("Album",true),entry("Artist",true)),
            valid::get,dismissed::incrementAndGet,e->chosen.incrementAndGet(),text->{});
    }
    private NowPlayingMenu.Entry entry(String title,boolean enabled){
        return new NowPlayingMenu.Entry(Component.literal(title),MusicControlRenderer.Icon.LIBRARY,enabled,()->{});
    }
    @Test void smallestSupportedHeaderKeepsMoreBetweenBackAndMode() {
        var frame=new Bounds(0,0,208,240);var more=NowPlayingMenu.anchor(frame,false);
        assertEquals(112,more.x());assertEquals(136,more.right());
        assertTrue(more.x()>=12+100);assertTrue(more.right()<=208-12-60);
    }
    @Test void pointerActivatesOnlyOnMatchingReleaseInsideAndNeverOnDisabledAction(){
        var chosen=new AtomicInteger();var m=menu(chosen,new AtomicBoolean(true),new AtomicInteger());
        assertTrue(m.mousePressed(100,40,0,0));assertEquals(0,chosen.get());
        m.mouseReleased(100,40,0,1);assertEquals(1,chosen.get());
        m.mousePressed(100,40,0,2);m.mouseReleased(400,400,0,3);assertEquals(1,chosen.get());
        m.mousePressed(15,40,0,4);m.mouseReleased(15,40,0,5);assertEquals(1,chosen.get());
    }
    @Test void keyboardSkipsDisabledAndRequiresRelease(){
        var chosen=new AtomicInteger();var m=menu(chosen,new AtomicBoolean(true),new AtomicInteger());
        assertTrue(m.keyPressed(257,0,0,0));assertEquals(0,chosen.get());
        m.keyReleased(257,0,0,1);assertEquals(1,chosen.get());
        m.keyPressed(265,0,0,2);m.keyReleased(265,0,0,3);
        m.keyPressed(32,0,0,4);m.keyReleased(32,0,0,5);assertEquals(2,chosen.get());
    }
    @Test void cancellationDoesNotLeakAnArmedAction(){
        var chosen=new AtomicInteger();var m=menu(chosen,new AtomicBoolean(true),new AtomicInteger());
        m.keyPressed(257,0,0,0);m.cancel(1);m.keyReleased(257,0,0,2);assertEquals(0,chosen.get());
        m.mousePressed(100,40,0,3);m.close();m.mouseReleased(100,40,0,4);assertEquals(0,chosen.get());
    }
    @Test void movingFocusCancelsPreviouslyArmedKeyboardAction(){
        var chosen=new AtomicInteger();var m=menu(chosen,new AtomicBoolean(true),new AtomicInteger());
        m.keyPressed(257,0,0,0);m.keyPressed(264,0,0,1);m.keyReleased(257,0,0,2);assertEquals(0,chosen.get());
    }
    @Test void trackChangeBetweenPressAndReleasePreventsWrongTrackAction(){
        var chosen=new AtomicInteger();var valid=new AtomicBoolean(true);var dismissed=new AtomicInteger();var m=menu(chosen,valid,dismissed);
        m.mousePressed(100,40,0,0);valid.set(false);m.mouseReleased(100,40,0,1);
        assertEquals(0,chosen.get());assertEquals(1,dismissed.get());
    }
    @Test void equalizerUsesFullWidthRowsAndFitsTheSmallestPlayer(){
        var choices=List.of(entry("Flat",true),entry("Bass",true),entry("Voice",true),entry("Treble",true));
        var chosen=new java.util.ArrayList<String>();
        int height=NowPlayingMenu.height(choices.size(),false);
        assertTrue(NowPlayingMenu.height(7,true)<240);
        var menu=new NowPlayingMenu(new Bounds(0,0,208,height),UiPreferences.DEFAULT,Component.literal("Equalizer"),
                choices,()->true,()->{},entry->chosen.add(entry.label().getString()),text->{},false);
        menu.mousePressed(180,30,0,0);menu.mouseReleased(180,30,0,1);
        menu.mousePressed(180,120,0,2);menu.mouseReleased(180,120,0,3);
        assertEquals(List.of("Flat","Treble"),chosen);
    }
    @Test void qqEffectsFitAndInitialFocusNarratesTheSelectedTile(){
        var choices=List.of(entry("Off",true),entry("Bass",true),entry("Voice",true),
                new NowPlayingMenu.Entry(Component.literal("Live (selected)"),null,true,()->{},true),
                entry("Wide",true),entry("Hall",true));
        var chosen=new java.util.ArrayList<String>();
        var narrated=new java.util.ArrayList<String>();
        var style=NowPlayingMenu.Layout.EFFECT_TILES;
        int height=NowPlayingMenu.height(choices.size(),style);
        assertTrue(height<150);
        var menu=new NowPlayingMenu(new Bounds(0,0,208,height),UiPreferences.DEFAULT,Component.literal("QQ Sound Effects"),
                choices,()->true,()->{},e->chosen.add(e.label().getString()),text->narrated.add(text.getString()),style);
        assertEquals(List.of("Live (selected)"),narrated);
        menu.keyPressed(264,0,0,0);
        menu.keyPressed(257,0,0,1); menu.keyReleased(257,0,0,2);
        assertEquals(List.of("Hall"),chosen);
        menu.mousePressed(104,40,0,3);menu.mouseReleased(104,40,0,4);
        assertEquals(1,chosen.size()); // gutter cannot trigger a preset
        menu.mousePressed(15,40,0,5);menu.mouseReleased(15,40,0,6);
        assertEquals(List.of("Hall","Off"),chosen);
    }
}
