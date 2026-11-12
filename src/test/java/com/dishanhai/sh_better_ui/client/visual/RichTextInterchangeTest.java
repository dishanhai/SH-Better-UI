// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocumentCodec;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextInterchangeTest {
    private static final UiId PREFIX = UiId.parse("test:rich");

    @Test
    void jsonAndDfuRoundTripDeterministicUnicodeDocuments() {
        var random = new Random(764);
        for (int sample = 0; sample < 64; sample++) {
            var text = new StringBuilder("漢🌊e\u0301\n\t\\\"");
            for (int i = 0; i < 20; i++) text.appendCodePoint(0x1f600 + random.nextInt(70));
            var style = new RichStyle(random.nextInt(0x1000000), random.nextBoolean(), false,
                    true, false, false, UiId.parse("minecraft:uniform"));
            var document = RichText.document(PREFIX)
                    .text(text.toString(), style)
                    .image(UiId.parse("test:textures/gui/image.png"), 30, 13, "海")
                    .image(UiId.parse(sample % 2 == 0 ? "test:textures/gui/motion.gif" : "test:textures/gui/motion.apng"),
                            16, 16, "動畫")
                    .paragraph(4)
                    .localized("test.rich.missing", "fallback %s", List.of("海"), RichStyle.EMPTY)
                    .build();
            var json = RichDocumentCodec.encode(document);
            assertEquals(document, RichDocumentCodec.decode(json));
            assertEquals(json, RichDocumentCodec.encode(RichDocumentCodec.decode(json)));
            var encoded = RichDocumentCodec.CODEC.encodeStart(JsonOps.INSTANCE, document).result().orElseThrow();
            assertEquals(document, RichDocumentCodec.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
            var nbt = RichDocumentCodec.CODEC.encodeStart(NbtOps.INSTANCE, document).result().orElseThrow();
            assertEquals(document, RichDocumentCodec.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow());
        }
    }

    @Test
    void codecRejectsUnknownFieldsDuplicatesUnsupportedKindsAndOversizeInput() {
        for (String bad : List.of(
                "{\"version\":1,\"version\":1,\"nodes\":[]}",
                "{\"version\":1,\"nodes\":[],\"onload\":\"test:action\"}",
                "{\"version\":2,\"nodes\":[]}",
                "{\"version\":1,\"nodes\":[{\"type\":\"button\",\"id\":\"test:button\"}]}",
                "{\"version\":1,\"nodes\":[{\"type\":\"text\",\"id\":\"test:dup\",\"text\":\"x\"},"
                        + "{\"type\":\"text\",\"id\":\"test:dup\",\"text\":\"y\"}]}",
                "{\"version\":1,\"nodes\":[{\"type\":\"text\",\"id\":\"test:x\",\"text\":\"\\ud800\"}]}",
                " ".repeat(262145), "[]", "{} {}")) {
            assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.decode(bad));
        }
    }

    @Test
    void componentSnapshotsPreserveInheritedNativeStyleAndKeepActionsOutOfData() {
        var click = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/help");
        var source = Component.translatableWithFallback("test.rich.missing", "Hello %s",
                Component.literal("海").withStyle(Style.EMPTY.withBold(false)))
                .withStyle(Style.EMPTY.withBold(true).withClickEvent(click).withInsertion("native"));
        var snapshot = RichTextComponents.resolve(PREFIX, source);
        var lead = (TextRun) snapshot.document().nodes().get(0);
        var tail = (TextRun) snapshot.document().nodes().get(1);
        assertEquals("Hello ", lead.text());
        assertTrue(lead.style().bold());
        assertFalse(tail.style().bold());
        assertEquals(click, snapshot.nativeStyles().get(tail.id()).getClickEvent());
        assertEquals("native", snapshot.nativeStyles().get(lead.id()).getInsertion());
        var projected = RichTextComponents.toComponent(snapshot.document());
        assertEquals(source.getString(), projected.getString());
        assertNull(projected.getSiblings().get(0).getStyle().getClickEvent());
        var restored = RichTextComponents.toComponent(snapshot);
        assertEquals(click, restored.getSiblings().get(1).getStyle().getClickEvent());
        source.append("late");
        assertEquals("Hello 海", projected.getString());
    }
}
