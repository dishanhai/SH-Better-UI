// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.richtext.network;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.visual.ClientRichDocuments;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ClientMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ServerMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncWire;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NeoForge optional, directional PLAY payloads. */
public final class PlatformRichSyncNetwork {
    private PlatformRichSyncNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PlatformRichSyncNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NativeRichSyncServer.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlatformRichSyncNetwork::logout);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NativeRichSyncServer.stop(event.getServer()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(RichSyncWire.CHANNEL_VERSION).optional();
        registrar.playToClient(Clientbound.TYPE, Clientbound.CODEC, (packet, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientRichDocuments.receive(context.connection(), packet.message());
        });
        registrar.executesOn(HandlerThread.NETWORK).playToServer(Serverbound.TYPE, Serverbound.CODEC, (packet, context) -> {
            var connection = context.connection();
            RichSyncAdmission.schedule(connection, context::enqueueWork, () -> {
                if (context.player() instanceof ServerPlayer player) {
                    NativeRichSyncServer.receive(player, connection, packet.message());
                }
            });
        });
    }

    static Connection connection(ServerPlayer player) { return player.connection.getConnection(); }

    public static boolean supported(Connection connection) {
        return NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Clientbound.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Serverbound.TYPE.id());
    }

    static void send(Connection connection, ServerMessage message) {
        connection.send(new ClientboundCustomPayloadPacket(new Clientbound(message)));
    }

    public static void request(Connection connection, ClientMessage message) {
        if (connection.isConnected() && supported(connection)) {
            connection.send(new ServerboundCustomPayloadPacket(new Serverbound(message)));
        }
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NativeRichSyncServer.logout(player);
    }

    private record Clientbound(ServerMessage message) implements CustomPacketPayload {
        private static final Type<Clientbound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShBetterUi.MOD_ID, "rich_sync/state_v" + RichSyncWire.VERSION));
        private static final StreamCodec<FriendlyByteBuf, Clientbound> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeBytes(RichSyncWire.encode(packet.message())),
                buffer -> new Clientbound(NativeRichSyncBuffers.readServer(buffer)));

        @Override public Type<Clientbound> type() { return TYPE; }
    }

    private record Serverbound(ClientMessage message) implements CustomPacketPayload {
        private static final Type<Serverbound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShBetterUi.MOD_ID, "rich_sync/request_v" + RichSyncWire.VERSION));
        private static final StreamCodec<FriendlyByteBuf, Serverbound> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeBytes(RichSyncWire.encode(packet.message())),
                buffer -> new Serverbound(NativeRichSyncBuffers.readClient(buffer)));

        @Override public Type<Serverbound> type() { return TYPE; }
    }
}
