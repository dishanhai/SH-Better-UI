package com.dishanhai.sh_better_ui.config;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicOverlaySettingsTest {
 @Test void optInDefaultsAndIndependentPositions(){var s=MusicOverlaySettings.DEFAULT;assertFalse(s.hudEnabled());assertFalse(s.islandEnabled());var n=s.withHud(true).withPosition(.1f,.2f);assertEquals(s.islandX(),n.islandX());assertEquals(s.islandY(),n.islandY());assertTrue(n.hudEnabled());}
 @Test void rejectsInvalidCoordinates(){assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(MusicOverlaySettings.DEFAULT.withIslandPosition(Float.NaN,0)));assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(MusicOverlaySettings.DEFAULT.withPosition(-1,0)));}
 @Test void persistenceRoundTrip(){var gson=new Gson();var value=MusicOverlaySettings.DEFAULT.withIsland(true).withExpanded(true).withIslandPosition(.8f,.6f);assertEquals(value,MusicOverlaySettings.KEY.validate(gson.fromJson(gson.toJson(value),MusicOverlaySettings.class)));}
 @Test void scaleLimitsAndAmbientAreIndependent(){var s=MusicOverlaySettings.DEFAULT.withScale(true,1.5f).withAmbient(true);assertEquals(1f,s.hudScale());assertEquals(1.5f,s.islandScale());assertTrue(s.ambientEnabled());assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(s.withScale(false,Float.POSITIVE_INFINITY)));}
 @Test void priorVersionJsonKeepsPlacementAndDefaultsNewSettings(){
  var gson=new Gson();
  var old=gson.toJsonTree(MusicOverlaySettings.DEFAULT.withIsland(true).withIslandPosition(.2f,.7f).withScale(true,1.5f)).getAsJsonObject();
  old.remove("islandStyle");old.remove("taskNotices");
  var restored=MusicOverlaySettings.KEY.validate(gson.fromJson(old,MusicOverlaySettings.class));
  assertEquals("classic",restored.islandStyle());assertFalse(restored.taskNotices());assertTrue(restored.islandEnabled());
  assertEquals(.2f,restored.islandX());assertEquals(.7f,restored.islandY());assertEquals(1.5f,restored.islandScale());
  old.add("islandStyle",com.google.gson.JsonNull.INSTANCE);
  assertEquals(restored,gson.fromJson(old,MusicOverlaySettings.class));
 }
 @Test void everyNativeIslandStylePersistsWithoutOrdinalMigration(){
  var gson=new Gson();
  for(var style:com.dishanhai.sh_better_ui.client.gui.scaled.IslandShellStyle.values()){
   var value=MusicOverlaySettings.DEFAULT.withIslandStyle(style.id());
   assertEquals(value,MusicOverlaySettings.KEY.validate(gson.fromJson(gson.toJson(value),MusicOverlaySettings.class)));
  }
 }
 @Test void stableStyleIdsRoundTripAndRejectUnknown(){
  var gson=new Gson();var value=MusicOverlaySettings.DEFAULT.withIslandStyle("minimal").withTaskNotices(true);
  assertEquals(value,MusicOverlaySettings.KEY.validate(gson.fromJson(gson.toJson(value),MusicOverlaySettings.class)));
  assertThrows(IllegalArgumentException.class,()->MusicOverlaySettings.KEY.validate(value.withIslandStyle("2")));
 }
}
