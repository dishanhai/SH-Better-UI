// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;

/** Client entry points currently available on the 26.x port. */
public final class UiClientApi {
    UiClientApi() {}

    public PreviewRegistry.Registration registerDeveloperPreview(PreviewSpec spec) {
        return ShBetterUiApi.developerPreviews().register(spec);
    }
}
