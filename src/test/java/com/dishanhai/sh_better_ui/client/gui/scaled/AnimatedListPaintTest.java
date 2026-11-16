// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.AnimatedListState;
import com.dishanhai.sh_better_ui.kernel.api.layout.ViewportReveal;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimatedListPaintTest {
    private static final StringSplitter SPLITTER = new StringSplitter((codePoint, style) -> 5f);
    private static final Font FONT = new Font(ignored -> null, false) {
        @Override public StringSplitter getSplitter() { return SPLITTER; }
        @Override public int width(FormattedCharSequence text) { return (int) Math.ceil(SPLITTER.stringWidth(text)); }
        @Override public int width(FormattedText text) { return (int) Math.ceil(SPLITTER.stringWidth(text)); }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            return SPLITTER.splitLines(text, width, Style.EMPTY).stream().map(Language.getInstance()::getVisualOrder).toList();
        }
    };

    @Test void lightPaletteHasReadableInkAndTheMaskChangesContentAlpha() {
        var canvas = new RecordingCanvas();
        draw(canvas, UiPreferences.DEFAULT, null);
        assertTrue(canvas.opacities.stream().anyMatch(a -> a > 0 && a < .9f));
        assertTrue(canvas.textColors.contains(0xFF27272A));
        assertEquals(0, canvas.transforms);
        assertEquals(0, canvas.clips);
        assertEquals(1, canvas.opacity);
    }

    @Test void highContrastDoesNotFadeForeground() {
        var canvas = new RecordingCanvas();
        draw(canvas, new UiPreferences(true, false, false), null);
        assertTrue(canvas.opacities.stream().allMatch(a -> a == 1));
        assertTrue(canvas.textColors.stream().allMatch(a -> a == 0xFF27272A));
    }

    @Test void customRendererFailureRestoresTheHostClipTransformAndAlpha() {
        var canvas = new RecordingCanvas();
        assertThrows(IllegalStateException.class, () -> draw(canvas, UiPreferences.DEFAULT,
                (c, font, area, item, index, selected, ink, muted) -> { throw new IllegalStateException("renderer"); }));
        assertEquals(0, canvas.transforms);
        assertEquals(0, canvas.clips);
        assertEquals(1, canvas.opacity);
    }

    private static void draw(RecordingCanvas canvas, UiPreferences prefs, UiAnimatedList.ItemRenderer<String> renderer) {
        var d = AnimatedListState.Options.DEFAULT;
        var state = new AnimatedListState(new AnimatedListState.Options(true, true, false,
                ViewportReveal.Animation.NONE, false, d.staggerMs(), true, 30));
        state.layout(List.of("a", "b", "c"), new double[]{56, 56, 56}, 8, 100);
        state.select(1);
        state.scroll().scrollTo(30);
        state.synchronizeScroll();
        state.advance(1000, false);
        var item = new UiAnimatedList.Item<>("a", "value", Component.literal("File"), CommonComponents.EMPTY, CommonComponents.EMPTY, null);
        AnimatedListPaint.draw(canvas, FONT, new Bounds(0, 0, 220, 124), new Bounds(12, 12, 188, 100),
                List.of(item, item, item), state, new UiAnimatedList.Style(14, 8, true, true, UiAnimatedList.Theme.LIGHT, null),
                prefs, true, true, -1, -1, renderer, 1000);
    }

    private static final class RecordingCanvas implements UiAnimatedList.Canvas {
        int transforms, clips;
        float opacity = 1;
        final List<Float> opacities = new ArrayList<>();
        final List<Integer> textColors = new ArrayList<>();
        public void opacity(float value) { opacity = value; opacities.add(value); }
        public void box(float x, float y, float w, float h, float radius, int edge, int fill) {}
        public void fill(int x, int y, int right, int bottom, int color) { assertTrue(right >= x); }
        public void text(Font font, FormattedCharSequence text, int x, int y, int color) { textColors.add(color); }
        public void icon(GraphicIcon icon, int x, int y, int color, UiPreferences prefs) {}
        public void push(float x, float y, float scale) { transforms++; }
        public void pop() { transforms--; }
        public void clip(int x, int y, int right, int bottom) { clips++; }
        public void unclip() { clips--; }
        public void plate(Bounds area, float radius, int edge, int fill, UiPreferences prefs) {}
    }
}
