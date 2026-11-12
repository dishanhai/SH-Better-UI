// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectDocument;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectEditsContractTest {
    private static final UiId PROJECT = id("project");
    private static final UiId LEFT = id("left");
    private static final UiId RIGHT = id("right");
    private static final UiId NODE = id("node");
    private static final TextRun TEXT = new TextRun(NODE, "a");

    private KernelHost host;
    private ProjectEdits edits;
    private ModuleContext owner;
    private ModuleContext other;

    @BeforeEach void start() {
        host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, new EnginePublication());
        edits = new ProjectEditsRuntime(host);
        host.start(List.of(
                TestModules.candidate(TestModules.module("owner").build(), Set.of(), false,
                        ignored -> {}, context -> owner = context, ignored -> {}),
                TestModules.candidate(TestModules.module("other").build(), Set.of(), false,
                        ignored -> {}, context -> other = context, ignored -> {})));
    }

    @AfterEach void stop() { host.close(); }

    @Test void moveUndoRedoPreserveNodeIdentityAndPublishNewRevisions() {
        var session = open();
        var first = current(session);
        var selected = Optional.of(new ProjectEdits.Selection(RIGHT, NODE));
        var patch = new ProjectEdits.Patch(first.document().stamp(), id("move"), List.of(
                new ProjectEdits.Move(LEFT, NODE, RIGHT, Optional.empty())), selected);
        assertEquals(APPLIED, edits.commit(owner, session, patch).status());
        var moved = current(session);
        assertEquals(1, moved.document().stamp().revision());
        assertSame(TEXT, moved.document().richText(RIGHT).orElseThrow().nodes().get(0));
        assertEquals(selected, moved.selection());
        assertEquals(List.of(TEXT), first.document().richText(LEFT).orElseThrow().nodes());
        assertEquals(ALREADY_APPLIED, edits.commit(owner, session, patch).status());
        assertEquals(CONFLICT, edits.undo(owner, session, first.document().stamp()).status());
        assertEquals(APPLIED, edits.undo(owner, session, moved.document().stamp()).status());
        var undone = current(session);
        assertEquals(2, undone.document().stamp().revision());
        assertEquals(first.document().documents(), undone.document().documents());
        assertTrue(undone.selection().isEmpty());
        assertEquals(APPLIED, edits.redo(owner, session, undone.document().stamp()).status());
        var redone = current(session);
        assertEquals(3, redone.document().stamp().revision());
        assertEquals(moved.document().documents(), redone.document().documents());
        assertEquals(selected, redone.selection());
    }

    @Test void invalidPatchLeavesEveryDocumentAndHistoryUntouched() {
        var session = open();
        var first = current(session);
        var patch = new ProjectEdits.Patch(first.document().stamp(), id("broken"), List.of(
                new ProjectEdits.Move(LEFT, NODE, RIGHT, Optional.empty()),
                new ProjectEdits.Remove(LEFT, NODE)), Optional.empty());
        assertEquals(INVALID, edits.commit(owner, session, patch).status());
        assertSame(first, current(session));
        assertEquals(NO_HISTORY, edits.undo(owner, session, first.document().stamp()).status());
    }

    @Test void budgetsAndUnicodeValidationRejectBeforePublication() {
        var session = open();
        var first = current(session);
        var oversized = new TextRun(NODE, "x".repeat(256 * 1024));
        var large = new ProjectEdits.Patch(first.document().stamp(), id("large"),
                List.of(new ProjectEdits.Replace(LEFT, oversized)), Optional.empty());
        var malformed = new ProjectEdits.Patch(first.document().stamp(), id("malformed"),
                List.of(new ProjectEdits.Replace(LEFT, new TextRun(NODE, "\ud800"))), Optional.empty());
        assertEquals(BUDGET_EXCEEDED, edits.commit(owner, session, large).status());
        assertEquals(INVALID, edits.commit(owner, session, malformed).status());
        assertSame(first, current(session));
    }

    @Test void ownerIdentityAndScopeClosurePreventReuse() {
        var screen = host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:project_screen"));
        var scope = host.mountTree(owner, screen, id("scope"));
        var session = edits.open(owner, scope, PROJECT, Map.of());
        var stamp = current(session).document().stamp();
        assertThrows(SecurityException.class, () -> edits.open(other, scope, PROJECT, Map.of()));
        assertEquals(DENIED, edits.undo(other, session, stamp).status());
        assertTrue(edits.current(other, session).isEmpty());
        scope.close();
        assertEquals(STALE_SCOPE, edits.undo(owner, session, stamp).status());
        assertTrue(edits.current(owner, session).isEmpty());
        var reopened = host.mountTree(owner, screen, id("scope"));
        var next = edits.open(owner, reopened, PROJECT, Map.of());
        assertNotSame(session, next);
        assertEquals(APPLIED, edits.close(owner, next));
        assertEquals(STALE_SCOPE, edits.close(owner, next));
    }

    @Test void closingScopeRejectsWritesEvenBeforeTheSessionCleanupRuns() {
        var screen = host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:project_screen"));
        var scope = host.mountTree(owner, screen, id("scope"));
        var session = edits.open(owner, scope, PROJECT, Map.of());
        var patch = new ProjectEdits.Patch(current(session).document().stamp(), id("late"),
                List.of(new ProjectEdits.PutDocument(LEFT, new RichDocument(List.of(TEXT)))), Optional.empty());
        var late = new AtomicReference<ProjectEdits.Status>();
        scope.track(() -> late.set(edits.commit(owner, session, patch).status()));
        scope.close();
        assertEquals(STALE_SCOPE, late.get());
        assertTrue(edits.current(owner, session).isEmpty());
    }

    @Test void queuedPatchFromPreviousSessionCannotEditReopenedProject() {
        var original = open();
        var stamp = current(original).document().stamp();
        var ready = new CompletableFuture<ProjectDocument.Stamp>();
        var queued = ready.thenApplyAsync(expected -> new ProjectEdits.Patch(expected, id("delete"), List.of(
                new ProjectEdits.RemoveDocument(LEFT)), Optional.empty()));

        assertEquals(APPLIED, edits.close(owner, original));
        var reopened = open();
        ready.complete(stamp);

        assertEquals(CONFLICT, edits.commit(owner, reopened, queued.join()).status());
        assertEquals(List.of(TEXT), current(reopened).document().richText(LEFT).orElseThrow().nodes());
    }

    @Test void parallelSessionsRequireTheirOwnStampForEditsAndHistory() {
        var screen = host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:project_screen"));
        var firstTree = host.mountTree(owner, screen, id("first_scope"));
        var secondTree = host.mountTree(owner, screen, id("second_scope"));
        var initial = Map.of(LEFT, new RichDocument(List.of(TEXT)));
        var first = edits.open(owner, firstTree, PROJECT, initial);
        var second = edits.open(owner, secondTree, PROJECT, initial);
        var firstStamp = current(first).document().stamp();
        var secondStamp = current(second).document().stamp();
        var remove = List.<ProjectEdits.Operation>of(new ProjectEdits.RemoveDocument(LEFT));
        var detached = new ProjectDocument.Stamp(PROJECT, secondStamp.revision(), secondStamp.schemaVersion());
        assertEquals(CONFLICT, edits.commit(owner, second,
                new ProjectEdits.Patch(detached, id("delete"), remove, Optional.empty())).status());
        assertEquals(CONFLICT, edits.commit(owner, second,
                new ProjectEdits.Patch(firstStamp, id("delete"), remove, Optional.empty())).status());
        assertEquals(APPLIED, edits.commit(owner, first,
                new ProjectEdits.Patch(firstStamp, id("delete"), remove, Optional.empty())).status());
        var copied = new ProjectDocument.Stamp(PROJECT, secondStamp.revision(), secondStamp.schemaVersion(),
                secondStamp.sessionId());
        assertEquals(APPLIED, edits.commit(owner, second,
                new ProjectEdits.Patch(copied, id("delete"), remove, Optional.empty())).status());

        var firstRevision = current(first).document().stamp();
        var secondRevision = current(second).document().stamp();
        assertEquals(CONFLICT, edits.undo(owner, second, firstRevision).status());
        assertEquals(APPLIED, edits.undo(owner, second, secondRevision).status());
        var firstUndo = edits.undo(owner, first, firstRevision).stamp().orElseThrow();
        var secondUndo = current(second).document().stamp();
        assertEquals(CONFLICT, edits.redo(owner, second, firstUndo).status());
        assertEquals(APPLIED, edits.redo(owner, second, secondUndo).status());
    }

    private ProjectEdits.Session open() {
        return edits.open(owner, owner.scope(), PROJECT,
                Map.of(LEFT, new RichDocument(List.of(TEXT)), RIGHT, new RichDocument(List.of())));
    }

    private ProjectEdits.View current(ProjectEdits.Session session) {
        return edits.current(owner, session).orElseThrow();
    }

    private static UiId id(String path) { return new UiId("example", path); }
}
