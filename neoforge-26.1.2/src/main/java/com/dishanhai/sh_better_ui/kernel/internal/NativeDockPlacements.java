// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockPersistence;
import java.util.*;

/** Module-owned schemas backed by the host's real ConfigDiskStore, not a separate filesystem. */
final class NativeDockPlacements {
    private final Map<ModuleContext, Map<UiId, ConfigKey<WindowPlacement>>> keys = new IdentityHashMap<>();

    ConfigKey<WindowPlacement> bind(ModuleContext module, UiId window, WindowPlacement defaults) {
        var configs = module.engine().configs();
        if (!configs.persistence().available()) throw new IllegalStateException("Persistent dock windows require host configuration storage");
        var owned = keys.get(module);
        if (owned != null && owned.containsKey(window)) return owned.get(window);
        if (owned != null && owned.size() >= 256) throw new IllegalStateException("Persistent dock schema limit reached");
        var moduleId = module.descriptor().id();
        // Length prefix avoids aliasing modules whose paths contain slashes.
        var id = new UiId(moduleId.namespace(), "native_dock/" + moduleId.path().length() + "/" + moduleId.path() + "/" + window.path());
        var key = new ConfigKey<>(id, WindowPlacement.class, defaults, Set.of(ConfigLayer.USER_GLOBAL),
                ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.USER_GLOBAL, 1, "Native dock placement",
                Objects::nonNull, value -> value, Map.of());
        configs.define(key);
        var subscription = configs.persist(key, ConfigCodec.of(
                value -> DockPersistence.encode(Map.of(window,value)),
                source -> {
                    if (source.length() > 8192) throw new IllegalArgumentException("Dock placement payload too large");
                    return Objects.requireNonNull(DockPersistence.decode(source).get(window),"Missing window placement");
                }));
        module.scope().track(subscription);
        if (owned == null) {
            owned = new HashMap<>(); keys.put(module,owned);
            module.scope().track(new Subscription() {
                private boolean active=true;
                public boolean isActive() { return active; }
                public void close() { if(active){active=false;keys.remove(module);} }
            });
        }
        owned.put(window,key);
        return key;
    }
    static WindowPlacement read(ModuleContext module,ConfigKey<WindowPlacement> key) {
        return module.engine().configs().get(key);
    }
    static void write(ModuleContext module,ConfigKey<WindowPlacement> key,WindowPlacement placement) {
        var configs=module.engine().configs();
        configs.beginTransaction().set(configs.userGlobal(),key,placement).commit();
    }
}
