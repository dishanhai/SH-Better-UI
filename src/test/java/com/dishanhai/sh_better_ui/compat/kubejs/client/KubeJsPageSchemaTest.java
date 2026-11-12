// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.SamplingHint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KubeJsPageSchemaTest {
    private static final String PAGE_ID = "kubejs:example";
    private static final String ACTION_ID = "kubejs:example_action";

    @Test
    void convertsRhinoShapedObjectsToTypedImmutablePageAndValues() {
        var page = KubeJsPageSchema.define(PAGE_ID, source());
        assertEquals(UiActionPolicy.LOCAL, page.actions().get(UiId.parse(ACTION_ID)).policy());
        assertEquals("demo.count", page.root().children().get(0).bindings().get("text"));
        assertEquals(new UiValue.Text("Increase"), page.root().children().get(1).properties().get("label"));
        assertEquals(Map.of("demo.count", new UiValue.Int(2)),
                KubeJsPageSchema.data(page.dataSchema(), Map.of("demo.count", 2.0), true));
        assertEquals(Map.of(), KubeJsPageSchema.data(page.dataSchema(), Map.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> KubeJsPageSchema.data(page.dataSchema(), Map.of("demo.count", 1.5), false));
        assertThrows(IllegalArgumentException.class,
                () -> KubeJsPageSchema.data(page.dataSchema(), Map.of("demo.count", 101), true));
        assertThrows(IllegalArgumentException.class,
                () -> KubeJsPageSchema.data(page.dataSchema(), Map.of("demo.authoritative", 500), false));
    }

    @Test
    void rejectsForeignIdsExecutableObjectsAndUnknownKeys() {
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.define("foreign:example", source()));
        Map<String, Object> foreignAction = new LinkedHashMap<>(source());
        foreignAction.put("actions", List.of("foreign:action"));
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.define(PAGE_ID, foreignAction));
        Map<String, Object> executable = new LinkedHashMap<>(source());
        executable.put("actions", List.of((Runnable) () -> fail("Unexpected script callback")));
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.define(PAGE_ID, executable));
        Map<String, Object> unknown = new LinkedHashMap<>(source());
        unknown.put("permission", "remote");
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.define(PAGE_ID, unknown));
    }

    @Test
    void refusesCyclicAndOversizedScriptTreesBeforeBuilding() {
        Map<String, Object> node = new LinkedHashMap<>();
        List<Map<String, Object>> children = new ArrayList<>();
        node.put("id", "root");
        node.put("type", "column");
        node.put("children", children);
        children.add(node);
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.define(PAGE_ID, Map.of("root", node)));
        children.clear();
        for (int i = 0; i < 65; i++) {
            children.add(Map.of("id", "node" + i, "type", "spacer"));
        }
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.define(PAGE_ID, Map.of("root", node)));
    }

    @Test
    void convertsLocalPageAndControlReadabilityWithoutChangingThePageSchema() {
        var configured = KubeJsPageSchema.defineWithText(PAGE_ID, Map.of("type", "modal",
                "textReadability", Map.of("adaptiveColor", true, "contrast", 7.0, "sampling", "important",
                        "samplingMode", "explicit_only", "lockHue", true),
                "root", Map.of("id", "body", "type", "text", "text", "Example",
                        "textReadability", Map.of("contrast", "classified", "lockHue", false, "localColor", true))));
        var options = configured.text().page().overlay(configured.text().nodes().get("body"));
        var policy = options.policy().resolve();
        assertTrue(policy.adaptiveColor());
        assertEquals(TextReadabilityPolicy.ClassifiedContrast.INSTANCE, policy.contrast());
        assertEquals(TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY, policy.samplingMode());
        assertEquals(Boolean.FALSE, policy.lockHue());
        assertTrue(policy.localColor());
        assertEquals(SamplingHint.IMPORTANT, options.sampling());
        assertEquals(Map.of("text", new UiValue.Text("Example")), configured.page().root().properties());
    }

    @Test
    void rejectsInvalidReadabilityOptionsBeforeRegistration() {
        for (var invalid : List.of(Map.of("contrast", 22), Map.of("contrast", Double.NaN),
                Map.of("localColor", "true"), Map.of("sampling", "invalid"),
                Map.of("samplingMode", 1), Map.of("unknown", false))) {
            assertThrows(IllegalArgumentException.class, () -> KubeJsTextReadabilityApi.options(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> KubeJsPageSchema.defineWithText(PAGE_ID, Map.of("type", "hud",
                "textReadability", Map.of("adaptiveColor", true),
                "root", Map.of("id", "body", "type", "text", "text", "Example"))));
    }

    @Test
    void onlyPopupConfigurationCanExplicitlyInheritThePage() {
        assertFalse(KubeJsTextReadabilityApi.popupOptions(Map.of("adaptiveColor", true)).inheritPage());
        var popup = KubeJsTextReadabilityApi.popupOptions(Map.of("inheritPage", true, "contrast", 7));
        assertTrue(popup.inheritPage());
        assertEquals(new TextReadabilityPolicy.FixedContrast(7), popup.text().policy().contrast());
        assertThrows(IllegalArgumentException.class,
                () -> KubeJsTextReadabilityApi.popupOptions(Map.of("inheritPage", "true")));
        assertThrows(IllegalArgumentException.class, () -> KubeJsTextReadabilityApi.options(Map.of("inheritPage", true)));
    }

    static Map<String, Object> source() {
        return Map.of("title", "Example", "type", "screen", "fields",
                Map.of("demo.count", Map.of("type", "integer", "min", 0, "max", 100)), "actions", List.of(ACTION_ID),
                "root", Map.of("id", "root", "type", "column", "children", List.of(
                        Map.of("id", "count", "type", "text", "bind", "demo.count"),
                        Map.of("id", "increment", "type", "button", "label", "Increase", "action", ACTION_ID))));
    }
}
