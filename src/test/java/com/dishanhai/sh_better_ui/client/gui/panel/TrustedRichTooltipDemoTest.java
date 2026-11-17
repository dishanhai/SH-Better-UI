// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustedRichTooltipDemoTest {
    @Test void sampleContainsNativeIconsAndExplicitPassiveCharts() {
        var document = TrustedRichTooltipDemo.document();
        var objects = document.nodes().stream().filter(RichObjectNode.class::isInstance)
                .map(RichObjectNode.class::cast).toList();
        assertEquals(3, objects.stream().filter(node -> node.content() instanceof RichObjectNode.Item).count());
        assertEquals(1, objects.stream().filter(node -> node.content() instanceof RichObjectNode.Fluid).count());
        assertTrue(objects.stream().allMatch(node -> !node.altText().isBlank()));
        var extensions = document.nodes().stream().filter(RichExtensionNode.class::isInstance)
                .map(RichExtensionNode.class::cast).toList();
        assertEquals(5, extensions.size());
        assertTrue(extensions.stream().allMatch(node -> node.payload().isEmpty() && !node.altText().isBlank()));
        assertTrue(extensions.stream().anyMatch(node -> node.type().path().endsWith("boiler_schematic")));
        assertTrue(extensions.stream().anyMatch(node -> node.type().path().endsWith("graph")));
        assertTrue(extensions.stream().anyMatch(node -> node.type().path().endsWith("bars")));
        assertEquals(document.nodes().size(), document.nodes().stream().map(node -> node.id()).distinct().count());
        String text = document.nodes().stream().filter(TextRun.class::isInstance).map(TextRun.class::cast)
                .map(TextRun::text).reduce("", String::concat);
        assertTrue(text.contains("演示数据"));
        assertTrue(text.contains("铜：40 L/t") && text.contains("铁：80 L/t") && text.contains("金：100 L/t"));
    }
}
