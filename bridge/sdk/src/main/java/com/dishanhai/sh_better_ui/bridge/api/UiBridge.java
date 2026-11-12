// Author: dishanhai
package com.dishanhai.sh_better_ui.bridge.api;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiComponentDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Experimental Bukkit SDK. Call on the server thread and load the service through {@link UiBridgeProvider}. */
public interface UiBridge {
    default Registration registerPage(Plugin owner, UiPageDefinition page, Predicate<Player> access,
                                      Map<UiId, ActionHandler> handlers) {
        return registerPage(owner, page, access, handlers, Map.of());
    }

    /** Registers one page in the plugin's lower-case name namespace. Access is checked before opening and on actions. */
    Registration registerPage(Plugin owner, UiPageDefinition page, Predicate<Player> access,
                              Map<UiId, ActionHandler> handlers,
                              Map<UiId, UiComponentDescriptor> customComponents);

    boolean isClientAvailable(Player player);

    /** Completes after the client reports the final open state, or after an explicit rejection, timeout, or disconnect. */
    default CompletionStage<UiOpenResult> openPage(Plugin owner, Player player, UiId pageId,
                                                   Map<String, UiValue> initialData) {
        return openPage(owner, player, pageId, initialData, result -> {});
    }

    /** Delivers intermediate protocol states on the server thread and returns a terminal result. */
    CompletionStage<UiOpenResult> openPage(Plugin owner, Player player, UiId pageId, Map<String, UiValue> initialData,
                                           Consumer<UiOpenResult> progress);

    boolean updatePage(Plugin owner, Player player, UUID instanceId, Map<String, UiValue> updates);

    boolean closePage(Plugin owner, Player player, UUID instanceId);

    interface Registration extends AutoCloseable {
        UiId pageId();

        boolean isActive();

        @Override
        void close();
    }

    @FunctionalInterface
    interface ActionHandler {
        /** Recheck business permissions and preconditions and commit the transaction before returning success. */
        ActionResult execute(ActionContext context);

        default Optional<Function<ActionContext, UiActionPrompt>> confirmationPreview() {
            return Optional.empty();
        }

        /** The preview describes the effect and may read authoritative state. Keep transaction writes in execute. */
        static ActionHandler confirmed(Function<ActionContext, UiActionPrompt> preview, ActionHandler execute) {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(execute, "execute");
            return new ActionHandler() {
                @Override public ActionResult execute(ActionContext context) { return execute.execute(context); }

                @Override
                public Optional<Function<ActionContext, UiActionPrompt>> confirmationPreview() {
                    return Optional.of(preview);
                }
            };
        }
    }

    record ActionContext(Player player, UiId pageId, UUID instanceId, UUID requestId, UiId actionId,
                         long revision, Map<String, UiValue> authoritativeData, Map<String, UiValue> parameters) {
        public ActionContext {
            authoritativeData = Map.copyOf(authoritativeData);
            parameters = Map.copyOf(parameters);
        }
    }

    record ActionResult(Map<String, UiValue> updates, Optional<UiErrorCode> error) {
        public ActionResult {
            updates = Map.copyOf(updates);
            Objects.requireNonNull(error, "error");
            if (error.isPresent() && !updates.isEmpty()) {
                throw new IllegalArgumentException("A failed action cannot publish data updates");
            }
        }

        public static ActionResult success(Map<String, UiValue> updates) {
            return new ActionResult(updates, Optional.empty());
        }

        public static ActionResult failure(UiErrorCode error) {
            return new ActionResult(Map.of(), Optional.of(error));
        }
    }
}
