// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.Map;
import java.util.Objects;

/** Closed top-level field schema. Nested values retain the global bounded data limits. */
public record CapabilitySchema(Map<String, Field> fields) {
    public enum Type { TEXT, INTEGER, DECIMAL, BOOLEAN, SEQUENCE, FIELDS }
    public record Field(Type type, boolean required) {
        public Field { Objects.requireNonNull(type, "type"); }
    }
    public CapabilitySchema {
        Objects.requireNonNull(fields, "fields");
        if (fields.size() > CapabilityValue.MAX_FIELDS) throw new IllegalArgumentException("Schema field limit exceeded");
        fields = Map.copyOf(fields);
        for (String name : fields.keySet()) {
            if (!name.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid schema field name");
        }
    }
    public void validate(Map<String, CapabilityValue> values) {
        CapabilityValue.copyFields(values);
        for (var entry : values.entrySet()) {
            Field field = fields.get(entry.getKey());
            if (field == null || typeOf(entry.getValue()) != field.type()) {
                throw new IllegalArgumentException("Unexpected field or field type: " + entry.getKey());
            }
        }
        for (var entry : fields.entrySet()) {
            if (entry.getValue().required() && !values.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Missing required field: " + entry.getKey());
            }
        }
    }
    private static Type typeOf(CapabilityValue value) {
        if (value instanceof CapabilityValue.Text) return Type.TEXT;
        if (value instanceof CapabilityValue.IntegerValue) return Type.INTEGER;
        if (value instanceof CapabilityValue.Decimal) return Type.DECIMAL;
        if (value instanceof CapabilityValue.Flag) return Type.BOOLEAN;
        if (value instanceof CapabilityValue.Sequence) return Type.SEQUENCE;
        return Type.FIELDS;
    }
}
