// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.LayoutApi;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;

import java.util.Optional;
import java.util.function.Consumer;

/** Incremental measure and arrangement using the existing alignment and native widget helpers. */
public interface LayoutService extends UIService {
    /** Native layout helpers for alignment, pixel projection and fitting styled text to a GUI box. */
    LayoutApi layout();

    Binding bind(ModuleContext module, ScopeHandle node, UiId writer, Runnable measure, Runnable arrange);

    /** Bind one visual layout to an owned mounted Screen tree. The sampler runs once before resolve. */
    default VisualLayoutBinding bindVisual(ModuleContext module, ScopeHandle node, AlignmentLayout layout,
                                           Consumer<VisualLayoutBinding.FrameTime> sample) {
        throw new UnsupportedOperationException("Visual composition requires the native layout service");
    }

    /**
     * Available area for a mounted Screen tree that opts into PUSH. Sample after animation
     * and geometry preparation, for example in the visual layout's frame sampler.
     */
    default Optional<Rect> dockContentArea(ModuleContext module, ScopeHandle node) {
        return Optional.empty();
    }

    /** Use this owner when binding style tokens that change measurement, arrangement or paint. */
    ThemeScope.InvalidationOwner styles(ModuleContext module, ScopeHandle node, ThemeScope theme);

    interface Binding extends Subscription {
        void invalidate();
    }
}
