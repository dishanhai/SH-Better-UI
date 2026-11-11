// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Stable bootstrap order and the dependencies required during initialization. */
final class DependencyGraph {
    private DependencyGraph() {
    }

    record Plan(List<ModuleDiscovery.Module> order, Map<UiId, Set<UiId>> requiredDependencies,
                List<String> diagnostics) {}

    private record Edge(UiId consumer, UiId provider, String requirement) {}

    static final class Rejected extends IllegalArgumentException {
        private final Set<UiId> affected;

        Rejected(String message, Set<UiId> affected) {
            super(message);
            this.affected = Set.copyOf(affected);
        }

        Set<UiId> affected() { return affected; }
    }

    static Plan resolve(List<ModuleDiscovery.Module> discovered) {
        var modules = new TreeMap<UiId, ModuleDiscovery.Module>();
        var edges = new TreeMap<UiId, Set<UiId>>();
        for (var module : discovered) {
            modules.put(module.id(), module);
            edges.put(module.id(), new TreeSet<>());
        }
        var capabilities = CapabilityIndex.fromModules(discovered);
        var optional = new ArrayList<Edge>();
        var diagnostics = new ArrayList<String>();
        for (var consumer : modules.values()) {
            var descriptor = consumer.descriptor();
            for (UiId conflict : new TreeSet<>(descriptor.conflicts())) {
                if (modules.containsKey(conflict)) {
                    throw new Rejected("Conflicting UI modules: " + consumer.id() + " and " + conflict,
                            Set.copyOf(List.of(consumer.id(), conflict)));
                }
            }
            new TreeMap<>(descriptor.dependencies()).forEach((id, dependency) -> {
                var provider = modules.get(id);
                boolean compatible = provider != null && eligible(consumer, provider)
                        && dependency.versions().contains(provider.descriptor().version());
                require(consumer.id(), compatible ? id : null, "module " + id + " " + dependency.versions(),
                        dependency.required(), edges, optional, diagnostics);
            });
            resolveCapabilities(consumer, descriptor.requiredCapabilities(), true, capabilities, modules,
                    edges, optional, diagnostics);
            resolveCapabilities(consumer, descriptor.optionalCapabilities(), false, capabilities, modules,
                    edges, optional, diagnostics);
        }
        var required = new TreeMap<UiId, Set<UiId>>();
        edges.forEach((module, providers) -> required.put(module, Set.copyOf(providers)));
        var order = sort(modules, edges);
        boolean added = false;
        for (var edge : optional) {
            if (edges.get(edge.consumer()).contains(edge.provider())) {
                continue;
            }
            if (reaches(edges, edge.provider(), edge.consumer())) {
                diagnostics.add(edge.consumer() + " skipped optional " + edge.requirement() + " to avoid a cycle");
            } else {
                edges.get(edge.consumer()).add(edge.provider());
                added = true;
            }
        }
        return new Plan(added ? sort(modules, edges) : order, Map.copyOf(required), List.copyOf(diagnostics));
    }

    private static boolean eligible(ModuleDiscovery.Module consumer, ModuleDiscovery.Module provider) {
        return !consumer.id().equals(provider.id()) && provider.descriptor().initializationPhase()
                .compareTo(consumer.descriptor().initializationPhase()) <= 0;
    }

    private static void resolveCapabilities(ModuleDiscovery.Module consumer,
                                            Map<CapabilityKey, VersionRange> requirements, boolean required,
                                            CapabilityIndex capabilities, Map<UiId, ModuleDiscovery.Module> modules,
                                            Map<UiId, Set<UiId>> edges, List<Edge> optional, List<String> diagnostics) {
        new TreeMap<>(requirements).forEach((key, range) -> {
            var offer = capabilities.select(key, range, provider -> eligible(consumer, modules.get(provider)));
            require(consumer.id(), offer == null ? null : offer.provider(), "capability " + key.id() + " " + range,
                    required, edges, optional, diagnostics);
        });
    }

    private static void require(UiId consumer, UiId provider, String requirement, boolean required,
                                Map<UiId, Set<UiId>> edges, List<Edge> optional, List<String> diagnostics) {
        if (provider == null) {
            if (required) {
                throw new Rejected(consumer + " has no compatible required " + requirement, Set.of(consumer));
            }
            diagnostics.add(consumer + " has no compatible optional " + requirement);
        } else if (required) {
            edges.get(consumer).add(provider);
        } else {
            optional.add(new Edge(consumer, provider, requirement));
        }
    }

    private static List<ModuleDiscovery.Module> sort(Map<UiId, ModuleDiscovery.Module> modules,
                                                       Map<UiId, Set<UiId>> edges) {
        var incoming = new TreeMap<UiId, Integer>();
        var dependents = new HashMap<UiId, List<UiId>>();
        var ready = new PriorityQueue<UiId>(Comparator
                .comparing((UiId id) -> modules.get(id).descriptor().initializationPhase()).thenComparing(id -> id));
        edges.forEach((consumer, providers) -> {
            incoming.put(consumer, providers.size());
            if (providers.isEmpty()) {
                ready.add(consumer);
            }
            for (UiId provider : providers) {
                dependents.computeIfAbsent(provider, id -> new ArrayList<>()).add(consumer);
            }
        });
        var sorted = new ArrayList<ModuleDiscovery.Module>();
        while (!ready.isEmpty()) {
            UiId id = ready.remove();
            sorted.add(modules.get(id));
            incoming.remove(id);
            for (UiId dependent : dependents.getOrDefault(id, List.of())) {
                if (incoming.compute(dependent, (ignored, count) -> count - 1) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (!incoming.isEmpty()) {
            var path = cycle(edges, incoming.keySet());
            throw new Rejected("UI module dependency cycle: "
                    + path.stream().map(UiId::toString).collect(Collectors.joining(" -> ")), new HashSet<>(path));
        }
        return List.copyOf(sorted);
    }

    private static List<UiId> cycle(Map<UiId, Set<UiId>> edges, Set<UiId> remaining) {
        var path = new ArrayList<UiId>();
        var visited = new HashMap<UiId, Integer>();
        UiId next = remaining.iterator().next();
        while (!visited.containsKey(next)) {
            visited.put(next, path.size());
            path.add(next);
            next = edges.get(next).stream().filter(remaining::contains).findFirst().orElseThrow();
        }
        int start = visited.get(next);
        path.add(next);
        return path.subList(start, path.size());
    }

    private static boolean reaches(Map<UiId, Set<UiId>> edges, UiId from, UiId target) {
        var queue = new ArrayDeque<UiId>();
        var visited = new HashSet<UiId>();
        queue.add(from);
        while (!queue.isEmpty()) {
            UiId next = queue.remove();
            if (next.equals(target)) {
                return true;
            }
            if (visited.add(next)) {
                queue.addAll(edges.get(next));
            }
        }
        return false;
    }
}
