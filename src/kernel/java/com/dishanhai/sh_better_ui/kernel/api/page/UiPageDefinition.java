// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Transport-neutral page description. Each opened instance owns its own state and lifecycle. */
public record UiPageDefinition(UiId id, UiVersion schemaVersion, UiPageType type, UiNode root,
                               UiDataSchema dataSchema, Map<UiId, UiActionDefinition> actions,
                               UiPageOptions options) {
    public static final UiVersion SCHEMA_VERSION = new UiVersion(1, 0, 0);

    public UiPageDefinition {
        PageNames.id(id);
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(dataSchema, "dataSchema");
        PageNames.size(actions.size(), PageNames.MAX_COLLECTION_SIZE);
        actions = Map.copyOf(actions);
        actions.keySet().forEach(PageNames::id);
        Objects.requireNonNull(options, "options");
    }

    public static Builder builder(UiId id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final UiId id;
        private final Map<UiId, UiActionDefinition> actions = new LinkedHashMap<>();
        private final Map<String, UiValueSchema> fields = new LinkedHashMap<>();
        private UiPageType type = UiPageType.SCREEN;
        private UiNode root;
        private UiPageOptions options = UiPageOptions.DEFAULT;

        private Builder(UiId id) {
            this.id = PageNames.id(id);
        }

        public Builder type(UiPageType type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        public Builder root(UiNode root) {
            this.root = Objects.requireNonNull(root);
            return this;
        }

        public Builder root(String id, String type, Consumer<UiNode.Builder> configure) {
            UiNode.Builder node = UiNode.builtin(id, type);
            configure.accept(node);
            return root(node.build());
        }

        public Builder title(String title) {
            options = new UiPageOptions(title, options.requiredCapabilities());
            return this;
        }

        public Builder options(UiPageOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        public Builder field(String path, UiValueSchema field) {
            if (fields.putIfAbsent(PageNames.name(path), Objects.requireNonNull(field)) != null) {
                throw new IllegalArgumentException("Duplicate page data field: " + path);
            }
            return this;
        }

        public Builder action(UiActionDefinition action) {
            if (actions.putIfAbsent(action.id(), action) != null) {
                throw new IllegalArgumentException("Duplicate page action: " + action.id());
            }
            return this;
        }

        public UiPageDefinition build() {
            return new UiPageDefinition(id, SCHEMA_VERSION, type, root, new UiDataSchema(fields), actions, options);
        }
    }
}
