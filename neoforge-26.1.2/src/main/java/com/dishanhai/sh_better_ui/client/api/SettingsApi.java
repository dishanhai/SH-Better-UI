// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Applied client preferences and the native control panel. */
public final class SettingsApi {
    SettingsApi() {}

    public ControlPanelSettings current() { return ControlPanelSettings.current(); }

    public UiPreferences preferences() { return UiPreferences.configured(); }

    public ControlPanelScreen controlPanel(@Nullable Screen parent) { return new ControlPanelScreen(parent); }

    public void openControlPanel() { ControlPanelScreen.open(Minecraft.getInstance()); }

    public DeveloperPanelScreen developerPanel(@Nullable Screen parent) { return new DeveloperPanelScreen(parent); }

    public DeveloperPanelScreen developerPanel(@Nullable Screen parent, UiPreferences preferences) {
        return new DeveloperPanelScreen(parent, preferences);
    }

    /** Built-in and registered extension preview IDs, in gallery order. */
    public List<String> developerSampleIds() { return DeveloperGalleryScreen.sampleIds(); }

    public Screen developerSampleFullscreen(@Nullable Screen parent, String sampleId) {
        return developerSampleFullscreen(parent, sampleId, preferences());
    }

    public Screen developerSampleFullscreen(@Nullable Screen parent, String sampleId, UiPreferences preferences) {
        return developerSampleFullscreen(parent, sampleId, preferences, returnFocus(parent));
    }

    public Screen developerSampleFullscreen(@Nullable Screen parent, String sampleId, UiPreferences preferences,
                                            Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperGalleryScreen.fullscreen(parent, sampleId, preferences, returnFocus);
    }

    public Screen developerMaterialFullscreen(@Nullable Screen parent, String materialId) {
        return developerMaterialFullscreen(parent, materialId, preferences(), returnFocus(parent));
    }

    public Screen developerMaterialFullscreen(@Nullable Screen parent, String materialId, UiPreferences preferences,
                                              Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperGalleryScreen.fullscreenMaterial(parent, materialId, preferences, returnFocus);
    }

    private static Supplier<? extends GuiEventListener> returnFocus(@Nullable Screen parent) {
        var focused = parent == null ? null : parent.getFocused();
        int index = parent == null ? -1 : parent.children().indexOf(focused);
        return () -> {
            if (parent == null) return null;
            if (focused != null && parent.children().contains(focused)) return focused;
            return index >= 0 && index < parent.children().size() ? parent.children().get(index) : null;
        };
    }
}
