// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedMetricSourceTest {
    @Test void explicitUpdatesPreserveSnapshotsAndIncrementOnlyWhenChanged() {
        var source = new BoundedMetricSource(40, 0, 100);
        var before = source.sample();
        assertEquals(0, before.revision());
        assertEquals("metric-v1|40.0|0.0|100.0", before.value());
        source.set(40, 0, 100);
        assertSame(before, source.sample());
        source.set(60, 0, 100);
        assertEquals(1, source.state().revision());
        assertEquals(0.6, source.state().ratio());
        assertEquals(40, BoundedMetricSource.decode(before).orElseThrow().value());
        assertEquals(source.state(), BoundedMetricSource.decode(source.sample()).orElseThrow());
        source.set(60, -100, 100);
        assertEquals(2, source.state().revision());
        assertEquals(0.8, source.state().ratio());
    }

    @Test void invalidChangesAreRejectedAtomically() {
        var source = new BoundedMetricSource(40, 0, 100);
        var before = source.sample();
        double[][] cases = {{Double.NaN, 0, 100}, {Double.POSITIVE_INFINITY, 0, 100},
                {0, Double.NEGATIVE_INFINITY, 100}, {0, 0, Double.NaN}, {1, 1, 1},
                {0, 5, 1}, {-1, 0, 100}, {101, 0, 100}};
        for (var c : cases) {
            assertThrows(IllegalArgumentException.class, () -> source.set(c[0], c[1], c[2]));
            assertSame(before, source.sample());
            assertThrows(IllegalArgumentException.class, () -> new BoundedMetricSource(c[0], c[1], c[2]));
        }
        assertThrows(IllegalArgumentException.class, () -> new BoundedMetricSource.State(0, 0, 1, -1));
    }

    @Test void fullFiniteRangeAndSubnormalRatiosRemainFiniteAndBounded() {
        for (double value : new double[]{-Double.MAX_VALUE, 0, Double.MAX_VALUE}) {
            var source = new BoundedMetricSource(value, -Double.MAX_VALUE, Double.MAX_VALUE);
            assertTrue(Double.isFinite(source.state().ratio()));
            assertTrue(source.state().ratio() >= 0 && source.state().ratio() <= 1);
            assertTrue(source.sample().value().length() <= 128);
            assertEquals(source.state(), BoundedMetricSource.decode(source.sample()).orElseThrow());
        }
        assertEquals(0.5, new BoundedMetricSource(0, -Double.MAX_VALUE, Double.MAX_VALUE).state().ratio());
        assertEquals(1, new BoundedMetricSource(Double.MIN_VALUE, 0, Double.MIN_VALUE).state().ratio());
        assertEquals(0, new BoundedMetricSource(0, 0, Double.MIN_VALUE).state().ratio());
    }

    @Test void decoderRejectsNonCanonicalMalformedOrUntrustedData() {
        assertTrue(BoundedMetricSource.decode(null).isEmpty());
        for (String invalid : new String[]{"", "40", "metric-v2|40.0|0.0|100.0",
                "metric-v1|40|0.0|100.0", "metric-v1| 40.0|0.0|100.0",
                "metric-v1|0x1.0p0|0.0|100.0", "metric-v1|NaN|0.0|100.0",
                "metric-v1|Infinity|0.0|100.0", "metric-v1|101.0|0.0|100.0",
                "metric-v1|0.0|0.0|0.0", "metric-v1|40.0|0.0|100.0|extra",
                "metric-v1|40.0|0.0|100.0\n", "<rich:item=minecraft:diamond>",
                "https://example.com/data", "x".repeat(129)}) {
            assertTrue(BoundedMetricSource.decode(new RichTextDataSource.Snapshot(0, invalid)).isEmpty(), invalid);
        }
    }
}
