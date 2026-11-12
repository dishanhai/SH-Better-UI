// Author: dishanhai
package com.dishanhai.sh_better_ui.bridge.internal;

import com.dishanhai.sh_better_ui.bridge.api.UiBridge;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiComponentDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageValidator;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.internal.protocol.UiServerConnection;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

final class BukkitUiBridge implements UiBridge, Listener, PluginMessageListener {
    private static final String PERMISSION = "shbetteruibridge.use";
    private static final UiProtocolCapabilities CAPABILITIES = UiProtocolCapabilities.standard(
            Map.of(UiProtocol.CONFIRMED_ACTION, UiProtocol.VERSION),
            Set.of(UiPageType.SCREEN, UiPageType.MODAL, UiPageType.HUD, UiPageType.NOTIFICATION));

    private final Plugin plugin;
    private final Server server;
    private final Map<UiId, RegisteredPage> pages = new HashMap<>();
    private final Map<UUID, Connection> connections = new HashMap<>();
    private final Set<Plugin> retiringOwners = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    BukkitUiBridge(Plugin plugin) {
        this.plugin = plugin;
        server = plugin.getServer();
    }

    @Override
    public Registration registerPage(Plugin owner, UiPageDefinition page, Predicate<Player> access,
                                     Map<UiId, ActionHandler> handlers,
                                     Map<UiId, UiComponentDescriptor> customComponents) {
        requireServerThread();
        if (closed || retiringOwners.contains(owner) || !owner.isEnabled() || owner.getServer() != server) {
            throw new IllegalStateException("Page registration requires an enabled plugin on this server");
        }
        Objects.requireNonNull(access, "access");
        handlers = Map.copyOf(handlers);
        requireNamespace(owner, page.id());
        var descriptors = new HashMap<>(UiBuiltinComponents.descriptors());
        Map.copyOf(customComponents).forEach((id, descriptor) -> {
            if (descriptors.putIfAbsent(id, descriptor) != null) {
                throw new IllegalArgumentException("Custom component cannot replace a built-in type: " + id);
            }
        });
        new UiPageValidator(descriptors).requireValid(page, page.type() != UiPageType.CONTAINER);
        if (pages.containsKey(page.id())) {
            throw new IllegalArgumentException("Page is already registered: " + page.id());
        }
        var expectedHandlers = new HashSet<UiId>();
        for (var action : page.actions().values()) {
            if (action.policy() == UiActionPolicy.SERVER || action.policy() == UiActionPolicy.CONFIRMED) {
                expectedHandlers.add(action.id());
            }
        }
        if (!handlers.keySet().equals(expectedHandlers)) {
            throw new IllegalArgumentException("Each authority action must have exactly one handler");
        }
        for (var action : page.actions().values()) {
            if (action.policy() == UiActionPolicy.CONFIRMED && handlers.get(action.id()).confirmationPreview().isEmpty()) {
                throw new IllegalArgumentException("A confirmed action requires an authoritative preview: " + action.id());
            }
        }
        var registration = new RegisteredPage(owner, page, access, handlers);
        pages.put(page.id(), registration);
        return registration;
    }

    @Override
    public boolean isClientAvailable(Player player) {
        requireServerThread();
        Connection connection = connection(player);
        return connection != null && connection.protocol.available();
    }

    @Override
    public CompletionStage<UiOpenResult> openPage(Plugin owner, Player player, UiId pageId,
                                                  Map<String, UiValue> initialData,
                                                  Consumer<UiOpenResult> progress) {
        requireServerThread();
        RegisteredPage page = pages.get(pageId);
        if (page == null) return rejected(UiErrorCode.PAGE_NOT_FOUND);
        if (page.owner != owner || !currentPlayer(player) || !allows(page, player)) {
            return rejected(UiErrorCode.PERMISSION_DENIED);
        }
        if (page.page.type() == UiPageType.CONTAINER) return rejected(UiErrorCode.CONTAINER_UNAVAILABLE);
        Connection connection = connection(player);
        if (connection == null) return rejected(UiErrorCode.CLIENT_UNAVAILABLE);
        if (connection.protocol.available() && !connection.attachActions(page)) {
            return rejected(UiErrorCode.RESOURCE_LIMIT_EXCEEDED);
        }
        return connection.protocol.open(page.page, initialData, progress);
    }

    @Override
    public boolean updatePage(Plugin owner, Player player, UUID instanceId, Map<String, UiValue> updates) {
        requireServerThread();
        Connection connection = connection(player);
        RegisteredPage page = ownedPage(owner, connection, instanceId);
        return page != null && allows(page, player) && connection.protocol.update(instanceId, updates);
    }

    @Override
    public boolean closePage(Plugin owner, Player player, UUID instanceId) {
        requireServerThread();
        Connection connection = connection(player);
        return ownedPage(owner, connection, instanceId) != null && connection.protocol.closePage(instanceId);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!server.isPrimaryThread() || closed || !UiProtocol.CHANNEL.equals(channel)
                || message.length == 0 || message.length > UiProtocol.MAX_FRAME_BYTES
                || !transportAvailable(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        Connection connection = connections.get(id);
        if (connection != null && connection.player != player) {
            disconnect(connection);
            connection = null;
        }
        if (connection == null) {
            connection = new Connection(player);
            connections.put(id, connection);
        }
        connection.protocol.receive(message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        disconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnregisterChannel(PlayerUnregisterChannelEvent event) {
        if (UiProtocol.CHANNEL.equals(event.getChannel())) {
            disconnect(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    void tick() {
        requireServerThread();
        for (Connection connection : List.copyOf(connections.values())) {
            if (transportAvailable(connection.player)) {
                connection.protocol.tick();
                connection.pruneActions();
            } else {
                disconnect(connection);
            }
        }
    }

    void unregisterOwner(Plugin owner) {
        requireServerThread();
        if (!retiringOwners.add(owner)) return;
        try {
            for (RegisteredPage page : List.copyOf(pages.values())) {
                if (page.owner == owner) page.close();
            }
        } finally {
            retiringOwners.remove(owner);
        }
    }

    void shutdown() {
        requireServerThread();
        closed = true;
        pages.clear();
        for (Connection connection : List.copyOf(connections.values())) {
            disconnect(connection);
        }
    }

    private Connection connection(Player player) {
        if (closed || !transportAvailable(player)) return null;
        Connection connection = connections.get(player.getUniqueId());
        return connection != null && connection.player == player ? connection : null;
    }

    private RegisteredPage ownedPage(Plugin owner, Connection connection, UUID instanceId) {
        if (connection == null) return null;
        RegisteredPage page = connection.protocol.pageId(instanceId).map(pages::get).orElse(null);
        return page != null && page.owner == owner && owner.isEnabled() ? page : null;
    }

    private boolean currentPlayer(Player player) {
        return player.isOnline() && server.getPlayer(player.getUniqueId()) == player;
    }

    private boolean transportAvailable(Player player) {
        return currentPlayer(player) && player.getListeningPluginChannels().contains(UiProtocol.CHANNEL);
    }

    private boolean allows(RegisteredPage page, Player player) {
        if (!page.isActive() || !page.owner.isEnabled() || !player.hasPermission(PERMISSION)) return false;
        try {
            return page.access.test(player) && page.isActive() && page.owner.isEnabled();
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Page access check failed for " + page.page.id());
            return false;
        }
    }

    private void disconnect(Player player) {
        Connection connection = connections.get(player.getUniqueId());
        if (connection != null && connection.player == player) disconnect(connection);
    }

    private void disconnect(Connection connection) {
        connections.remove(connection.player.getUniqueId(), connection);
        connection.protocol.close();
    }

    private void requireServerThread() {
        if (!server.isPrimaryThread()) {
            throw new IllegalStateException("SH-Better-UI Bridge requires the Bukkit server thread");
        }
    }

    private static void requireNamespace(Plugin plugin, UiId id) {
        if (!id.namespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Page or action is outside the plugin namespace: " + id);
        }
    }

    private static CompletionStage<UiOpenResult> rejected(UiErrorCode code) {
        return CompletableFuture.completedStage(UiOpenResult.rejected(code));
    }

    private final class RegisteredPage implements Registration {
        private final Plugin owner;
        private final UiPageDefinition page;
        private final Predicate<Player> access;
        private final Map<UiId, ActionHandler> handlers;
        private boolean active = true;

        private RegisteredPage(Plugin owner, UiPageDefinition page, Predicate<Player> access,
                               Map<UiId, ActionHandler> handlers) {
            this.owner = owner;
            this.page = page;
            this.access = access;
            this.handlers = handlers;
        }

        @Override
        public UiId pageId() {
            return page.id();
        }

        @Override
        public boolean isActive() {
            requireServerThread();
            return active && pages.get(page.id()) == this;
        }

        @Override
        public void close() {
            requireServerThread();
            if (!isActive()) return;
            active = false;
            try {
                for (Connection connection : List.copyOf(connections.values())) {
                    connection.detachActions(this);
                }
            } finally {
                pages.remove(page.id(), this);
            }
        }
    }

    private final class Connection {
        private final Player player;
        private final UiServerConnection protocol;
        private final Map<RegisteredPage, List<AutoCloseable>> actionBindings = new HashMap<>();

        private Connection(Player player) {
            this.player = player;
            protocol = new UiServerConnection(player.getUniqueId(), this::send, CAPABILITIES, System::nanoTime);
        }

        private void send(byte[] frame) {
            if (plugin.isEnabled() && transportAvailable(player)) {
                player.sendPluginMessage(plugin, UiProtocol.CHANNEL, frame);
            }
        }

        private boolean attachActions(RegisteredPage page) {
            if (page.handlers.isEmpty()) return true;
            pruneActions();
            if (actionBindings.containsKey(page)) return true;
            var bindings = new ArrayList<AutoCloseable>(page.handlers.size());
            try {
                page.handlers.forEach((id, handler) -> bindings.add(bindAction(page, id, handler)));
            } catch (IllegalStateException limit) {
                closeActions(bindings);
                return false;
            }
            actionBindings.put(page, bindings);
            return true;
        }

        private AutoCloseable bindAction(RegisteredPage page, UiId id, ActionHandler handler) {
            Predicate<UUID> permission = sender -> player.getUniqueId().equals(sender) && allows(page, player);
            Function<UiServerConnection.ActionContext, UiServerConnection.ActionOutcome> execute =
                    context -> execute(handler, context);
            var preview = handler.confirmationPreview();
            if (preview.isEmpty()) return protocol.registerAction(page.page.id(), id, permission, execute);
            return protocol.registerAction(page.page.id(), id, permission,
                    context -> preview.orElseThrow().apply(actionContext(context)), execute);
        }

        private UiServerConnection.ActionOutcome execute(ActionHandler handler, UiServerConnection.ActionContext context) {
            var result = handler.execute(actionContext(context));
            return result.error().map(UiServerConnection.ActionOutcome::failure)
                    .orElseGet(() -> UiServerConnection.ActionOutcome.success(result.updates()));
        }

        private ActionContext actionContext(UiServerConnection.ActionContext context) {
            return new ActionContext(player, context.page().id(), context.instanceId(), context.requestId(),
                    context.actionId(), context.revision(), context.authoritativeData(), context.data());
        }

        private void detachActions(RegisteredPage page) {
            List<AutoCloseable> bindings = actionBindings.remove(page);
            if (bindings != null) closeActions(bindings);
            protocol.closePages(page.page.id());
        }

        private void pruneActions() {
            actionBindings.entrySet().removeIf(entry -> {
                if (protocol.hasPage(entry.getKey().page.id())) return false;
                closeActions(entry.getValue());
                return true;
            });
        }

        private void closeActions(List<AutoCloseable> bindings) {
            for (AutoCloseable action : bindings) {
                try {
                    action.close();
                } catch (Exception failure) {
                    plugin.getLogger().warning("Failed to release a page action");
                }
            }
        }
    }
}
