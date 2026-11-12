// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Schema catalog. A platform advertises the subset for which it has renderers. */
public final class UiBuiltinComponents {
    public static final UiVersion VERSION = new UiVersion(1, 0, 0);

    private static final UiValueSchema TEXT = UiValueSchema.text(4096);
    private static final UiValueSchema LABEL = UiValueSchema.text(256);
    private static final UiValueSchema RESOURCE_ID = UiValueSchema.text(128);
    private static final UiValueSchema DIMENSION = UiValueSchema.integer(0, 8192);
    private static final UiValueSchema SPACING = UiValueSchema.integer(0, 256);
    private static final UiValueSchema NORMALIZED = UiValueSchema.decimal(0.0, 1.0);
    private static final UiPropertyDescriptor PAYLOAD_FIELD = optional(UiValueSchema.text(128), false);

    private static final Map<String, UiPropertyDescriptor> COMMON = Map.of(
            "visible", property(UiValueSchema.bool(), new UiValue.Bool(true), true),
            "enabled", property(UiValueSchema.bool(), new UiValue.Bool(true), true),
            "narration", optional(LABEL, true),
            "tooltip", optional(TEXT, true),
            "width", optional(DIMENSION, false),
            "height", optional(DIMENSION, false)
    );
    private static final Map<String, UiPropertyDescriptor> LAYOUT = Map.of(
            "gap", property(SPACING, new UiValue.Int(4), false),
            "padding", property(SPACING, new UiValue.Int(0), false)
    );
    private static final Map<UiId, UiComponentDescriptor> DESCRIPTORS = create();

    private UiBuiltinComponents() {}

    public static UiId id(String name) {
        return new UiId("sh_better_ui", name);
    }

    public static Map<UiId, UiComponentDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    private static Map<UiId, UiComponentDescriptor> create() {
        Map<UiId, UiComponentDescriptor> components = new HashMap<>();
        for (String name : Set.of("row", "column", "stack", "scroll_view", "panel", "card", "modal",
                "hud_panel", "notification")) {
            add(components, name, true, false, true, LAYOUT);
        }
        Map<String, UiPropertyDescriptor> grid = new HashMap<>(LAYOUT);
        grid.put("columns", property(UiValueSchema.integer(1, 32), new UiValue.Int(2), false));
        add(components, "grid", true, false, true, grid);
        for (String name : Set.of("spacer", "divider")) {
            add(components, name, false, false, true, Map.of());
        }
        for (String name : Set.of("text", "rich_text", "badge", "tooltip")) {
            add(components, name, false, false, true, Map.of("text", required(TEXT, true)));
        }
        add(components, "icon", false, false, true, Map.of("icon", required(RESOURCE_ID, true)));
        add(components, "image", false, false, true, Map.of("image", required(RESOURCE_ID, true)));
        add(components, "progress_bar", false, false, true, Map.of("value", required(NORMALIZED, true)));
        add(components, "button", false, true, true, Map.of("label", required(LABEL, true)));
        for (String name : Set.of("toggle", "checkbox")) {
            add(components, name, false, true, true, Map.of(
                    "label", required(LABEL, true),
                    "checked", property(UiValueSchema.bool(), new UiValue.Bool(false), true),
                    "payloadField", PAYLOAD_FIELD));
        }
        add(components, "slider", false, true, true, Map.of(
                "label", required(LABEL, true),
                "value", property(NORMALIZED, new UiValue.Decimal(0), true),
                "payloadField", PAYLOAD_FIELD));
        for (String name : Set.of("text_field", "search_field")) {
            add(components, name, false, true, true, Map.of(
                    "value", new UiPropertyDescriptor(LABEL, Optional.of(new UiValue.Text("")), false, false, false),
                    "placeholder", optional(LABEL, false),
                    "label", optional(LABEL, false),
                    "payloadField", PAYLOAD_FIELD));
        }
        for (String name : Set.of("dropdown", "tab_bar")) {
            add(components, name, false, true, true, Map.of(
                    "options", required(UiValueSchema.list(LABEL, 64), false),
                    "selected", optional(UiValueSchema.integer(0, 63), true),
                    "payloadField", PAYLOAD_FIELD));
        }
        for (String name : Set.of("list_view", "virtual_list")) {
            add(components, name, false, false, true, Map.of(
                    "items", required(UiValueSchema.list(LABEL, 512), true),
                    "rowHeight", property(UiValueSchema.integer(1, 128), new UiValue.Int(20), false),
                    "search", optional(UiValueSchema.text(128), false)));
        }
        add(components, "item_display", false, false, true, Map.of(
                "item", optional(UiValueSchema.itemReference(), true)));
        add(components, "ghost_slot", false, true, true, Map.of(
                "item", optional(UiValueSchema.itemReference(), true),
                "choices", optional(UiValueSchema.list(UiValueSchema.itemReference(), 64), false),
                "payloadField", PAYLOAD_FIELD));
        add(components, "container_slot", false, false, false, Map.of(
                "slot", required(UiValueSchema.integer(0, 511), false)));
        return Map.copyOf(components);
    }

    private static void add(Map<UiId, UiComponentDescriptor> target, String name, boolean children,
                            boolean action, boolean remote, Map<String, UiPropertyDescriptor> properties) {
        UiId id = id(name);
        Map<String, UiPropertyDescriptor> merged = new HashMap<>(COMMON);
        merged.putAll(properties);
        target.put(id, new UiComponentDescriptor(id, VERSION, merged, Set.of(), remote, children,
                action, Optional.empty()));
    }

    private static UiPropertyDescriptor optional(UiValueSchema schema, boolean bindable) {
        return new UiPropertyDescriptor(schema, Optional.empty(), false, bindable, true);
    }

    private static UiPropertyDescriptor required(UiValueSchema schema, boolean bindable) {
        return new UiPropertyDescriptor(schema, Optional.empty(), true, bindable, true);
    }

    private static UiPropertyDescriptor property(UiValueSchema schema, UiValue defaultValue, boolean bindable) {
        return new UiPropertyDescriptor(schema, Optional.of(defaultValue), false, bindable, true);
    }

    public static Optional<UiValueType> inputType(UiId id) {
        if (!id.namespace().equals("sh_better_ui")) return Optional.empty();
        return Optional.ofNullable(switch (id.path()) {
            case "toggle", "checkbox" -> UiValueType.BOOLEAN;
            case "slider" -> UiValueType.DOUBLE;
            case "text_field", "search_field" -> UiValueType.STRING;
            case "dropdown", "tab_bar" -> UiValueType.INTEGER;
            case "ghost_slot" -> UiValueType.ITEM_REFERENCE;
            default -> null;
        });
    }
}
