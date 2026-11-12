// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.List;
import java.util.Optional;

/** Client-thread window creation and queries. IDs and lifetimes belong to the calling module. */
public interface WindowService extends UIService {
    /** Mounts on an owned Screen node or on an owned client session for passive HUD rendering. */
    MiniWindowHandle createMiniWindow(ModuleContext module, ScopeHandle scope, MiniWindowSpec spec);
    Optional<MiniWindowHandle> find(ModuleContext module, UiId id);
    List<MiniWindowHandle> windows(ModuleContext module);
}
