// 作者：山海／dishanhai
package com.dishanhai.sh_better_ui.automation;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 服务端声明式控制中心；每次字段提交仍由服务器校验，按钮只提交明确的操作。 */
public final class AutomationPage {
    public static final UiId ID = UiId.parse("sh_better_ui:automation");
    public static final List<String> TEXT_FIELDS = List.of("ruleId", "target", "item", "lower", "targetAmount", "script");
    public static final List<String> OPERATIONS = List.of("save", "pause", "resume", "refresh", "run_script");

    private AutomationPage() {}

    public static UiId action(String operation) {
        return UiId.parse("sh_better_ui:automation_" + operation.toLowerCase(java.util.Locale.ROOT));
    }

    public static UiPageDefinition create(UiId id, Function<String, String> text) {
        var builder = UiPageDefinition.builder(id).title(t(text, "title"));
        TEXT_FIELDS.forEach(field -> builder.field(field, UiValueSchema.text(field.equals("script") ? 4096 : 256)));
        builder.field("mode", UiValueSchema.integer(0, 1));
        builder.field("editor", UiValueSchema.integer(0, 2));
        builder.field("status", UiValueSchema.text(4096));
        builder.field("rules", UiValueSchema.list(UiValueSchema.text(256), 512));
        for (String field : TEXT_FIELDS) builder.action(new UiActionDefinition(action("edit_" + field),
                UiActionPolicy.SERVER, new UiDataSchema(Map.of(field, UiValueSchema.text(field.equals("script") ? 4096 : 256)))));
        for (String field : List.of("mode", "editor")) builder.action(new UiActionDefinition(action("edit_" + field),
                UiActionPolicy.SERVER, new UiDataSchema(Map.of(field, UiValueSchema.integer(0, field.equals("mode") ? 1 : 2)))));
        for (String operation : OPERATIONS) builder.action(new UiActionDefinition(action(operation),
                operation.equals("run_script") || operation.equals("resume") ? UiActionPolicy.CONFIRMED : UiActionPolicy.SERVER,
                UiDataSchema.EMPTY));
        builder.action(new UiActionDefinition(action("apply"), UiActionPolicy.SERVER, editSchema()));

        var column = UiNode.builtin("automation_content", "column").property("gap", new UiValue.Int(6));
        column.child(UiNode.builtin("native_editors", "spacer").property("height", new UiValue.Int(24)).build());
        column.child(UiNode.builtin("intro", "text").text(t(text, "intro")).build());
        column.child(UiNode.builtin("edit_hint", "text").text(t(text, "edit_hint")).build());
        column.child(UiNode.builtin("status", "text").bind("text", "status").build());
        column.child(UiNode.builtin("template", "text").text(t(text, "template")).build());
        column.child(field("ruleId", "rule_id", text));
        column.child(field("target", "target", text));
        column.child(field("item", "item", text));
        column.child(field("lower", "lower", text));
        column.child(field("targetAmount", "target_amount", text));
        column.child(UiNode.builtin("mode_label", "text").text(t(text, "mode")).build());
        column.child(choice("mode", List.of("mode_page", "mode_online"), text));
        column.child(UiNode.builtin("editor_label", "text").text(t(text, "editor")).build());
        column.child(choice("editor", List.of("editor_form", "editor_nodes", "editor_script"), text));
        column.child(UiNode.builtin("script_hint", "text").text(t(text, "script_hint")).build());
        column.child(field("script", "script", text));
        for (String operation : OPERATIONS) column.child(UiNode.builtin(operation, "button")
                .label(t(text, operation)).action(action(operation)).build());
        column.child(UiNode.builtin("rules_label", "text").text(t(text, "rules")).build());
        column.child(UiNode.builtin("rules", "list_view").bind("items", "rules")
                .property("height", new UiValue.Int(100)).build());
        return builder.root(UiNode.builtin("automation_root", "scroll_view")
                .property("padding", new UiValue.Int(8)).child(column.build()).build()).build();
    }

    public static Map<String, UiValue> initialData(String status) {
        var data = new LinkedHashMap<String, UiValue>();
        data.put("ruleId", new UiValue.Text("restock_iron"));
        data.put("target", new UiValue.Text(""));
        data.put("item", new UiValue.Text("minecraft:iron_ingot"));
        data.put("lower", new UiValue.Text("64"));
        data.put("targetAmount", new UiValue.Text("256"));
        data.put("script", new UiValue.Text(""));
        data.put("mode", new UiValue.Int(0));
        data.put("editor", new UiValue.Int(0));
        data.put("status", new UiValue.Text(status));
        data.put("rules", new UiValue.Sequence(List.of()));
        return Map.copyOf(data);
    }

    public static UiDataSchema editSchema() {
        var fields = new LinkedHashMap<String, UiValueSchema>();
        TEXT_FIELDS.forEach(field -> fields.put(field, UiValueSchema.text(field.equals("script") ? 4096 : 256)));
        fields.put("mode", UiValueSchema.integer(0, 1));
        fields.put("editor", UiValueSchema.integer(0, 2));
        return new UiDataSchema(fields);
    }

    private static UiNode field(String field, String label, Function<String, String> text) {
        return UiNode.builtin(field + "_row", "column")
                .child(UiNode.builtin(field + "_label", "text").text(t(text, label)).build())
                .child(UiNode.builtin(field + "_authority", "text").bind("text", field).build())
                .child(UiNode.builtin(field, "text_field").label(t(text, label))
                        .property("payloadField", new UiValue.Text(field))
                        .action(action("edit_" + field)).build()).build();
    }

    private static UiNode choice(String field, List<String> keys, Function<String, String> text) {
        return UiNode.builtin(field, "dropdown")
                .property("options", new UiValue.Sequence(keys.stream().<UiValue>map(key -> new UiValue.Text(t(text, key))).toList()))
                .property("payloadField", new UiValue.Text(field)).bind("selected", field)
                .action(action("edit_" + field)).build();
    }

    private static String t(Function<String, String> text, String key) {
        return text.apply("sh_better_ui.automation." + key);
    }
}
