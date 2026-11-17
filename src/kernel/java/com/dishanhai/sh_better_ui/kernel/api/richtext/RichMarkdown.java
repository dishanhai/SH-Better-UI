// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bounded, passive Markdown block model. Inline content remains Markdown, not executable markup.
 * This deliberately implements a subset, not CommonMark: ATX headings, flat quote/list rows,
 * fences, rules and pipe tables. HTML, images, formulas and unknown syntax remain literal content.
 * Parsing never loads resources, evaluates expressions or dispatches link actions.
 */
public final class RichMarkdown {
    public static final int MAX_LENGTH = 65_536;
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_TABLE_COLUMNS = 32;
    public static final int MAX_TABLE_ROWS = 256;
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.*)$");
    private static final Pattern LIST = Pattern.compile("^( *)([-+*]|[0-9]{1,9}[.)])[ \\t]+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>[ \\t]?(.*)$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern ALIGNMENT = Pattern.compile(":?-{3,}:?");

    private RichMarkdown() {}
    public enum Alignment { LEFT, CENTER, RIGHT }
    public sealed interface Block permits Paragraph, Heading, Quote, ListItem, CodeBlock, ThematicBreak, Table {}
    public record Paragraph(String text) implements Block { public Paragraph { Objects.requireNonNull(text); } }
    public record Heading(int level, String text) implements Block {
        public Heading { if (level < 1 || level > 6) throw new IllegalArgumentException("heading level"); Objects.requireNonNull(text); }
    }
    public record Quote(String text) implements Block { public Quote { Objects.requireNonNull(text); } }
    /** checked is null for a normal list row; task markers are display-only. */
    public record ListItem(int indent, String marker, String text, Boolean checked) implements Block {
        public ListItem { if (indent < 0) throw new IllegalArgumentException("indent"); Objects.requireNonNull(marker); Objects.requireNonNull(text); }
    }
    public record CodeBlock(String language, String text) implements Block {
        public CodeBlock { Objects.requireNonNull(language); Objects.requireNonNull(text); }
    }
    public record ThematicBreak() implements Block {}
    public record Table(List<String> header, List<Alignment> alignments, List<List<String>> rows) implements Block {
        public Table {
            header = List.copyOf(header); alignments = List.copyOf(alignments);
            rows = rows.stream().map(List::copyOf).toList();
            if (header.isEmpty() || header.size() > MAX_TABLE_COLUMNS || header.size() != alignments.size()
                    || rows.size() > MAX_TABLE_ROWS) throw new IllegalArgumentException("table bounds");
            for (var row : rows) if (row.size() != header.size()) throw new IllegalArgumentException("table row width");
        }
    }
    public record Document(List<Block> blocks) {
        public Document { blocks = List.copyOf(blocks); if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("too many blocks"); }
    }

    /** Rejects oversized input instead of silently dropping text. CRLF and CR normalize to LF. */
    public static Document parse(String source) {
        Objects.requireNonNull(source, "source");
        if (source.length() > MAX_LENGTH) throw new IllegalArgumentException("Markdown input is too large");
        var lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        var blocks = new ArrayList<Block>();
        var paragraph = new StringBuilder();
        for (int i = 0; i < lines.length;) {
            String line = lines[i];
            var fence = FENCE.matcher(line);
            if (fence.matches() && (fence.group(1).charAt(0) != '`' || !fence.group(2).contains("`"))) {
                flush(blocks, paragraph);
                String delimiter = fence.group(1), language = fence.group(2).strip();
                var code = new StringBuilder();
                boolean wrote = false;
                for (i++; i < lines.length; i++) {
                    var close = FENCE.matcher(lines[i]);
                    if (close.matches() && close.group(1).charAt(0) == delimiter.charAt(0)
                            && close.group(1).length() >= delimiter.length() && close.group(2).isBlank()) { i++; break; }
                    if (wrote) code.append('\n');
                    code.append(lines[i]); wrote = true;
                }
                add(blocks, new CodeBlock(language, code.toString())); continue;
            }
            Table start = i + 1 < lines.length ? tableStart(line, lines[i + 1]) : null;
            if (start != null) {
                flush(blocks, paragraph);
                var rows = new ArrayList<List<String>>();
                i += 2;
                while (i < lines.length && !lines[i].isBlank() && hasPipe(lines[i])) {
                    var cells = cells(lines[i]);
                    // A malformed width is preserved as ordinary text, never silently truncated.
                    if (cells.size() != start.header().size()) break;
                    if (rows.size() == MAX_TABLE_ROWS) throw new IllegalArgumentException("too many table rows");
                    rows.add(cells); i++;
                }
                add(blocks, new Table(start.header(), start.alignments(), rows)); continue;
            }
            var heading = HEADING.matcher(line);
            var quote = QUOTE.matcher(line);
            var list = LIST.matcher(line);
            Block block = null;
            if (isRule(line)) block = new ThematicBreak();
            else if (heading.matches()) block = new Heading(heading.group(1).length(), headingText(heading.group(2)));
            else if (quote.matches()) block = new Quote(quote.group(1));
            else if (list.matches()) {
                String text = list.group(3); Boolean checked = null;
                if (text.length() >= 3 && text.charAt(0) == '[' && text.charAt(2) == ']'
                        && " xX".indexOf(text.charAt(1)) >= 0 && (text.length() == 3 || Character.isWhitespace(text.charAt(3)))) {
                    checked = text.charAt(1) != ' '; text = text.substring(3).stripLeading();
                }
                block = new ListItem(list.group(1).length(), list.group(2), text, checked);
            }
            if (block != null) { flush(blocks, paragraph); add(blocks, block); }
            else if (line.isBlank()) flush(blocks, paragraph);
            else { if (!paragraph.isEmpty()) paragraph.append('\n'); paragraph.append(line); }
            i++;
        }
        flush(blocks, paragraph);
        return new Document(blocks);
    }

    private static String headingText(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) end--;
        int hashes = end;
        while (hashes > 0 && text.charAt(hashes - 1) == '#') hashes--;
        if (hashes == end || hashes == 0 || (text.charAt(hashes - 1) != ' ' && text.charAt(hashes - 1) != '\t')) return text;
        while (hashes > 0 && (text.charAt(hashes - 1) == ' ' || text.charAt(hashes - 1) == '\t')) hashes--;
        return text.substring(0, hashes);
    }

    private static boolean isRule(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') index++;
        if (index > 3 || index == line.length()) return false;
        char marker = line.charAt(index);
        if (marker != '*' && marker != '-' && marker != '_') return false;
        int count = 0;
        for (; index < line.length(); index++) {
            char c = line.charAt(index);
            if (c == marker) count++;
            else if (c != ' ' && c != '\t') return false;
        }
        return count >= 3;
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) { add(blocks, new Paragraph(paragraph.toString())); paragraph.setLength(0); }
    }
    private static void add(List<Block> blocks, Block block) {
        if (blocks.size() == MAX_BLOCKS) throw new IllegalArgumentException("too many Markdown blocks");
        blocks.add(block);
    }
    private static Table tableStart(String header, String separator) {
        if (!hasPipe(header) || !hasPipe(separator)) return null;
        var headings = cells(header); var markers = cells(separator);
        if (headings.isEmpty() || headings.size() != markers.size()) return null;
        var alignments = new ArrayList<Alignment>();
        for (String marker : markers) {
            if (!ALIGNMENT.matcher(marker).matches()) return null;
            alignments.add(marker.endsWith(":") ? marker.startsWith(":") ? Alignment.CENTER : Alignment.RIGHT : Alignment.LEFT);
        }
        if (headings.size() > MAX_TABLE_COLUMNS) throw new IllegalArgumentException("too many table columns");
        return new Table(headings, alignments, List.of());
    }
    private static boolean hasPipe(String text) { return cells(text).size() > 1 || text.strip().startsWith("|") || text.strip().endsWith("|"); }

    /** Splits only unescaped pipes outside a matched code span; retains escapes for inline parsing. */
    private static List<String> cells(String source) {
        String text = source.strip();
        var cells = new ArrayList<String>();
        // Pre-index delimiter runs: repeated unmatched backticks must not cause quadratic scans.
        var ticks = new HashMap<Integer, ArrayDeque<Integer>>();
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) != '`') { i++; continue; }
            int end = i + 1; while (end < text.length() && text.charAt(end) == '`') end++;
            ticks.computeIfAbsent(end - i, unused -> new ArrayDeque<>()).add(i); i = end;
        }
        int start = text.startsWith("|") ? 1 : 0;
        int cellStart = start;
        boolean endedAtPipe = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) { i++; endedAtPipe = false; continue; }
            if (c == '`') {
                int end = i + 1; while (end < text.length() && text.charAt(end) == '`') end++;
                int count = end - i;
                var candidates = ticks.get(count);
                while (!candidates.isEmpty() && candidates.peekFirst() < end) candidates.removeFirst();
                int close = candidates.isEmpty() ? -1 : candidates.peekFirst();
                if (close >= 0) i = close + count - 1; else i = end - 1;
                endedAtPipe = false; continue;
            }
            if (c == '|') { cells.add(text.substring(cellStart, i).strip()); cellStart = i + 1; endedAtPipe = true; }
            else endedAtPipe = false;
        }
        if (!endedAtPipe || cellStart < text.length()) cells.add(text.substring(cellStart).strip());
        return cells;
    }
}
