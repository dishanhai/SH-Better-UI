// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.BackgroundRegistry;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffectPresets;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Native visual effects, editable profiles and previews for caller-owned configuration. */
public interface AppearanceService extends UIService {
    record Preset(String id, String titleKey) {}
    enum ThemeSection { COLORS, BACKGROUNDS, EFFECTS }

    /** Edits an isolated custom theme. Confirmation returns a draft; persistence remains the host's responsibility. */
    default Screen editTheme(Screen parent, ClientUiSettings settings, ThemeSection section,
                             Consumer<com.dishanhai.sh_better_ui.config.ThemeSettings> apply) {
        return new com.dishanhai.sh_better_ui.kernel.internal.appearance.ThemeCustomizationScreen(
                this, parent, settings, section, apply);
    }

    /** The shared native effect registry. Create and close one layer per rendered view. */
    default BackgroundRegistry backgrounds() { return ShBetterUiApi.backgrounds(); }

    default Set<String> groups() { return ThemeEffects.groups(); }

    /** Returns a fresh object with the effect's canonical defaults. */
    default JsonObject template(String group) { return ThemeEffects.template(group); }

    /** Ordered choices with stable IDs within the effect group. */
    default List<Preset> presets(String group) { return ThemeEffectPresets.describe(group); }

    /** Applies a named preset to a copy of the local override and validates it against inheritance. */
    default JsonObject applyPreset(ClientUiSettings settings, String group, String preset,
                                   JsonObject inherited, JsonObject local) {
        return ThemeEffectPresets.apply(settings, group, preset, inherited, local);
    }

    /** Reads the typed profile from the current native theme or enclosing preview scope. */
    default <T extends Record> T profile(String group, T defaults) {
        return ThemeEffects.profile(group, defaults);
    }

    /** Creates a local appearance snapshot from the same inheritance inputs as the editor. */
    default ClientUiSettings draft(ClientUiSettings settings, String group, JsonObject inherited, JsonObject local) {
        return ThemeEffects.previewSettings(settings, group, inherited, local);
    }

    /** Runs a native draw with an isolated draft and restores the prior appearance on exit. */
    default void preview(ClientUiSettings draft, Runnable draw) {
        ThemeEffects.preview(draft, draw);
    }

    /**
     * Edits one registered effect group. Inherited and local values are copied on entry.
     * The callback receives the local group only when the user confirms.
     */
    Screen edit(Screen parent, ClientUiSettings settings, String group, JsonObject inherited, JsonObject local,
                Consumer<JsonObject> apply);
}
