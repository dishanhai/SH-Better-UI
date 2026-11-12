// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection.Position;

import java.util.ArrayList;
import java.util.Objects;

/** Immutable edit of one literal run, keeping its ID and style. The host owns undo and input composition. */
public final class RichTextEdit {
    private RichTextEdit() {}

    public static RichDocument replace(RichDocument document, RichTextSelection selection, String committedText) {
        Objects.requireNonNull(committedText, "committedText");
        if (committedText.codePoints().anyMatch(point -> point >= 0xd800 && point <= 0xdfff)) {
            throw new IllegalArgumentException("Unpaired UTF-16 surrogate in committed text");
        }
        Position anchor = selection.anchor();
        Position focus = selection.focus();
        if (!anchor.node().equals(focus.node())) throw new IllegalArgumentException("Edit must stay in one literal run");
        int index = RichTextSelection.indexOf(document, anchor);
        RichTextSelection.indexOf(document, focus);
        var run = (TextRun) document.nodes().get(index);
        int start = Math.min(anchor.offset(), focus.offset());
        int end = Math.max(anchor.offset(), focus.offset());
        if (run.text().regionMatches(start, committedText, 0, committedText.length())
                && end - start == committedText.length()) return document;
        var nodes = new ArrayList<>(document.nodes());
        nodes.set(index, new TextRun(run.id(), run.text().substring(0, start) + committedText + run.text().substring(end),
                run.style()));
        return new RichDocument(nodes);
    }
}
