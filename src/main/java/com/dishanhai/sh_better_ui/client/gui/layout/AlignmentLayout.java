package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment.Snap;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.*;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.DoubleFunction;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/**
 * 山海：页面独占的增量布局。登记、状态更新、求解和读取由同一客户端线程执行。
 * 先更新测量与动画状态，再调用 resolve，绘制和输入使用同一份已发布的 View。
 */
public final class AlignmentLayout {
    public enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT }
    public enum Axis { HORIZONTAL, VERTICAL }
    public enum Strategy { KEEP_ANCHOR, REFLOW, AVOID_OVERLAP, ALLOW_OVERLAY, CLAMP_TO_PARENT }
    public enum Reason { INITIAL, MEASURE, ANIMATION, TARGET, CONSTRAINT, DEPENDENCY, VIEWPORT, DIRECTION }
    public enum Issue { CONFLICT, MISSING_BASELINE, OUTSIDE_PARENT, OVERLAP, POLICY_OVERRIDE }

    /** 同轴冲突的 object 为目标名，遮挡时为阻挡节点名。 */
    public record Diagnostic(Issue issue, String object, Axis axis, Reference state) {}

    /** 源锚点和目标锚点保留在 GUI 坐标，供只读调试绘制。 */
    public record Relation(String target, Axis axis, Anchor sourcePoint, Anchor targetPoint,
                           int priority, boolean selected) {}

    /** 未吸附的约束结果与最近一次失效原因。 */
    public record Debug(Set<Reason> reasons, List<Relation> relations, List<Diagnostic> diagnostics) {}

    /**
     * 同一帧的屏幕空间结果。四种边界均为包围盒，hitTest 使用变换前的交互矩形和屏幕裁剪。
     * transform 可直接用于从控件局部坐标绘制。
     */
    public record View(Rect layoutBounds, Rect renderBounds, Rect visualBounds, Rect hitBounds, Rect clip,
                       Anchor anchor, Anchor opticalCenter, Transform transform, Transform hitTransform, Rect localHit) {
        public boolean hitTest(double guiX, double guiY) {
            return (clip == null || clip.contains(guiX, guiY)) && hitTransform.contains(localHit, guiX, guiY);
        }
    }

    /** 本次刷新求解与直接复用的节点数量。 */
    public record Stats(int solved, int cached, boolean rebuiltGraph) {}

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Set<Node> dirty = new LinkedHashSet<>();
    private final Set<Node> animating = new LinkedHashSet<>();
    private Rect screen;
    private Direction direction = Direction.LEFT_TO_RIGHT;
    private boolean graphDirty;
    private long lastNowMs = Long.MIN_VALUE;
    private boolean lastReducedMotion;
    private Map<Node, PublishedNode> publishedNodes;
    private PixelAlignment publishedGrid;

    public AlignmentLayout(Rect screen) { this.screen = Objects.requireNonNull(screen); }

    /** 新建根节点。名字在同一布局内唯一，并用于诊断。 */
    public Node node(String name, AlignmentFrame frame) { return node(name, null, frame); }

    /** 子节点逻辑原点使用父节点局部坐标，继承父节点姿态与裁剪。 */
    public Node node(String name, Node parent, AlignmentFrame frame) {
        Objects.requireNonNull(name);
        if (name.isBlank() || nodes.containsKey(name)) throw new IllegalArgumentException("Invalid node name: " + name);
        if (parent != null) requireOwned(parent);
        Node node = new Node(name, parent, Objects.requireNonNull(frame));
        nodes.put(name, node);
        publishedNodes = null;
        graphDirty = true;
        mark(node, Reason.INITIAL);
        return node;
    }

    /** 仅直接依赖屏幕的节点和受其结果影响的后继会重新求解。 */
    public void screen(Rect value) {
        if (screen.equals(Objects.requireNonNull(value))) return;
        screen = value;
        for (Node node : nodes.values()) {
            if (node.usesScreen()) mark(node, Reason.VIEWPORT);
        }
    }

    public void direction(Direction value) {
        if (direction == Objects.requireNonNull(value)) return;
        direction = value;
        for (Node node : nodes.values()) {
            for (Constraint constraint : node.constraints) {
                AxisRule rule = constraint.rule.horizontal();
                if (rule != null && (logical(rule.source()) || logical(rule.target()))) {
                    mark(node, Reason.DIRECTION);
                    break;
                }
            }
        }
    }

    /** 在宿主状态更新后求解。无失效时直接返回，依赖环在发布结果前报出节点路径。 */
    public Stats resolve() { return resolve(0, true); }

    /**
     * 用宿主的单调帧时钟刷新。启用了 animateTargetAlignment 的节点会从实时位置接续新目标。
     * reducedMotion 直接使用目标。未启用该功能的节点只由几何或依赖更新驱动。
     */
    public Stats resolve(long nowMs, boolean reducedMotion) {
        boolean rebuild = graphDirty;
        if (rebuild) rebuildGraph();
        if (nowMs != lastNowMs || reducedMotion != lastReducedMotion) {
            for (Node node : animating) mark(node, Reason.ANIMATION);
            lastNowMs = nowMs;
            lastReducedMotion = reducedMotion;
        }
        if (dirty.isEmpty()) return new Stats(0, nodes.size(), rebuild);
        publishedNodes = null;
        var pending = new PriorityQueue<>(Comparator.comparingInt((Node node) -> node.order));
        pending.addAll(dirty);
        int solved = 0;
        while (!pending.isEmpty()) {
            Node node = pending.remove();
            var old = node.result;
            node.result = AlignmentSolver.solve(node, screen, direction, nowMs, reducedMotion);
            if (node.targetMotion != null && node.targetMotion.running()) animating.add(node);
            else animating.remove(node);
            node.reasons.clear();
            dirty.remove(node);
            solved++;
            if (old == null || !node.result.samePlacement(old)) {
                for (Node dependent : node.dependents) {
                    if (mark(dependent, Reason.DEPENDENCY)) pending.add(dependent);
                }
            }
        }
        return new Stats(solved, nodes.size() - solved, rebuild);
    }

    /** 所有已登记节点按创建顺序的快照，适用于调试绘制。 */
    public List<Node> nodes() { return List.copyOf(nodes.values()); }

    /** Immutable presentation and original local render rectangle from the same solver result. */
    public record PublishedNode(View view, Rect localRender, Rect localVisual) {}

    public Map<Node, PublishedNode> snapshot(PixelAlignment grid) {
        Objects.requireNonNull(grid);
        if (publishedNodes != null && grid.equals(publishedGrid)) return publishedNodes;
        var snapshot = new LinkedHashMap<Node, PublishedNode>();
        for (var node : nodes.values()) {
            var frame = node.resolved().current().frame();
            snapshot.put(node, new PublishedNode(node.current(grid), frame.render(), frame.visual().ink()));
        }
        publishedGrid = grid;
        publishedNodes = Map.copyOf(snapshot);
        return publishedNodes;
    }

    private boolean mark(Node node, Reason reason) {
        node.reasons.add(reason);
        return dirty.add(node);
    }

    private void requireOwned(Node node) {
        if (nodes.get(node.name) != node) throw new IllegalArgumentException("Node belongs to another layout: " + node.name);
    }

    private static boolean logical(Align align) { return align == Align.START || align == Align.END; }

    private void rebuildGraph() {
        var incoming = new LinkedHashMap<Node, LinkedHashSet<Node>>();
        var ready = new ArrayDeque<Node>();
        for (Node node : nodes.values()) {
            node.dependents.clear();
            node.flowParticipant = false;
            node.viewed = null;
        }
        for (Node node : nodes.values()) {
            var dependencies = node.dependencies();
            incoming.put(node, dependencies);
            for (Node dependency : dependencies) dependency.dependents.add(node);
            if (!node.previous.isEmpty()) {
                node.flowParticipant = true;
                for (Node previous : node.previous) previous.flowParticipant = true;
            }
            if (dependencies.isEmpty()) ready.addLast(node);
        }
        int order = 0;
        while (!ready.isEmpty()) {
            Node node = ready.removeFirst();
            node.order = order++;
            incoming.remove(node);
            for (Node dependent : node.dependents) {
                var dependencies = incoming.get(dependent);
                dependencies.remove(node);
                if (dependencies.isEmpty()) ready.addLast(dependent);
            }
        }
        if (!incoming.isEmpty()) throw cycle(incoming);
        graphDirty = false;
    }

    private static IllegalStateException cycle(Map<Node, LinkedHashSet<Node>> incoming) {
        var visited = new LinkedHashMap<Node, Integer>();
        Node node = incoming.keySet().iterator().next();
        while (!visited.containsKey(node)) {
            visited.put(node, visited.size());
            node = incoming.get(node).iterator().next();
        }
        var path = new ArrayList<String>();
        int start = visited.get(node);
        visited.forEach((entry, index) -> { if (index >= start) path.add(entry.name); });
        path.add(node.name);
        return new IllegalStateException("Circular visual alignment: " + String.join(" -> ", path));
    }

    /** 单个控件或动态锚点的布局状态。移除前先移除引用此节点的关系和后代。 */
    public final class Node implements AutoCloseable {
        final String name;
        final Node parent;
        final EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        final List<Constraint> constraints = new ArrayList<>();
        private final List<Node> dependents = new ArrayList<>();
        AlignmentFrame current;
        AlignmentFrame target;
        AlignmentSolver.Result result;
        AlignmentTargetMotion targetMotion;
        EnumSet<Strategy> strategies = EnumSet.noneOf(Strategy.class);
        Axis flowAxis = Axis.HORIZONTAL;
        List<Node> previous = List.of();
        double gap;
        private Snap snap = Snap.NONE;
        private boolean flowParticipant;
        private int order;
        private PixelAlignment viewGrid;
        private AlignmentSolver.Result viewed;
        private View currentView;
        private View targetView;

        private Node(String name, Node parent, AlignmentFrame frame) {
            this.name = name;
            this.parent = parent;
            current = target = frame;
        }

        public String name() { return name; }

        /** 设置静态度量，或由音频等连续信号驱动的即时状态。 */
        public void update(AlignmentFrame frame) { update(frame, frame); }

        /** 同一时刻的可见状态及动画目标。相同输入不会造成失效。 */
        public void update(AlignmentFrame current, AlignmentFrame target) {
            update(current, target, null);
        }

        private void update(AlignmentFrame current, AlignmentFrame target, Reason cause) {
            requireOwned(this);
            Objects.requireNonNull(current);
            Objects.requireNonNull(target);
            if (!this.current.equals(current)) {
                if (cause == null) cause = this.current.withPose(current.pose()).equals(current)
                        ? Reason.ANIMATION : Reason.MEASURE;
                mark(this, cause);
                this.current = current;
            }
            if (!this.target.equals(target)) {
                mark(this, Reason.TARGET);
                this.target = target;
            }
        }

        /** 用同一条现有可打断轨道采样实时值和终点，并投影成同种几何。 */
        public void sample(InterruptibleAnimation animation, long nowMs, boolean reducedMotion,
                           DoubleFunction<AlignmentFrame> geometry) {
            double value = animation.sample(nowMs, reducedMotion);
            double destination = animation.target();
            AlignmentFrame frame = geometry.apply(value);
            update(frame, value == destination ? frame : geometry.apply(destination), Reason.ANIMATION);
        }

        /** 让 TARGET 约束的位移从当前已安排位置连续重定向。配合带时钟的 resolve 使用。 */
        public Node animateTargetAlignment(Spring.Preset preset) {
            requireOwned(this);
            Objects.requireNonNull(preset);
            if (targetMotion != null) targetMotion.close();
            targetMotion = new AlignmentTargetMotion(preset);
            mark(this, Reason.ANIMATION);
            return this;
        }

        /** 使用未吸附位置更新绘制，改变策略不会使求解缓存失效。 */
        public Node snap(Snap value) {
            requireOwned(this);
            if (snap != Objects.requireNonNull(value)) {
                snap = value;
                publishedNodes = null;
                viewed = null;
            }
            return this;
        }

        /**
         * 选择布局策略。REFLOW 按指定轴排到所有前项后；AVOID_OVERLAP 只在相交时沿该轴前移。
         * ALLOW_OVERLAY 与前两者互斥。无可用空间时优先满足 CLAMP_TO_PARENT 并报告冲突。
         */
        public Node policies(Axis axis, double gap, List<Node> previous, Strategy... selected) {
            requireOwned(this);
            Objects.requireNonNull(axis);
            nonnegative(gap);
            previous = List.copyOf(previous);
            var strategies = EnumSet.noneOf(Strategy.class);
            for (Strategy strategy : selected) strategies.add(strategy);
            boolean flow = strategies.contains(Strategy.REFLOW) || strategies.contains(Strategy.AVOID_OVERLAP);
            if (flow && strategies.contains(Strategy.ALLOW_OVERLAY)) throw new IllegalArgumentException("Conflicting overlap policies");
            if (!flow && !previous.isEmpty()) throw new IllegalArgumentException("Preceding nodes require a flow policy");
            for (Node node : previous) requireOwned(node);
            if (this.strategies.equals(strategies) && flowAxis == axis && this.gap == gap && this.previous.equals(previous)) {
                return this;
            }
            this.strategies = strategies;
            this.previous = previous;
            flowAxis = axis;
            this.gap = gap;
            changed();
            return this;
        }

        public View current(PixelAlignment grid) { refreshViews(grid); return currentView; }
        public View target(PixelAlignment grid) { refreshViews(grid); return targetView; }

        /** 读取最近一次已发布的诊断。调试查询不会触发布局求解。 */
        public Debug debug() { return resolved().debug(); }

        @Override
        public void close() {
            if (nodes.get(name) != this) return;
            for (Node node : nodes.values()) {
                if (node != this && node.dependencies().contains(this)) {
                    throw new IllegalStateException("Alignment node " + name + " is used by " + node.name);
                }
            }
            nodes.remove(name);
            publishedNodes = null;
            dirty.remove(this);
            animating.remove(this);
            if (targetMotion != null) targetMotion.close();
            constraints.clear();
            dependents.clear();
            graphDirty = true;
        }

        Constraint add(Rule rule) {
            requireOwned(this);
            if (rule.target() instanceof NodeTarget explicit) requireOwned(explicit.node());
            var constraint = new Constraint(this, rule);
            constraints.add(constraint);
            changed();
            return constraint;
        }

        private void changed() {
            graphDirty = true;
            mark(this, Reason.CONSTRAINT);
        }

        private LinkedHashSet<Node> dependencies() {
            var dependencies = new LinkedHashSet<Node>();
            if (parent != null) dependencies.add(parent);
            for (Constraint constraint : constraints) {
                if (constraint.rule.target() instanceof NodeTarget explicit) dependencies.add(explicit.node());
            }
            dependencies.addAll(previous);
            return dependencies;
        }

        private boolean usesScreen() {
            if (parent == null && strategies.contains(Strategy.CLAMP_TO_PARENT)) return true;
            for (Constraint constraint : constraints) {
                var relative = constraint.rule.target();
                if (relative == ParentTarget.SCREEN || (relative == ParentTarget.PARENT && parent == null)) return true;
            }
            return false;
        }

        private AlignmentSolver.Result resolved() {
            requireOwned(this);
            if (result == null) throw new IllegalStateException("Resolve alignment before reading " + name);
            return result;
        }

        private void refreshViews(PixelAlignment grid) {
            var published = resolved();
            Objects.requireNonNull(grid);
            if (viewed == published && grid.equals(viewGrid)) return;
            // 流式布局的间隙可能为零，避免独立吸附把两个邻接边推到一起。
            Snap mode = flowParticipant ? Snap.NONE : snap;
            currentView = published.current().view(grid, mode, published.currentClamp());
            targetView = published.target().view(grid, mode, published.targetClamp());
            viewGrid = grid;
            viewed = published;
        }
    }

    /** 已登记的对齐关系。close 可以重复调用。 */
    public final class Constraint implements AutoCloseable {
        final Node node;
        final Rule rule;

        private Constraint(Node node, Rule rule) {
            this.node = node;
            this.rule = rule;
        }

        @Override
        public void close() { if (node.constraints.remove(this)) node.changed(); }
    }
}
