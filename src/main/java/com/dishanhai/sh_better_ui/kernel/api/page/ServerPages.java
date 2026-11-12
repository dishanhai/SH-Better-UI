// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.internal.page.network.NativePageServer;
import com.dishanhai.sh_better_ui.kernel.internal.page.network.PlatformPageNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Experimental Forge and NeoForge authority API. Call on the owning server thread. */
public final class ServerPages {
    private static final StackWalker CALLERS = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private ServerPages() {}

    public static RegistrationHandle register(MinecraftServer server, UiPageDefinition page,
                                              Predicate<ServerPlayer> mayOpen, Map<UiId, ActionHandler> actions) {
        if (!PlatformPageNetwork.ownsNamespace(CALLERS.getCallerClass().getModule(), page.id().namespace())) {
            throw new SecurityException("Server page namespace must belong to the registering mod");
        }
        return NativePageServer.register(server, page, mayOpen, actions);
    }

    /** Allows a locally requested page ID to obtain authoritative initial data. */
    public static RegistrationHandle registerRequested(MinecraftServer server, UiPageDefinition page,
                                                        Predicate<ServerPlayer> mayOpen,
                                                        Map<UiId, ActionHandler> actions,
                                                        Function<ServerPlayer, Map<String, UiValue>> initialData) {
        if (!PlatformPageNetwork.ownsNamespace(CALLERS.getCallerClass().getModule(), page.id().namespace())) {
            throw new SecurityException("Server page namespace must belong to the registering mod");
        }
        return NativePageServer.register(server, page, mayOpen, actions, Optional.of(initialData));
    }

    public static CompletionStage<UiOpenResult> open(ServerPlayer player, UiId page, Map<String, UiValue> data) {
        return open(player, page, data, ignored -> {});
    }

    public static CompletionStage<UiOpenResult> open(ServerPlayer player, UiId page, Map<String, UiValue> data,
                                                     Consumer<UiOpenResult> progress) {
        return NativePageServer.open(player, page, data, progress);
    }

    /** Open from a matching local block or entity interaction on the player's current connection. */
    public static CompletionStage<UiOpenResult> openForInteraction(ServerPlayer player, UiInteractionTarget target,
                                                                   UiId page, Map<String, UiValue> data) {
        return NativePageServer.openForInteraction(player, page, data, target);
    }

    /** The client must separately grant priority-modal permission. */
    public static CompletionStage<UiOpenResult> openPriorityModal(ServerPlayer player, UiId page,
                                                                  Map<String, UiValue> data) {
        return NativePageServer.openPriorityModal(player, page, data);
    }

    public static boolean update(ServerPlayer player, UUID instance, Map<String, UiValue> changes) {
        return NativePageServer.update(player, instance, changes);
    }

    public static boolean close(ServerPlayer player, UUID instance) {
        return NativePageServer.close(player, instance);
    }

    public record ActionHandler(Predicate<ServerPlayer> mayAct, Function<Action, Outcome> execute,
                                Optional<Function<Action, UiActionPrompt>> preview) {
        public ActionHandler(Predicate<ServerPlayer> mayAct, Function<Action, Outcome> execute) {
            this(mayAct, execute, Optional.empty());
        }

        public ActionHandler {
            Objects.requireNonNull(mayAct);
            Objects.requireNonNull(execute);
            Objects.requireNonNull(preview);
        }

        public static ActionHandler confirmed(Predicate<ServerPlayer> mayAct, Function<Action, UiActionPrompt> preview,
                                              Function<Action, Outcome> execute) {
            return new ActionHandler(mayAct, execute, Optional.of(preview));
        }
    }

    public record Action(ServerPlayer player, UUID instance, UUID request, long revision,
                         Map<String, UiValue> state, Map<String, UiValue> payload) {
        public Action {
            state = Map.copyOf(state);
            payload = Map.copyOf(payload);
        }
    }

    public record Outcome(Map<String, UiValue> changes, Optional<UiErrorCode> error) {
        public Outcome {
            changes = Map.copyOf(changes);
            Objects.requireNonNull(error);
            if (error.isPresent() && !changes.isEmpty()) throw new IllegalArgumentException("Failed action has changes");
        }

        public static Outcome success(Map<String, UiValue> changes) { return new Outcome(changes, Optional.empty()); }

        public static Outcome failed(UiErrorCode error) { return new Outcome(Map.of(), Optional.of(error)); }
    }
}
