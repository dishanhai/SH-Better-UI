// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, bounded transport data. Game objects and executable Java objects are not values. */
public sealed interface CapabilityValue {
    int MAX_TEXT = 8192;
    int MAX_FIELDS = 64;
    int MAX_ITEMS = 256;
    int MAX_DEPTH = 8;
    int MAX_NODES = 2048;

    record Text(String value) implements CapabilityValue {
        public Text {
            Objects.requireNonNull(value, "value");
            if (value.length() > MAX_TEXT) throw new IllegalArgumentException("Text limit exceeded");
        }
    }
    record IntegerValue(long value) implements CapabilityValue {}
    record Decimal(double value) implements CapabilityValue {
        public Decimal {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Number must be finite");
        }
    }
    record Flag(boolean value) implements CapabilityValue {}
    record Sequence(List<CapabilityValue> values) implements CapabilityValue {
        public Sequence {
            if (values.size() > MAX_ITEMS) throw new IllegalArgumentException("Sequence limit exceeded");
            values = List.copyOf(values);
            validateTree(values, 1, new int[]{0});
        }
    }
    record Fields(Map<String, CapabilityValue> values) implements CapabilityValue {
        public Fields { values = copyFields(values); }
    }

    static Map<String, CapabilityValue> copyFields(Map<String, CapabilityValue> values) {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_FIELDS) throw new IllegalArgumentException("Field limit exceeded");
        for (String key : values.keySet()) {
            if (key == null || !key.matches("[a-z][a-z0-9_]{0,63}")) {
                throw new IllegalArgumentException("Invalid field name");
            }
        }
        Map<String, CapabilityValue> result = Map.copyOf(values);
        validateTree(result.values(), 1, new int[]{0});
        return result;
    }

    private static void validateTree(Iterable<CapabilityValue> values, int depth, int[] nodes) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Value depth limit exceeded");
        for (CapabilityValue value : values) {
            Objects.requireNonNull(value, "value");
            if (++nodes[0] > MAX_NODES) throw new IllegalArgumentException("Value node limit exceeded");
            if (value instanceof Sequence sequence) validateTree(sequence.values(), depth + 1, nodes);
            if (value instanceof Fields fields) validateTree(fields.values().values(), depth + 1, nodes);
        }
    }
}
