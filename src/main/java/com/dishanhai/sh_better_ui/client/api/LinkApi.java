package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;

/** 山海：在客户端按 Minecraft 的网页链接设置打开外部地址。 */
public final class LinkApi {
    LinkApi() {}

    /**
     * Call from a user action on the client thread. Returns whether the request was accepted.
     * The operating system handles the browser launch.
     */
    public boolean open(Screen owner, String address) {
        var focused = owner == null ? null : owner.getFocused();
        return open(owner, address, () -> focused);
    }

    /** Supply a fresh widget when the parent rebuilds its controls on return from confirmation. */
    public boolean open(Screen owner, String address, Supplier<? extends GuiEventListener> returnFocus) {
        var uri = webUri(address);
        if (uri == null || owner == null) return false;
        var client = Minecraft.getInstance();
        if (client == null || !client.isSameThread() || client.screen != owner
                || !client.options.chatLinks().get()) return false;
        if (!client.options.chatLinksPrompt().get()) {
            Util.getPlatform().openUri(uri);
            return true;
        }
        var prompt = new UiConfirmation.Prompt(Component.translatable("chat.link.confirm"),
                Component.literal(uri.toString()), Component.translatable("chat.link.open"),
                CommonComponents.GUI_CANCEL);
        return UiConfirmation.open(owner, prompt, UiPreferences.configured(), null, returnFocus,
                () -> Util.getPlatform().openUri(uri), GraphicIcon.LINK);
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
