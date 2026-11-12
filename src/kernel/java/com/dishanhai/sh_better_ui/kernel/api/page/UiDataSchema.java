// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import java.util.Map;
import java.util.Objects;

/** A fixed field namespace shared by initial data, patches and action payloads. */
public record UiDataSchema(Map<String, UiValueSchema> fields) {
    public static final UiDataSchema EMPTY = new UiDataSchema(Map.of());

    public UiDataSchema {
        PageNames.size(fields.size(), PageNames.MAX_COLLECTION_SIZE);
        fields = Map.copyOf(fields);
        fields.keySet().forEach(PageNames::name);
        UiValueSchema.checkSchemaBudget(fields.values());
    }

    /** A partial patch replaces complete fields. Every field in a full snapshot is required. */
    public void validate(Map<String, UiValue> values, boolean requireAll) {
        Objects.requireNonNull(values, "values");
        PageNames.size(values.size(), fields.size());
        if (requireAll && values.size() != fields.size()) {
            throw new IllegalArgumentException("Required page data fields are missing");
        }
        UiValueSchema.Budget budget = new UiValueSchema.Budget();
        values.forEach((path, value) -> {
            PageNames.name(path);
            UiValueSchema schema = fields.get(path);
            if (schema == null) {
                throw new IllegalArgumentException("Undeclared page data field: " + path);
            }
            schema.validate(value, path, 1, budget);
        });
    }
}
