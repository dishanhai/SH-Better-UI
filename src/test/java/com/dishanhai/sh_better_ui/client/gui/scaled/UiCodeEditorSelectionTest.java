// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class UiCodeEditorSelectionTest {
    @Test void nativeTextFieldAndSelectionRecordAreAccessibleWithoutPrivateNames() throws Exception {
        var matching = Arrays.stream(MultiLineEditBox.class.getDeclaredFields())
                .filter(field -> field.getType() == MultilineTextField.class).toList();
        assertEquals(1, matching.size());
        assertTrue(matching.get(0).trySetAccessible());
        var components = MultilineTextField.class.getDeclaredMethod("getSelected").getReturnType().getRecordComponents();
        assertNotNull(components);
        assertEquals(2, components.length);
        for (var component : components) {
            assertEquals(int.class, component.getType());
            assertTrue(component.getAccessor().trySetAccessible());
        }
    }
}
