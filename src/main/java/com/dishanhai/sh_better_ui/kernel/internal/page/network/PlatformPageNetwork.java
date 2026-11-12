// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.network;

import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.nio.charset.StandardCharsets;

/** Forge 1.20.1 raw optional PLAY channel, compatible with Bukkit plugin messaging. */
public final class PlatformPageNetwork {
    private static final ResourceLocation ID = ResourceLocation.tryParse(UiProtocol.CHANNEL);
    private static final ResourceLocation REGISTER = ResourceLocation.tryParse("minecraft:register");
    private static final String VERSION = UiProtocol.VERSION.toString();
    private static final EventNetworkChannel CHANNEL = NetworkRegistry.newEventChannel(ID, () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private PlatformPageNetwork() {}

    public static void register() {
        CHANNEL.addListener(PlatformPageNetwork::receive);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) NativePageServer.tick(event.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) NativePageServer.logout(player);
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NativePageServer.stop(event.getServer()));
    }

    private static void receive(NetworkEvent event) {
        var buffer = event.getPayload();
        if (buffer == null) return;
        var context = event.getSource().get();
        var direction = context.getDirection();
        if (direction != NetworkDirection.PLAY_TO_CLIENT && direction != NetworkDirection.PLAY_TO_SERVER) return;
        context.setPacketHandled(true);
        var connection = context.getNetworkManager();
        byte[] frame;
        try {
            frame = PageFrameGate.read(buffer);
        } finally {
            // Forge's 1.20.1 clientbound event owns a copy; serverbound uses the packet's buffer.
            if (direction == NetworkDirection.PLAY_TO_CLIENT) buffer.release();
        }
        if (direction == NetworkDirection.PLAY_TO_CLIENT) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PageFrameGate.submit(
                    connection, frame, context::enqueueWork, bytes -> ClientUiKernel.receivePageFrame(connection, bytes)));
        } else {
            var player = context.getSender();
            if (player != null) PageFrameGate.submit(connection, frame, context::enqueueWork,
                    bytes -> NativePageServer.receive(player, connection, bytes));
        }
    }

    public static boolean supported(Connection connection) { return CHANNEL.isRemotePresent(connection); }

    public static void announce(Connection connection) {
        if (!NetworkHooks.isVanillaConnection(connection)) return;
        connection.send(new ServerboundCustomPayloadPacket(REGISTER,
                buffer((UiProtocol.CHANNEL + "\0").getBytes(StandardCharsets.UTF_8))));
    }

    public static void sendClient(Connection connection, byte[] frame) {
        connection.send(new ServerboundCustomPayloadPacket(ID, buffer(frame)));
    }

    static void sendServer(Connection connection, byte[] frame) {
        connection.send(new ClientboundCustomPayloadPacket(ID, buffer(frame)));
    }

    static Connection connection(ServerPlayer player) { return player.connection.connection; }

    public static boolean ownsNamespace(Module source, String namespace) {
        return source.isNamed() && ModList.get().getModFiles().stream().anyMatch(file ->
                source.getName().equals(file.moduleName())
                        && file.getMods().stream().anyMatch(mod -> namespace.equals(mod.getModId())));
    }

    private static FriendlyByteBuf buffer(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }
}
