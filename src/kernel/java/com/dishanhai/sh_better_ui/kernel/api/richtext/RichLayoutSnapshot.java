// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable logical GUI geometry relative to the document origin, before the host transform. */
public record RichLayoutSnapshot(int width, int height, List<LineBox> lines) {
    public RichLayoutSnapshot {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative layout size");
        lines = List.copyOf(lines);
        long end = 0;
        for (var line : lines) {
            if (line.y() < end) throw new IllegalArgumentException("Lines must be ordered and non-overlapping");
            end = (long) line.y() + line.height();
        }
        if (end > height) throw new IllegalArgumentException("Line outside layout height");
    }

    /** Geometry only; this neither dispatches events nor assigns interactivity to a node. */
    public Optional<Fragment> hitTest(double x, double y) {
        if (!(x >= 0 && y >= 0 && x < width && y < height)) return Optional.empty();
        int index = firstLineEndingAfter(y);
        if (index < lines.size() && y >= lines.get(index).y()) {
            for (var fragment : lines.get(index).fragments()) {
                if (fragment.bounds().contains(x, y)) return Optional.of(fragment);
            }
        }
        return Optional.empty();
    }

    /** Visible rows for a half-open vertical viewport, in paint order, without copying fragments. */
    public List<LineBox> visibleLines(double top, double bottom) {
        if (!(bottom > top)) return List.of();
        int start = firstLineEndingAfter(top);
        int low = start;
        int high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).y() < bottom) low = mid + 1;
            else high = mid;
        }
        return lines.subList(start, low);
    }

    private int firstLineEndingAfter(double y) {
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            var line = lines.get(mid);
            if (y >= (long) line.y() + line.height()) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    /** Baseline and fragment positions are relative to the document, not the line. */
    public record LineBox(int y, int baseline, int height, List<Fragment> fragments) {
        public LineBox {
            if (y < 0 || baseline < y || height <= 0 || baseline > (long) y + height) {
                throw new IllegalArgumentException("Invalid line geometry");
            }
            fragments = List.copyOf(fragments);
        }
    }

    /** Text is the exact measured slice; image fragments use an empty string. */
    public record Fragment(InlineNode node, String text, Rect bounds, int textOffset) {
        public Fragment(InlineNode node, String text, Rect bounds) {
            this(node, text, bounds, 0);
        }

        public Fragment {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(bounds, "bounds");
            if (textOffset < 0) throw new IllegalArgumentException("Negative text offset");
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (x < 0 || y < 0 || width < 0 || height < 0) throw new IllegalArgumentException("Invalid fragment rectangle");
        }

        public boolean contains(double localX, double localY) {
            return localX >= x && localY >= y && localX < (long) x + width && localY < (long) y + height;
        }
    }
}
