// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.function.Consumer;

public interface UIConfigCenter {
    /** Defines a schema in the calling module's namespace. */
    void define(ConfigKey<?> key);

    <T> T get(ConfigKey<T> key);

    <T> T get(ScopeHandle scope, ConfigKey<T> key);

    /** Sets a runtime override in the view's own scope. */
    <T> void set(ConfigKey<T> key, T value);

    ConfigSnapshot snapshot();

    ConfigSnapshot snapshot(ScopeHandle scope);

    ConfigTransaction beginTransaction();

    <T> Subscription observe(ScopeHandle scope, ConfigKey<T> key, Consumer<ValueChange<T>> listener);

    default <T> Subscription persist(ConfigKey<T> key, ConfigCodec<T> codec) {
        throw new UnsupportedOperationException("This configuration provider has no shared persistence");
    }

    default ConfigScope userGlobal() {
        throw new UnsupportedOperationException("This configuration provider has no global scope");
    }

    default ConfigPersistenceSnapshot persistence() {
        return new ConfigPersistenceSnapshot(false, 0, 0, false, "");
    }

    default java.util.concurrent.CompletionStage<Void> flush() {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("This configuration provider has no shared persistence"));
    }
}
