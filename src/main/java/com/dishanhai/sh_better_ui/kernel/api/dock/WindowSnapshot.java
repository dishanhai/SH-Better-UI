// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;

/** Geometry and interaction state sampled at presentation, in GUI coordinates. */
public record WindowSnapshot(long layoutVersion, DockLayoutSnapshot.Entry geometry, Rect contentBounds,
                             CollapseState state, float progress, boolean targetExpanded,
                             boolean hovered, boolean focused, boolean pointerCaptured) {}
