// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.project;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Experimental owner-thread editing service for in-memory rich text projects. */
public interface ProjectEdits extends UIService {
    ServiceKey<ProjectEdits> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:project_edits"), ProjectEdits.class, 1);
    int MAX_DOCUMENTS = 32;
    int MAX_PROJECT_NODES = 4096;
    int MAX_OPERATIONS = 128;

    /**
     * Opens revision zero in an owned scope. The project ID must belong to the issuer's namespace.
     * A scope can hold one session per project ID. Closing the scope discards the live session.
     * @throws SecurityException for an unauthorized issuer or scope
     * @throws IllegalArgumentException for invalid input, duplicate sessions, or exhausted capacity
     */
    Session open(ModuleContext owner, ScopeHandle scope, UiId projectId, Map<UiId, RichDocument> initial);

    Optional<View> current(ModuleContext owner, Session session);
    Result commit(ModuleContext owner, Session session, Patch patch);
    Result undo(ModuleContext owner, Session session, ProjectDocument.Stamp expected);
    Result redo(ModuleContext owner, Session session, ProjectDocument.Stamp expected);
    Status close(ModuleContext owner, Session session);

    /** Opaque handle issued by the service. */
    interface Session {}

    record View(ProjectDocument document, Optional<Selection> selection) {}
    record Selection(UiId documentId, UiId nodeId) {
        public Selection {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    /**
     * The expected stamp belongs to the target session. The operation ID is the retry key.
     * An empty selection clears the previous selection.
     */
    record Patch(ProjectDocument.Stamp expected, UiId operationId, List<Operation> operations,
                 Optional<Selection> selection) {
        public Patch {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(selection, "selection");
            if (operations.size() > MAX_OPERATIONS) throw new IllegalArgumentException("Too many patch operations");
            operations = List.copyOf(operations);
        }
    }

    sealed interface Operation permits PutDocument, RemoveDocument, Insert, Remove, Replace, Move {}
    record PutDocument(UiId documentId, RichDocument document) implements Operation {}
    record RemoveDocument(UiId documentId) implements Operation {}
    /** Empty beforeNode appends to the document. */
    record Insert(UiId documentId, Optional<UiId> beforeNode, InlineNode node) implements Operation {}
    record Remove(UiId documentId, UiId nodeId) implements Operation {}
    /** A replacement keeps the ID of the existing node. */
    record Replace(UiId documentId, InlineNode node) implements Operation {}
    /** Empty beforeNode appends. The source node keeps its ID across documents. */
    record Move(UiId sourceDocument, UiId nodeId, UiId destinationDocument, Optional<UiId> beforeNode)
            implements Operation {}

    enum Status { APPLIED, ALREADY_APPLIED, CONFLICT, DENIED, INVALID, STALE_SCOPE, BUDGET_EXCEEDED, NO_HISTORY }
    /** Rejection exposes the current stamp only to the authenticated session owner. */
    record Result(Status status, Optional<ProjectDocument.Stamp> stamp) {}
}
