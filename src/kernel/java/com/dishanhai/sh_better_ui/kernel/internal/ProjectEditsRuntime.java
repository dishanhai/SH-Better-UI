// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectDocument;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Local project state bound to authenticated host-issued scopes. */
final class ProjectEditsRuntime implements ProjectEdits {
    private static final int MAX_SESSIONS = 8;
    private static final int HISTORY = 16;
    private static final int RETRY_WINDOW = 64;
    private static final int MAX_PROJECT_TEXT_UNITS = 256 * 1024;
    private static final int MAX_ID_UNITS = 256;

    private final KernelHost host;
    private final Map<SessionKey, EditSession> sessions = new LinkedHashMap<>();

    ProjectEditsRuntime(KernelHost host) {
        this.host = host;
    }

    @Override
    public Session open(ModuleContext owner, ScopeHandle scope, UiId projectId, Map<UiId, RichDocument> initial) {
        var target = host.ownedScope(owner, scope);
        var access = host.moduleAccess(owner);
        access.assertSideEffectAllowed();
        access.requireSchemaOwner(projectId);
        var key = new SessionKey(target.generation(), projectId);
        if (sessions.containsKey(key)) throw new IllegalArgumentException("Project session is already open in this scope");
        if (sessions.size() >= MAX_SESSIONS) throw new IllegalArgumentException("Project session capacity reached");
        try {
            if (initial.size() > MAX_DOCUMENTS) throw new Rejected();
            var contents = Map.copyOf(initial);
            validate(projectId, contents);
            var stamp = new ProjectDocument.Stamp(projectId, 0, ProjectDocument.SCHEMA_VERSION, UUID.randomUUID());
            var document = new ProjectDocument(stamp, contents);
            var session = new EditSession(owner, key, new View(document, Optional.empty()));
            session.cleanup = host.onScopeClose(target, () -> release(session));
            sessions.put(key, session);
            return session;
        } catch (Rejected rejected) {
            throw new IllegalArgumentException("Project exceeds its document budget");
        }
    }

    @Override
    public Optional<View> current(ModuleContext owner, Session session) {
        return verify(owner, session) == Status.APPLIED ? Optional.of(((EditSession) session).view) : Optional.empty();
    }

    @Override
    public Result commit(ModuleContext owner, Session session, Patch patch) {
        var authorization = verify(owner, session);
        if (authorization != Status.APPLIED) return result(authorization, null);
        host.moduleAccess(owner).assertSideEffectAllowed();
        var edit = (EditSession) session;
        var current = edit.view.document();
        Objects.requireNonNull(patch, "patch");
        if (!patch.operationId().namespace().equals(owner.descriptor().id().namespace())) {
            return result(Status.DENIED, current.stamp());
        }
        if (idLength(patch.operationId()) > MAX_ID_UNITS) {
            return result(Status.BUDGET_EXCEEDED, current.stamp());
        }
        var known = edit.recent.get(patch.operationId());
        if (known != null) {
            return result(known.equals(patch.expected()) ? Status.ALREADY_APPLIED : Status.CONFLICT, current.stamp());
        }
        if (!current.stamp().equals(patch.expected())) return result(Status.CONFLICT, current.stamp());
        try {
            var documents = new LinkedHashMap<>(current.documents());
            for (var operation : patch.operations()) {
                apply(documents, operation);
                validate(current.stamp().projectId(), documents);
            }
            if (patch.selection().isPresent()) {
                var selection = patch.selection().orElseThrow();
                indexOf(nodes(documents, selection.documentId()), selection.nodeId());
            }
            var next = viewAtNextRevision(edit, documents, patch.selection());
            push(edit.undo, edit.view);
            edit.redo.clear();
            edit.view = next;
            edit.recent.put(patch.operationId(), patch.expected());
            if (edit.recent.size() > RETRY_WINDOW) edit.recent.remove(edit.recent.keySet().iterator().next());
            return result(Status.APPLIED, next.document().stamp());
        } catch (Rejected rejected) {
            return result(Status.BUDGET_EXCEEDED, current.stamp());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return result(Status.INVALID, current.stamp());
        }
    }

    @Override
    public Result undo(ModuleContext owner, Session session, ProjectDocument.Stamp expected) {
        return restore(owner, session, expected, true);
    }

    @Override
    public Result redo(ModuleContext owner, Session session, ProjectDocument.Stamp expected) {
        return restore(owner, session, expected, false);
    }

    @Override
    public Status close(ModuleContext owner, Session session) {
        var authorization = verify(owner, session);
        if (authorization != Status.APPLIED) return authorization;
        host.moduleAccess(owner).assertSideEffectAllowed();
        ((EditSession) session).cleanup.close();
        return Status.APPLIED;
    }

    private Result restore(ModuleContext owner, Session session, ProjectDocument.Stamp expected, boolean undo) {
        var authorization = verify(owner, session);
        if (authorization != Status.APPLIED) return result(authorization, null);
        host.moduleAccess(owner).assertSideEffectAllowed();
        var edit = (EditSession) session;
        var stamp = edit.view.document().stamp();
        if (!stamp.equals(expected)) return result(Status.CONFLICT, stamp);
        var source = undo ? edit.undo : edit.redo;
        var destination = undo ? edit.redo : edit.undo;
        var saved = source.peekLast();
        if (saved == null) return result(Status.NO_HISTORY, stamp);
        if (stamp.revision() == Long.MAX_VALUE) return result(Status.BUDGET_EXCEEDED, stamp);
        var next = viewAtNextRevision(edit, saved.document().documents(), saved.selection());
        push(destination, edit.view);
        source.removeLast();
        edit.view = next;
        return result(Status.APPLIED, next.document().stamp());
    }

    private Status verify(ModuleContext owner, Session handle) {
        host.assertOwner();
        if (!(handle instanceof EditSession edit) || edit.owner != owner) return Status.DENIED;
        if (edit.view == null) return Status.STALE_SCOPE;
        if (sessions.get(edit.key) != edit) return Status.DENIED;
        if (!edit.cleanup.isActive()) return Status.STALE_SCOPE;
        try {
            host.moduleAccess(owner);
            return Status.APPLIED;
        } catch (SecurityException denied) {
            return Status.DENIED;
        }
    }

    private void release(EditSession edit) {
        sessions.remove(edit.key, edit);
        edit.view = null;
        edit.undo.clear();
        edit.redo.clear();
        edit.recent.clear();
        edit.cleanup = null;
    }

    private static View viewAtNextRevision(EditSession edit, Map<UiId, RichDocument> documents,
                                           Optional<Selection> selection) {
        var stamp = edit.view.document().stamp();
        if (stamp.revision() == Long.MAX_VALUE) throw new Rejected();
        var next = new ProjectDocument.Stamp(stamp.projectId(), stamp.revision() + 1, stamp.schemaVersion(),
                stamp.sessionId());
        return new View(new ProjectDocument(next, documents), selection);
    }

    private static void push(ArrayDeque<View> history, View view) {
        history.addLast(view);
        if (history.size() > HISTORY) history.removeFirst();
    }

    private static Result result(Status status, ProjectDocument.Stamp stamp) {
        return new Result(status, Optional.ofNullable(stamp));
    }

    private static void apply(Map<UiId, RichDocument> documents, Operation operation) {
        if (operation instanceof PutDocument put) {
            documents.put(Objects.requireNonNull(put.documentId()), Objects.requireNonNull(put.document()));
        } else if (operation instanceof RemoveDocument remove) {
            if (documents.remove(remove.documentId()) == null) throw new IllegalArgumentException("Unknown document");
        } else if (operation instanceof Insert insert) {
            var nodes = nodes(documents, insert.documentId());
            nodes.add(insertionIndex(nodes, insert.beforeNode()), insert.node());
            documents.put(insert.documentId(), new RichDocument(nodes));
        } else if (operation instanceof Remove remove) {
            var nodes = nodes(documents, remove.documentId());
            nodes.remove(indexOf(nodes, remove.nodeId()));
            documents.put(remove.documentId(), new RichDocument(nodes));
        } else if (operation instanceof Replace replace) {
            var nodes = nodes(documents, replace.documentId());
            nodes.set(indexOf(nodes, replace.node().id()), replace.node());
            documents.put(replace.documentId(), new RichDocument(nodes));
        } else if (operation instanceof Move move) {
            var source = nodes(documents, move.sourceDocument());
            int from = indexOf(source, move.nodeId());
            boolean sameDocument = move.sourceDocument().equals(move.destinationDocument());
            var destination = sameDocument ? source : nodes(documents, move.destinationDocument());
            int to = insertionIndex(destination, move.beforeNode());
            var node = source.remove(from);
            destination.add(sameDocument && from < to ? to - 1 : to, node);
            if (!sameDocument) documents.put(move.sourceDocument(), new RichDocument(source));
            documents.put(move.destinationDocument(), new RichDocument(destination));
        }
    }

    private static List<InlineNode> nodes(Map<UiId, RichDocument> documents, UiId id) {
        var document = documents.get(Objects.requireNonNull(id));
        if (document == null) throw new IllegalArgumentException("Unknown document");
        return new ArrayList<>(document.nodes());
    }

    private static int insertionIndex(List<InlineNode> nodes, Optional<UiId> before) {
        return before.isPresent() ? indexOf(nodes, before.orElseThrow()) : nodes.size();
    }

    private static int indexOf(List<InlineNode> nodes, UiId id) {
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).id().equals(id)) return index;
        }
        throw new IllegalArgumentException("Unknown node");
    }

    private static void validate(UiId projectId, Map<UiId, RichDocument> documents) {
        if (documents.size() > MAX_DOCUMENTS) throw new Rejected();
        var budget = new DocumentBudget();
        budget.id(projectId);
        int nodes = 0;
        for (var entry : documents.entrySet()) {
            budget.id(entry.getKey());
            var contents = entry.getValue().nodes();
            if (contents.size() > MAX_PROJECT_NODES - nodes) throw new Rejected();
            nodes += contents.size();
            for (var node : contents) budget.node(node);
        }
    }

    private static long idLength(UiId id) {
        return (long) id.namespace().length() + id.path().length() + 1;
    }

    private static final class DocumentBudget {
        private int remaining = MAX_PROJECT_TEXT_UNITS;

        void id(UiId id) {
            long length = idLength(id);
            if (length > MAX_ID_UNITS) throw new Rejected();
            charge((int) length);
        }

        void text(String text) {
            charge(text.length());
            for (int index = 0; index < text.length(); index++) {
                char value = text.charAt(index);
                if (Character.isHighSurrogate(value)) {
                    if (++index == text.length() || !Character.isLowSurrogate(text.charAt(index))) {
                        throw new IllegalArgumentException("Unpaired UTF-16 surrogate in project");
                    }
                } else if (Character.isLowSurrogate(value)) {
                    throw new IllegalArgumentException("Unpaired UTF-16 surrogate in project");
                }
            }
        }

        private void charge(int units) {
            if (units > remaining) throw new Rejected();
            remaining -= units;
        }

        void style(RichStyle style) {
            if (style.font() != null) id(style.font());
        }

        void node(InlineNode node) {
            id(node.id());
            if (node instanceof TextRun run) {
                text(run.text());
                style(run.style());
            } else if (node instanceof LocalizedText localized) {
                if (localized.arguments().size() > 32) throw new Rejected();
                text(localized.key());
                text(localized.fallback());
                localized.arguments().forEach(this::text);
                style(localized.style());
            } else if (node instanceof ImageNode image) {
                id(image.texture());
                text(image.altText());
            } else if (node instanceof RichExtensionNode extension) {
                id(extension.type());
                text(extension.altText());
                text(extension.payload());
            } else if (node instanceof RichObjectNode object) {
                text(object.altText());
                object.textures().forEach(this::id);
                var content = object.content();
                if (content instanceof RichObjectNode.Item item) id(item.item());
                if (content instanceof RichObjectNode.Block block) id(block.block());
                if (content instanceof RichObjectNode.Fluid fluid) id(fluid.fluid());
                if (content instanceof RichObjectNode.Entity entity) id(entity.type());
                if (content instanceof RichObjectNode.Button button) text(button.label());
                if (content instanceof RichObjectNode.Slider slider) text(slider.label());
            }
        }
    }

    private record SessionKey(long scopeGeneration, UiId project) {}

    private static final class EditSession implements Session {
        private final ModuleContext owner;
        private final SessionKey key;
        private final ArrayDeque<View> undo = new ArrayDeque<>();
        private final ArrayDeque<View> redo = new ArrayDeque<>();
        private final Map<UiId, ProjectDocument.Stamp> recent = new LinkedHashMap<>();
        private View view;
        private Subscription cleanup;

        private EditSession(ModuleContext owner, SessionKey key, View view) {
            this.owner = owner;
            this.key = key;
            this.view = view;
        }
    }

    private static final class Rejected extends RuntimeException {}
}
