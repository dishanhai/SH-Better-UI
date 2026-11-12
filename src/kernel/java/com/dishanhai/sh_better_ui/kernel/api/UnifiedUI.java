// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.internal.EnginePublication;

/** Entry point for consumers of the client UI kernel. */
public final class UnifiedUI {
    private UnifiedUI() {
    }

    /** @throws IllegalStateException if the client kernel has no published engine */
    public static UIEngine get() {
        UIEngine engine = EnginePublication.clientEngine();
        if (engine == null) {
            throw new IllegalStateException("The client UI engine has not been initialized or is no longer running");
        }
        return engine;
    }

    public static boolean isAvailable() {
        return EnginePublication.clientEngine() != null;
    }

    public static PageService pages() {
        return get().require(PageService.KEY);
    }
}
