// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class NativePageScreenInputTest {
    @Test
    void advertisesOnlyImplementedAdditionsAndResolvesTheirSchema() {
        for (String name : new String[]{"row", "grid", "toggle", "slider"}) {
            assertTrue(NativePageScreen.COMPONENTS.containsKey(UiBuiltinComponents.id(name)));
        }
        assertFalse(NativePageScreen.COMPONENTS.containsKey(UiBuiltinComponents.id("modal")));
        var definition = UiPageDefinition.builder(new UiId("example", "form"))
                .root("form", "grid", root -> root
                        .child("enabled", "toggle", input -> input.label("Enabled"))
                        .child("volume", "slider", input -> input.label("Volume")))
                .build();
        var result = new UiPageValidator().resolve(definition, false, NativePageScreen.COMPONENTS);
        assertTrue(result.page().isPresent(), result.problems().toString());
        assertDoesNotThrow(() -> ClientPages.validateActions(result.page().orElseThrow().root(), definition));
    }

    @Test
    void unrelatedDataUpdatesRetainDraftButChangedAuthoritySupersedesIt() {
        var node = UiNode.builtin("volume", "slider").label("Volume").bind("value", "volume").build();
        var inputs = new NativePageScreen.Inputs();
        var initial = new UiValue.Decimal(.2);
        var draft = new UiValue.Decimal(.7);
        assertEquals(initial, inputs.value(node, "value", Map.of("volume", initial)));
        inputs.edit(node, draft);
        assertEquals(draft, inputs.value(node, "value", Map.of("volume", initial, "other", new UiValue.Bool(true))));
        var changed = new UiValue.Decimal(.4);
        assertEquals(changed, inputs.value(node, "value", Map.of("volume", changed)));
        assertFalse(inputs.snapshot().containsKey("volume"));
    }

    @Test
    void hiddenInputAuthorityChangesClearDraftEvenIfItReturnsBeforeShowing() {
        var node = UiNode.builtin("volume", "slider").label("Volume")
                .bind("value", "volume").bind("visible", "show").build();
        var root = UiNode.builtin("root", "column").child(node).build();
        var inputs = new NativePageScreen.Inputs();
        inputs.synchronize(root, Map.of("volume", new UiValue.Decimal(.2), "show", new UiValue.Bool(true)));
        inputs.edit(node, new UiValue.Decimal(.7));
        inputs.synchronize(root, Map.of("volume", new UiValue.Decimal(.4), "show", new UiValue.Bool(false)));
        assertFalse(inputs.snapshot().containsKey("volume"));
        inputs.synchronize(root, Map.of("volume", new UiValue.Decimal(.2), "show", new UiValue.Bool(false)));
        assertFalse(inputs.snapshot().containsKey("volume"));
        assertEquals(new UiValue.Decimal(.2), inputs.value(node, "value", Map.of("volume", new UiValue.Decimal(.2))));
    }

    @Test
    void unboundInputsAreLocalToTheInstanceAndSnapshotsAreImmutable() {
        var node = UiNode.builtin("enabled", "toggle").label("Enabled")
                .property("checked", new UiValue.Bool(false)).build();
        var first = new NativePageScreen.Inputs();
        var second = new NativePageScreen.Inputs();
        first.edit(node, new UiValue.Bool(true));
        var saved = first.snapshot();
        first.edit(node, new UiValue.Bool(false));
        assertEquals(new UiValue.Bool(true), saved.get("enabled"));
        assertEquals(new UiValue.Bool(false), second.value(node, "checked", Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> saved.clear());
    }

    @Test
    void actualSliderCallbacksCancelWithoutRoundingModelOrCreatingDraft() {
        var node = UiNode.builtin("volume", "slider").label("Volume").bind("value", "volume").build();
        var inputs = new NativePageScreen.Inputs();
        var original = new UiValue.Decimal(.235);
        var data = Map.<String, UiValue>of("volume", original);
        var edit = new NativePageScreen.SliderInput(inputs, node, data);
        var slider = new com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider(
                new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(10, 20, 208, 22),
                0, 100, 1, 24, com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT,
                value -> net.minecraft.network.chat.Component.literal(Integer.toString(value)), edit::preview);
        slider.setOnCommit(edit::commit, ignored -> edit.cancel());
        var mouse = new net.minecraft.client.input.MouseButtonEvent(114, 25,
                new net.minecraft.client.input.MouseButtonInfo(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        slider.onClick(mouse, false);
        assertEquals(new UiValue.Decimal(.5), inputs.snapshot().get("volume"));
        slider.setFocused(false);
        assertFalse(inputs.snapshot().containsKey("volume"));
        assertEquals(original, inputs.value(node, "value", data));
    }

    @Test
    void cancelRestoresExactPriorDraftAndReturnToOriginalTickIsNotAnEdit() {
        var node = UiNode.builtin("volume", "slider").label("Volume").build();
        var inputs = new NativePageScreen.Inputs();
        var original = new UiValue.Decimal(.235);
        inputs.edit(node, original);
        var edit = new NativePageScreen.SliderInput(inputs, node, Map.of());
        edit.preview(80);
        edit.cancel();
        assertEquals(original, inputs.snapshot().get("volume"));
        edit.preview(60);
        edit.preview(24);
        assertEquals(original, inputs.snapshot().get("volume"));
        edit.commit(24);
        assertEquals(original, inputs.snapshot().get("volume"));
        edit.preview(60);
        edit.commit(60);
        assertEquals(new UiValue.Decimal(.6), inputs.snapshot().get("volume"));
    }

    @Test
    void rowsAndGridsFitExactlyEvenWhenNarrowerThanRequestedSpacing() {
        for (int width = 1; width <= 384; width++) {
            for (int count = 1; count <= Math.min(width, 32); count++) {
                int gap = NativePageScreen.boundedGap(width, count, 256);
                int used = (count - 1) * gap;
                for (int cell = 0; cell < count; cell++) {
                    int cellWidth = NativePageScreen.cellWidth(width, count, gap, cell);
                    assertTrue(cellWidth > 0);
                    used += cellWidth;
                }
                assertEquals(width, used);
            }
        }
        assertEquals(4, NativePageScreen.cellWidth(10, 3, 0, 0));
        assertEquals(3, NativePageScreen.cellWidth(10, 3, 0, 2));
    }

    @Test
    void unsupportedPayloadIsRejectedRatherThanSilentlyDropped() {
        var definition = UiPageDefinition.builder(new UiId("example", "payload"))
                .root("enabled", "toggle", node -> node.label("Enabled")
                        .property("payloadField", new UiValue.Text("enabled"))).build();
        assertThrows(IllegalArgumentException.class, () -> ClientPages.validateActions(definition.root(), definition));
    }
}
