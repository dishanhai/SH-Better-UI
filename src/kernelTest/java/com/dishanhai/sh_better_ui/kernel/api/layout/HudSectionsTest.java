// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HudSectionsTest {
    record Row(String section, String text) {}
    private static HudSections.Plan<Row> plan(List<Row> rows, boolean titles, boolean lines) {
        return HudSections.layout(rows, Row::section, key -> titles && !key.equals("controls"),
                key -> new Row(key, "title"), lines);
    }

    @Test void insertsOnlyVisibleHeadingsAndBoundaries() {
        var plan = plan(List.of(new Row("ae", "a"), new Row("ae", "b"),
                new Row("ftb", "c"), new Row("controls", "key")), true, true);
        assertEquals(List.of("title", "a", "b", "title", "c", "key"),
                plan.rows().stream().map(Row::text).toList());
        assertEquals(List.of(3, 5), plan.boundaries());
        assertTrue(plan(List.of(), true, true).rows().isEmpty());
    }

    @Test void optionsAndPageStartAreIndependent() {
        var rows = List.of(new Row("ftb", "c"), new Row("controls", "key"));
        assertEquals(List.of("c", "key"), plan(rows, false, false).rows().stream().map(Row::text).toList());
        assertEquals(List.of(2), plan(rows, true, true).boundaries());
        assertTrue(plan(rows, true, false).boundaries().isEmpty());
        assertEquals(List.of(1), plan(rows, false, true).boundaries());
    }
}
