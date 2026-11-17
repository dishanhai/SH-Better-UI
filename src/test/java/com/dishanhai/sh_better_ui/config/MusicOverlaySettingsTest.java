package com.dishanhai.sh_better_ui.config;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicOverlaySettingsTest {
 @Test void optInDefaultsAndIndependentPositions(){var s=MusicOverlaySettings.DEFAULT;assertFalse(s.hudEnabled());assertFalse(s.islandEnabled());var n=s.withHud(true).withPosition(.1f,.2f);assertEquals(s.islandX(),n.islandX());assertEquals(s.islandY(),n.islandY());assertTrue(n.hudEnabled());}
 @Test void rejectsInvalidCoordinates(){assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(MusicOverlaySettings.DEFAULT.withIslandPosition(Float.NaN,0)));assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(MusicOverlaySettings.DEFAULT.withPosition(-1,0)));}
 @Test void persistenceRoundTrip(){var gson=new Gson();var value=MusicOverlaySettings.DEFAULT.withIsland(true).withExpanded(true).withIslandPosition(.8f,.6f);assertEquals(value,MusicOverlaySettings.KEY.validate(gson.fromJson(gson.toJson(value),MusicOverlaySettings.class)));}
 @Test void scaleLimitsAndAmbientAreIndependent(){var s=MusicOverlaySettings.DEFAULT.withScale(true,1.5f).withAmbient(true);assertEquals(1f,s.hudScale());assertEquals(1.5f,s.islandScale());assertTrue(s.ambientEnabled());assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(s.withScale(false,Float.POSITIVE_INFINITY)));}
}
