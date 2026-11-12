// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.network;

import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.ServerPages;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageValidator;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionProof;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolLimits;
import com.dishanhai.sh_better_ui.kernel.internal.protocol.UiServerConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Shared server-side transport and registry for both mod loaders. */
public final class NativePageServer {
    private static final UiProtocolCapabilities OFFER = new UiProtocolCapabilities(Set.of(UiProtocol.VERSION),
            Set.of(UiPageDefinition.SCHEMA_VERSION), capabilities(),
            Set.of(UiPageType.SCREEN, UiPageType.MODAL, UiPageType.HUD, UiPageType.NOTIFICATION), UiProtocol.MAX_MESSAGE_BYTES);
    private static final Map<MinecraftServer, Registry> SERVERS = new ConcurrentHashMap<>();

    private NativePageServer() {}

    private static Map<UiId, UiVersion> capabilities() {
        var components = UiBuiltinComponents.descriptors().values().stream()
                .filter(descriptor -> descriptor.remoteAllowed())
                .collect(Collectors.toMap(descriptor -> descriptor.typeId(), descriptor -> descriptor.version()));
        components.put(UiProtocol.CONFIRMED_ACTION, UiProtocol.VERSION);
        components.put(UiProtocol.INTERACTION_OPEN, UiProtocol.VERSION);
        components.put(UiProtocol.PRIORITY_MODAL, UiProtocol.VERSION);
        return Map.copyOf(components);
    }

    public static RegistrationHandle register(MinecraftServer server, UiPageDefinition page,
            Predicate<ServerPlayer> mayOpen, Map<UiId, ServerPages.ActionHandler> handlers) {
        return register(server, page, mayOpen, handlers, Optional.empty());
    }

    public static RegistrationHandle register(MinecraftServer server, UiPageDefinition page,
            Predicate<ServerPlayer> mayOpen, Map<UiId, ServerPages.ActionHandler> handlers,
            Optional<Function<ServerPlayer, Map<String, UiValue>>> requestedData) {
        ownerThread(server);
        Objects.requireNonNull(mayOpen);
        var actions = Map.copyOf(handlers);
        new UiPageValidator().requireValid(page, true);
        if (!OFFER.pageTypes().contains(page.type())) throw new IllegalArgumentException("Unsupported page surface");
        if (!actions.keySet().equals(page.actions().keySet()) || page.actions().values().stream().anyMatch(action ->
                action.policy() != UiActionPolicy.SERVER && action.policy() != UiActionPolicy.CONFIRMED
                        || (action.policy() == UiActionPolicy.CONFIRMED) != actions.get(action.id()).preview().isPresent())) {
            throw new IllegalArgumentException("Each authority action needs a matching handler and confirmation policy");
        }
        var registry = SERVERS.computeIfAbsent(server, ignored -> new Registry());
        if (registry.pages.size() >= 256) throw new IllegalStateException("Server page registry is full");
        int actionCount = registry.pages.values().stream().mapToInt(current -> current.actions.size()).sum();
        if (actionCount + actions.size() > UiProtocolLimits.DEFAULT.maxActionRegistrations()) {
            throw new IllegalStateException("Server page action registry is full");
        }
        var entry = new Registered(page, mayOpen, actions, Objects.requireNonNull(requestedData));
        if (registry.pages.putIfAbsent(page.id(), entry) != null) {
            throw new IllegalArgumentException("Server page already registered");
        }
        for (var peer : registry.peers.values()) bind(peer, entry);
        return new RegistrationHandle() {
            @Override public Status status() {
                ownerThread(server);
                return registry.pages.get(page.id()) == entry ? Status.ACTIVE : Status.REVOKED;
            }

            @Override public void close() {
                ownerThread(server);
                if (!registry.pages.remove(page.id(), entry)) return;
                for (var peer : List.copyOf(registry.peers.values())) {
                    peer.session.closePages(page.id());
                    var handles = peer.actions.remove(page.id());
                    if (handles != null) for (var handle : handles) closeAction(handle);
                }
            }
        };
    }

    private static void bind(Peer peer, Registered page) {
        var handles = page.actions.entrySet().stream().map(entry -> {
            var handler = entry.getValue();
            Predicate<UUID> permission = ignored -> {
                var current = currentPlayer(peer);
                return current != null && page.mayOpen.test(current) && handler.mayAct().test(current);
            };
            Function<UiServerConnection.ActionContext, UiServerConnection.ActionOutcome> execute = action -> {
                var current = currentPlayer(peer);
                if (current == null) return UiServerConnection.ActionOutcome.failure(UiErrorCode.SESSION_CLOSED);
                var result = handler.execute().apply(action(current, action));
                return result.error().map(UiServerConnection.ActionOutcome::failure)
                        .orElseGet(() -> UiServerConnection.ActionOutcome.success(result.changes()));
            };
            if (handler.preview().isPresent()) {
                var preview = handler.preview().orElseThrow();
                return peer.session.registerAction(page.definition.id(), entry.getKey(), permission,
                        action -> preview.apply(action(Objects.requireNonNull(currentPlayer(peer)), action)), execute);
            }
            return peer.session.registerAction(page.definition.id(), entry.getKey(), permission, execute);
        }).toList();
        peer.actions.put(page.definition.id(), handles);
    }

    private static ServerPages.Action action(ServerPlayer current, UiServerConnection.ActionContext action) {
        return new ServerPages.Action(current, action.instanceId(), action.requestId(), action.revision(),
                action.authoritativeData(), action.data());
    }

    private static void closeAction(AutoCloseable handle) {
        try {
            handle.close();
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to release page action", failure);
        }
    }

    static void receive(ServerPlayer player, Connection connection, byte[] frame) {
        var server = player.server;
        ownerThread(server);
        if (!connection.isConnected() || PlatformPageNetwork.connection(player) != connection) return;
        var registry = SERVERS.computeIfAbsent(server, ignored -> new Registry());
        var peer = registry.peers.get(player.getUUID());
        if (peer == null || peer.connection != connection) {
            if (peer != null) peer.session.close();
            var session = new UiServerConnection(player.getUUID(), bytes -> {
                if (!connection.isConnected()) throw new IllegalStateException("Page connection closed");
                PlatformPageNetwork.sendServer(connection, bytes);
            }, OFFER, System::nanoTime);
            peer = new Peer(player, connection, session, new LinkedHashMap<>());
            var bound = peer;
            session.onPageRequest(request -> requested(registry, bound, request));
            for (var page : registry.pages.values()) bind(peer, page);
            registry.peers.put(player.getUUID(), peer);
        }
        peer.session.receive(frame);
    }

    private static ServerPlayer currentPlayer(Peer peer) {
        var current = peer.player.server.getPlayerList().getPlayer(peer.player.getUUID());
        return current != null && PlatformPageNetwork.connection(current) == peer.connection && peer.connection.isConnected()
                ? current : null;
    }

    public static CompletionStage<UiOpenResult> open(ServerPlayer player, UiId id, Map<String, UiValue> initial,
                                                     Consumer<UiOpenResult> progress) {
        ownerThread(player.server);
        var registry = SERVERS.get(player.server);
        var page = registry == null ? null : registry.pages.get(id);
        if (page == null) return rejected(UiErrorCode.PAGE_NOT_FOUND);
        var peer = peer(player);
        if (peer == null) return rejected(UiErrorCode.CLIENT_UNAVAILABLE);
        return peer.session.open(page.definition, initial, progress, authorization(registry, peer, page));
    }

    public static CompletionStage<UiOpenResult> openForInteraction(ServerPlayer player, UiId id,
            Map<String, UiValue> initial, UiInteractionTarget target) {
        ownerThread(player.server);
        var registry = SERVERS.get(player.server);
        var page = registry == null ? null : registry.pages.get(id);
        if (page == null) return rejected(UiErrorCode.PAGE_NOT_FOUND);
        var peer = peer(player);
        if (peer == null) return rejected(UiErrorCode.CLIENT_UNAVAILABLE);
        if (!nearTarget(player, target)) return rejected(UiErrorCode.INVALID_INTERACTION);
        var permitted = authorization(registry, peer, page);
        return peer.session.openForInteraction(page.definition, initial, ignored -> {},
                () -> {
                    var current = currentPlayer(peer);
                    return current != null && nearTarget(current, target) && permitted.getAsBoolean();
                }, target);
    }

    public static CompletionStage<UiOpenResult> openPriorityModal(ServerPlayer player, UiId id,
                                                                 Map<String, UiValue> initial) {
        ownerThread(player.server);
        var registry = SERVERS.get(player.server);
        var page = registry == null ? null : registry.pages.get(id);
        if (page == null) return rejected(UiErrorCode.PAGE_NOT_FOUND);
        var peer = peer(player);
        if (peer == null) return rejected(UiErrorCode.CLIENT_UNAVAILABLE);
        return peer.session.openPriorityModal(page.definition, initial, ignored -> {}, authorization(registry, peer, page));
    }

    private static BooleanSupplier authorization(Registry registry, Peer peer, Registered page) {
        return () -> {
            var current = currentPlayer(peer);
            return current != null && registry.pages.get(page.definition.id()) == page && page.mayOpen.test(current)
                    && registry.pages.get(page.definition.id()) == page && currentPlayer(peer) == current;
        };
    }

    private static boolean nearTarget(ServerPlayer player, UiInteractionTarget target) {
        var level = player.serverLevel();
        var dimension = UiId.parse(level.dimension().location().toString());
        if (target instanceof UiInteractionTarget.Block block) {
            if (!block.dimension().equals(dimension)
                    || player.distanceToSqr(block.x() + 0.5, block.y() + 0.5, block.z() + 0.5) > 64) return false;
            var pos = new BlockPos(block.x(), block.y(), block.z());
            return level.hasChunkAt(pos) && !level.getBlockState(pos).isAir();
        }
        if (target instanceof UiInteractionTarget.Entity entity && entity.dimension().equals(dimension)) {
            var selected = level.getEntity(entity.entityId());
            return selected != null && selected.isAlive() && player.distanceToSqr(selected) <= 64;
        }
        return false;
    }

    private static void requested(Registry registry, Peer peer, UiInteractionProof proof) {
        if (!(proof.target() instanceof UiInteractionTarget.Page target)) return;
        var current = currentPlayer(peer);
        var page = registry.pages.get(target.pageId());
        if (current == null) return;
        if (page == null || page.requestedData.isEmpty()) {
            peer.session.rejectInteraction(proof, UiErrorCode.PAGE_NOT_FOUND);
            return;
        }
        var permitted = authorization(registry, peer, page);
        try {
            if (!permitted.getAsBoolean()) {
                peer.session.rejectInteraction(proof, UiErrorCode.PERMISSION_DENIED);
                return;
            }
            var initial = page.requestedData.orElseThrow().apply(current);
            peer.session.openForInteraction(page.definition, initial, ignored -> {}, permitted, target)
                    .thenAccept(result -> result.error().ifPresent(error -> peer.session.rejectInteraction(proof, error)));
        } catch (RuntimeException failure) {
            peer.session.rejectInteraction(proof, UiErrorCode.INTERNAL_ERROR);
        }
    }

    private static CompletionStage<UiOpenResult> rejected(UiErrorCode error) {
        return CompletableFuture.completedStage(UiOpenResult.rejected(error));
    }

    public static boolean update(ServerPlayer player, UUID instance, Map<String, UiValue> changes) {
        var peer = peer(player);
        return peer != null && peer.session.update(instance, changes);
    }

    public static boolean close(ServerPlayer player, UUID instance) {
        var peer = peer(player);
        return peer != null && peer.session.closePage(instance);
    }

    private static Peer peer(ServerPlayer player) {
        ownerThread(player.server);
        var registry = SERVERS.get(player.server);
        var peer = registry == null ? null : registry.peers.get(player.getUUID());
        return peer != null && currentPlayer(peer) == player ? peer : null;
    }

    static void tick(MinecraftServer server) {
        var registry = SERVERS.get(server);
        if (registry == null) return;
        for (var peer : List.copyOf(registry.peers.values())) {
            if (!peer.connection.isConnected()) logout(peer.player);
            else peer.session.tick();
        }
    }

    static void logout(ServerPlayer player) {
        var registry = SERVERS.get(player.server);
        var peer = registry == null ? null : registry.peers.get(player.getUUID());
        if (peer == null || peer.connection != PlatformPageNetwork.connection(player)) return;
        registry.peers.remove(player.getUUID(), peer);
        peer.session.close();
    }

    static void stop(MinecraftServer server) {
        var registry = SERVERS.remove(server);
        if (registry != null) {
            for (var peer : registry.peers.values()) peer.session.close();
            registry.pages.clear();
            registry.peers.clear();
        }
    }

    private static void ownerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Page authority requires the server owner thread");
    }

    private static final class Registry {
        final Map<UiId, Registered> pages = new LinkedHashMap<>();
        final Map<UUID, Peer> peers = new LinkedHashMap<>();
    }

    private record Registered(UiPageDefinition definition, Predicate<ServerPlayer> mayOpen,
                              Map<UiId, ServerPages.ActionHandler> actions,
                              Optional<Function<ServerPlayer, Map<String, UiValue>>> requestedData) {}

    private record Peer(ServerPlayer player, Connection connection, UiServerConnection session,
                        Map<UiId, List<AutoCloseable>> actions) {}
}
