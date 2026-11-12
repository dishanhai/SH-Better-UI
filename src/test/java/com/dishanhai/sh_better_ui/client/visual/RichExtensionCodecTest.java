// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionInput;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichExtensionCodecTest {
    private static final UiId PREFIX = UiId.parse("test:rich");
    private static final UiId TYPE = UiId.parse("test:energy");
    private static final RichExtensionInput SUPPORTED = new RichExtensionInput(Map.of(TYPE,
            new RichExtensionInput.Format(1, payload -> payload.startsWith("v="))));
    private static final RichExtensionInput EMPTY = new RichExtensionInput(Map.of());

    @Test void jsonAndBothDfuOpsRoundTripNamespacedExtensions() {
        var codec = RichDocumentCodec.codec(SUPPORTED);
        var random = new Random(209);
        for (int i = 0; i < 64; i++) {
            String payload = "v=充能 🌊\"\n\t" + random.nextLong();
            var document = RichText.document(PREFIX).text("left ")
                    .extension(TYPE, 1, 40, 10, 10, "Energy", payload).text(" right").build();
            var json = RichDocumentCodec.encode(document, SUPPORTED);
            assertEquals(document, RichDocumentCodec.decode(json, SUPPORTED));
            var jsonTree = codec.encodeStart(JsonOps.INSTANCE, document).result().orElseThrow();
            assertEquals(document, codec.parse(JsonOps.INSTANCE, jsonTree).result().orElseThrow());
            assertTrue(RichDocumentCodec.CODEC.parse(JsonOps.INSTANCE, jsonTree).result().isEmpty());
            var nbtTree = codec.encodeStart(NbtOps.INSTANCE, document).result().orElseThrow();
            assertEquals(document, codec.parse(NbtOps.INSTANCE, nbtTree).result().orElseThrow());
            assertEquals("left Energy right", RichTextComponents.toComponent(document).getString());
        }
    }

    @Test void unknownTypesAndVersionsAreInertAndDefaultInputNeverOptsIn() {
        var document = RichText.document(PREFIX).extension(TYPE, 1, 32, 8, 8, "Energy", "v=20").build();
        var json = RichDocumentCodec.encode(document, SUPPORTED);
        assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.decode(json));
        assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.encode(document));
        assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.encode(document, EMPTY));
        var missing = RichDocumentCodec.decode(json, EMPTY);
        assertFalse(((RichExtensionNode) missing.nodes().get(0)).renderable());
        assertEquals(json, RichDocumentCodec.encode(missing, EMPTY));
        var upgraded = RichDocumentCodec.decode(json.replace("\"extension\":\"test:energy\",\"version\":1",
                "\"extension\":\"test:energy\",\"version\":2"), SUPPORTED);
        assertFalse(((RichExtensionNode) upgraded.nodes().get(0)).renderable());
    }

    @Test void adversarialEnvelopesCannotInvokeProviderBeforeSchemaChecks() {
        var calls = new AtomicInteger();
        var restricted = new RichExtensionInput(Map.of(TYPE,
                new RichExtensionInput.Format(1, payload -> calls.incrementAndGet() > 0 && payload.equals("v=20"))));
        var json = RichDocumentCodec.encode(RichText.document(PREFIX)
                .extension(TYPE, 1, 32, 8, 8, "Energy", "v=20").build(), SUPPORTED);
        for (var bad : List.of(
                json.replace("\"payload\":\"v=20\"", "\"payload\":{},\"command\":\"/op @s\""),
                json.replace("\"payload\":\"v=20\"", "\"payload\":\"" + "x".repeat(4097) + "\""),
                json.replace("\"width\":32", "\"width\":32,\"width\":32"),
                json.replace("\"logicalHeight\":8", "\"logicalHeight\":257"),
                json.replace("\"payload\":\"v=20\"", "\"payload\":\"\\ud800\""),
                json.replace("\"extension\":\"test:energy\"", "\"extension\":\"test:../../energy\""))) {
            assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.decode(bad, restricted));
        }
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.decode(json.replace("v=20", "/op @s"),
                restricted));
        assertEquals(1, calls.get());
    }
}
