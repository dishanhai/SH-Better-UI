// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Map;

/** Replacement presentation for an unavailable, non-action component. */
public record UiComponentAlternative(UiId typeId, Map<String, UiValue> properties, Map<String, String> bindings) {
    public UiComponentAlternative {
        PageNames.id(typeId);
        PageNames.size(properties.size(), PageNames.MAX_COLLECTION_SIZE);
        PageNames.size(bindings.size(), PageNames.MAX_COLLECTION_SIZE);
        properties = Map.copyOf(properties);
        bindings = Map.copyOf(bindings);
        properties.keySet().forEach(PageNames::name);
        bindings.forEach((name, path) -> {
            PageNames.name(name);
            PageNames.name(path);
        });
    }
}
