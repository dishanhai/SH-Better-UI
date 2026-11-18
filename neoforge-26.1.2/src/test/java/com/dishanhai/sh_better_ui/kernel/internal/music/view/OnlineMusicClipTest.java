// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OnlineMusicClipTest {
    private com.dishanhai.sh_better_ui.client.api.NativeTestClient clientFixture;
    @org.junit.jupiter.api.BeforeEach void installNativeClient() throws Exception {
        clientFixture = com.dishanhai.sh_better_ui.client.api.NativeTestClient.install();
    }
    @org.junit.jupiter.api.AfterEach void restoreNativeClient() throws Exception {
        if (clientFixture != null) clientFixture.close();
    }

    static final class Widget extends AbstractWidget {
        Widget() { super(0, 0, 100, 100, Component.empty()); }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float p) {}
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    @Test void clippedLyricsOnlyReceivePointersInsideCurrentViewport() {
        var screen = ShBetterUiApi.screen(Component.empty()).build();
        var clip = new Bounds[]{new Bounds(10, 10, 40, 40)};
        var widget = screen.addClipped(new Widget(), () -> clip[0], "lyrics");
        assertSame(widget, screen.getChildAt(20, 20).orElseThrow());
        assertTrue(screen.getChildAt(5, 5).isEmpty());
        clip[0] = new Bounds(50, 50, 20, 20);
        assertTrue(screen.getChildAt(20, 20).isEmpty());
        assertSame(widget, screen.getChildAt(55, 55).orElseThrow());
    }

    @Test void topmostVisibleLyricLayerWinsWithoutStealingOutsideItsClip() {
        var screen = ShBetterUiApi.screen(Component.empty()).build();
        var lower = screen.addClipped(new Widget(), () -> new Bounds(0, 0, 100, 100), "lyrics");
        var upper = screen.addClipped(new Widget(), () -> new Bounds(20, 20, 30, 30), "lyrics");
        assertSame(upper, screen.getChildAt(30, 30).orElseThrow());
        assertSame(lower, screen.getChildAt(5, 5).orElseThrow());
        upper.visible = false;
        assertSame(lower, screen.getChildAt(30, 30).orElseThrow());
    }
}
