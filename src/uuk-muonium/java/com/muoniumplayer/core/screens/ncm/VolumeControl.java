package com.muoniumplayer.core.screens.ncm;

import com.dishanhai.sh_better_ui.kernel.internal.music.EmbeddedMusicSlider;
import com.muoniumplayer.compat.lwjgl2.Mouse;
import com.muoniumplayer.core.input.TouchGestures;
import com.muoniumplayer.core.interfaces.SharedRenderingConstants;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.ExternalPlayback;
import com.muoniumplayer.core.ncm.music.VanillaMusicDucker;
import com.muoniumplayer.core.ncm.spotify.SpotifyConnectMirror;
import com.muoniumplayer.core.rendering.DownloadDynamicIsland;
import com.muoniumplayer.core.settings.PlayerAppearance;
import com.muoniumplayer.core.settings.TouchSettings;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import net.minecraft.network.chat.Component;

/** Shared compact and fullscreen volume control. The drag previews the percentage before applying it. */
public final class VolumeControl implements SharedRenderingConstants {
    private static final int MAX_PERCENT = 100;
    private static final double HIT_HEIGHT = 18;
    private static final double ENDPOINT_GAP = 6;

    private final EmbeddedMusicSlider slider = new EmbeddedMusicSlider();

    private static int percent() {
        return Math.round(CloudMusic.displayVolume() * MAX_PERCENT);
    }

    private void sync(boolean enabled) {
        String target = ExternalPlayback.isMirroring() ? "external/" + ExternalPlayback.rawSnapshot().app()
                : SpotifyConnectMirror.isMirroring() ? "spotify/" + SpotifyConnectMirror.currentDeviceId() : "local";
        boolean verticalDrag = TouchSettings.ENABLED && TouchGestures.get().isVerticalDrag();
        slider.sync(target, percent(), MAX_PERCENT, enabled && !verticalDrag);
    }

    public boolean press(double x, double y, int button) {
        sync(true);
        if (button == 0) return slider.press(x, y);
        if (button != 1 || slider.editing() || !slider.hits(x, y)) return false;
        boolean enabled = !VanillaMusicDucker.isEnabled();
        VanillaMusicDucker.setEnabled(enabled);
        DownloadDynamicIsland.showVanillaMusicDucker(enabled);
        return true;
    }

    public boolean hasPress() {
        return slider.editing();
    }

    public boolean cancelPress() {
        return slider.cancel();
    }

    public void release(double x) {
        sync(true);
        slider.release(x).ifPresent(edit -> {
            if (edit.positionMs() != percent()) {
                CloudMusic.setVolume(edit.positionMs() / (float) MAX_PERCENT, true);
            }
        });
    }

    public String tooltip() {
        String state = Component.translatable(VanillaMusicDucker.isEnabled()
                ? "sh_better_ui.muonium.player.ducking_on" : "sh_better_ui.muonium.player.ducking_off").getString();
        return Component.translatable("sh_better_ui.muonium.player.volume_tooltip", percent(), state).getString();
    }

    public void render(double mouseX, double mouseY, double x, double centerY,
                       double width, float alpha, boolean inputBlocked) {
        double lowGlyphWidth = FontManager.music40.getStringWidthD("I");
        double highGlyphWidth = FontManager.music40.getStringWidthD("J");
        double endpointGap = width < 74 ? 3 : ENDPOINT_GAP;
        double sliderX = x + lowGlyphWidth + endpointGap;
        double sliderWidth = width - lowGlyphWidth - highGlyphWidth - endpointGap * 2;
        slider.layout(sliderX, centerY, sliderWidth, HIT_HEIGHT);
        sync(!inputBlocked && alpha >= .95f);
        if (sliderWidth <= 0 || alpha <= .01f) return;

        if (!Mouse.isButtonDown(0)) slider.cancel();
        if (slider.editing()) slider.drag(mouseX);
        boolean hovered = slider.hits(mouseX, mouseY);
        boolean engaged = hovered || slider.editing();
        boolean flat = PlayerAppearance.useShbuiControls();
        int accent = NCMScreen.getColor(NCMScreen.ColorType.ACCENT);
        slider.paintTrack(hovered, flat, flat ? 1.5 : 4.5, accent, alpha);
        slider.paintThumb(alpha);

        double iconY = centerY - FontManager.music40.getHeight() * .5 - .5;
        int ink = reAlpha(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT), alpha * (engaged ? .95f : .64f));
        FontManager.music40.drawString("I", x, iconY, ink);
        FontManager.music40.drawString("J", x + width - highGlyphWidth, iconY, ink);

        if (slider.editing()) {
            String value = Component.translatable("sh_better_ui.muonium.player.volume_percent", slider.position()).getString();
            slider.paintPreview(value, slider.fraction(), alpha);
        }
        if (engaged) CursorUtils.setOverride(CursorUtils.HAND);
    }
}
