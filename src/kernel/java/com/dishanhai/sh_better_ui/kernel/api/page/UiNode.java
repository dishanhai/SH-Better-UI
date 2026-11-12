// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Immutable node in a fixed page tree. Bindings map property names to declared field paths. */
public record UiNode(String id, UiId typeId, Map<String, UiValue> properties,
                     Map<String, String> bindings, Optional<UiId> action,
                     List<UiNode> children, UiNodeFallback fallback, Optional<UiComponentAlternative> alternative) {
    public UiNode {
        PageNames.name(id);
        PageNames.id(typeId);
        PageNames.size(properties.size(), PageNames.MAX_COLLECTION_SIZE);
        PageNames.size(bindings.size(), PageNames.MAX_COLLECTION_SIZE);
        PageNames.size(children.size(), PageNames.MAX_COLLECTION_SIZE);
        properties = Map.copyOf(properties);
        bindings = Map.copyOf(bindings);
        children = List.copyOf(children);
        properties.keySet().forEach(PageNames::name);
        bindings.forEach((name, path) -> {
            PageNames.name(name);
            PageNames.name(path);
        });
        Objects.requireNonNull(action, "action").ifPresent(PageNames::id);
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(alternative, "alternative").ifPresent(replacement -> {
            if (action.isPresent() || typeId.equals(replacement.typeId())) {
                throw new IllegalArgumentException("A replacement requires a different, non-action component");
            }
        });
    }

    public UiNode(String id, UiId typeId, Map<String, UiValue> properties,
                  Map<String, String> bindings, Optional<UiId> action,
                  List<UiNode> children, UiNodeFallback fallback) {
        this(id, typeId, properties, bindings, action, children, fallback, Optional.empty());
    }

    public static Builder builder(String id, UiId typeId) {
        return new Builder(id, typeId);
    }

    public static Builder builtin(String id, String type) {
        return builder(id, UiBuiltinComponents.id(type));
    }

    public static final class Builder {
        private final String id;
        private final UiId typeId;
        private final Map<String, UiValue> properties = new LinkedHashMap<>();
        private final Map<String, String> bindings = new LinkedHashMap<>();
        private final List<UiNode> children = new ArrayList<>();
        private UiId action;
        private UiNodeFallback fallback = UiNodeFallback.REJECT;
        private UiComponentAlternative alternative;

        private Builder(String id, UiId typeId) {
            this.id = PageNames.name(id);
            this.typeId = PageNames.id(typeId);
        }

        public Builder property(String property, UiValue value) {
            if (properties.putIfAbsent(PageNames.name(property), Objects.requireNonNull(value)) != null) {
                throw new IllegalArgumentException("Duplicate component property: " + property);
            }
            return this;
        }

        public Builder text(String value) {
            return property("text", new UiValue.Text(value));
        }

        public Builder label(String value) {
            return property("label", new UiValue.Text(value));
        }

        public Builder bind(String property, String field) {
            if (bindings.putIfAbsent(PageNames.name(property), PageNames.name(field)) != null) {
                throw new IllegalArgumentException("Duplicate property binding: " + property);
            }
            return this;
        }

        public Builder action(UiId id) {
            action = PageNames.id(id);
            return this;
        }

        public Builder child(UiNode node) {
            children.add(Objects.requireNonNull(node));
            return this;
        }

        public Builder child(String id, String type, Consumer<Builder> configure) {
            Builder child = builtin(id, type);
            configure.accept(child);
            return child(child.build());
        }

        public Builder fallback(UiNodeFallback fallback) {
            this.fallback = Objects.requireNonNull(fallback);
            return this;
        }

        public Builder alternative(UiComponentAlternative alternative) {
            this.alternative = Objects.requireNonNull(alternative);
            return this;
        }

        public UiNode build() {
            return new UiNode(id, typeId, properties, bindings, Optional.ofNullable(action), children, fallback,
                    Optional.ofNullable(alternative));
        }
    }
}
