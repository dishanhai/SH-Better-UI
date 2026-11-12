// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextLayout;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection.Position;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextEdit;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RichTextLayoutTest {
    private static final RichTextLayout.FontMetrics FONT = new RichTextLayout.FontMetrics() {
        @Override public int ascent() { return 8; }
        @Override public int descent() { return 2; }
        @Override public int width(String text) { return text.codePointCount(0, text.length()) * 4; }
    };

    @Test void mixedLineUsesFontBaselineAndDeclaredAspect() {
        var icon = image(64, 32);
        var snapshot = RichTextLayout.layout(new RichDocument(List.of(run("left", "A"), icon,
                run("right", "B"))), 80, FONT);
        var line = snapshot.lines().get(0);
        assertEquals(1, snapshot.lines().size());
        assertEquals(10, line.baseline());
        assertEquals(12, snapshot.height());
        assertEquals(List.of(new Rect(0, 2, 4, 10), new Rect(4, 0, 20, 10), new Rect(24, 2, 4, 10)),
                line.fragments().stream().map(Fragment::bounds).toList());
        assertSame(icon, snapshot.hitTest(4, 0).orElseThrow().node());
        assertEquals(id("right"), snapshot.hitTest(24, 2).orElseThrow().node().id());
        assertTrue(snapshot.hitTest(24, 1).isEmpty());
        assertTrue(snapshot.hitTest(28, 2).isEmpty());
    }

    @Test void wrapsWordsAndKeepsOversizedImageInsideTheViewport() {
        var snapshot = RichTextLayout.layout(new RichDocument(List.of(run("words", "ab cd"),
                image(128, 16))), 16, FONT);
        assertEquals(List.of(List.of("ab"), List.of("cd"), List.of("")), textLines(snapshot));
        assertEquals(new Rect(0, 26, 16, 2), snapshot.lines().get(2).fragments().get(0).bounds());
        assertEquals(30, snapshot.height());
        assertTrue(snapshot.hitTest(16, 26).isEmpty());
    }

    @Test void forcedBreakDoesNotSplitAnEmojiClusterOrEraseHardBreaks() {
        var snapshot = RichTextLayout.layout(new RichDocument(List.of(run("unicode", "a👩‍💻b\r\n\n末\n"))), 4, FONT);
        assertEquals(List.of(List.of("a"), List.of("👩‍💻"), List.of("b"), List.of(), List.of("末"), List.of()),
                textLines(snapshot));
        assertEquals(60, snapshot.height());
        assertTrue(snapshot.hitTest(5, 12).isEmpty(), "outside the snapshot viewport even on an overwide glyph");
    }

    @Test void indicConjunctIsAtomicWhenWrappingSelectingAndEditing() {
        String conjunct = "\u0915\u094D\u0937";
        var text = run("indic", "a" + conjunct + "b");
        var document = new RichDocument(List.of(text));
        var layout = RichTextLayout.layout(document, 4, FONT);
        assertEquals(List.of(List.of("a"), List.of(conjunct), List.of("b")), textLines(layout));
        var fragment = layout.lines().get(1).fragments().get(0);
        assertEquals(new Position(text.id(), 1), RichTextSelection.caret(fragment, 5, FONT));
        assertEquals(new Position(text.id(), 4), RichTextSelection.caret(fragment, 7, FONT));
        var invalid = new RichTextSelection(new Position(text.id(), 1), new Position(text.id(), 3));
        assertThrows(IllegalArgumentException.class, () -> invalid.copy(document));
        assertThrows(IllegalArgumentException.class, () -> RichTextEdit.replace(document, invalid, "x"));
    }

    @Test void trailingWhitespaceIsDiscardedOnlyWhenItsWholeClusterIsBlank() {
        String prependAndSpace = "\u0600 ";
        var document = new RichDocument(List.of(run("prepend", prependAndSpace + "AB")));
        assertEquals(List.of(List.of(prependAndSpace), List.of("AB")),
                textLines(RichTextLayout.layout(document, 8, FONT)));
    }

    @Test void fittingTextIsMeasuredAsOneFragmentAndEmptyWidthDisablesHits() {
        var font = new RichTextLayout.FontMetrics() {
            @Override public int ascent() { return 8; }
            @Override public int descent() { return 2; }
            @Override public int width(String text) { return text.equals("a b") ? 10 : FONT.width(text); }
        };
        var document = new RichDocument(List.of(run("words", "a b")));
        assertEquals(List.of(List.of("a b")), textLines(RichTextLayout.layout(document, 10, font)));
        var hidden = RichTextLayout.layout(document, 0, font);
        assertEquals(0, hidden.height());
        assertTrue(hidden.hitTest(0, 0).isEmpty());
    }

    @Test void documentAndSnapshotCannotBeChangedAfterPublishing() {
        var content = new ArrayList<InlineNode>(List.of(run("a", "a")));
        var document = new RichDocument(content);
        content.clear();
        assertThrows(UnsupportedOperationException.class, () -> document.nodes().clear());
        var snapshot = RichTextLayout.layout(document, 40, FONT);
        assertEquals(List.of(List.of("a")), textLines(snapshot));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.lines().get(0).fragments().clear());
        assertThrows(IllegalArgumentException.class, () -> new RichDocument(List.of(run("a", "a"), run("a", "b"))));
        assertThrows(IllegalArgumentException.class, () -> image(0, 10));
    }

    @Test void longUnbrokenRunDoesNotRemeasureItsWholeRemainderForEveryLine() {
        var measuredCharacters = new AtomicInteger();
        var font = new RichTextLayout.FontMetrics() {
            @Override public int ascent() { return 8; }
            @Override public int descent() { return 2; }
            @Override public int width(String text) {
                measuredCharacters.addAndGet(text.length());
                return text.length() * 4;
            }
        };
        var snapshot = RichTextLayout.layout(new RichDocument(List.of(run("long", "a".repeat(10_000)))), 16, font);
        assertEquals(2_500, snapshot.lines().size());
        assertTrue(measuredCharacters.get() < 100_000, () -> "measured " + measuredCharacters.get() + " characters");

        measuredCharacters.set(0);
        var wide = RichTextLayout.layout(new RichDocument(List.of(run("long", "a".repeat(10_000)))), 20_000, font);
        assertEquals(2, wide.lines().size());
        assertTrue(measuredCharacters.get() < 150_000, () -> "measured " + measuredCharacters.get() + " characters");
    }

    @Test void spacesBetweenRunsDoNotCreatePhantomLines() {
        assertEquals(List.of(List.of("a")), textLines(RichTextLayout.layout(
                new RichDocument(List.of(run("content", "a"), run("space", " "))), 4, FONT)));
        assertEquals(List.of(List.of("a")), textLines(RichTextLayout.layout(
                new RichDocument(List.of(run("space", " "), run("content", "a"))), 4, FONT)));
        assertEquals(List.of(List.of("a"), List.of()), textLines(RichTextLayout.layout(
                new RichDocument(List.of(run("content", "a"), run("break", "\n"))), 4, FONT)));
    }

    @Test void styledMetricsOffsetsAndParagraphSpacingSurviveWrapping() {
        var style = new RichStyle(0xffaac4, true, false, false, false, false, id("wide"));
        var font = new RichTextLayout.FontMetrics() {
            @Override public int ascent() { return 8; }
            @Override public int descent() { return 2; }
            @Override public int width(String text) { return FONT.width(text); }
            @Override public int width(TextRun run, String text) {
                return run.style().bold() ? 2 * width(text) : width(text);
            }
        };
        var run = new TextRun(id("first"), "ab  cd\r\né末", style);
        var document = new RichDocument(List.of(run, new ParagraphBreak(id("space"), 4), run("last", "xy")));
        var layout = RichTextLayout.layout(document, 16, font);
        assertEquals(List.of(List.of("ab"), List.of("cd"), List.of("é末"), List.of("xy")), textLines(layout));
        assertEquals(List.of(0, 4, 8, 0), layout.lines().stream().map(line -> line.fragments().get(0).textOffset()).toList());
        assertEquals(34, layout.lines().get(3).y());
        assertTrue(layout.hitTest(0, 31).isEmpty());
        assertEquals(new Position(run.id(), 5),
                RichTextSelection.caret(layout.lines().get(1).fragments().get(0), 9, font));
    }

    @Test void graphemeSelectionAndCommittedEditKeepIdentityAndStyle() {
        var accent = "e\u0301";
        var emoji = "👩‍💻";
        var first = run("first", accent + emoji);
        var second = run("second", "中文");
        var document = new RichDocument(List.of(first, image(16, 16), new ParagraphBreak(id("p"), 2), second));
        var selection = new RichTextSelection(new Position(second.id(), 1), new Position(first.id(), accent.length()));
        assertEquals(emoji + "icon\n中", selection.copy(document));
        assertThrows(IllegalArgumentException.class, () -> new RichTextSelection(new Position(first.id(), 1),
                new Position(first.id(), accent.length())).copy(document));
        var editable = new RichTextSelection(new Position(first.id(), accent.length()),
                new Position(first.id(), first.text().length()));
        assertThrows(IllegalArgumentException.class, () -> RichTextEdit.replace(document, editable, "\ud83d"));
        var edited = RichTextEdit.replace(document, editable, "漢字🌊");
        assertEquals(new TextRun(first.id(), accent + "漢字🌊", first.style()), edited.nodes().get(0));
        assertSame(second, edited.nodes().get(3));
        assertSame(edited, RichTextEdit.replace(edited, new RichTextSelection(
                new Position(first.id(), 0), new Position(first.id(), 0)), ""));
    }

    @Test void visibleRowsMatchExhaustiveIntersectionAcrossRandomizedViewports() {
        var random = new Random(19112026);
        var nodes = new ArrayList<InlineNode>();
        for (int i = 0; i < 125; i++) {
            nodes.add(run("r" + i, "文e\u0301👩‍💻 ".repeat(1 + random.nextInt(8))));
            nodes.add(new ParagraphBreak(id("p" + i), random.nextInt(12)));
        }
        var layout = RichTextLayout.layout(new RichDocument(nodes), 48, FONT);
        for (int i = 0; i < 1000; i++) {
            double top = random.nextInt(layout.height() + 20) - 10;
            double bottom = top + 1 + random.nextInt(90);
            assertEquals(layout.lines().stream().filter(line -> line.y() < bottom && line.y() + line.height() > top).toList(),
                    layout.visibleLines(top, bottom));
        }
    }

    private static List<List<String>> textLines(RichLayoutSnapshot snapshot) {
        return snapshot.lines().stream().map(line -> line.fragments().stream().map(Fragment::text).toList()).toList();
    }

    private static TextRun run(String id, String value) { return new TextRun(id(id), value); }

    private static ImageNode image(int width, int height) {
        return new ImageNode(id("icon"), id("textures/icon.png"), width, height, "icon");
    }

    private static UiId id(String path) { return new UiId("test", path); }
}
