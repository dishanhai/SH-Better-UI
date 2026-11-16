// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;

import java.lang.reflect.Field;

/** Resolves the native selection without depending on remapped private field names. */
final class UiCodeEditorSelection {
    record Selection(MultilineTextField field, int start, int end) {}
    private static final Field TEXT_FIELD = findField();
    private UiCodeEditorSelection() {}

    private static Field findField() {
        try {
            for (Field field : MultiLineEditBox.class.getDeclaredFields()) {
                if (field.getType() != MultilineTextField.class) continue;
                return field.trySetAccessible() ? field : null;
            }
        } catch (RuntimeException denied) {
            return null;
        }
        return null;
    }

    static Selection read(MultiLineEditBox editor) {
        if (TEXT_FIELD == null) return null;
        try {
            var field = (MultilineTextField) TEXT_FIELD.get(editor);
            Object view = field.getSelected();
            var components = view.getClass().getRecordComponents();
            if (components == null || components.length != 2) return null;
            var begin = components[0].getAccessor();
            var end = components[1].getAccessor();
            if (!begin.trySetAccessible() || !end.trySetAccessible()) return null;
            return new Selection(field, (int) begin.invoke(view), (int) end.invoke(view));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }
}
