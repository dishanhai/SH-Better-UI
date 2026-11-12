// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.render;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.dishanhai.sh_better_ui.kernel.api.render.RenderGraph.Stage.*;
import static com.dishanhai.sh_better_ui.kernel.api.render.RenderGraph.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class RenderGraphTest {
    private static final UiId TARGET = id("target");
    private static final UiId A = id("a");
    private static final UiId B = id("b");
    private static final UiId C = id("c");

    @Test void compilesStableOrderFromStagesAttachmentsAndExplicitDependencies() {
        var a = pass(A, PREPARE, Set.of(), Set.of(), Set.of(TARGET));
        var b = pass(B, CONTENT, Set.of(), Set.of(TARGET), Set.of());
        var c = pass(C, CONTENT, Set.of(B), Set.of(), Set.of());
        var plan = RenderGraph.compile(List.of(c, b, a), Set.of(), Set.of(), 3).plan().orElseThrow();
        assertEquals(List.of(a, b, c), plan.order());
        assertEquals(3, plan.cost());
        assertEquals(plan.order(), RenderGraph.compile(List.of(b, a, c), Set.of(), Set.of(), 3)
                .plan().orElseThrow().order());
        var called = new ArrayList<UiId>();
        assertFalse(plan.execute(pass -> {
            called.add(pass.id());
            return !pass.id().equals(B);
        }));
        assertEquals(List.of(A, B), called);
    }

    @Test void rejectsFeedbackMissingProducersAndAmbiguousWrites() {
        var feedback = pass(A, CONTENT, Set.of(), Set.of(TARGET), Set.of(TARGET));
        assertEquals(INVALID, compile(feedback).status());
        var reads = pass(A, CONTENT, Set.of(), Set.of(TARGET), Set.of());
        assertEquals(INVALID, compile(reads).status());
        assertEquals(READY, RenderGraph.compile(List.of(reads), Set.of(TARGET), Set.of(), 1).status());
        var write = pass(A, CONTENT, Set.of(), Set.of(), Set.of(TARGET));
        assertEquals(INVALID, RenderGraph.compile(List.of(write), Set.of(TARGET), Set.of(), 1).status());
        assertEquals(INVALID, compile(write, pass(B, POST, Set.of(), Set.of(), Set.of(TARGET))).status());
    }

    @Test void rejectsCyclesReversedStagesUnsupportedCapabilitiesAndBudgets() {
        var a = pass(A, CONTENT, Set.of(B), Set.of(), Set.of());
        var b = pass(B, CONTENT, Set.of(A), Set.of(), Set.of());
        assertEquals(CYCLE, compile(a, b).status());
        assertEquals(INVALID, compile(a, pass(B, POST, Set.of(), Set.of(), Set.of())).status());
        assertEquals(BUDGET_EXCEEDED, RenderGraph.compile(List.of(b), Set.of(), Set.of(), 0).status());
        var gpu = new RenderGraph.Pass(A, CONTENT, Set.of(), Set.of(), Set.of(), Set.of(id("gpu")), 1);
        assertEquals(UNSUPPORTED, compile(gpu).status());
        assertEquals(READY, RenderGraph.compile(List.of(gpu), Set.of(), Set.of(id("gpu")), 1).status());
    }

    private static RenderGraph.Compilation compile(RenderGraph.Pass... passes) {
        return RenderGraph.compile(List.of(passes), Set.of(), Set.of(), passes.length);
    }

    private static RenderGraph.Pass pass(UiId id, RenderGraph.Stage stage, Set<UiId> after, Set<UiId> reads,
                                         Set<UiId> writes) {
        return new RenderGraph.Pass(id, stage, after, reads, writes, Set.of(), 1);
    }

    private static UiId id(String value) { return new UiId("test", value); }
}
