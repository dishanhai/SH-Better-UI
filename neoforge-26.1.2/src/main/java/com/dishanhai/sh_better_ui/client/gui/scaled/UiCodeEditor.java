// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.controls.CodeEditorValue;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Multi-line source editor backed by the native text and scrolling implementation. */
public final class UiCodeEditor extends UiCodeEditorPlatform {
    private final CodeEditorValue value;
    private Consumer<String> onChange = ignored -> {};
    private boolean synchronizing;

    public UiCodeEditor(Font font, Bounds bounds, Component label, int maxLength, UiPreferences preferences) {
        super(font, bounds, label, preferences);
        value = new CodeEditorValue(maxLength);
        editor.setValueListener(this::changed);
    }

    public String getValue() { return value.get(); }

    /** Sets the full text without invoking onChange; overflow is rejected without mutation. */
    public void setValue(String text) {
        if (!value.replace(text)) throw new IllegalArgumentException("Code editor text exceeds maximum length");
        synchronizing = true;
        try { editor.setValue(text); } finally { synchronizing = false; }
    }

    public void setOnChange(Consumer<String> listener) { onChange = Objects.requireNonNull(listener); }

    private void changed(String text) {
        if (synchronizing) return;
        if (!value.replace(text)) {
            synchronizing = true;
            try { editor.setValue(value.get()); } finally { synchronizing = false; }
            return;
        }
        onChange.accept(text);
    }

    @Override boolean pasteExact(String text) {
        var selection = UiCodeEditorSelection.read(editor);
        if (selection == null || !value.replaceRange(selection.start(), selection.end(), text)) return true;
        synchronizing = true;
        try {
            editor.setValue(value.get());
            selection.field().setSelecting(false);
            selection.field().seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, selection.start() + text.length());
        } finally {
            synchronizing = false;
        }
        onChange.accept(value.get());
        return true;
    }
}
