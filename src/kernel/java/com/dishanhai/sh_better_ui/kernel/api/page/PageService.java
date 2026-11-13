// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Experimental declarative pages. Use from the kernel owner thread with a live module context. */
public interface PageService extends UIService {
    ServiceKey<PageService> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:pages"), PageService.class, 1);

    default UiPageDefinition.Builder define(UiId id) { return UiPageDefinition.builder(id); }

    Map<UiId, UiVersion> components();

    Set<UiPageType> pageTypes();

    default RegistrationHandle registerComponent(ModuleContext owner, UiComponentDescriptor descriptor,
                                                 UiComponentFactory factory) {
        throw new UnsupportedOperationException("Declarative component factories are unavailable");
    }

    RegistrationHandle register(ModuleContext owner, UiPageDefinition page);

    /** Registers a local Screen or modal with readability overrides keyed by stable node ID. */
    default RegistrationHandle register(ModuleContext owner, UiPageDefinition page, UiPageTextReadability text) {
        if (!UiPageTextReadability.DEFAULT.equals(Objects.requireNonNull(text, "text"))) {
            throw new UnsupportedOperationException("Declarative page text readability is unavailable");
        }
        return register(owner, page);
    }

    Optional<UiPageDefinition> find(ModuleContext owner, UiId id);

    /** Opens a local page owned by the caller. The callback receives only declared LOCAL actions. */
    Instance open(ModuleContext owner, UiId id, Map<String, UiValue> initialData, Consumer<UiId> onAction);

    /** Changes fields of an owned local page in one revision. */
    boolean update(ModuleContext owner, UUID instanceId, Map<String, UiValue> updates);

    enum State { CREATED, VALIDATING, OPENING, ACTIVE, SUSPENDED, CLOSING, CLOSED, FAILED }

    record Snapshot(UUID instanceId, UiId pageId, State state, long revision,
                    Map<String, UiValue> data, boolean synchronizedData) {
        public Snapshot {
            data = Map.copyOf(data);
        }
    }

    interface Instance extends AutoCloseable {
        Snapshot snapshot();

        /** Temporary input indexed by stable node ID. Available to the local page owner. */
        default Map<String, UiValue> localValues() { return Map.of(); }

        /** True after the first presentation. False when the instance terminates before presentation. */
        CompletionStage<Boolean> displayed();

        /** Final immutable state, published when the page's scope has been released. */
        CompletionStage<Snapshot> closed();

        @Override
        void close();
    }
}
