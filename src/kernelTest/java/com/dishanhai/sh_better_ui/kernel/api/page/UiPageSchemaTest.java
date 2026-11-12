// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class UiPageSchemaTest {
    private static final UiId PAGE = UiId.parse("example:exchange");
    private static final UiId EXCHANGE = UiId.parse("example:redeem");
    private static final UiId CUSTOM = UiId.parse("example:custom");

    @Test
    void roundTripsFixedTreeAndUsesCanonicalOrderForMapsAndEnumChoices() {
        UiPageDefinition page = exchangePage();
        byte[] bytes = UiPageCodec.encode(page);
        UiPageDefinition decoded = UiPageCodec.decode(bytes);
        assertEquals(page, decoded);
        assertArrayEquals(bytes, UiPageCodec.encode(decoded));
        assertTrue(new UiPageValidator().validate(decoded, true).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> decoded.root().children().clear());
    }

    @Test
    void fullDataAndPatchesValidateBeforeReplacingAnyValues() {
        UiDataSchema schema = new UiDataSchema(Map.of(
                "player.balance", UiValueSchema.integer(0, 100),
                "shop.items", UiValueSchema.list(UiValueSchema.itemReference(), 2)));
        UiValue.Sequence items = new UiValue.Sequence(List.of(new UiValue.ItemReference(UiId.parse("minecraft:stone"), 1)));
        schema.validate(Map.of("player.balance", new UiValue.Int(50), "shop.items", items), true);
        schema.validate(Map.of("shop.items", new UiValue.Sequence(List.of())), false);
        assertThrows(IllegalArgumentException.class, () -> schema.validate(Map.of("shop.items", items), true));
        assertThrows(IllegalArgumentException.class,
                () -> schema.validate(Map.of("player.balance", new UiValue.Int(-1)), false));
        assertThrows(IllegalArgumentException.class,
                () -> schema.validate(Map.of("player.balance", new UiValue.LongInt(1)), false));
        assertThrows(IllegalArgumentException.class,
                () -> schema.validate(Map.of("shop.items.0", new UiValue.Text("forged")), false));
        assertThrows(IllegalArgumentException.class, () -> new UiValue.Decimal(Double.NaN));
    }

    @Test
    void rejectsDuplicateIdsUndeclaredActionsAndUnsafeRemoteProperties() {
        UiPageDefinition duplicate = UiPageDefinition.builder(PAGE).root("root", "column", column -> column
                .child("caption", "text", text -> text.text("One"))
                .child("caption", "text", text -> text.text("Two"))).build();
        assertEquals(UiPageProblem.Code.DUPLICATE_ID, new UiPageValidator().validate(duplicate, false).get(0).code());

        UiPageDefinition missingAction = UiPageDefinition.builder(PAGE)
                .root("root", "button", button -> button.label("Redeem").action(EXCHANGE)).build();
        assertEquals(UiPageProblem.Code.INVALID_ACTION, new UiPageValidator().validate(missingAction, false).get(0).code());

        UiPageDefinition textField = UiPageDefinition.builder(PAGE).root("root", "text_field",
                node -> node.property("value", new UiValue.Text("untrusted initial text"))).build();
        assertTrue(new UiPageValidator().validate(textField, false).isEmpty());
        assertEquals(UiPageProblem.Code.INVALID_PROPERTY, new UiPageValidator().validate(textField, true).get(0).code());

        UiPageDefinition image = UiPageDefinition.builder(PAGE).root("root", "image",
                node -> node.property("image", new UiValue.Text("https://example.test/asset.png"))).build();
        assertEquals(UiPageProblem.Code.INVALID_PROPERTY, new UiPageValidator().validate(image, true).get(0).code());
    }

    @Test
    void stackAcceptsPassiveLayersAndRejectsInteractiveDescendants() {
        var passive = UiPageDefinition.builder(PAGE).root("root", "stack", stack -> stack
                .child("lower", "tooltip", tooltip -> tooltip.text("Lower"))
                .child("upper", "tooltip", tooltip -> tooltip.text("Upper"))).build();
        var interactive = UiPageDefinition.builder(PAGE).root("root", "stack", stack -> stack
                .child("nested", "column", column -> column.child("input", "search_field", ignored -> {}))).build();
        var validator = new UiPageValidator();
        for (boolean remote : new boolean[]{false, true}) {
            assertTrue(validator.validate(passive, remote).isEmpty());
            assertEquals(UiPageProblem.Code.INVALID_CHILDREN, validator.validate(interactive, remote).get(0).code());
        }
    }

    @Test
    void bindingsMustBeSafeForEveryValueInTheFieldRange() {
        UiPageDefinition page = UiPageDefinition.builder(PAGE)
                .field("progress", UiValueSchema.decimal(-1, 1))
                .root("root", "progress_bar", progress -> progress.bind("value", "progress")).build();
        assertEquals(UiPageProblem.Code.INVALID_BINDING, new UiPageValidator().validate(page, true).get(0).code());

        UiPageDefinition balance = UiPageDefinition.builder(PAGE).field("balance", UiValueSchema.longInt(0, Long.MAX_VALUE))
                .root("root", "text", text -> text.bind("text", "balance")).build();
        assertTrue(new UiPageValidator().validate(balance, true).isEmpty());
    }

    @Test
    void remoteControlPayloadRequiresAnExplicitTypedFieldAndNoLocalPrefill() {
        UiActionDefinition choose = new UiActionDefinition(EXCHANGE, UiActionPolicy.SERVER,
                new UiDataSchema(Map.of("chosen", UiValueSchema.itemReference())));
        UiPageDefinition page = UiPageDefinition.builder(PAGE).action(choose)
                .root("filter", "ghost_slot", slot -> slot.action(EXCHANGE)
                        .property("payloadField", new UiValue.Text("chosen"))
                        .property("choices", new UiValue.Sequence(List.of(
                                new UiValue.ItemReference(UiId.parse("minecraft:stone"), 1))))).build();
        assertTrue(new UiPageValidator().validate(UiPageCodec.decode(UiPageCodec.encode(page)), true).isEmpty());
        UiPageDefinition missing = UiPageDefinition.builder(PAGE).action(choose)
                .root("filter", "ghost_slot", slot -> slot.action(EXCHANGE)).build();
        assertEquals(UiPageProblem.Code.INVALID_ACTION, new UiPageValidator().validate(missing, true).get(0).code());
        UiPageDefinition wrongType = UiPageDefinition.builder(PAGE).action(choose)
                .root("toggle", "toggle", toggle -> toggle.action(EXCHANGE).label("Enable")
                        .property("payloadField", new UiValue.Text("chosen"))).build();
        assertEquals(UiPageProblem.Code.INVALID_ACTION, new UiPageValidator().validate(wrongType, true).get(0).code());
    }

    @Test
    void iconsCanBindOnlySafeDeclaredAssetChoicesRemotely() {
        UiPageDefinition icon = UiPageDefinition.builder(PAGE)
                .field("demo.icon", UiValueSchema.choice(Set.of("success", "warning")))
                .root("status", "icon", node -> node.bind("icon", "demo.icon")).build();
        assertTrue(new UiPageValidator().validate(icon, true).isEmpty());
        UiPageDefinition arbitrary = UiPageDefinition.builder(PAGE)
                .field("demo.icon", UiValueSchema.text(128))
                .root("status", "icon", node -> node.bind("icon", "demo.icon")).build();
        assertEquals(UiPageProblem.Code.INVALID_BINDING, new UiPageValidator().validate(arbitrary, true).get(0).code());
    }

    @Test
    void resolvedFallbackKeepsStableIdsAndCannotHideBusinessActions() {
        UiNode missing = UiNode.builder("card", CUSTOM).alternative(new UiComponentAlternative(
                UiBuiltinComponents.id("text"), Map.of("text", new UiValue.Text("Fallback")), Map.of())).build();
        UiNode hidden = UiNode.builder("decoration", CUSTOM).fallback(UiNodeFallback.HIDE).build();
        UiPageDefinition page = UiPageDefinition.builder(PAGE).root("root", "column", root -> root.child(missing).child(hidden))
                .build();
        Map<UiId, UiVersion> support = Map.of(UiBuiltinComponents.id("column"), UiBuiltinComponents.VERSION,
                UiBuiltinComponents.id("text"), UiBuiltinComponents.VERSION);
        UiResolvedPage resolved = new UiPageValidator().resolve(UiPageCodec.decode(UiPageCodec.encode(page)), true, support);
        assertEquals(2, resolved.degradations().size());
        List<UiNode> children = resolved.page().orElseThrow().root().children();
        assertEquals(1, children.size());
        assertEquals("card", children.get(0).id());
        assertEquals(UiBuiltinComponents.id("text"), children.get(0).typeId());

        UiNode actionRegion = UiNode.builder("region", CUSTOM).fallback(UiNodeFallback.HIDE)
                .child("action", "button", button -> button.label("Redeem").action(EXCHANGE)).build();
        UiPageDefinition required = UiPageDefinition.builder(PAGE).root("root", "column", root -> root.child(actionRegion))
                .action(new UiActionDefinition(EXCHANGE, UiActionPolicy.SERVER, UiDataSchema.EMPTY)).build();
        assertEquals(UiPageProblem.Code.COMPONENT_MISSING,
                new UiPageValidator().resolve(required, true, support).problems().get(0).code());
    }

    @Test
    void budgetRejectsDeepAndLargeTreesBeforeResolvingThem() {
        UiNode node = UiNode.builtin("leaf", "spacer").build();
        for (int i = 0; i < 40; i++) {
            node = UiNode.builtin("nested" + i, "column").child(node).build();
        }
        UiPageDefinition deep = UiPageDefinition.builder(PAGE).root(node).build();
        assertEquals(UiPageProblem.Code.RESOURCE_LIMIT, new UiPageValidator().validate(deep, false).get(0).code());
        assertThrows(IllegalArgumentException.class, () -> UiPageCodec.encode(deep));

        UiNode.Builder many = UiNode.builtin("root", "column");
        for (int i = 0; i < 64; i++) {
            many.child("text" + i, "text", text -> text.text("x".repeat(4096)));
        }
        assertThrows(IllegalArgumentException.class, () -> UiPageCodec.encode(UiPageDefinition.builder(PAGE).root(many.build()).build()));
    }

    @Test
    void rejectsBrokenEncodingIncompatibleVersionsAndMalformedValues() throws IOException {
        byte[] bytes = UiPageCodec.encode(exchangePage());
        assertThrows(IllegalArgumentException.class, () -> UiPageCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> UiPageCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        bytes[7] = 2;
        assertThrows(IllegalArgumentException.class, () -> UiPageCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class,
                () -> UiPageCodec.decode(new byte[UiPageCodec.MAX_ENCODED_BYTES + 1]));

        Map<String, UiValue> values = Map.of("list", new UiValue.Sequence(List.of(
                new UiValue.Bool(true), new UiValue.ObjectValue(Map.of("name", new UiValue.Text("魔法🟪"))))));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        UiPageCodec.writeValues(new DataOutputStream(output), values);
        assertEquals(values, UiPageCodec.readValues(new DataInputStream(new ByteArrayInputStream(output.toByteArray()))));
        byte[] invalid = output.toByteArray();
        invalid[invalid.length - 1] = (byte) 0xff;
        assertThrows(IOException.class, () -> UiPageCodec.readValues(new DataInputStream(new ByteArrayInputStream(invalid))));
    }

    @Test
    void largeValuesAreRejectedEvenForMissingComponentsWithHideFallback() {
        UiValue value = new UiValue.Text("leaf");
        for (int i = 0; i < 20; i++) {
            value = new UiValue.Sequence(List.of(value));
        }
        UiNode decoration = UiNode.builder("decoration", CUSTOM).property("deep", value)
                .fallback(UiNodeFallback.HIDE).build();
        UiPageDefinition page = UiPageDefinition.builder(PAGE).root("root", "column", root -> root.child(decoration)).build();
        assertEquals(UiPageProblem.Code.RESOURCE_LIMIT, new UiPageValidator().resolve(page, true,
                Map.of(UiBuiltinComponents.id("column"), UiBuiltinComponents.VERSION)).problems().get(0).code());
        assertThrows(IllegalArgumentException.class, () -> UiPageCodec.encode(page));
    }

    private static UiPageDefinition exchangePage() {
        List<UiNode> children = new ArrayList<>();
        children.add(UiNode.builtin("balance", "text").bind("text", "player.balance").build());
        children.add(UiNode.builtin("redeem", "button").label("兑换").action(EXCHANGE).build());
        return UiPageDefinition.builder(PAGE)
                .title("兑换中心")
                .field("player.balance", UiValueSchema.integer(0, 1000))
                .action(new UiActionDefinition(EXCHANGE, UiActionPolicy.CONFIRMED,
                        new UiDataSchema(Map.of("selection", UiValueSchema.choice(Set.of("emerald", "diamond"))))))
                .root(UiNode.builtin("root", "column").child(children.get(0)).child(children.get(1)).build())
                .build();
    }
}
