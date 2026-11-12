// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.LineBox;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Measures and places the contents of one rich text box; its parent UUK layout places the box. */
public final class RichTextLayout {
    private static final Pattern HARD_BREAK = Pattern.compile("\\R");

    private RichTextLayout() {}

    /** Host metrics in logical GUI pixels. Plain text advance is nonnegative and monotonic over prefixes. */
    public interface FontMetrics {
        int ascent();
        int descent();
        int width(String text);
        default int width(TextRun run, String text) { return width(text); }
    }

    public static RichLayoutSnapshot layout(RichDocument document, int maxWidth, FontMetrics font) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(font, "font");
        if (maxWidth < 0) throw new IllegalArgumentException("Negative layout width");
        if (maxWidth == 0 || document.nodes().isEmpty()) return new RichLayoutSnapshot(maxWidth, 0, List.of());
        var flow = new Flow(maxWidth, font);
        for (var node : document.nodes()) {
            if (node instanceof TextRun run) flow.text(run);
            else if (node instanceof LocalizedText text) flow.text(new TextRun(text.id(), text.fallback(), text.style()));
            else if (node instanceof ImageNode image) {
                flow.object(image, image.intrinsicWidth(), image.intrinsicHeight(), flow.fontHeight);
            } else if (node instanceof RichObjectNode object) {
                flow.object(object, object.intrinsicWidth(), object.intrinsicHeight(), object.logicalHeight());
            } else if (node instanceof RichExtensionNode extension) {
                flow.object(extension, extension.intrinsicWidth(), extension.intrinsicHeight(), extension.logicalHeight());
            } else if (node instanceof ParagraphBreak paragraph) {
                flow.finishLine(false);
                flow.y = Math.addExact(flow.y, paragraph.spacing());
                flow.afterHardBreak = true;
            }
        }
        if (!flow.pending.isEmpty() || flow.afterHardBreak) flow.finishLine(false);
        return new RichLayoutSnapshot(maxWidth, flow.y, flow.lines);
    }

    private record Pending(InlineNode node, String text, int width, int height, int textOffset) {}

    private static final class Flow {
        private final int maxWidth;
        private final FontMetrics font;
        private final int ascent;
        private final int descent;
        private final int fontHeight;
        private final List<LineBox> lines = new ArrayList<>();
        private final List<Pending> pending = new ArrayList<>();
        private int used;
        private int y;
        private boolean afterHardBreak;

        private Flow(int maxWidth, FontMetrics font) {
            this.maxWidth = maxWidth;
            this.font = font;
            ascent = font.ascent();
            descent = font.descent();
            if (ascent <= 0 || descent < 0) throw new IllegalArgumentException("Invalid font metrics");
            fontHeight = Math.addExact(ascent, descent);
        }

        private void text(TextRun run) {
            var breaks = HARD_BREAK.matcher(run.text());
            int start = 0;
            while (breaks.find()) {
                paragraph(run, run.text().substring(start, breaks.start()), start);
                finishLine(false);
                afterHardBreak = true;
                start = breaks.end();
            }
            paragraph(run, run.text().substring(start), start);
        }

        private void paragraph(TextRun run, String text, int offset) {
            if (text.isEmpty()) return;
            int measured = measure(run, text);
            if (measured <= maxWidth - used) {
                add(new Pending(run, text, measured, fontHeight, offset));
                return;
            }

            int[] clusters = RichTextGraphemes.ends(text);
            BitSet breaks = lineBreaks(text, clusters);
            int start = 0;
            while (start < clusters.length) {
                int fit = fitPrefix(run, text, clusters, start, maxWidth - used);
                if (fit == clusters.length) {
                    addText(run, text.substring(edge(clusters, start)), offset + edge(clusters, start));
                    return;
                }
                // Allow a soft wrap after spaces that are discarded at the end of this line.
                int breakAt = breaks.previousSetBit(skipSpace(text, clusters, fit));
                if (breakAt > start) {
                    addText(run, trimTrailingClusters(text.substring(edge(clusters, start), edge(clusters, breakAt))),
                            offset + edge(clusters, start));
                    start = breakAt;
                } else if (!pending.isEmpty()) {
                    finishLine(true);
                    start = skipSpace(text, clusters, start);
                    continue;
                } else {
                    // A single overwide grapheme keeps its geometry; the host viewport clips it.
                    int end = Math.max(start + 1, fit);
                    addText(run, text.substring(edge(clusters, start), edge(clusters, end)), offset + edge(clusters, start));
                    start = end;
                }
                if (start < clusters.length) {
                    finishLine(true);
                    start = skipSpace(text, clusters, start);
                }
            }
        }

        private void addText(TextRun run, String text, int offset) {
            if (!text.isEmpty()) add(new Pending(run, text, measure(run, text), fontHeight, offset));
        }

        private int fitPrefix(TextRun run, String text, int[] clusters, int start, int available) {
            int from = edge(clusters, start);
            int low = start;
            int high = start + 1;
            while (measure(run, text.substring(from, edge(clusters, high))) <= available) {
                if (high == clusters.length) return high;
                low = high;
                high = (int) Math.min(clusters.length, start + 2L * (high - start));
            }
            while (high - low > 1) {
                int mid = low + (high - low) / 2;
                if (measure(run, text.substring(from, edge(clusters, mid))) <= available) low = mid;
                else high = mid;
            }
            return low;
        }

        private void object(InlineNode node, int intrinsicWidth, int intrinsicHeight, int logicalHeight) {
            double aspect = intrinsicWidth / (double) intrinsicHeight;
            double fittedWidth = Math.min(maxWidth, logicalHeight * aspect);
            int width = Math.max(1, (int) Math.round(fittedWidth));
            int height = Math.max(1, Math.min(logicalHeight, (int) Math.round(fittedWidth / aspect)));
            if (width > maxWidth - used && !pending.isEmpty()) finishLine(true);
            add(new Pending(node, "", width, height, 0));
        }

        private void add(Pending fragment) {
            used = Math.addExact(used, fragment.width());
            pending.add(fragment);
        }

        private void finishLine(boolean softWrap) {
            if (softWrap) {
                trimTrailingSpace();
                if (pending.isEmpty()) {
                    used = 0;
                    return;
                }
            }
            int lineAscent = ascent;
            for (var fragment : pending) {
                if (!(fragment.node() instanceof TextRun)) lineAscent = Math.max(lineAscent, fragment.height());
            }
            int baseline = Math.addExact(y, lineAscent);
            int height = Math.addExact(lineAscent, descent);
            var fragments = new ArrayList<Fragment>(pending.size());
            int x = 0;
            for (var fragment : pending) {
                int top = baseline - (fragment.node() instanceof TextRun ? ascent : fragment.height());
                fragments.add(new Fragment(fragment.node(), fragment.text(), new Rect(x, top, fragment.width(), fragment.height()),
                        fragment.textOffset()));
                x = Math.addExact(x, fragment.width());
            }
            lines.add(new LineBox(y, baseline, height, fragments));
            y = Math.addExact(y, height);
            pending.clear();
            used = 0;
            afterHardBreak = false;
        }

        private void trimTrailingSpace() {
            while (!pending.isEmpty()) {
                int index = pending.size() - 1;
                var last = pending.get(index);
                if (!(last.node() instanceof TextRun run)) return;
                String trimmed = trimTrailingClusters(last.text());
                if (!trimmed.isEmpty()) {
                    if (!trimmed.equals(last.text())) {
                        pending.set(index, new Pending(run, trimmed, measure(run, trimmed), fontHeight, last.textOffset()));
                    }
                    return;
                }
                pending.remove(index);
            }
        }

        private int measure(TextRun run, String text) {
            int width = font.width(run, text);
            if (width < 0) throw new IllegalArgumentException("Negative text width");
            return width;
        }
    }

    private static BitSet lineBreaks(String text, int[] clusters) {
        var result = new BitSet(clusters.length + 1);
        var breaks = BreakIterator.getLineInstance(Locale.ROOT);
        breaks.setText(text);
        int boundary = breaks.next();
        for (int i = 0; i < clusters.length; i++) {
            while (boundary < clusters[i]) boundary = breaks.next();
            if (boundary == clusters[i]) result.set(i + 1);
        }
        return result;
    }

    private static int edge(int[] clusters, int index) { return index == 0 ? 0 : clusters[index - 1]; }

    private static String trimTrailingClusters(String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.length() == text.length() || trimmed.isEmpty()) return trimmed;
        int boundary = RichTextGraphemes.boundaryAtOrAfter(text, trimmed.length());
        return boundary == trimmed.length() ? trimmed : text.substring(0, boundary);
    }

    private static int skipSpace(String text, int[] clusters, int index) {
        while (index < clusters.length) {
            int offset = edge(clusters, index);
            if (!Character.isWhitespace(text.codePointAt(offset)) || !text.substring(offset, clusters[index]).isBlank()) break;
            index++;
        }
        return index;
    }
}
