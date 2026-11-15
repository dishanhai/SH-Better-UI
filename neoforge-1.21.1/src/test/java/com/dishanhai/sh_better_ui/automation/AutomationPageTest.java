// 作者：山海／dishanhai
package com.dishanhai.sh_better_ui.automation;

import com.dishanhai.sh_better_ui.kernel.api.page.*;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

final class AutomationPageTest {
    @Test void declarativeControlsAndInitialSnapshotPassRemoteValidation() {
        var page = AutomationPage.create(AutomationPage.ID, key -> key);
        var renderers = UiBuiltinComponents.descriptors().entrySet().stream()
                .collect(Collectors.toMap(java.util.Map.Entry::getKey, entry -> entry.getValue().version()));
        var resolved = new UiComponentRegistry().resolve(page, true, renderers);
        assertTrue(resolved.page().isPresent(), () -> resolved.problems().toString());
        assertDoesNotThrow(() -> page.dataSchema().validate(AutomationPage.initialData(""), true));
        for (String field : AutomationPage.TEXT_FIELDS) {
            assertTrue(nodes(page.root()).anyMatch(node -> node.id().equals(field + "_authority")
                    && field.equals(node.bindings().get("text"))));
            assertTrue(nodes(page.root()).filter(node -> node.typeId().path().equals("text_field"))
                    .noneMatch(node -> node.properties().containsKey("value")));
        }
    }

    @Test void editorPayloadAllowsMultilineScriptAndRejectsUnsupportedExecutionMode() {
        var payload = new LinkedHashMap<>(AutomationPage.initialData(""));
        payload.remove("status"); payload.remove("rules");
        payload.put("script", new UiValue.Text("// script\n".repeat(300)));
        assertDoesNotThrow(() -> AutomationPage.editSchema().validate(payload, true));
        payload.put("mode", new UiValue.Int(2));
        assertThrows(IllegalArgumentException.class, () -> AutomationPage.editSchema().validate(payload, true));
        payload.put("mode", new UiValue.Int(0));
        payload.remove("target");
        assertThrows(IllegalArgumentException.class, () -> AutomationPage.editSchema().validate(payload, true));
    }

    private static java.util.stream.Stream<UiNode> nodes(UiNode root) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(root), root.children().stream().flatMap(AutomationPageTest::nodes));
    }
}
