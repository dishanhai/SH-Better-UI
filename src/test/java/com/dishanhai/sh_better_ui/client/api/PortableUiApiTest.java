// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortableUiApiTest {
    @Test
    void bothFrameAdaptersExposeTheSamePortableContract() throws Exception {
        assertTrue(UiCanvas.class.isAssignableFrom(UiFrame.class));
        assertEquals(UiCanvas.class, UiFrame.class.getMethod("canvas").getReturnType());
        assertEquals(UiScreenBuilder.class, ShBetterUiApi.class.getMethod("screen", String.class).getReturnType());
    }

    @Test
    void layoutUsesTheLogicalViewportIncludingItsOrigin() {
        var layout = new TestCanvas().createLayout();
        var node = layout.node("label", AlignmentFrame.box(new Rect(0, 0, 20, 8)));
        VisualAlignment.align(node).relativeToScreen()
                .horizontal(VisualAlignment.Align.CENTER).vertical(VisualAlignment.Align.TOP).apply();
        layout.resolve();
        var view = node.current(PixelAlignment.ONE);
        assertEquals(new Rect(50, 20, 20, 8), view.layoutBounds());
        assertTrue(view.hitTest(50, 20));
        assertFalse(view.hitTest(70, 20));
    }

    @Test
    void portableGlassFallbackUsesTheReadableSolidSurface() {
        var canvas = new TestCanvas();
        var area = new Bounds(10, 12, 60, 20);
        var panel = new UiGlassPanel(area, 16, GlassVariant.CLEAR, Foreground.LIGHT);
        assertEquals(10f, panel.radius());
        assertEquals(UiGlassPanel.Paint.SOLID, canvas.glass(panel));
        assertEquals(area, canvas.painted);
        assertEquals(0xff000000, canvas.fill);
        assertEquals(UiGlassPanel.Paint.EMPTY, canvas.glass(
                new UiGlassPanel(new Bounds(0, 0, 0, 40), 5, GlassVariant.REGULAR, Foreground.DARK)));
        assertEquals(area, canvas.painted);
    }

    private static final class TestCanvas implements UiCanvas {
        private Bounds painted;
        private int fill;
        @Override public Bounds viewport() { return new Bounds(10, 20, 100, 40); }
        @Override public UiPreferences preferences() { return new UiPreferences(true, false, true); }
        @Override public long nowMs() { return 0; }
        @Override public float partialTick() { return 0; }
        @Override public void fill(Bounds bounds, int argb) { painted = bounds; fill = argb; }
        @Override public void drawScaledText(Font font, Component text, int x, int y, int argb, float scale) {
            throw new UnsupportedOperationException();
        }
        @Override public void icon(GraphicIcon icon, int x, int y, int scale, int argb) {
            throw new UnsupportedOperationException();
        }
        @Override public void clipped(Bounds bounds, Runnable content) { throw new UnsupportedOperationException(); }
        @Override public void placed(AlignmentLayout.View view, Runnable content) { throw new UnsupportedOperationException(); }
    }
}
