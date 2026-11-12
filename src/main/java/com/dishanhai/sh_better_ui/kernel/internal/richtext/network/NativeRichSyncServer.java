// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.richtext.network;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ClientMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Number;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncServer;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loader callbacks pass the authenticated PLAY sender on its own server thread. */
public final class NativeRichSyncServer {
    public static final UiId DEMO_DOCUMENT = UiId.parse("sh_better_ui:rich_text_example/session");
    public static final UiId DEMO_VALUE = UiId.parse("sh_better_ui:rich_text_example/energy");
    private static final Map<MinecraftServer, Session> SESSIONS = new ConcurrentHashMap<>();

    private NativeRichSyncServer() {}

    public static RichSyncServer.Registration register(MinecraftServer server, UiId document,
                                                        RichSyncServer.Access<ServerPlayer> access) {
        checkThread(server);
        if (DEMO_DOCUMENT.equals(document)) throw new IllegalArgumentException("Reserved rich text demo document");
        var registration = session(server).authority.register(document, access);
        return () -> {
            checkThread(server);
            registration.close();
        };
    }

    static void receive(ServerPlayer sender, Connection connection, ClientMessage message) {
        var server = sender.getServer();
        if (server == null || !isCurrent(sender, connection)) return;
        checkThread(server);
        session(server).authority.receive(connection, sender, message, packet ->
                PlatformRichSyncNetwork.send(connection, packet));
    }

    static void tick(MinecraftServer server) {
        var session = SESSIONS.get(server);
        if (session == null || server.getTickCount() % 5 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            var connection = PlatformRichSyncNetwork.connection(player);
            if (isCurrent(player, connection)) {
                session.authority.refresh(connection, player, packet -> PlatformRichSyncNetwork.send(connection, packet));
            }
        }
    }

    static void logout(ServerPlayer player) {
        var server = player.getServer();
        var session = server == null ? null : SESSIONS.get(server);
        if (player.connection == null) return;
        var connection = PlatformRichSyncNetwork.connection(player);
        RichSyncAdmission.forget(connection);
        if (session != null) session.authority.disconnect(connection);
    }

    static void stop(MinecraftServer server) {
        var session = SESSIONS.remove(server);
        if (session != null) session.authority.clear();
    }

    private static boolean isCurrent(ServerPlayer player, Connection connection) {
        return player.connection != null && player.connection.player == player && connection != null
                && PlatformRichSyncNetwork.connection(player) == connection && connection.isConnected()
                && PlatformRichSyncNetwork.supported(connection);
    }

    private static Session session(MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, Session::new);
    }

    private static void checkThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Rich document authority requires the server thread");
    }

    private static final class Session {
        private final RichSyncServer<ServerPlayer> authority = new RichSyncServer<>(failure ->
                ShBetterUi.LOGGER.warn("Rich document authority callback failed", failure));
        private long energy = 40;

        private Session(MinecraftServer server) {
            if (!server.isDedicatedServer()) {
                authority.register(DEMO_DOCUMENT, new RichSyncServer.Access<>(
                        player -> server.isSingleplayerOwner(player.getGameProfile()),
                        player -> Map.of(DEMO_VALUE, new Number(energy)),
                        (player, edit) -> {
                            if (!DEMO_VALUE.equals(edit.key()) || !(edit.value() instanceof Number value)
                                    || value.number() < 0 || value.number() > 80) return false;
                            energy = value.number();
                            return true;
                        }));
            }
        }
    }
}
