// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import java.util.function.Supplier;

final class PillNavPreview implements PreviewScene {
    private final PillNavDemo demo = new PillNavDemo();
    @Override public void layout(PreviewContext host) {
        demo.mount(host.font(), host.viewport(), host.preferences(), host::add);
    }
    @Override public void suspend() { demo.suspend(); }
    @Override public Supplier<? extends PreviewScene> saveForRemount() { return () -> this; }
}
