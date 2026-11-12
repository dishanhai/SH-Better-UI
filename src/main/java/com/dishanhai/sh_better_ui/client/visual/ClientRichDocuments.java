// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncClient;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ServerMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Value;
import com.dishanhai.sh_better_ui.kernel.internal.richtext.network.PlatformRichSyncNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

import java.util.Objects;
import java.util.function.Consumer;

/** One scoped rich document subscription on the client's current PLAY connection. */
public final class ClientRichDocuments {
    private static final RichSyncClient CLIENT = new RichSyncClient();
    private static Watch active;
    private static Connection connected;

    private ClientRichDocuments() {}

    public static boolean hasActiveWatch() {
        requireClientThread();
        return active != null;
    }

    /** Observes immutable snapshots on the game thread. Use the latest snapshot at layout preparation. */
    public static Watch watch(ScopeHandle scope, UiId document, Consumer<RichSyncClient.View> observer) {
        requireClientThread();
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(observer, "observer");
        if (!scope.isOpen()) throw new IllegalStateException("Rich sync scope is closed");
        if (active != null) throw new IllegalStateException("Only one rich document can be watched at a time");
        var subscription = new Watch(scope);
        active = subscription;
        try {
            scope.track(subscription);
            // Reconcile the native link before a new watch can send on an earlier world's connection.
            tick(Minecraft.getInstance());
            subscription.watch = CLIENT.watch(document, view -> {
                if (!subscription.isActive()) return;
                try {
                    observer.accept(view);
                } catch (RuntimeException failure) {
                    ShBetterUi.LOGGER.warn("Rich document observer failed", failure);
                }
            });
            if (!subscription.isActive()) subscription.watch.close();
            return subscription;
        } catch (RuntimeException failure) {
            subscription.close();
            throw failure;
        }
    }

    public static void tick(Minecraft minecraft) {
        var subscription = active;
        if (subscription == null && connected == null) return;
        var current = connection(minecraft);
        if (current != connected) {
            connected = null;
            CLIENT.disconnect();
            if (current != null && minecraft.level != null && active != null && active.isActive()) {
                connected = current;
                CLIENT.connect(packet -> PlatformRichSyncNetwork.request(current, packet));
            }
        }
        if (connected != null) CLIENT.tick();
    }

    public static void disconnected(Object connection) {
        if (connected != null && connected == connection) {
            connected = null;
            CLIENT.disconnect();
        }
    }

    /** The platform handler schedules this on the client game thread after decoding. */
    public static void receive(Connection source, ServerMessage message) {
        requireClientThread();
        if (connected == source && source == connection(Minecraft.getInstance())) {
            CLIENT.receive(message);
        }
    }

    private static Connection connection(Minecraft minecraft) {
        var listener = minecraft.getConnection();
        if (listener == null) return null;
        var connection = listener.getConnection();
        return connection.isConnected() && PlatformRichSyncNetwork.supported(connection) ? connection : null;
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Rich document sync requires the client game thread");
        }
    }

    public static final class Watch implements AutoCloseable {
        private final ScopeHandle scope;
        private RichSyncClient.Watch watch;

        private Watch(ScopeHandle scope) { this.scope = scope; }

        public boolean isActive() { return active == this && scope.isOpen(); }

        public boolean supported() {
            requireClientThread();
            return isActive() && connected != null;
        }

        public boolean edit(UiId key, Value value, boolean predict) {
            requireClientThread();
            return supported() && watch != null && watch.edit(key, value, predict);
        }

        @Override public void close() {
            requireClientThread();
            if (active != this) return;
            active = null;
            if (watch != null) watch.close();
        }
    }
}
