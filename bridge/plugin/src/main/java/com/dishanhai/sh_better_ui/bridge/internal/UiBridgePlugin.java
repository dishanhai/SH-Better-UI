// Author: dishanhai
package com.dishanhai.sh_better_ui.bridge.internal;

import com.dishanhai.sh_better_ui.bridge.api.UiBridge;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitTask;

public final class UiBridgePlugin extends JavaPlugin {
    private BukkitUiBridge bridge;
    private BukkitTask clock;

    @Override
    public void onEnable() {
        if (UiProtocol.MAX_FRAME_BYTES > Messenger.MAX_MESSAGE_SIZE
                || UiProtocol.CHANNEL.length() > Messenger.MAX_CHANNEL_SIZE) {
            throw new IllegalStateException("SH-Better-UI protocol exceeds the Bukkit messaging limits");
        }
        bridge = new BukkitUiBridge(this);
        var server = getServer();
        var messenger = server.getMessenger();
        messenger.registerOutgoingPluginChannel(this, UiProtocol.CHANNEL);
        messenger.registerIncomingPluginChannel(this, UiProtocol.CHANNEL, bridge);
        server.getPluginManager().registerEvents(bridge, this);
        server.getServicesManager().register(UiBridge.class, bridge, this, ServicePriority.Normal);
        clock = server.getScheduler().runTaskTimer(this, bridge::tick, 1, 1);
    }

    @Override
    public void onDisable() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
        var server = getServer();
        server.getServicesManager().unregisterAll(this);
        if (bridge != null) {
            HandlerList.unregisterAll(bridge);
            bridge.shutdown();
            bridge = null;
        }
        server.getMessenger().unregisterIncomingPluginChannel(this, UiProtocol.CHANNEL);
        server.getMessenger().unregisterOutgoingPluginChannel(this, UiProtocol.CHANNEL);
    }
}
