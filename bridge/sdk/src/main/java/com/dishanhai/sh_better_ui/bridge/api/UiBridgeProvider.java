// Author: dishanhai
package com.dishanhai.sh_better_ui.bridge.api;

import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;

/** Resolves the bridge through the Bukkit services manager. */
public final class UiBridgeProvider {
    private UiBridgeProvider() {}

    public static Optional<UiBridge> find(Plugin consumer) {
        return Optional.ofNullable(Objects.requireNonNull(consumer, "consumer")
                .getServer().getServicesManager().load(UiBridge.class));
    }

    public static UiBridge get(Plugin consumer) {
        return find(consumer).orElseThrow(() -> new IllegalStateException("SHBetterUIBridge service is unavailable"));
    }
}
