// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import net.minecraft.client.gui.screens.Screen;

/** Opt-in mounting of module trees on native Minecraft Screens. */
public interface ScreenService extends UIService {
    UIEventType<UIPlatform.Viewport> VIEWPORT_CHANGED = new UIEventType<>(UiId.parse("sh_better_ui:viewport_changed"),
            UIPlatform.Viewport.class, false);

    /** Builds a shared UiScreen and associates it with the calling module before it is shown. */
    UiScreen create(ModuleContext module, UiScreenBuilder builder);

    /** Creates a module-owned lifetime that automatically closes with the current native connection. */
    ScopeHandle session(ModuleContext module, UiId id);

    /** Mounts on an active Screen owned by the module's mod file, or created by this service. */
    ScopeHandle mount(ModuleContext module, Screen owner, UiId tree);
}
