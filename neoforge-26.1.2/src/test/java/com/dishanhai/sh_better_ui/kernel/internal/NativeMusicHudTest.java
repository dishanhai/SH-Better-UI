// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class NativeMusicHudTest {
    @Test void coverFitsEntireSourceWithoutCroppingOrStretching() {
        var area = new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(10, 20, 42, 42);
        assertEquals(area, NativeMusicHud.coverBounds(area, 240, 240));
        var landscape = NativeMusicHud.coverBounds(area, 240, 120);
        assertEquals(42, landscape.width()); assertEquals(21, landscape.height());
        assertEquals(10, landscape.x()); assertEquals(30, landscape.y());
        var portrait = NativeMusicHud.coverBounds(area, 120, 240);
        assertEquals(21, portrait.width()); assertEquals(42, portrait.height());
        assertEquals(20, portrait.x()); assertEquals(20, portrait.y());
    }
    @Test void actualHudGeometryUsesNormalizedTravelAndReflowsScale() {
        var settings = MusicOverlaySettings.DEFAULT.withHud(true).withPosition(1,1);
        var bounds = NativeMusicHud.layout(800,450,settings,9);
        assertEquals(800,bounds.right()); assertEquals(450,bounds.bottom());
        var larger = NativeMusicHud.layout(800,450,settings.withScale(false,2),9);
        assertTrue(larger.width()>bounds.width()); assertEquals(800,larger.right());
        assertNull(NativeMusicHud.layout(50,30,settings,9));
    }
    @Test void hudEnabledWithoutIslandRetainsPausedCardAndOwnedReservation() {
        var host = new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication());
        var live = new LiveSurfaceRuntime(host);
        var frame = NativeMusicActivityTest.sample("one",MusicSnapshot.PlaybackStatus.PAUSED);
        var music = new NativeMusicActivity(false,()->frame,()->99_000_000_000L,()->true,()->{});
        var platforms=Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        var artwork=new NativeMusicArtwork(); var hud=new NativeMusicHud(artwork);
        try {
            host.start(List.of(BuiltinService.candidate(platforms,ActivityService.KEY,live,ActivityService.CAPABILITY),
                    BuiltinService.candidate(platforms,NotificationService.KEY,live,NotificationService.CAPABILITY),
                    BuiltinService.candidate(platforms,LiveSurfaceService.KEY,live,LiveSurfaceService.CAPABILITY), music.candidate()));
            music.settings(MusicOverlaySettings.DEFAULT.withHud(true));
            hud.prepare(music,frame,true,800,450,9); music.advance();
            assertTrue(hud.visible()); assertEquals(1,live.occupied().size());
            assertEquals(0,live.diagnostics().activities(),"HUD must not publish a second island activity");
            var bounds=NativeMusicHud.layout(800,450,music.settings(),9);
            assertEquals(new SurfacePlacement.Rect(bounds.x(),bounds.y(),bounds.width(),bounds.height()),live.occupied().getFirst());
            hud.prepare(music,frame,false,800,450,9); assertFalse(hud.visible()); assertTrue(live.occupied().isEmpty());
            hud.prepare(music,frame,true,800,450,9); assertTrue(hud.visible());
            hud.suspend(music); assertFalse(hud.visible()); assertTrue(live.occupied().isEmpty());
            hud.prepare(music,frame,true,800,450,9); host.close(); assertTrue(live.occupied().isEmpty());
        } finally { host.close(); artwork.close(); }
    }
    @Test void hiddenMissingAndDisabledHudNeverReserveSpace() {
        var host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication());
        var live=new LiveSurfaceRuntime(host); var frame=NativeMusicActivityTest.sample("one",MusicSnapshot.PlaybackStatus.STOPPED);
        var music=new NativeMusicActivity(false,()->frame,()->0,()->true,()->{});
        var platforms=Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        var artwork=new NativeMusicArtwork();var hud=new NativeMusicHud(artwork);
        try {
            host.start(List.of(BuiltinService.candidate(platforms,ActivityService.KEY,live,ActivityService.CAPABILITY),
                    BuiltinService.candidate(platforms,NotificationService.KEY,live,NotificationService.CAPABILITY),
                    BuiltinService.candidate(platforms,LiveSurfaceService.KEY,live,LiveSurfaceService.CAPABILITY),music.candidate()));
            hud.prepare(music,frame,true,800,450,9); assertFalse(hud.visible());
            music.settings(MusicOverlaySettings.DEFAULT.withHud(true));
            hud.prepare(music,frame,true,800,450,9); assertFalse(hud.visible()); assertTrue(live.occupied().isEmpty());
        } finally {host.close();artwork.close();}
    }
}
