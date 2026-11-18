// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

/** Explicit HTTP(S) actions only, respecting the player's native link and confirmation settings. */
public final class LinkApi {
    LinkApi() {}
    public boolean open(Screen owner, String address) {
        var focused = owner == null ? null : owner.getFocused();
        return open(owner, address, () -> focused);
    }
    public boolean open(Screen owner, String address, Supplier<? extends GuiEventListener> returnFocus) {
        var uri = webUri(address);
        if (uri == null || owner == null) return false;
        var client = Minecraft.getInstance();
        if (client == null || !client.isSameThread() || NativePort.screen(client) != owner
                || !client.options.chatLinks().get()) return false;
        if (!client.options.chatLinksPrompt().get()) {
            Util.getPlatform().openUri(uri);
            return true;
        }
        ConfirmLinkScreen[] confirmation = new ConfirmLinkScreen[1];
        confirmation[0] = new ConfirmLinkScreen(accepted -> {
            if (NativePort.screen(client) != confirmation[0]) return;
            // Return through normal Screen initialization, then resolve any rebuilt focus target.
            NativePort.show(client, owner);
            var focus = returnFocus.get();
            if (NativePort.screen(client) == owner && focus != null && owner.children().contains(focus)) owner.setFocused(focus);
            if (accepted) Util.getPlatform().openUri(uri);
        }, uri.toString(), false);
        NativePort.show(client, confirmation[0]);
        return true;
    }

    static URI webUri(String address) {
        if (address == null) return null;
        try {
            var uri = new URI(address);
            if (uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
            // An invalid destination cannot become a browser action.
        }
        return null;
    }
}
