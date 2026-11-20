// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicAppearanceStore.Preference;
import net.minecraft.network.chat.Component;

final class MusicAppearanceLabels {
    private MusicAppearanceLabels() {}

    static Component label(String suffix, Object... args) {
        return Component.translatable("sh_better_ui.online_music.appearance." + suffix, args);
    }

    static Component option(Preference preference) { return label(preference.id()); }

    static Component current() {
        var choice = MusicAppearance.current();
        if (choice != Preference.AUTOMATIC) return option(choice);
        return label("automatic_value", option(MusicGlass.active() ? Preference.LIQUID_GLASS : Preference.CLASSIC));
    }
}
