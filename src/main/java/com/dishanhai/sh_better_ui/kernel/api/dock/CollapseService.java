// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

/** Scope-bound collapse state and frame-clock motion for any UI component. */
public interface CollapseService extends UIService {
    /** Declare this claim in the module descriptor when using this service directly. */
    ResourceClaim PROGRESS_CLAIM = new ResourceClaim(ResourceKind.ANIMATION_PROPERTY,
            UiId.parse("sh_better_ui:collapse/progress"));

    /**
     * Creates an independent controller in a tree or component scope owned by the module.
     * The controller closes with the scope. Call on the kernel UI thread.
     *
     * @throws SecurityException if the module, scope ownership or animation claim is invalid
     * @throws IllegalArgumentException if the scope kind is unsupported
     * @throws IllegalStateException if the scope is closed
     */
    CollapseHandle create(ModuleContext module, ScopeHandle scope, CollapseSpec spec);
}
