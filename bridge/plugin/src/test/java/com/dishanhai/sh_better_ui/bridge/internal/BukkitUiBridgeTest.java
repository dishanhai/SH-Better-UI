// Author: dishanhai
package com.dishanhai.sh_better_ui.bridge.internal;

import com.dishanhai.sh_better_ui.bridge.api.UiBridge;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolChannel;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitUiBridgeTest {
    private final UUID playerId = UUID.randomUUID();
    private final AtomicBoolean primaryThread = new AtomicBoolean(true);
    private final AtomicBoolean listening = new AtomicBoolean();
    private final ArrayDeque<byte[]> clientFrames = new ArrayDeque<>();
    private final Player player = proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
        case "getUniqueId" -> playerId;
        case "isOnline", "hasPermission" -> true;
        case "getListeningPluginChannels" -> listening.get() ? Set.of(UiProtocol.CHANNEL) : Set.of();
        case "sendPluginMessage" -> {
            assertEquals(UiProtocol.CHANNEL, args[1]);
            clientFrames.addLast((byte[]) args[2]);
            yield null;
        }
        default -> throw new AssertionError(method);
    });
    private final Server server = proxy(Server.class, (proxy, method, args) -> switch (method.getName()) {
        case "isPrimaryThread" -> primaryThread.get();
        case "getPlayer" -> player;
        default -> throw new AssertionError(method);
    });
    private final Plugin plugin = proxy(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
        case "getServer" -> server;
        case "isEnabled" -> true;
        case "getName" -> "Example";
        default -> throw new AssertionError(method);
    });
    private final BukkitUiBridge bridge = new BukkitUiBridge(plugin);

    @Test
    void vanillaClientDoesNotCreateAnAvailableConnection() {
        var page = page("example:exchange", UiPageType.SCREEN);
        bridge.registerPage(plugin, page, ignored -> true, Map.of());
        assertFalse(bridge.isClientAvailable(player));
        assertEquals(UiErrorCode.CLIENT_UNAVAILABLE, openError(page.id()));
    }

    @Test
    void unknownPageReturnsAnExplicitFailure() {
        var result = bridge.openPage(plugin, player, UiId.parse("example:missing"), Map.of())
                .toCompletableFuture().join();
        assertEquals(UiOpenResult.Status.REJECTED, result.status());
        assertEquals(UiErrorCode.PAGE_NOT_FOUND, result.error().orElseThrow());
    }

    @Test
    void rejectsOffThreadCallsBeforeReadingBukkitPlayerState() {
        primaryThread.set(false);
        assertThrows(IllegalStateException.class, () -> bridge.isClientAvailable(player));
    }

    @Test
    void requiresPluginOwnershipAndReleasesRegistrationsOnDisable() {
        var page = page("example:exchange", UiPageType.SCREEN);
        var registration = bridge.registerPage(plugin, page, ignored -> true, Map.of());
        assertTrue(registration.isActive());
        assertThrows(IllegalArgumentException.class,
                () -> bridge.registerPage(plugin, page, ignored -> true, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> bridge.registerPage(plugin,
                page("foreign:exchange", UiPageType.SCREEN), ignored -> true, Map.of()));
        bridge.unregisterOwner(plugin);
        assertFalse(registration.isActive());
        assertEquals(UiErrorCode.PAGE_NOT_FOUND, openError(page.id()));
        registration.close();
    }

    @Test
    void containerRequestsOfferExplicitNativeFallbackAndHonorAccessPolicy() {
        var page = page("example:container", UiPageType.CONTAINER);
        var registration = bridge.registerPage(plugin, page, ignored -> false, Map.of());
        assertEquals(UiErrorCode.PERMISSION_DENIED, openError(page.id()));
        registration.close();
        bridge.registerPage(plugin, page, ignored -> true, Map.of());
        assertEquals(UiErrorCode.CONTAINER_UNAVAILABLE, openError(page.id()));
    }

    @Test
    void openWaitsForDisplayAndChannelUnregisterClosesPendingPage() {
        var page = page("example:exchange", UiPageType.SCREEN);
        bridge.registerPage(plugin, page, ignored -> true, Map.of());
        listening.set(true);
        assertFalse(bridge.isClientAvailable(player));

        var channel = connect();
        assertTrue(bridge.isClientAvailable(player));

        var opening = bridge.openPage(plugin, player, page.id(), Map.of()).toCompletableFuture();
        var request = assertInstanceOf(UiProtocolMessage.PageOpen.class, nextPayload(channel));
        channel.send(new UiProtocolMessage.PageOpened(request.instanceId(), UiOpenResult.Status.CONFIRMED, Optional.empty()));
        assertFalse(opening.isDone());
        channel.send(new UiProtocolMessage.PageOpened(request.instanceId(), UiOpenResult.Status.DISPLAYED, Optional.empty()));
        assertEquals(UiOpenResult.Status.DISPLAYED, opening.join().status());

        var pending = bridge.openPage(plugin, player, page.id(), Map.of()).toCompletableFuture();
        assertFalse(pending.isDone());
        listening.set(false);
        bridge.onUnregisterChannel(new PlayerUnregisterChannelEvent(player, UiProtocol.CHANNEL));
        assertEquals(UiErrorCode.SESSION_CLOSED, pending.join().error().orElseThrow());
        assertFalse(bridge.isClientAvailable(player));
    }

    @Test
    void actionsWithTheSameIdRemainBoundToTheirRegisteredPage() {
        UiId action = UiId.parse("example:redeem");
        var first = actionPage("example:first", action);
        var second = actionPage("example:second", action);
        var executed = new AtomicInteger();
        var secondAllowed = new AtomicBoolean(true);
        var firstRegistration = bridge.registerPage(plugin, first, ignored -> true, Map.of(action, context -> {
            executed.incrementAndGet();
            return UiBridge.ActionResult.success(Map.of());
        }));
        bridge.registerPage(plugin, second, ignored -> secondAllowed.get(), Map.of(action, context -> {
            executed.addAndGet(2);
            return UiBridge.ActionResult.success(Map.of());
        }));
        var channel = connect();
        UUID firstInstance = display(channel, first.id());
        UUID secondInstance = display(channel, second.id());

        UUID requestId = UUID.randomUUID();
        channel.send(new UiProtocolMessage.ActionRequest(firstInstance, requestId, action, 0, Map.of()));
        assertEquals(UiActionState.SUCCEEDED,
                assertInstanceOf(UiProtocolMessage.ActionResult.class, nextPayload(channel)).state());
        assertEquals(1, executed.get());

        firstRegistration.close();
        assertEquals(firstInstance,
                assertInstanceOf(UiProtocolMessage.PageClose.class, nextPayload(channel)).instanceId());
        channel.send(new UiProtocolMessage.ActionRequest(secondInstance, UUID.randomUUID(), action, 0, Map.of()));
        assertEquals(UiActionState.SUCCEEDED,
                assertInstanceOf(UiProtocolMessage.ActionResult.class, nextPayload(channel)).state());
        assertEquals(3, executed.get());

        secondAllowed.set(false);
        channel.send(new UiProtocolMessage.ActionRequest(secondInstance, UUID.randomUUID(), action, 0, Map.of()));
        assertEquals(UiErrorCode.PERMISSION_DENIED,
                assertInstanceOf(UiProtocolMessage.ActionResult.class, nextPayload(channel)).error().orElseThrow());
        assertEquals(3, executed.get());
    }

    @Test
    void confirmedActionWaitsForTheAuthoritativePreviewToken() {
        UiId action = UiId.parse("example:redeem");
        var page = actionPage("example:confirmed", action, UiActionPolicy.CONFIRMED);
        var commits = new AtomicInteger();
        UiBridge.ActionHandler commit = context -> {
            commits.incrementAndGet();
            return UiBridge.ActionResult.success(Map.of());
        };
        assertThrows(IllegalArgumentException.class,
                () -> bridge.registerPage(plugin, page, ignored -> true, Map.of(action, commit)));
        var prompt = new UiActionPrompt("Test credit", 1, "Receive one experience point.");
        bridge.registerPage(plugin, page, ignored -> true,
                Map.of(action, UiBridge.ActionHandler.confirmed(context -> prompt, commit)));
        var channel = connect(Map.of(UiProtocol.CONFIRMED_ACTION, UiProtocol.VERSION));
        UUID instance = display(channel, page.id());
        UUID request = UUID.randomUUID();

        channel.send(new UiProtocolMessage.ActionRequest(instance, request, action, 0, Map.of()));
        var challenge = assertInstanceOf(UiProtocolMessage.ActionChallenge.class, nextPayload(channel));
        assertEquals(prompt, challenge.prompt());
        assertEquals(0, commits.get());

        channel.send(new UiProtocolMessage.ActionConfirm(instance, request, challenge.token(), true));
        assertEquals(UiActionState.SUCCEEDED,
                assertInstanceOf(UiProtocolMessage.ActionResult.class, nextPayload(channel)).state());
        assertEquals(1, commits.get());
    }

    private UiProtocolChannel connect() {
        return connect(Map.of());
    }

    private UiProtocolChannel connect(Map<UiId, UiVersion> capabilities) {
        listening.set(true);
        var channel = new UiProtocolChannel(UUID.randomUUID(),
                frame -> bridge.onPluginMessageReceived(UiProtocol.CHANNEL, player, frame));
        channel.send(new UiProtocolMessage.Hello(
                UiProtocolCapabilities.standard(capabilities, Set.of(UiPageType.SCREEN))));
        assertInstanceOf(UiProtocolMessage.Capabilities.class, nextPayload(channel));
        return channel;
    }

    private UUID display(UiProtocolChannel channel, UiId pageId) {
        var opening = bridge.openPage(plugin, player, pageId, Map.of()).toCompletableFuture();
        var request = assertInstanceOf(UiProtocolMessage.PageOpen.class, nextPayload(channel));
        channel.send(new UiProtocolMessage.PageOpened(request.instanceId(), UiOpenResult.Status.DISPLAYED, Optional.empty()));
        return opening.join().instanceId();
    }

    private UiProtocolMessage.Payload nextPayload(UiProtocolChannel channel) {
        return channel.receive(clientFrames.removeFirst(), System.nanoTime()).orElseThrow().payload();
    }

    private UiErrorCode openError(UiId pageId) {
        return bridge.openPage(plugin, player, pageId, Map.of()).toCompletableFuture().join().error().orElseThrow();
    }

    private static UiPageDefinition page(String id, UiPageType type) {
        return UiPageDefinition.builder(UiId.parse(id)).type(type)
                .root("root", "text", node -> node.text("Example"))
                .build();
    }

    private static UiPageDefinition actionPage(String id, UiId action) {
        return actionPage(id, action, UiActionPolicy.SERVER);
    }

    private static UiPageDefinition actionPage(String id, UiId action, UiActionPolicy policy) {
        return UiPageDefinition.builder(UiId.parse(id))
                .action(new UiActionDefinition(action, policy, UiDataSchema.EMPTY))
                .root("root", "button", node -> node.label("Redeem").action(action))
                .build();
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
