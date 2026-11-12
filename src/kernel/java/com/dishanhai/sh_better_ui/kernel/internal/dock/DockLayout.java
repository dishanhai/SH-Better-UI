// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockCollisionResult;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockEdge;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockLayoutSnapshot.Entry;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockLayoutSnapshot.Insets;
import com.dishanhai.sh_better_ui.kernel.api.dock.ExpansionLayoutMode;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Stateless dock solver. The same set of inputs gives the same bounds regardless of registration order. */
public final class DockLayout {
    private static final Comparator<Item> PRIORITY_ORDER = Comparator.comparingInt(Item::priority)
            .reversed().thenComparing(Item::id);
    private static final Comparator<Item> PAINT_ORDER = Comparator.comparingInt(Item::zIndex)
            .thenComparing(Item::id);

    private DockLayout() {}

    /** Preferred placement and the sampled animation value for this frame. */
    public record Item(UiId id, WindowPlacement placement, double compactWidth, double compactHeight,
                       double progress, int priority, int zIndex, ExpansionLayoutMode expansionLayout) {
        public Item {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(expansionLayout, "expansionLayout");
            if (!Double.isFinite(compactWidth) || compactWidth <= 0 || !Double.isFinite(compactHeight)
                    || compactHeight <= 0 || !Double.isFinite(progress)) {
                throw new IllegalArgumentException("Invalid compact dimensions or animation progress");
            }
            compactWidth = Math.min(compactWidth, placement.width());
            compactHeight = Math.min(compactHeight, placement.height());
            progress = clamp(progress, 0, 1);
        }

        private double width() {
            return compactWidth + (placement.width() - compactWidth) * progress;
        }

        private double height() {
            return compactHeight + (placement.height() - compactHeight) * progress;
        }
    }

    /**
     * Pack the compact lengths first, then grant remaining length to expanded panels in the same order.
     * Groups share the rank of their highest-ranked member and stay together on each edge.
     * Compact windows that do not fit are published as invisible overflow entries for this frame.
     * Different edges and floating panels can overlap, with the intersections reported in the snapshot.
     */
    public static DockLayoutSnapshot solve(long version, Rect safeArea, Collection<Item> windows, double gap) {
        requireGui(safeArea);
        if (!Double.isFinite(gap) || gap < 0) throw new IllegalArgumentException("Invalid dock gap");
        var paintOrder = windows.stream().sorted(PAINT_ORDER).toList();
        Set<UiId> ids = new HashSet<>();
        Map<DockEdge, List<Item>> byEdge = new EnumMap<>(DockEdge.class);
        Map<UiId, Entry> solved = new HashMap<>();
        for (Item item : paintOrder) {
            if (!ids.add(item.id)) throw new IllegalArgumentException("Duplicate window ID " + item.id);
            if (item.placement.mode().onEdge()) {
                byEdge.computeIfAbsent(item.placement.edge(), ignored -> new ArrayList<>()).add(item);
            } else {
                solved.put(item.id, floating(item, safeArea));
            }
        }
        double[] push = new double[DockEdge.values().length];
        for (var edge : byEdge.entrySet()) {
            pack(edge.getKey(), edge.getValue(), safeArea, gap, solved, push);
        }
        fitOpposingInsets(push, DockEdge.LEFT, DockEdge.RIGHT, safeArea.width());
        fitOpposingInsets(push, DockEdge.TOP, DockEdge.BOTTOM, safeArea.height());
        Insets insets = new Insets(push[DockEdge.TOP.ordinal()], push[DockEdge.RIGHT.ordinal()],
                push[DockEdge.BOTTOM.ordinal()], push[DockEdge.LEFT.ordinal()]);
        Map<UiId, Entry> entries = new LinkedHashMap<>();
        for (Item item : paintOrder) entries.put(item.id, solved.get(item.id));
        return new DockLayoutSnapshot(version, safeArea, entries, insets, collisions(entries));
    }

    /**
     * Select the nearest safe edge to the corresponding window border within the given GUI-pixel threshold.
     * Equal distances favor left, right, top, then bottom.
     */
    public static Optional<DockEdge> snap(Rect window, Rect safeArea, double threshold) {
        requireGui(window);
        requireGui(safeArea);
        if (!Double.isFinite(threshold) || threshold < 0) throw new IllegalArgumentException("Invalid snap threshold");
        if (window.empty() || safeArea.empty()) return Optional.empty();
        DockEdge nearest = null;
        double nearestDistance = threshold;
        for (DockEdge edge : DockEdge.values()) {
            double distance = Math.abs(edgePosition(edge, window) - edgePosition(edge, safeArea));
            if (distance <= threshold && (nearest == null || distance < nearestDistance)) {
                nearest = edge;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static void pack(DockEdge edge, List<Item> items, Rect safeArea, double gap,
                             Map<UiId, Entry> solved, double[] push) {
        boolean vertical = edge.vertical();
        double length = vertical ? safeArea.height() : safeArea.width();
        List<Item> visible = new ArrayList<>();
        List<Double> lengths = new ArrayList<>();
        double available = length;
        for (Item item : packingOrder(items)) {
            double compactLength = Math.min(length, vertical ? item.compactHeight : item.compactWidth);
            double spacing = visible.isEmpty() ? 0 : gap;
            if (safeArea.empty() || spacing > available || compactLength > available - spacing) {
                Rect empty = rect(safeArea.x(), safeArea.y(), 0, 0);
                solved.put(item.id, new Entry(item.placement, empty, empty, true, item.zIndex));
                continue;
            }
            available = (available - spacing) - compactLength;
            visible.add(item);
            lengths.add(compactLength);
        }

        double cursor = vertical ? safeArea.y() : safeArea.x();
        double end = vertical ? safeArea.bottom() : safeArea.right();
        for (int index = 0; index < visible.size(); index++) {
            Item item = visible.get(index);
            double compactLength = lengths.get(index);
            double preferredLength = vertical ? item.height() : item.width();
            double extra = Math.min(available, Math.max(0, preferredLength - compactLength));
            double renderedLength = clipExtent(cursor, end, compactLength + extra);
            available -= extra;
            double width = vertical ? Math.min(safeArea.width(), item.width()) : renderedLength;
            double height = vertical ? renderedLength : Math.min(safeArea.height(), item.height());
            double x = switch (edge) {
                case LEFT -> safeArea.x();
                case RIGHT -> safeArea.right() - width;
                case TOP, BOTTOM -> cursor;
            };
            double y = switch (edge) {
                case LEFT, RIGHT -> cursor;
                case TOP -> safeArea.y();
                case BOTTOM -> safeArea.bottom() - height;
            };
            x = Math.max(safeArea.x(), x);
            y = Math.max(safeArea.y(), y);
            Rect bounds = rect(x, y, clipExtent(x, safeArea.right(), width),
                    clipExtent(y, safeArea.bottom(), height));
            solved.put(item.id, new Entry(item.placement, bounds, compact(item, bounds, edge), false, item.zIndex));
            if (item.expansionLayout == ExpansionLayoutMode.PUSH && !bounds.empty()) {
                int slot = edge.ordinal();
                push[slot] = Math.max(push[slot], vertical ? bounds.width() : bounds.height());
            }
            cursor = Math.min(end, (vertical ? bounds.bottom() : bounds.right()) + gap);
        }
    }

    private static List<Item> packingOrder(List<Item> items) {
        items.sort(PRIORITY_ORDER);
        Map<UiId, List<Item>> groups = new HashMap<>();
        for (Item item : items) {
            if (item.placement.group() != null) {
                groups.computeIfAbsent(item.placement.group(), ignored -> new ArrayList<>()).add(item);
            }
        }
        if (groups.isEmpty()) return items;
        List<Item> ordered = new ArrayList<>(items.size());
        for (Item item : items) {
            UiId group = item.placement.group();
            if (group == null) {
                ordered.add(item);
            } else {
                List<Item> members = groups.remove(group);
                if (members != null) ordered.addAll(members);
            }
        }
        return ordered;
    }

    private static Entry floating(Item item, Rect safeArea) {
        Rect bounds = fit(safeArea, item.placement.x(), item.placement.y(), item.width(), item.height());
        return new Entry(item.placement, bounds, compact(item, bounds, null), false, item.zIndex);
    }

    private static Rect compact(Item item, Rect bounds, DockEdge edge) {
        double width = Math.min(item.compactWidth, bounds.width());
        double height = Math.min(item.compactHeight, bounds.height());
        double x = edge == DockEdge.RIGHT ? bounds.right() - width : bounds.x();
        double y = edge == DockEdge.BOTTOM ? bounds.bottom() - height : bounds.y();
        return fit(bounds, x, y, width, height);
    }

    private static DockCollisionResult collisions(Map<UiId, Entry> entries) {
        var visible = entries.entrySet().stream().filter(entry -> entry.getValue().visible())
                .sorted(Comparator.comparingDouble(entry -> entry.getValue().bounds().x())).toList();
        List<DockCollisionResult.Overlap> overlaps = new ArrayList<>();
        for (int index = 0; index < visible.size(); index++) {
            var first = visible.get(index);
            Rect firstBounds = first.getValue().bounds();
            for (int other = index + 1; other < visible.size(); other++) {
                var second = visible.get(other);
                Rect secondBounds = second.getValue().bounds();
                if (secondBounds.x() >= firstBounds.right()) break;
                if (firstBounds.intersects(secondBounds)) {
                    overlaps.add(new DockCollisionResult.Overlap(first.getKey(), second.getKey(),
                            firstBounds.intersection(secondBounds)));
                }
            }
        }
        List<UiId> overflowed = entries.entrySet().stream().filter(entry -> entry.getValue().overflowed())
                .map(Map.Entry::getKey).toList();
        return new DockCollisionResult(overlaps, overflowed);
    }

    private static void fitOpposingInsets(double[] push, DockEdge start, DockEdge end, double extent) {
        double first = push[start.ordinal()];
        double second = push[end.ordinal()];
        if (second <= extent - first) return;
        double total = first / extent + second / extent;
        push[start.ordinal()] = first / total;
        push[end.ordinal()] = extent - push[start.ordinal()];
    }

    private static double edgePosition(DockEdge edge, Rect bounds) {
        return switch (edge) {
            case LEFT -> bounds.x();
            case RIGHT -> bounds.right();
            case TOP -> bounds.y();
            case BOTTOM -> bounds.bottom();
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static Rect fit(Rect area, double x, double y, double width, double height) {
        width = Math.min(width, area.width());
        height = Math.min(height, area.height());
        x = clamp(x, area.x(), area.right() - width);
        y = clamp(y, area.y(), area.bottom() - height);
        return rect(x, y, clipExtent(x, area.right(), width), clipExtent(y, area.bottom(), height));
    }

    private static double clipExtent(double start, double end, double extent) {
        if (start + extent <= end) return extent;
        double clipped = Math.min(extent, end - start);
        // Keep half-open edges disjoint when addition rounds a fractional extent upward.
        return start + clipped > end ? Math.nextDown(clipped) : clipped;
    }

    private static Rect rect(double x, double y, double width, double height) {
        return new Rect(x, y, width, height, Space.GUI);
    }

    private static void requireGui(Rect bounds) {
        if (bounds.space() != Space.GUI) throw new IllegalArgumentException("Dock bounds must use GUI coordinates");
    }
}
