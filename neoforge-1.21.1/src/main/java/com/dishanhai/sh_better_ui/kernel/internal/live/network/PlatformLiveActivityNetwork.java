// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.Action;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.ServerMessage;
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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NeoForge optional, directional PLAY payloads for network live activities. */
public final class PlatformLiveActivityNetwork {
    private PlatformLiveActivityNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PlatformLiveActivityNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(PlatformLiveActivityNetwork::logout);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NativeLiveActivityServer.stop());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(LiveActivityWire.CHANNEL_VERSION).optional();
        registrar.playToClient(Clientbound.TYPE, Clientbound.CODEC, (packet, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                Connection connection = context.connection();
                ClientLiveActivityNetwork.receive(connection, packet.message(), action -> reply(connection, action));
            }
        });
        registrar.playToServer(Serverbound.TYPE, Serverbound.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                NativeLiveActivityServer.receive(player, context.connection(), packet.action());
            }
        });
    }

    static Connection connection(ServerPlayer player) {
        return player.connection.getConnection();
    }

    static boolean supported(Connection connection) {
        return NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Clientbound.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Serverbound.TYPE.id());
    }

    static void send(Connection connection, ServerMessage packet) {
        connection.send(new ClientboundCustomPayloadPacket(new Clientbound(packet)));
    }

    private static void reply(Connection connection, Action packet) {
        if (connection.isConnected() && supported(connection)) {
            connection.send(new ServerboundCustomPayloadPacket(new Serverbound(packet)));
        }
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NativeLiveActivityServer.logout(player);
    }

    private record Clientbound(ServerMessage message) implements CustomPacketPayload {
        private static final Type<Clientbound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShBetterUi.MOD_ID, "live_activity/sync_v" + LiveActivityWire.VERSION));
        private static final StreamCodec<FriendlyByteBuf, Clientbound> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeBytes(LiveActivityWire.encode(packet.message())),
                buffer -> new Clientbound(NativeLiveActivityBuffers.readServer(buffer)));

        @Override
        public Type<Clientbound> type() {
            return TYPE;
        }
    }

    private record Serverbound(Action action) implements CustomPacketPayload {
        private static final Type<Serverbound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShBetterUi.MOD_ID, "live_activity/action_v" + LiveActivityWire.VERSION));
        private static final StreamCodec<FriendlyByteBuf, Serverbound> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeBytes(LiveActivityWire.encode(packet.action())),
                buffer -> new Serverbound(NativeLiveActivityBuffers.readAction(buffer)));

        @Override
        public Type<Serverbound> type() {
            return TYPE;
        }
    }
}
