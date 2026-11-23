// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.muoniumplayer.MuoniumPlayerClient;
import com.muoniumplayer.core.screens.onboarding.OnboardingManager;
import com.muoniumplayer.render.skija.SkijaHost;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Opens the actual embedded music interfaces from the developer gallery. */
public final class EmbeddedMusicGalleryPreview implements PreviewScene {
    private static final String UNAVAILABLE = "sh_better_ui.panel.developer.gallery.embedded_music.unavailable";

    public static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:embedded_music", PreviewSpec.Category.CONTROLS, 30,
                "sh_better_ui.panel.developer.gallery.sample.embedded_music", 46, 0, "",
                EmbeddedMusicGalleryPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        var area = host.viewport();
        if (area.width() < 40 || area.height() < 44) return;
        int width = Math.min(180, area.width());
        int x = area.x() + (area.width() - width) / 2;
        int y = area.y() + (area.height() - 44) / 2;
        host.add(button(host, x, y, width, "sh_better_ui.panel.muonium.player",
                () -> OnboardingManager.openPlayerPreview(host.screen())));
        host.add(button(host, x, y + 24, width, "sh_better_ui.panel.muonium.open",
                () -> MuoniumPlayerClient.getInstance().openHudEditor(Minecraft.getInstance())));
    }

    private static UiButton button(PreviewContext host, int x, int y, int width, String label, Runnable open) {
        return new UiButton(x, y, width, 20, Component.translatable(label), pressed -> {
            SkijaHost.checkNativeAvailability();
            if (SkijaHost.canOpenScreen()) {
                open.run();
            } else {
                pressed.setMessage(Component.translatable(UNAVAILABLE + "_label"));
                ((UiButton) pressed).setPreview(Component.translatable(UNAVAILABLE));
            }
        }, host.preferences(), false);
    }
}
