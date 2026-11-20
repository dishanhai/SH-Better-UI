package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackRateMenuTest {
    @Test void choicesAreExactAndLabelsDoNotDependOnDefaultLocale() {
        assertEquals(List.of(.5,.75,1d,1.25,1.5,1.75,2d),PlaybackRateChoices.VALUES);
        assertEquals("1",PlaybackRateChoices.format(1));assertEquals("0.75",PlaybackRateChoices.format(.75));
    }
    @Test void sevenRatesFitMinimumViewportAndKeyboardChoosesRealEntry() {
        var chosen=new ArrayList<Double>();var entries=new ArrayList<NowPlayingMenu.Entry>();
        for(double rate:PlaybackRateChoices.VALUES)entries.add(new NowPlayingMenu.Entry(Component.literal(PlaybackRateChoices.format(rate)),null,true,()->chosen.add(rate),rate==1));
        int height=NowPlayingMenu.height(entries.size(),NowPlayingMenu.Layout.EFFECT_TILES);assertTrue(height<=208);
        var menu=new NowPlayingMenu(new Bounds(0,0,208,height),UiPreferences.DEFAULT,Component.literal("Speed & pitch"),entries,
                ()->true,()->{},entry->entry.action().run(),text->{},NowPlayingMenu.Layout.EFFECT_TILES);
        menu.keyPressed(264,0,0,0);menu.keyReleased(264,0,0,1);
        menu.keyPressed(257,0,0,2);assertTrue(chosen.isEmpty());menu.keyReleased(257,0,0,3);
        assertEquals(List.of(1.5),chosen);
    }
    @Test void invalidatedCapabilityBetweenPressAndReleaseDoesNotApply() {
        var valid=new AtomicBoolean(true);var chosen=new ArrayList<Double>();
        var entry=new NowPlayingMenu.Entry(Component.literal("1.5×"),null,true,()->chosen.add(1.5),true);
        var menu=new NowPlayingMenu(new Bounds(0,0,208,70),UiPreferences.DEFAULT,Component.literal("Speed"),List.of(entry),
                valid::get,()->{},e->e.action().run(),text->{},NowPlayingMenu.Layout.EFFECT_TILES);
        menu.keyPressed(257,0,0,0);valid.set(false);menu.keyReleased(257,0,0,1);assertTrue(chosen.isEmpty());
    }
    @Test void disabledUnsupportedProviderDoesNotActivate() {
        var chosen=new ArrayList<Double>();
        var entry=new NowPlayingMenu.Entry(Component.literal("Speed unavailable"),null,false,()->chosen.add(1d));
        var menu=new NowPlayingMenu(new Bounds(0,0,208,70),UiPreferences.DEFAULT,Component.literal("Speed"),List.of(entry),
                ()->true,()->{},e->e.action().run(),text->{},NowPlayingMenu.Layout.EFFECT_TILES);
        menu.keyPressed(257,0,0,0);menu.keyReleased(257,0,0,1);assertTrue(chosen.isEmpty());
    }
}
