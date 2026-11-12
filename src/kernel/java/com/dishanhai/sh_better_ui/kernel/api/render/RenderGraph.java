// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.render;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/** Experimental dependency planner for one render target and one host-owned scope. */
public final class RenderGraph {
    private static final int MAX_PASSES = 128;
    private static final int MAX_LINKS = 64;

    private RenderGraph() {}

    public enum Stage { PREPARE, CONTENT, POST }
    public enum Status { READY, INVALID, CYCLE, UNSUPPORTED, BUDGET_EXCEEDED }

    /** Attachments have one producer. Imported attachments are read-only. Costs are host-defined units. */
    public record Pass(UiId id, Stage stage, Set<UiId> after, Set<UiId> reads, Set<UiId> writes,
                       Set<UiId> capabilities, long cost) {
        public Pass {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(stage, "stage");
            after = links(after);
            reads = links(reads);
            writes = links(writes);
            capabilities = links(capabilities);
            if (cost < 0) throw new IllegalArgumentException("Negative render pass cost");
        }
    }

    public record Compilation(Status status, Optional<UiId> pass, Optional<Plan> plan) {}

    /** Stable immutable order. Execute only inside an active render callback of the intended target. */
    public static final class Plan {
        private final List<Pass> order;
        private final long cost;

        private Plan(List<Pass> order, long cost) {
            this.order = List.copyOf(order);
            this.cost = cost;
        }

        public List<Pass> order() { return order; }
        public long cost() { return cost; }

        /**
         * Runs the bound backend in dependency order. Returning false aborts downstream work.
         * Backends own native render state restoration and attachment cleanup.
         */
        public boolean execute(Runner backend) {
            Objects.requireNonNull(backend, "backend");
            for (var pass : order) {
                if (!backend.draw(pass)) return false;
            }
            return true;
        }
    }

    @FunctionalInterface
    public interface Runner {
        boolean draw(Pass pass);
    }

    public static Compilation compile(List<Pass> passes, Set<UiId> imports, Set<UiId> capabilities, long budget) {
        if (budget < 0) throw new IllegalArgumentException("Negative render graph budget");
        if (passes.size() > MAX_PASSES || imports.size() > MAX_PASSES || capabilities.size() > MAX_PASSES) {
            return failed(Status.BUDGET_EXCEEDED, null);
        }
        var byId = new HashMap<UiId, Pass>();
        var writers = new HashMap<UiId, Pass>();
        var next = new HashMap<Pass, List<Pass>>();
        var inDegree = new HashMap<Pass, Integer>();
        long cost = 0;
        for (var pass : passes) {
            if (byId.putIfAbsent(pass.id(), pass) != null || !Collections.disjoint(pass.reads(), pass.writes())) {
                return failed(Status.INVALID, pass.id());
            }
            if (!capabilities.containsAll(pass.capabilities())) return failed(Status.UNSUPPORTED, pass.id());
            if (pass.cost() > budget - cost) return failed(Status.BUDGET_EXCEEDED, pass.id());
            cost += pass.cost();
            for (var write : pass.writes()) {
                if (imports.contains(write) || writers.putIfAbsent(write, pass) != null) {
                    return failed(Status.INVALID, pass.id());
                }
            }
            next.put(pass, new ArrayList<>());
        }
        for (var pass : passes) {
            var dependencies = new HashSet<Pass>();
            for (var dependency : pass.after()) {
                var producer = byId.get(dependency);
                if (producer == null) return failed(Status.INVALID, pass.id());
                dependencies.add(producer);
            }
            for (var attachment : pass.reads()) {
                if (imports.contains(attachment)) continue;
                var producer = writers.get(attachment);
                if (producer == null) return failed(Status.INVALID, pass.id());
                dependencies.add(producer);
            }
            for (var dependency : dependencies) {
                if (dependency.stage().compareTo(pass.stage()) > 0) return failed(Status.INVALID, pass.id());
                next.get(dependency).add(pass);
            }
            inDegree.put(pass, dependencies.size());
        }
        var ready = new PriorityQueue<>(Comparator.comparing(Pass::stage).thenComparing(Pass::id));
        inDegree.forEach((pass, count) -> {
            if (count == 0) ready.add(pass);
        });
        var order = new ArrayList<Pass>();
        while (!ready.isEmpty()) {
            var pass = ready.remove();
            order.add(pass);
            for (var successor : next.get(pass)) {
                int count = inDegree.merge(successor, -1, Integer::sum);
                if (count == 0) ready.add(successor);
            }
        }
        if (order.size() != passes.size()) {
            var unresolved = passes.stream().filter(pass -> inDegree.get(pass) > 0)
                    .map(Pass::id).min(UiId::compareTo).orElseThrow();
            return failed(Status.CYCLE, unresolved);
        }
        return new Compilation(Status.READY, Optional.empty(), Optional.of(new Plan(order, cost)));
    }

    private static Set<UiId> links(Set<UiId> ids) {
        if (ids.size() > MAX_LINKS) throw new IllegalArgumentException("Too many render pass dependencies");
        return Set.copyOf(ids);
    }

    private static Compilation failed(Status status, UiId pass) {
        return new Compilation(status, Optional.ofNullable(pass), Optional.empty());
    }
}
