// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.LongConsumer;

/** Published resource generations and caches using the client resource manager. */
public interface ResourcesService extends UIService {
    long version();

    /** Called on the UI thread after caches are invalidated for a successfully applied generation. */
    Subscription observe(ModuleContext module, ScopeHandle scope, LongConsumer observer);

    /** Cache is invalidated on reload and released with the caller's scope. */
    AlignmentAssets alignmentAssets(ModuleContext module, ScopeHandle scope, ResourceManager resources);

    @FunctionalInterface
    interface DataLoader<T> {
        T prepare(ResourceManager resources) throws Exception;
    }

    record DataSnapshot<T>(long generation, java.util.Optional<T> value) {
        public DataSnapshot { java.util.Objects.requireNonNull(value, "value"); }
    }

    interface Data<T> extends Subscription {
        DataSnapshot<T> snapshot();
    }

    default <T> Data<T> registerData(ModuleContext module, ScopeHandle scope, ResourceDataKey<T> key,
                                     DataLoader<T> loader, java.util.function.Predicate<? super T> validator) {
        throw new UnsupportedOperationException("This resource provider has no managed data pipeline");
    }
}
