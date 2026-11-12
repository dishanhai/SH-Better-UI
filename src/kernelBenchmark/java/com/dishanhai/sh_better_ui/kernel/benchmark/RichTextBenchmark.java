// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextLayout;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Number;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Patch;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.ServerMessage;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Snapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Text;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Value;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncWire;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.MAX_FIELDS;
import static com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.MAX_ID_BYTES;

/** CPU-only rich layout and viewport traversal. Run with a fixed Java 17 heap. */
public final class RichTextBenchmark {
    private static final int WARMUP = 150;
    private static final int SAMPLES = 400;
    private static final int VIEWPORTS_PER_SAMPLE = 100;
    private static final com.sun.management.ThreadMXBean ALLOCATIONS = allocationBean();
    private static volatile RichLayoutSnapshot layoutSink;
    private static volatile int rowSink;
    private static volatile ServerMessage packetSink;

    private RichTextBenchmark() {}

    public static void main(String[] args) {
        System.out.printf(Locale.ROOT, "vm=%s os=%s arch=%s heap_bytes=%d processors=%d allocation_counter=%s%n",
                System.getProperty("java.vm.name"), System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().maxMemory(), Runtime.getRuntime().availableProcessors(), ALLOCATIONS != null);
        var nodes = new ArrayList<InlineNode>();
        for (int i = 0; i < 3000; i++) {
            nodes.add(new TextRun(new UiId("bench", "run_" + i), "Rich text bench 汉字 abc 👩‍💻 glyph text row " + i + " "));
        }
        var doc = new RichDocument(nodes);
        var font = new RichTextLayout.FontMetrics() {
            @Override public int ascent() { return 7; }
            @Override public int descent() { return 2; }
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 4; }
        };
        measure("layout", 1, () -> layoutSink = RichTextLayout.layout(doc, 290, font));
        var layout = layoutSink;
        int exhaustive = visit(layout, false);
        int indexed = visit(layout, true);
        if (indexed != exhaustive) throw new AssertionError("Viewport workloads disagree");
        System.out.printf(Locale.ROOT, "java=%s nodes=%d rows=%d visited-rows-per-100=%d%n",
                System.getProperty("java.version"), nodes.size(), layout.lines().size(), indexed);
        measure("exhaustive", VIEWPORTS_PER_SAMPLE, () -> rowSink = visit(layout, false));
        measure("indexed", VIEWPORTS_PER_SAMPLE, () -> rowSink = visit(layout, true));
        var texture = UiId.parse("bench:textures/gui/animation.gif");
        var mixedNodes = new ArrayList<InlineNode>(4000);
        for (int i = 0; i < nodes.size(); i += 3) {
            mixedNodes.add(new ImageNode(new UiId("bench", "image_" + i), texture, 48, 24, ""));
            mixedNodes.addAll(nodes.subList(i, i + 3));
        }
        var mixed = new RichDocument(mixedNodes);
        measure("mixed-layout-4000", 1, () -> layoutSink = RichTextLayout.layout(mixed, 290, font));
        System.out.printf(Locale.ROOT, "mixed_nodes=%d image_boxes=1000 rows=%d%n", mixed.nodes().size(),
                layoutSink.lines().size());
        benchmarkPackets();
    }

    private static void benchmarkPackets() {
        var session = new UUID(20261111, 7);
        var fields = new LinkedHashMap<UiId, Value>();
        var text = new Text("漢".repeat(170) + "ab");
        for (int i = 0; i < MAX_FIELDS; i++) {
            String prefix = "bench:f" + i;
            fields.put(UiId.parse(prefix + "x".repeat(MAX_ID_BYTES - prefix.length())), text);
        }
        var snapshot = new Snapshot(session, 1, 1, true, fields);
        var snapshotBytes = RichSyncWire.encode(snapshot);
        measure("sync-snapshot-round-trip", 1, () -> roundTrip(snapshot));
        if (!snapshot.equals(packetSink)) throw new AssertionError("Snapshot round trip diverged");

        var key = fields.keySet().iterator().next();
        var stream = new ArrayList<Patch>(128);
        for (int i = 0; i < 128; i++) {
            stream.add(new Patch(session, 1, i + 1L, i + 2L,
                    Map.of(key, new Number(i)), Set.of()));
        }
        int patchBytes = RichSyncWire.encode(stream.get(0)).length;
        measure("sync-patch-stream", stream.size(), () -> stream.forEach(RichTextBenchmark::roundTrip));
        if (!stream.get(stream.size() - 1).equals(packetSink)) throw new AssertionError("Patch round trip diverged");
        System.out.printf(Locale.ROOT, "snapshot_fields=%d snapshot_bytes=%d patches_per_sample=%d patch_bytes=%d%n",
                fields.size(), snapshotBytes.length, stream.size(), patchBytes);
    }

    private static void roundTrip(ServerMessage packet) {
        try {
            packetSink = RichSyncWire.decodeServer(RichSyncWire.encode(packet));
        } catch (IOException invalid) {
            throw new UncheckedIOException(invalid);
        }
    }

    private static int visit(RichLayoutSnapshot layout, boolean indexed) {
        int result = 0;
        for (int i = 0; i < VIEWPORTS_PER_SAMPLE; i++) {
            int top = i * 341 % layout.height();
            int bottom = top + 180;
            if (indexed) {
                result += layout.visibleLines(top, bottom).size();
            } else {
                for (var line : layout.lines()) {
                    if (line.y() < bottom && line.y() + line.height() > top) result++;
                }
            }
        }
        return result;
    }

    private static void measure(String name, int operations, Runnable work) {
        for (int i = 0; i < WARMUP; i++) work.run();
        long[] times = new long[SAMPLES];
        long[] allocated = new long[SAMPLES];
        long thread = Thread.currentThread().getId();
        for (int i = 0; i < times.length; i++) {
            long before = ALLOCATIONS == null ? 0 : ALLOCATIONS.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            work.run();
            times[i] = System.nanoTime() - start;
            if (ALLOCATIONS != null) allocated[i] = ALLOCATIONS.getThreadAllocatedBytes(thread) - before;
        }
        Arrays.sort(times);
        Arrays.sort(allocated);
        double divisor = 1000.0 * operations;
        System.out.printf(Locale.ROOT, "%s warmup=%d samples=%d mean_us=%.3f p95_us=%.3f%n",
                name, WARMUP, SAMPLES, Arrays.stream(times).average().orElseThrow() / divisor,
                times[(int) Math.ceil(times.length * 0.95) - 1] / divisor);
        if (ALLOCATIONS != null) {
            System.out.printf(Locale.ROOT, "%s allocated_mean_bytes=%.3f allocated_p95_bytes=%.3f%n", name,
                    Arrays.stream(allocated).average().orElseThrow() / operations,
                    allocated[(int) Math.ceil(allocated.length * 0.95) - 1] / (double) operations);
        }
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocations)
                || !allocations.isThreadAllocatedMemorySupported()) return null;
        if (!allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        return allocations;
    }
}
