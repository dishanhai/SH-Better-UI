// Author: dishanhai
package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.kernel.api.*;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Opt-in native music overlays. Positions are fractions of the remaining screen space. */
public record MusicOverlaySettings(boolean hudEnabled, boolean islandEnabled, boolean expanded, float x, float y, float islandX, float islandY, float hudScale, float islandScale, boolean ambientEnabled, boolean taskNotices, String islandStyle) {
    public MusicOverlaySettings { if (islandStyle == null) islandStyle = "classic"; }
    public MusicOverlaySettings withIslandStyle(String style) { return new MusicOverlaySettings(hudEnabled,islandEnabled,expanded,x,y,islandX,islandY,hudScale,islandScale,ambientEnabled,taskNotices,style); }
    public static final MusicOverlaySettings DEFAULT = new MusicOverlaySettings(false, false, false, .5f, .75f, .5f, .05f, 1f, 1f, false, false, "classic");
    public static final ConfigKey<MusicOverlaySettings> KEY = new ConfigKey<>(
            UiId.parse("sh_better_ui:music_overlay"), MusicOverlaySettings.class, DEFAULT,
            Set.of(ConfigLayer.USER_GLOBAL), ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.USER_GLOBAL,
            1, "Native music HUD and dynamic island", MusicOverlaySettings::valid,
            UnaryOperator.identity(), Map.of());
    public MusicOverlaySettings withExpanded(boolean value) { return new MusicOverlaySettings(hudEnabled, islandEnabled, value, x, y, islandX, islandY, hudScale, islandScale, ambientEnabled, taskNotices, islandStyle); }
    public MusicOverlaySettings withTaskNotices(boolean value) { return new MusicOverlaySettings(hudEnabled,islandEnabled,expanded,x,y,islandX,islandY,hudScale,islandScale,ambientEnabled,value,islandStyle); }
    public boolean valid() { return Set.of("classic","pill","minimal","glass","compact","card","system_card","music_focus","liquid_glass","bounce","music_capsule").contains(islandStyle) && Float.isFinite(x) && Float.isFinite(y) && x >= 0 && x <= 1 && y >= 0 && y <= 1 && Float.isFinite(islandX) && Float.isFinite(islandY) && islandX>=0 && islandX<=1 && islandY>=0 && islandY<=1 && Float.isFinite(hudScale) && hudScale>=.5f && hudScale<=2 && Float.isFinite(islandScale) && islandScale>=.5f && islandScale<=2; }
    public MusicOverlaySettings withHud(boolean value) { return new MusicOverlaySettings(value, islandEnabled, expanded, x, y, islandX, islandY, hudScale, islandScale, ambientEnabled, taskNotices, islandStyle); }
    public MusicOverlaySettings withIsland(boolean value) { return new MusicOverlaySettings(hudEnabled, value, expanded, x, y, islandX, islandY, hudScale, islandScale, ambientEnabled, taskNotices, islandStyle); }
    public MusicOverlaySettings withPosition(float x, float y) { return new MusicOverlaySettings(hudEnabled, islandEnabled, expanded, x, y, islandX, islandY, hudScale, islandScale, ambientEnabled, taskNotices, islandStyle); }
    public MusicOverlaySettings withIslandPosition(float x, float y) { return new MusicOverlaySettings(hudEnabled, islandEnabled, expanded, this.x, this.y, x, y, hudScale, islandScale, ambientEnabled, taskNotices, islandStyle); }
    public MusicOverlaySettings withScale(boolean island, float value) { return new MusicOverlaySettings(hudEnabled,islandEnabled,expanded,x,y,islandX,islandY,island?hudScale:value,island?value:islandScale,ambientEnabled,taskNotices,islandStyle); }
    public MusicOverlaySettings withAmbient(boolean value) { return new MusicOverlaySettings(hudEnabled,islandEnabled,expanded,x,y,islandX,islandY,hudScale,islandScale,value,taskNotices,islandStyle); }
}
