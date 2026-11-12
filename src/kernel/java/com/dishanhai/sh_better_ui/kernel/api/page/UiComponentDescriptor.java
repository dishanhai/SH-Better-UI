// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record UiComponentDescriptor(UiId typeId, UiVersion version, Map<String, UiPropertyDescriptor> properties,
                                    Set<UiId> capabilities, boolean remoteAllowed, boolean acceptsChildren,
                                    boolean acceptsAction, Optional<UiId> fallbackType) {
    public UiComponentDescriptor {
        PageNames.id(typeId);
        Objects.requireNonNull(version, "version");
        PageNames.size(properties.size(), PageNames.MAX_COLLECTION_SIZE);
        properties = Map.copyOf(properties);
        properties.keySet().forEach(PageNames::name);
        PageNames.size(capabilities.size(), PageNames.MAX_COLLECTION_SIZE);
        capabilities = Set.copyOf(capabilities);
        capabilities.forEach(PageNames::id);
        Objects.requireNonNull(fallbackType, "fallbackType").ifPresent(id -> {
            PageNames.id(id);
            if (typeId.equals(id)) {
                throw new IllegalArgumentException("A component cannot be its own fallback");
            }
        });
    }
}
