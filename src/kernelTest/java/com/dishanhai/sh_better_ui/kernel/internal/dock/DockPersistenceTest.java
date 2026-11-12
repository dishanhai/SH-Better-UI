// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockEdge;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockMode;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockRequest;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DockPersistenceTest {
    @Test
    void roundTripKeepsDetachedPlacementPinDimensionsAndGroup() {
        var placements = new LinkedHashMap<UiId, WindowPlacement>();
        var primary = UiId.parse("mod:a");
        var secondary = UiId.parse("mod:b");
        placements.put(secondary, WindowPlacement.docked(DockEdge.LEFT, DockMode.EDGE_DOCKED, 240, 190));
        placements.put(primary, WindowPlacement.docked(DockEdge.RIGHT, DockMode.PINNED, 180, 91.25)
                .withGroup(UiId.parse("mod:group/tools")).withRequest(DockRequest.detached(-5.25, 30)));

        String text = DockPersistence.encode(placements);
        assertTrue(text.startsWith("version=2\nmod:a\tRIGHT\tDETACHED\t-5.25\t30.0\t180.0\t91.25\ttrue\tmod:group/tools\n"));
        assertEquals(placements, DockPersistence.decode(text));
        assertThrows(UnsupportedOperationException.class, () -> DockPersistence.decode(text).clear());
    }

    @Test
    void priorVersionRestoresWithoutAGroupAndWritesTheCurrentVersion() {
        var id = UiId.parse("mod:monitor");
        var migrated = DockPersistence.decode("version=1\r\nmod:monitor\tTOP\tFLOATING\t17\t19\t100\t60\tfalse\r\n");

        assertEquals(Map.of(id, new WindowPlacement(DockEdge.TOP, DockMode.FLOATING, 17, 19, 100, 60, false, null)),
                migrated);
        assertEquals(migrated, DockPersistence.decode(DockPersistence.encode(migrated)));
    }

    @Test
    void malformedRowsAndDuplicatesDoNotOverrideValidNeighbors() {
        String text = "version=2\n"
                + "broken\n"
                + "mod:a\tLEFT\tAUTO_HIDE\t0\t0\tNaN\t40\ttrue\t\n"
                + "mod:a\tLEFT\tAUTO_HIDE\t0\t0\t30\t40\tfalse\t\n"
                + "mod:a\tRIGHT\tAUTO_HIDE\t0\t0\t30\t40\tfalse\t\n"
                + "mod:b\tTOP\tPINNED\t0\t0\t50\t40\tyes\t\n";
        var restored = DockPersistence.decode(text);

        assertEquals(1, restored.size());
        assertEquals(DockEdge.LEFT, restored.get(UiId.parse("mod:a")).edge());
        assertThrows(IllegalArgumentException.class, () -> DockPersistence.decode("version=99\n"));
        assertThrows(IllegalArgumentException.class, () -> DockPersistence.decode(""));
    }
}
