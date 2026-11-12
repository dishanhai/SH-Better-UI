// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.richtext.network;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.visual.ClientRichDocuments;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ClientMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ServerMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncWire;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge optional, directional PLAY packets. */
public final class PlatformRichSyncNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "rich_sync_v" + RichSyncWire.VERSION),
            () -> RichSyncWire.CHANNEL_VERSION,
            NetworkRegistry.acceptMissingOr(RichSyncWire.CHANNEL_VERSION),
            NetworkRegistry.acceptMissingOr(RichSyncWire.CHANNEL_VERSION));

    private PlatformRichSyncNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Clientbound.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> buffer.writeBytes(RichSyncWire.encode(packet.message())))
                .decoder(buffer -> new Clientbound(NativeRichSyncBuffers.readServer(buffer)))
                .consumerMainThread((packet, context) -> {
                    var connection = context.get().getNetworkManager();
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> ClientRichDocuments.receive(connection, packet.message()));
                }).add();
        CHANNEL.messageBuilder(Serverbound.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> buffer.writeBytes(RichSyncWire.encode(packet.message())))
                .decoder(buffer -> new Serverbound(NativeRichSyncBuffers.readClient(buffer)))
                .consumerNetworkThread((packet, supplied) -> {
                    var context = supplied.get();
                    var connection = context.getNetworkManager();
                    RichSyncAdmission.schedule(connection, context::enqueueWork, () -> {
                        var player = context.getSender();
                        if (player != null) NativeRichSyncServer.receive(player, connection, packet.message());
                    });
                    return true;
                }).add();
        MinecraftForge.EVENT_BUS.addListener(PlatformRichSyncNetwork::tick);
        MinecraftForge.EVENT_BUS.addListener(PlatformRichSyncNetwork::logout);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NativeRichSyncServer.stop(event.getServer()));
    }

    static Connection connection(ServerPlayer player) { return player.connection.connection; }

    public static boolean supported(Connection connection) { return CHANNEL.isRemotePresent(connection); }

    static void send(Connection connection, ServerMessage message) {
        CHANNEL.sendTo(new Clientbound(message), connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void request(Connection connection, ClientMessage message) {
        if (connection.isConnected() && supported(connection)) {
            CHANNEL.sendTo(new Serverbound(message), connection, NetworkDirection.PLAY_TO_SERVER);
        }
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) NativeRichSyncServer.tick(event.getServer());
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NativeRichSyncServer.logout(player);
    }

    private record Clientbound(ServerMessage message) {}
    private record Serverbound(ClientMessage message) {}
}
