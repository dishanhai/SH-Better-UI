// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Constraints shared by component properties, page fields and action arguments. */
public record UiValueSchema(UiValueType type, long minimumInteger, long maximumInteger,
                            double minimumDecimal, double maximumDecimal, int maxSize,
                            Set<String> choices, Optional<UiValueSchema> element,
                            Map<String, UiValueSchema> fields) {
    public UiValueSchema {
        Objects.requireNonNull(type, "type");
        PageNames.size(choices.size(), PageNames.MAX_COLLECTION_SIZE);
        PageNames.size(fields.size(), PageNames.MAX_COLLECTION_SIZE);
        choices = Set.copyOf(choices);
        choices.forEach(PageNames::name);
        fields = Map.copyOf(fields);
        fields.keySet().forEach(PageNames::name);
        Objects.requireNonNull(element, "element");

        if (minimumInteger > maximumInteger || (type == UiValueType.INTEGER
                && (minimumInteger < Integer.MIN_VALUE || maximumInteger > Integer.MAX_VALUE))) {
            throw new IllegalArgumentException("Invalid integer schema range");
        }
        if (!Double.isFinite(minimumDecimal) || !Double.isFinite(maximumDecimal)
                || minimumDecimal > maximumDecimal) {
            throw new IllegalArgumentException("Invalid decimal schema range");
        }
        int maximumSize = type == UiValueType.STRING ? PageNames.MAX_TEXT_LENGTH : PageNames.MAX_COLLECTION_SIZE;
        if (maxSize < 0 || maxSize > maximumSize) {
            throw new IllegalArgumentException("Invalid schema size limit");
        }
        if ((type == UiValueType.ENUM) != !choices.isEmpty() || (type == UiValueType.LIST) != element.isPresent()
                || (type != UiValueType.OBJECT && !fields.isEmpty())) {
            throw new IllegalArgumentException("Schema type and constraints do not agree");
        }
        if ((type != UiValueType.INTEGER && type != UiValueType.LONG && (minimumInteger != 0 || maximumInteger != 0))
                || (type != UiValueType.DOUBLE && (minimumDecimal != 0 || maximumDecimal != 0))
                || (type != UiValueType.LIST && type != UiValueType.STRING && maxSize != 0)) {
            throw new IllegalArgumentException("Schema includes constraints for a different type");
        }
    }

    public static UiValueSchema bool() {
        return scalar(UiValueType.BOOLEAN, 0, 0, 0, 0, 0);
    }

    public static UiValueSchema integer(int minimum, int maximum) {
        return scalar(UiValueType.INTEGER, minimum, maximum, 0, 0, 0);
    }

    public static UiValueSchema longInt(long minimum, long maximum) {
        return scalar(UiValueType.LONG, minimum, maximum, 0, 0, 0);
    }

    public static UiValueSchema decimal(double minimum, double maximum) {
        return scalar(UiValueType.DOUBLE, 0, 0, minimum, maximum, 0);
    }

    public static UiValueSchema text(int maximumLength) {
        return scalar(UiValueType.STRING, 0, 0, 0, 0, maximumLength);
    }

    public static UiValueSchema choice(Set<String> choices) {
        return new UiValueSchema(UiValueType.ENUM, 0, 0, 0, 0, 0, choices, Optional.empty(), Map.of());
    }

    public static UiValueSchema list(UiValueSchema element, int maximumEntries) {
        return new UiValueSchema(UiValueType.LIST, 0, 0, 0, 0, maximumEntries,
                Set.of(), Optional.of(element), Map.of());
    }

    public static UiValueSchema object(Map<String, UiValueSchema> fields) {
        return new UiValueSchema(UiValueType.OBJECT, 0, 0, 0, 0, 0, Set.of(), Optional.empty(), fields);
    }

    public static UiValueSchema itemReference() {
        return scalar(UiValueType.ITEM_REFERENCE, 0, 0, 0, 0, 0);
    }

    private static UiValueSchema scalar(UiValueType type, long minimumInteger, long maximumInteger,
                                       double minimumDecimal, double maximumDecimal, int maxSize) {
        return new UiValueSchema(type, minimumInteger, maximumInteger, minimumDecimal, maximumDecimal,
                maxSize, Set.of(), Optional.empty(), Map.of());
    }

    /** Returns whether every value described by source fits this target schema. */
    public boolean accepts(UiValueSchema source) {
        checkSchemaBudget(List.of(this, source));
        return acceptsChecked(source);
    }

    private boolean acceptsChecked(UiValueSchema source) {
        if (type != source.type) {
            return false;
        }
        return switch (type) {
            case INTEGER, LONG -> minimumInteger <= source.minimumInteger && source.maximumInteger <= maximumInteger;
            case DOUBLE -> minimumDecimal <= source.minimumDecimal && source.maximumDecimal <= maximumDecimal;
            case STRING -> source.maxSize <= maxSize;
            case ENUM -> choices.containsAll(source.choices);
            case LIST -> source.maxSize <= maxSize && element.orElseThrow().acceptsChecked(source.element.orElseThrow());
            case OBJECT -> fields.keySet().equals(source.fields.keySet()) && fields.entrySet().stream()
                    .allMatch(field -> field.getValue().acceptsChecked(source.fields.get(field.getKey())));
            case BOOLEAN, ITEM_REFERENCE -> true;
        };
    }

    public void validate(UiValue value) {
        validate(value, "value", 1, new Budget());
    }

    void validate(UiValue value, String path, int depth, Budget budget) {
        budget.enter(depth);
        if (value == null || type != value.type()) {
            throw new IllegalArgumentException("Expected " + type + " at " + path
                    + ", got " + (value == null ? "null" : value.type()));
        }
        boolean valid = switch (type) {
            case BOOLEAN, ITEM_REFERENCE -> true;
            case INTEGER -> validInteger(((UiValue.Int) value).value());
            case LONG -> validInteger(((UiValue.LongInt) value).value());
            case DOUBLE -> {
                double number = ((UiValue.Decimal) value).value();
                yield minimumDecimal <= number && number <= maximumDecimal;
            }
            case STRING -> ((UiValue.Text) value).value().length() <= maxSize;
            case ENUM -> choices.contains(((UiValue.Choice) value).value());
            case LIST -> validateList(((UiValue.Sequence) value).value(), path, depth, budget);
            case OBJECT -> validateObject(((UiValue.ObjectValue) value).value(), path, depth, budget);
        };
        if (!valid) {
            throw new IllegalArgumentException("Value at " + path + " is outside the declared " + type + " constraints");
        }
    }

    private boolean validInteger(long number) {
        return minimumInteger <= number && number <= maximumInteger;
    }

    private boolean validateList(List<UiValue> values, String path, int depth, Budget budget) {
        if (values.size() > maxSize) {
            return false;
        }
        UiValueSchema item = element.orElseThrow();
        for (int i = 0; i < values.size(); i++) {
            item.validate(values.get(i), path + "[" + i + "]", depth + 1, budget);
        }
        return true;
    }

    private boolean validateObject(Map<String, UiValue> values, String path, int depth, Budget budget) {
        if (!values.keySet().equals(fields.keySet())) {
            return false;
        }
        values.forEach((name, value) -> fields.get(name).validate(value, path + "." + name, depth + 1, budget));
        return true;
    }

    static void checkSchemaBudget(Iterable<UiValueSchema> schemas) {
        Budget budget = new Budget();
        schemas.forEach(schema -> schema.checkDepth(1, budget));
    }

    static void checkValueBudget(UiValue value, int depth, Budget budget) {
        budget.enter(depth);
        if (value instanceof UiValue.Sequence list) {
            list.value().forEach(item -> checkValueBudget(item, depth + 1, budget));
        } else if (value instanceof UiValue.ObjectValue object) {
            object.value().values().forEach(item -> checkValueBudget(item, depth + 1, budget));
        }
    }

    private void checkDepth(int depth, Budget budget) {
        budget.enter(depth);
        element.ifPresent(schema -> schema.checkDepth(depth + 1, budget));
        fields.values().forEach(schema -> schema.checkDepth(depth + 1, budget));
    }

    static final class Budget {
        private int nodes;

        void enter(int depth) {
            if (depth > PageNames.MAX_VALUE_DEPTH || ++nodes > PageNames.MAX_VALUE_NODES) {
                throw new IllegalArgumentException("Page value or schema exceeds its depth or node limit");
            }
        }
    }
}
