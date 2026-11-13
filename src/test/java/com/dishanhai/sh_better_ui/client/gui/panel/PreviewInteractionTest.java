// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiApiContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreviewInteractionTest {
    @Test
    void transitionDuringLayoutDoesNotPermanentlyDisablePreviewButtons() {
        var registry = new PreviewRegistry();
        BuiltinPreviews.registerInto(registry);
        for (String id : List.of("dynamic_theme", "screen_crossfade")) {
            var host = new TransitionHost();
            try (var scene = registry.find(id).orElseThrow().factory().get()) {
                scene.layout(host);
                assertEquals(1, host.widgets.size(), id);
                assertTrue(host.widgets.getFirst().active, id);
            }
        }
    }

    private static final class TransitionHost implements PreviewContext {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        @Override public Screen screen() { return null; }
        @Override public Bounds viewport() { return new Bounds(0, 0, 200, 140); }
        @Override public Bounds toolbar() { return new Bounds(0, 0, 200, 20); }
        @Override public Font font() { return null; }
        @Override public UiPreferences preferences() { return UiPreferences.DEFAULT; }
        @Override public UiOverlayHost overlays() { return null; }
        @Override public UiApiContext context() { return null; }
        @Override public boolean interactive() { return false; }
        @Override public <T extends AbstractWidget> T add(T widget) { widgets.add(widget); return widget; }
        @Override public <T extends AbstractWidget> T addOverlay(T widget) { return add(widget); }
    }
}
