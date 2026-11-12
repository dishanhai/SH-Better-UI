// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.richtext.network.NativeRichSyncServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Binds a trusted server-thread provider to the optional rich text PLAY protocol. */
public final class ServerRichDocuments {
    private ServerRichDocuments() {}

    /**
     * Registers a namespaced document for this server lifetime. The returned registration
     * can be closed on the same thread to revoke it early. Each callback receives the
     * authenticated connection's current player.
     */
    public static RichSyncServer.Registration register(MinecraftServer server, UiId document,
                                                        RichSyncServer.Access<ServerPlayer> access) {
        return NativeRichSyncServer.register(server, document, access);
    }
}
