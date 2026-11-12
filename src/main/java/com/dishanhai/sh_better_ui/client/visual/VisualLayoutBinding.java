// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;

import java.util.Optional;
import java.util.function.Consumer;

/** One managed visual layout on a native Screen mount. Client-thread only. */
public interface VisualLayoutBinding extends Subscription {
    record FrameTime(long frameId, long nowMs, boolean reducedMotion) {}

    /** Resolves once inside native Screen rendering. Successful composition is published after foreground drawing. */
    void renderScreen(UiFrame frame, Consumer<LayoutPass> draw);

    /** The last published presentation for input. Input never advances animations or resolves new geometry. */
    Optional<FrameViewSnapshot> currentSnapshot();

    default boolean hitTest(Node node, double guiX, double guiY) {
        return currentSnapshot().filter(snapshot -> snapshot.hitTest(node, guiX, guiY)).isPresent();
    }

    interface LayoutPass {
        FrameViewSnapshot snapshot();
        void at(Node node, Consumer<VisualNodePass> draw);
    }
}
