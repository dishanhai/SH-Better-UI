// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionInput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;

/** Local, validated document transactions. Selection is restored with the transaction. */
final class RichTextEditorDocument {
    private static final int HISTORY_LIMIT = 64;
    private static final int JSON_BYTES = 256 * 1024;
    private static final RichExtensionInput INERT_EXTENSIONS = new RichExtensionInput(Map.of());

    private record State(RichDocument document, int selection) {}

    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private State state;
    private RichDocument saved;

    RichTextEditorDocument(RichDocument initial) { reopen(initial); }

    RichDocument document() { return state.document(); }
    int selection() { return state.selection(); }
    InlineNode selected() { return selection() < 0 ? null : document().nodes().get(selection()); }
    boolean dirty() { return !document().equals(saved); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    void select(int index) {
        if (index >= 0 && index < document().nodes().size()) state = new State(document(), index);
    }

    void select(UiId id) {
        var nodes = document().nodes();
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).id().equals(id)) {
                select(index);
                return;
            }
        }
    }

    void insert(InlineNode node) {
        var nodes = new ArrayList<>(document().nodes());
        int index = selection() + 1;
        nodes.add(index, node);
        commit(new RichDocument(nodes), index);
    }

    void change(InlineNode node) {
        if (selected() == null || !selected().id().equals(node.id())) {
            throw new IllegalArgumentException("Property edits must retain the selected node ID");
        }
        var nodes = new ArrayList<>(document().nodes());
        nodes.set(selection(), node);
        commit(new RichDocument(nodes), selection());
    }

    void delete() {
        if (selected() == null) return;
        var nodes = new ArrayList<>(document().nodes());
        nodes.remove(selection());
        commit(new RichDocument(nodes), Math.min(selection(), nodes.size() - 1));
    }

    void move(int step) {
        int index = selection() + step;
        if (selected() == null || index < 0 || index >= document().nodes().size()) return;
        var nodes = new ArrayList<>(document().nodes());
        Collections.swap(nodes, selection(), index);
        commit(new RichDocument(nodes), index);
    }

    void replace(RichDocument document) { commit(document, first(document)); }
    void undo() { restore(undo, redo); }
    void redo() { restore(redo, undo); }

    void reopen(RichDocument document) {
        encode(document);
        saved = document;
        state = new State(document, first(document));
        undo.clear();
        redo.clear();
    }

    void markSaved() { saved = document(); }

    private void commit(RichDocument document, int selection) {
        if (document.equals(document())) return;
        encode(document);
        undo.addLast(state);
        if (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear();
        state = new State(document, selection);
    }

    private void restore(Deque<State> from, Deque<State> to) {
        if (from.isEmpty()) return;
        to.addLast(state);
        state = from.removeLast();
    }

    private static int first(RichDocument document) { return document.nodes().isEmpty() ? -1 : 0; }

    static RichDocument decode(String json) { return RichDocumentCodec.decode(json, INERT_EXTENSIONS); }
    static String encode(RichDocument document) { return RichDocumentCodec.encode(document, INERT_EXTENSIONS); }

    static RichDocument load(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a local rich document file");
        }
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] data = input.readNBytes(JSON_BYTES + 1);
            if (data.length > JSON_BYTES) throw new IOException("Rich document exceeds 256 KiB");
            return decode(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString());
        }
    }

    static void write(Path file, String contents) throws IOException {
        Path directory = file.getParent();
        if (directory == null) throw new IllegalArgumentException("Expected a destination directory");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "rich-text-", ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Base64 chunks keep literal control characters and large documents within javac's constant limits. */
    static String javaSource(RichDocument document) {
        String encoded = Base64.getEncoder().encodeToString(encode(document).getBytes(StandardCharsets.UTF_8));
        var java = new StringBuilder("""
                import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
                import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
                import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionInput;
                import java.nio.charset.StandardCharsets;
                import java.util.Base64;
                import java.util.Map;

                public final class ExportedRichText {
                    private ExportedRichText() {}

                    public static RichDocument document() {
                        String payload = String.join("",
                """);
        for (int start = 0; start < encoded.length(); start += 4096) {
            if (start > 0) java.append(",\n");
            java.append("                \"").append(encoded, start, Math.min(start + 4096, encoded.length())).append('"');
        }
        return java.append("""
                );
                        String json = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
                        return RichDocumentCodec.decode(json, new RichExtensionInput(Map.of()));
                    }
                }
                """).toString();
    }
}
