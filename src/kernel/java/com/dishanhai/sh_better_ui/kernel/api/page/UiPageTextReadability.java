// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Client-local readability for a declarative Screen or modal. Node options inherit field by field. */
public record UiPageTextReadability(Options page, Map<String, Options> nodes) {
    public static final UiPageTextReadability DEFAULT = new UiPageTextReadability(Options.INHERIT, Map.of());

    public UiPageTextReadability {
        Objects.requireNonNull(page, "page");
        PageNames.size(nodes.size(), UiPageLimits.DEFAULT.maxNodes());
        nodes = Map.copyOf(nodes);
        nodes.keySet().forEach(PageNames::name);
    }

    public UiPageTextReadability(Options page) {
        this(page, Map.of());
    }

    /** Validates the target surface and checks that configured node IDs exist in the definition. */
    public void validateFor(UiPageDefinition definition) {
        if (equals(DEFAULT)) return;
        if (definition.type() != UiPageType.SCREEN && definition.type() != UiPageType.MODAL) {
            throw new IllegalArgumentException("Text readability requires a local Screen or modal page");
        }
        if (nodes.isEmpty()) return;
        var remaining = new HashSet<>(nodes.keySet());
        var pending = new ArrayDeque<UiNode>();
        pending.push(definition.root());
        while (!pending.isEmpty() && !remaining.isEmpty()) {
            var node = pending.pop();
            remaining.remove(node.id());
            pending.addAll(node.children());
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Unknown text readability nodes: " + remaining);
        }
    }

    public record Options(TextReadabilityPolicy policy, Sampling sampling) {
        public static final Options INHERIT = new Options(TextReadabilityPolicy.inherit(), null);

        public Options {
            Objects.requireNonNull(policy, "policy");
        }

        public Options(TextReadabilityPolicy policy) {
            this(policy, null);
        }

        public Options overlay(Options overrides) {
            return new Options(policy.overlay(overrides.policy),
                    overrides.sampling == null ? sampling : overrides.sampling);
        }
    }

    /** A null option inherits. AUTO hints retain any explicit-only restriction on the page. */
    public enum Sampling { DEFAULT, IMPORTANT, HOVERED, FOCUSED, DECLARED_ONLY, AUTO_HOVERED, AUTO_FOCUSED }
}
