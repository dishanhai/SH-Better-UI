// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.Map;

public record UiPageOptions(String title, Map<UiId, UiVersion> requiredCapabilities) {
    public static final UiPageOptions DEFAULT = new UiPageOptions("", Map.of());

    public UiPageOptions {
        PageNames.text(title, 256);
        PageNames.size(requiredCapabilities.size(), PageNames.MAX_COLLECTION_SIZE);
        requiredCapabilities = Map.copyOf(requiredCapabilities);
        requiredCapabilities.keySet().forEach(PageNames::id);
    }
}
