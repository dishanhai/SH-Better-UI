// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.PublishedNode;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Geometry is immutable. Drawing failures are recorded before publication. Validity follows the mount. */
public final class FrameViewSnapshot {
    private final VisualFrameCoordinator.Binding binding;
    private final long frameId;
    private final long nowMs;
    private final Rect clip;
    private final Map<Node, PublishedNode> nodes;
    private Set<Node> failedNodes;

    FrameViewSnapshot(VisualFrameCoordinator.Binding binding, long frameId, long nowMs,
                      Rect clip, Map<Node, PublishedNode> nodes) {
        this.binding = binding;
        this.frameId = frameId;
        this.nowMs = nowMs;
        this.clip = clip;
        this.nodes = nodes;
    }

    public long frameId() { return frameId; }
    public long nowMs() { return nowMs; }
    public long mountGeneration() { return binding.mountGeneration; }
    public UiId scopeId() { return binding.scope.id(); }
    public long scopeGeneration() { return binding.scope.generation(); }
    public boolean isValid() { return binding.isActive(); }
    public boolean isCurrent() { return isValid() && binding.snapshot == this; }
    public View view(Node node) { return entry(node).view(); }
    public Rect clip(Node node) { return VisualCanvas.clip(clip, view(node)); }

    /** A node with a failed draw callback is ineligible for input for this presentation. */
    public boolean hitTest(Node node, double guiX, double guiY) {
        return (isCurrent() || binding.drawing(this) != null) && !failed(node)
                && clip.contains(guiX, guiY) && view(node).hitTest(guiX, guiY);
    }

    boolean acceptsScope(ScopeHandle candidate) {
        return binding.drawing(this) != null && binding.ownsScope(candidate);
    }

    void markFailed(Node node) {
        if (failedNodes == null) failedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        failedNodes.add(node);
    }

    boolean failed(Node node) { return failedNodes != null && failedNodes.contains(node); }

    /** Place an ordinary native widget only when its full native hit rectangle is visible. */
    public void placeWidget(Node node, AbstractWidget widget) {
        var frame = binding.drawing(this);
        if (frame == null && !isCurrent()) throw new IllegalStateException("Widget placement requires the current snapshot");
        var view = view(node);
        if (!view.renderBounds().equals(view.hitBounds()) || !view.hitBounds().intersect(clip).equals(view.hitBounds())) {
            throw new IllegalArgumentException("Native widget must be fully inside the visual viewport");
        }
        if (frame != null && !isCurrent()) frame.rememberWidgetPosition(widget);
        ShBetterUiApi.layout().placeWidget(widget, view);
    }

    PublishedNode entry(Node node) {
        var entry = nodes.get(node);
        if (entry == null) throw new IllegalArgumentException("Node is absent from this snapshot");
        return entry;
    }

    Rect clip() { return clip; }
}
