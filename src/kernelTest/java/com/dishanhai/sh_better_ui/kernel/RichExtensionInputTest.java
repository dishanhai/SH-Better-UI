// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionInput;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextDataSource;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextLayout;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextMarkup;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextSelection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichExtensionInputTest {
    private static final UiId ID = UiId.parse("test:rich");
    private static final UiId TYPE = UiId.parse("test:energy");
    private static final String MARKUP = "[ext=test:energy;1;32;8;8;Energy;full]";

    @Test void enabledAndMissingProvidersHaveTheSameLayoutAndStableIds() {
        var providers = new HashMap<UiId, RichExtensionInput.Format>();
        providers.put(TYPE, new RichExtensionInput.Format(1, "100"::equals, value -> {
            if (value.equals("full")) return "100";
            throw new IllegalArgumentException();
        }));
        var policy = new RichExtensionInput(providers);
        providers.clear();
        var enabled = RichTextMarkup.parse(ID, "A " + MARKUP + " Z", policy);
        var missing = RichTextMarkup.parse(ID, "A " + MARKUP + " Z", new RichExtensionInput(Map.of()));
        var active = (RichExtensionNode) enabled.nodes().get(1);
        var inactive = (RichExtensionNode) missing.nodes().get(1);
        assertEquals(active.id(), inactive.id());
        assertEquals("Energy", inactive.altText());
        assertTrue(active.renderable());
        assertFalse(inactive.renderable());
        assertEquals("100", active.payload());
        assertEquals("full", inactive.payload());
        assertEquals(enabled, RichTextMarkup.parse(ID, "A " + MARKUP + " Z", policy));
        var selection = new RichTextSelection(new RichTextSelection.Position(missing.nodes().get(0).id(), 0),
                new RichTextSelection.Position(missing.nodes().get(2).id(), 2));
        assertEquals("A Energy Z", selection.copy(enabled));
        assertEquals("A Energy Z", selection.copy(missing));
        var font = new RichTextLayout.FontMetrics() {
            @Override public int ascent() { return 7; }
            @Override public int descent() { return 2; }
            @Override public int width(String text) { return text.length() * 4; }
        };
        for (int width : new int[]{1, 16, 32, 54, 100}) {
            var a = RichTextLayout.layout(enabled, width, font);
            var b = RichTextLayout.layout(missing, width, font);
            var activeBoxes = a.lines().stream().flatMap(line -> line.fragments().stream())
                    .map(fragment -> fragment.bounds()).toList();
            var inactiveBoxes = b.lines().stream().flatMap(line -> line.fragments().stream())
                    .map(fragment -> fragment.bounds()).toList();
            assertEquals(activeBoxes, inactiveBoxes);
        }
    }

    @Test void dispatchIsExplicitAndChecksEnvelopeBeforeCallingTheProvider() {
        var calls = new AtomicInteger();
        var policy = new RichExtensionInput(Map.of(TYPE,
                new RichExtensionInput.Format(1, value -> false, value -> {
                    calls.incrementAndGet();
                    return value;
                })));
        assertThrows(IllegalArgumentException.class, () -> RichTextMarkup.parse(ID, MARKUP));
        assertThrows(IllegalArgumentException.class,
                () -> RichTextMarkup.parse(ID, MARKUP.replace(";32;", ";9999;"), policy));
        assertThrows(IllegalArgumentException.class, () -> RichTextMarkup.parse(ID,
                MARKUP.replace("test:energy", "test:../energy"), policy));
        assertThrows(IllegalArgumentException.class,
                () -> RichTextMarkup.parse(ID, MARKUP.replace("full", "x".repeat(4097)), policy));
        var unmatched = RichTextMarkup.parse(ID, MARKUP.replace(";1;", ";2;"), policy);
        assertFalse(((RichExtensionNode) unmatched.nodes().get(0)).renderable());
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class, () -> RichTextMarkup.parse(ID, MARKUP, policy));
        assertEquals(1, calls.get());
    }

    @Test void providerOutputAndCumulativeMarkupExpansionStayBounded() {
        var maxPayload = new RichExtensionInput(Map.of(TYPE,
                new RichExtensionInput.Format(1, value -> true, value -> "⚡".repeat(1365))));
        assertEquals(64, RichTextMarkup.parse(ID, MARKUP.repeat(64), maxPayload).nodes().size());
        assertThrows(IllegalArgumentException.class,
                () -> RichTextMarkup.parse(ID, MARKUP.repeat(65), maxPayload));
        var oversized = new RichExtensionInput(Map.of(TYPE,
                new RichExtensionInput.Format(1, value -> true, value -> "⚡".repeat(1366))));
        assertThrows(IllegalArgumentException.class, () -> RichTextMarkup.parse(ID, MARKUP, oversized));
        assertThrows(IllegalArgumentException.class, () -> new RichTextDataSource.Snapshot(-1, "0"));
        assertThrows(IllegalArgumentException.class, () -> new RichTextDataSource.Snapshot(1, "\ud800"));
    }
}
