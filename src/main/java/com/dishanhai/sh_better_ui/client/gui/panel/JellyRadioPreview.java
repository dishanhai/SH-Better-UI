// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiJellyRadio;

import java.util.function.Supplier;

final class JellyRadioPreview implements PreviewScene {
    private String selected = JellyRadioDemo.DEFAULT_VALUE;
    private UiJellyRadio control;

    @Override
    public void layout(PreviewContext host) {
        suspend();
        control = JellyRadioDemo.layout(host.viewport(), host.preferences(), selected, (value, index) -> {
            if (host.interactive()) selected = value;
            else control.select(selected);
        });
        if (control != null) host.add(control);
    }

    @Override
    public void suspend() {
        if (control != null) control.cancelPress();
        control = null;
    }

    @Override
    public void close() {
        suspend();
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        String value = selected;
        return () -> {
            var preview = new JellyRadioPreview();
            preview.selected = value;
            return preview;
        };
    }
}
