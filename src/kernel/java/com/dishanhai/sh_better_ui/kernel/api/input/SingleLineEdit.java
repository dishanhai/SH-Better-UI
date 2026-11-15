// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextGraphemes;

import java.text.BreakIterator;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Single-line editing state. Selection offsets are UTF-16 grapheme boundaries. */
public final class SingleLineEdit {
    private static final int HISTORY_LIMIT = 64;
    private final int maxLength;
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    private String value = "";
    private int[] boundaries = {0};
    private int caret;
    private int anchor;

    public SingleLineEdit(int maxLength) {
        if (maxLength < 1 || maxLength > 4096) throw new IllegalArgumentException("Invalid text field limit");
        this.maxLength = maxLength;
    }

    public String value() { return value; }
    public int caret() { return caret; }
    public int selectionStart() { return Math.min(anchor, caret); }
    public int selectionEnd() { return Math.max(anchor, caret); }
    public String selectedText() { return value.substring(selectionStart(), selectionEnd()); }
    public int[] boundaries() { return boundaries.clone(); }

    /** Synchronizes the host's value and starts a new editing history. */
    public void setValue(String text) {
        update(boundedLine(text, maxLength));
        caret = anchor = value.length();
        undo.clear();
        redo.clear();
    }

    public void moveTo(int offset, boolean extend) {
        caret = boundary(offset, 1);
        if (!extend) anchor = caret;
    }

    public void selectAll() {
        anchor = 0;
        caret = value.length();
    }

    public void selectWord(int offset) {
        int point = boundary(offset, -1);
        if (point == value.length() && point > 0) point = step(point, -1, false);
        var words = words();
        int start = words.isBoundary(point) ? point : words.preceding(point);
        int end = words.following(point);
        anchor = boundary(Math.max(0, start), -1);
        caret = boundary(end < 0 ? value.length() : end, 1);
    }

    public void move(int direction, boolean byWord, boolean extend) {
        if (!extend && selectionStart() != selectionEnd()) {
            moveTo(direction < 0 ? selectionStart() : selectionEnd(), false);
        } else {
            moveTo(step(caret, direction, byWord), extend);
        }
    }

    public boolean insert(String text) {
        int start = selectionStart();
        int end = selectionEnd();
        var insertion = boundedLine(text, maxLength - (value.length() - (end - start)));
        return !insertion.isEmpty() && replace(start, end, insertion);
    }

    public boolean delete(int direction, boolean byWord) {
        if (selectionStart() != selectionEnd()) return deleteSelection();
        int other = step(caret, direction, byWord);
        return replace(Math.min(caret, other), Math.max(caret, other), "");
    }

    public boolean deleteSelection() { return replace(selectionStart(), selectionEnd(), ""); }
    public boolean undo() { return restore(undo, redo); }
    public boolean redo() { return restore(redo, undo); }

    private boolean replace(int start, int end, String text) {
        String next = value.substring(0, start) + text + value.substring(end);
        if (next.equals(value)) {
            moveTo(start + text.length(), false);
            return false;
        }
        saveTo(undo);
        redo.clear();
        update(next);
        moveTo(start + text.length(), false);
        return true;
    }

    private void saveTo(ArrayDeque<Snapshot> history) {
        if (history.size() == HISTORY_LIMIT) history.removeLast();
        history.push(new Snapshot(value, caret, anchor));
    }

    private boolean restore(ArrayDeque<Snapshot> history, ArrayDeque<Snapshot> opposite) {
        if (history.isEmpty()) return false;
        saveTo(opposite);
        var previous = history.pop();
        update(previous.value());
        caret = previous.caret();
        anchor = previous.anchor();
        return true;
    }

    private int step(int offset, int direction, boolean byWord) {
        if (byWord) {
            var words = words();
            int target = direction < 0 ? words.preceding(offset) : words.following(offset);
            while (target > 0 && target < value.length() && Character.isWhitespace(value.codePointAt(target))) {
                target = direction < 0 ? words.preceding(target) : words.following(target);
            }
            return target < 0 ? (direction < 0 ? 0 : value.length()) : boundary(target, direction);
        }
        int current = Arrays.binarySearch(boundaries, offset);
        return boundaries[Math.max(0, Math.min(boundaries.length - 1, current + Integer.signum(direction)))];
    }

    private BreakIterator words() {
        var iterator = BreakIterator.getWordInstance(Locale.ROOT);
        iterator.setText(value);
        return iterator;
    }

    private int boundary(int offset, int direction) {
        int index = Arrays.binarySearch(boundaries, Math.max(0, Math.min(value.length(), offset)));
        if (index >= 0) return boundaries[index];
        return boundaries[direction < 0 ? -index - 2 : -index - 1];
    }

    private void update(String text) {
        value = text;
        int[] ends = RichTextGraphemes.ends(text);
        boundaries = new int[ends.length + 1];
        System.arraycopy(ends, 0, boundaries, 1, ends.length);
    }

    private static String boundedLine(String input, int limit) {
        Objects.requireNonNull(input);
        // Read one extra code point to avoid truncating a cluster that crosses the limit.
        var line = new StringBuilder();
        for (int start = 0; start < input.length() && line.length() <= limit;) {
            int point = input.codePointAt(start);
            start += Character.charCount(point);
            if (point == '\n' || point == '\r' || point == '\t'
                    || point == 0x2028 || point == 0x2029) line.append(' ');
            else if (!Character.isISOControl(point) && !(point >= 0xD800 && point <= 0xDFFF)) line.appendCodePoint(point);
        }
        if (line.length() <= limit) return line.toString();
        int end = 0;
        for (int boundary : RichTextGraphemes.ends(line.toString())) {
            if (boundary > limit) break;
            end = boundary;
        }
        return line.substring(0, end);
    }

    private record Snapshot(String value, int caret, int anchor) {}
}
