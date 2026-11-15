// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.kernel.api.UIModule;

import java.util.List;

/** Optional module declaration discovered through META-INF/sh_better_ui.modules. */
public interface DeveloperPreviewProvider extends UIModule {
    /**
     * Called on the client thread after {@link #start} succeeds. IDs must use the module namespace.
     * Return declarations only. The gallery creates each scene on demand and removes the entries
     * when the module stops.
     */
    List<PreviewSpec> developerPreviews();
}
