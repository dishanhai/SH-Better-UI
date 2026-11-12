// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.network;

import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.Set;

/** NeoForge 1.21.1 optional bidirectional PLAY payload, with raw Bukkit framing. */
public final class PlatformPageNetwork {
    private static final ResourceLocation ID = ResourceLocation.tryParse(UiProtocol.CHANNEL);

    private PlatformPageNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PlatformPageNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NativePageServer.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NativePageServer.logout(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NativePageServer.stop(event.getServer()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(UiProtocol.VERSION.toString()).optional().executesOn(HandlerThread.NETWORK)
                .playBidirectional(Frame.TYPE, Frame.CODEC, (packet, context) -> {
                    Connection connection = context.connection();
                    if (context.flow() == PacketFlow.CLIENTBOUND) {
                        if (FMLEnvironment.dist == Dist.CLIENT) PageFrameGate.submit(connection, packet.bytes,
                                context::enqueueWork, bytes -> ClientUiKernel.receivePageFrame(connection, bytes));
                    } else if (context.player() instanceof ServerPlayer player) {
                        PageFrameGate.submit(connection, packet.bytes, context::enqueueWork,
                                bytes -> NativePageServer.receive(player, connection, bytes));
                    }
                });
    }

    public static boolean supported(Connection connection) {
        return NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, ID);
    }

    public static void announce(Connection connection) {
        connection.send(new ServerboundCustomPayloadPacket(new MinecraftRegisterPayload(Set.of(ID))));
    }

    public static void sendClient(Connection connection, byte[] frame) {
        connection.send(new ServerboundCustomPayloadPacket(new Frame(frame)));
    }

    static void sendServer(Connection connection, byte[] frame) {
        connection.send(new ClientboundCustomPayloadPacket(new Frame(frame)));
    }

    static Connection connection(ServerPlayer player) { return player.connection.getConnection(); }

    public static boolean ownsNamespace(Module source, String namespace) {
        return source.isNamed() && ModList.get().getModFiles().stream().anyMatch(file ->
                source.getName().equals(file.moduleName())
                        && file.getMods().stream().anyMatch(mod -> namespace.equals(mod.getModId())));
    }

    private record Frame(byte[] bytes) implements CustomPacketPayload {
        private static final Type<Frame> TYPE = new Type<>(ID);
        private static final StreamCodec<FriendlyByteBuf, Frame> CODEC = StreamCodec.of(
                (buffer, frame) -> buffer.writeBytes(frame.bytes), buffer -> new Frame(PageFrameGate.read(buffer)));

        @Override public Type<Frame> type() { return TYPE; }
    }
}
