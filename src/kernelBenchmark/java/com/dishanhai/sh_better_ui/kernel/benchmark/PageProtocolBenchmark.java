// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCodec;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolFraming;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.UUID;

public final class PageProtocolBenchmark {
    private static final int WARMUP = 2000;
    private static final int SAMPLES = 2000;
    private static final UUID SESSION = new UUID(1, 2);
    private static final UUID INSTANCE = new UUID(3, 4);
    private static volatile UiProtocolMessage sink;

    private PageProtocolBenchmark() {}

    public static void main(String[] args) throws IOException {
        System.out.println(BenchmarkSamples.MEDIAN_SUMMARY_HEADER.stripTrailing());
        for (int nodes : new int[]{12, 192}) {
            var page = UiPageDefinition.builder(UiId.parse("bench:catalog")).root("root", "column", root -> {
                for (int i = 0; i < nodes; i += 12) {
                    int start = i;
                    root.child("group_" + start, "column", group -> {
                        for (int j = start; j < start + 12; j++) {
                            int index = j;
                            group.child("row_" + index, "text", row -> row.text("漢字".repeat(48) + index));
                        }
                    });
                }
            }).build();
            measure("open_" + nodes, new UiProtocolMessage.PageOpen(INSTANCE, page, Map.of()));
        }
        measure("one_field_update", new UiProtocolMessage.DataUpdate(INSTANCE, 1, 2,
                Map.of("balance", new UiValue.Int(7))));
    }

    private static void measure(String name, UiProtocolMessage.Payload payload) throws IOException {
        var message = new UiProtocolMessage(UiProtocol.VERSION, SESSION, 1, payload);
        for (int i = 0; i < WARMUP; i++) roundTrip(message);
        var samples = new BenchmarkSamples(SAMPLES);
        for (int i = 0; i < SAMPLES; i++) {
            samples.begin();
            roundTrip(message);
            samples.end();
        }
        if (!message.equals(sink)) throw new IllegalStateException("Protocol benchmark lost data");
        var out = new StringWriter();
        samples.writeSummaryWithMedian(out, name);
        System.out.print(out);
        var encoded = UiProtocolCodec.encode(message);
        var frames = UiProtocolFraming.split(encoded, SESSION);
        System.out.printf("%s message_bytes=%d frames=%d wire_bytes=%d%n", name, encoded.length,
                frames.size(), frames.stream().mapToInt(frame -> frame.length).sum());
    }

    private static void roundTrip(UiProtocolMessage message) {
        var encoded = UiProtocolCodec.encode(message);
        var receiver = new UiProtocolFraming.Reassembler();
        for (var frame : UiProtocolFraming.split(encoded, SESSION)) {
            receiver.accept(frame, 0).ifPresent(bytes -> sink = UiProtocolCodec.decode(bytes));
        }
    }
}
