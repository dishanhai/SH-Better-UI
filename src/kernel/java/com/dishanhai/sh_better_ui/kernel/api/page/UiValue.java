// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.List;
import java.util.Map;

/** Immutable data admitted by page definitions and runtime patches. */
public sealed interface UiValue {
    UiValueType type();

    record Bool(boolean value) implements UiValue {
        @Override public UiValueType type() { return UiValueType.BOOLEAN; }
    }

    record Int(int value) implements UiValue {
        @Override public UiValueType type() { return UiValueType.INTEGER; }
    }

    record LongInt(long value) implements UiValue {
        @Override public UiValueType type() { return UiValueType.LONG; }
    }

    record Decimal(double value) implements UiValue {
        public Decimal {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Page decimal must be finite");
            }
        }

        @Override public UiValueType type() { return UiValueType.DOUBLE; }
    }

    record Text(String value) implements UiValue {
        public Text { PageNames.text(value, PageNames.MAX_TEXT_LENGTH); }

        @Override public UiValueType type() { return UiValueType.STRING; }
    }

    record Choice(String value) implements UiValue {
        public Choice { PageNames.name(value); }

        @Override public UiValueType type() { return UiValueType.ENUM; }
    }

    record Sequence(List<UiValue> value) implements UiValue {
        public Sequence {
            PageNames.size(value.size(), PageNames.MAX_COLLECTION_SIZE);
            value = List.copyOf(value);
        }

        @Override public UiValueType type() { return UiValueType.LIST; }
    }

    record ObjectValue(Map<String, UiValue> value) implements UiValue {
        public ObjectValue {
            PageNames.size(value.size(), PageNames.MAX_COLLECTION_SIZE);
            value = Map.copyOf(value);
            value.keySet().forEach(PageNames::name);
        }

        @Override public UiValueType type() { return UiValueType.OBJECT; }
    }

    /** Display metadata. It carries no mutable inventory or native stack state. */
    record ItemReference(UiId itemId, int count) implements UiValue {
        public ItemReference {
            PageNames.id(itemId);
            if (count < 0) {
                throw new IllegalArgumentException("Item display count must be nonnegative");
            }
        }

        @Override public UiValueType type() { return UiValueType.ITEM_REFERENCE; }
    }
}
