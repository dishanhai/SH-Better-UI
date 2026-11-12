// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Logical selection endpoints in a resolved document. Text offsets are UTF-16 grapheme boundaries. */
public record RichTextSelection(Position anchor, Position focus) {
    public RichTextSelection {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(focus, "focus");
    }

    public record Position(UiId node, int offset) {
        public Position {
            Objects.requireNonNull(node, "node");
            if (offset < 0) throw new IllegalArgumentException("Negative selection offset");
        }
    }

    public record Span(int start, int end) {}

    /** Map a visible logical-order fragment to the nearest caret, using its original styled metrics. */
    public static Position caret(Fragment fragment, double x, RichTextLayout.FontMetrics metrics) {
        if (!(fragment.node() instanceof TextRun run)) throw new IllegalArgumentException("Text fragment required");
        int[] edges = RichTextGraphemes.ends(fragment.text());
        double localX = x - fragment.bounds().x();
        if (edges.length == 0 || localX <= 0) return new Position(run.id(), fragment.textOffset());
        int low = 0;
        int high = edges.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (metrics.width(run, fragment.text().substring(0, edges[mid])) < localX) low = mid + 1;
            else high = mid;
        }
        int before = low == 0 ? 0 : edges[low - 1];
        int offset = before;
        if (low < edges.length) {
            int after = edges[low];
            int left = metrics.width(run, fragment.text().substring(0, before));
            int right = metrics.width(run, fragment.text().substring(0, after));
            if (localX - left >= right - localX) offset = after;
        }
        return new Position(run.id(), fragment.textOffset() + offset);
    }

    /** Compute nonempty spans in document order. Reversed selections produce the same spans. */
    public Map<UiId, Span> spans(RichDocument resolved) {
        var nodes = resolved.nodes();
        int first = indexOf(resolved, anchor);
        int last = indexOf(resolved, focus);
        Position start = anchor;
        Position end = focus;
        if (first > last || first == last && anchor.offset() > focus.offset()) {
            int swap = first;
            first = last;
            last = swap;
            start = focus;
            end = anchor;
        }
        Map<UiId, Span> result = new LinkedHashMap<>();
        for (int i = first; i <= last; i++) {
            var node = nodes.get(i);
            int from = i == first ? start.offset() : 0;
            int to = i == last ? end.offset() : plain(node).length();
            if (from < to) result.put(node.id(), new Span(from, to));
        }
        return Map.copyOf(result);
    }

    public String copy(RichDocument resolved) {
        var spans = spans(resolved);
        var result = new StringBuilder();
        for (var node : resolved.nodes()) {
            var span = spans.get(node.id());
            if (span != null) result.append(plain(node), span.start(), span.end());
        }
        return result.toString();
    }

    static int indexOf(RichDocument document, Position position) {
        for (int i = 0; i < document.nodes().size(); i++) {
            var node = document.nodes().get(i);
            if (node.id().equals(position.node()) && node instanceof TextRun run
                    && isBoundary(run.text(), position.offset())) return i;
        }
        throw new IllegalArgumentException("Selection requires a current literal run and a grapheme boundary");
    }

    static boolean isBoundary(String text, int offset) {
        return RichTextGraphemes.isBoundary(text, offset);
    }

    private static String plain(InlineNode node) {
        if (node instanceof TextRun run) return run.text();
        if (node instanceof ImageNode image) return image.altText();
        if (node instanceof RichObjectNode object) return object.altText();
        if (node instanceof RichExtensionNode extension) return extension.altText();
        if (node instanceof ParagraphBreak) return "\n";
        throw new IllegalArgumentException("Resolve translations before selecting text");
    }
}
