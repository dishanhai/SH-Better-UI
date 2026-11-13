// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.PublishedNode;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;

import java.util.Map;
import java.util.Objects;

/** Immutable visual layout coordinates captured during the current native Screen extraction. */
public final class FrameViewSnapshot {
    private final ScopeHandle scope;
    private final long frameId;
    private final long mountGeneration;
    private final long nowMs;
    private final Rect viewport;
    private final Map<Node, PublishedNode> nodes;

    private FrameViewSnapshot(ScopeHandle scope, long frameId, long mountGeneration, long nowMs, Rect viewport,
                              Map<Node, PublishedNode> nodes) {
        this.scope = scope;
        this.frameId = frameId;
        this.mountGeneration = mountGeneration;
        this.nowMs = nowMs;
        this.viewport = viewport;
        this.nodes = nodes;
    }

    /** Captures a resolved layout using the mount and scale of the live Screen. */
    public static FrameViewSnapshot capture(ModuleContext module, ScopeHandle scope, AlignmentLayout layout, Rect viewport) {
        var stamp = ClientUiKernel.screenStamp(module, scope);
        return new FrameViewSnapshot(scope, stamp.frameId(), stamp.mountGeneration(), stamp.nowMs(),
                Objects.requireNonNull(viewport), layout.snapshot(new PixelAlignment(stamp.guiScale())));
    }

    public long frameId() { return frameId; }
    public long nowMs() { return nowMs; }
    public long mountGeneration() { return mountGeneration; }
    public UiId scopeId() { return scope.id(); }
    public long scopeGeneration() { return scope.generation(); }
    public boolean isValid() { return scope.isOpen(); }
    public boolean isCurrent() { return isValid() && ClientUiKernel.isCurrentScreenFrame(frameId, mountGeneration); }
    public View view(Node node) {
        var entry = nodes.get(node);
        if (entry == null) throw new IllegalArgumentException("Node is absent from this snapshot");
        return entry.view();
    }
    public Rect clip(Node node) {
        var clip = view(node).clip();
        return clip == null ? viewport : viewport.intersect(clip);
    }
    public boolean hitTest(Node node, double x, double y) {
        return isCurrent() && clip(node).contains(x, y) && view(node).hitTest(x, y);
    }
}
