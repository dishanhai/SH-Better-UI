// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.muoniumplayer.render.skija.SkijaHost;
import com.muoniumplayer.render.skija.SkijaNatives;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/** Vanilla title-screen control, usable before the Skija renderer starts. */
public final class SkijaRecoveryButton {
    private static final String KEY = "sh_better_ui.muonium.render.recovery.";
    private static final Map<Screen, SkijaRecoveryButton> CONTROLS = new WeakHashMap<>();
    private final Button button;
    private SkijaNatives.DownloadSnapshot previous;

    private SkijaRecoveryButton(int x, int width) {
        button = Button.builder(Component.empty(), ignored -> SkijaNatives.requestCompanionDownload())
                .bounds(x, 4, width, 20).build();
    }

    public static Button create(Screen screen) {
        SkijaHost.checkNativeAvailability();
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int width = Math.max(1, Math.min(200, screenWidth - 8));
        SkijaRecoveryButton control = new SkijaRecoveryButton((screenWidth - width) / 2, width);
        control.refresh();
        CONTROLS.put(screen, control);
        return control.button;
    }

    public static void update(Screen screen) {
        if (screen instanceof TitleScreen) {
            SkijaRecoveryButton control = CONTROLS.get(screen);
            if (control != null) {
                control.refresh();
                if (!control.button.visible && screen.getFocused() == control.button) {
                    screen.setFocused(null);
                }
            }
        }
    }

    private void refresh() {
        SkijaNatives.Status status = SkijaNatives.status();
        button.visible = status.companionMissing();
        if (!button.visible) {
            return;
        }
        SkijaNatives.DownloadSnapshot download = SkijaNatives.companionDownloadSnapshot();
        if (download.equals(previous)) {
            return;
        }
        previous = download;
        button.active = SkijaNatives.canDownloadCompanion();
        Component label = switch (download.phase()) {
            case "PREPARING" -> text("preparing");
            case "DOWNLOADING" -> download.percent() < 0 ? text("downloading")
                    : text("progress", download.percent());
            case "INSTALLING" -> text("installing");
            case "DONE" -> text("installed");
            case "FAILED" -> text("retry");
            default -> button.active ? text("download") : text("unavailable");
        };
        button.setMessage(label);

        var hint = Component.translatable("sh_better_ui.muonium.render.native_missing", status.mappedPlatform())
                .append("\n").append(text("play"));
        if ("DONE".equals(download.phase())) {
            hint.append("\n").append(text("restart"));
        }
        if ("FAILED".equals(download.phase())) {
            hint.append("\n").append(switch (download.failure()) {
                case DIRECTORY -> text("failure.directory");
                case PACKAGE -> text("failure.package");
                case INSTALL -> text("failure.install");
            });
        }
        if ("FAILED".equals(download.phase()) || "IDLE".equals(download.phase())) {
            hint.append("\n").append(text("manual", status.expectedPackageName(),
                    SkijaNatives.NATIVE_DOWNLOAD_PAGE, status.expectedDirectory()));
        }
        button.setTooltip(Tooltip.create(hint));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }
}
