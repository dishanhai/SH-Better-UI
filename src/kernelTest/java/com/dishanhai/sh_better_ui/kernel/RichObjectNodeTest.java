// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Frames;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Slider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RichObjectNodeTest {
    @Test void frameSequenceIsImmutableBoundedAndSamplesTheHostClock() {
        var first = UiId.parse("test:textures/first.png");
        var second = UiId.parse("test:textures/second.png");
        var input = new ArrayList<>(List.of(first, second));
        var sequence = new Frames(input, 100);
        input.clear();
        assertThrows(UnsupportedOperationException.class, () -> sequence.textures().clear());
        assertEquals(first, sequence.textureAt(99, false));
        assertEquals(second, sequence.textureAt(100, false));
        assertEquals(first, sequence.textureAt(200, false));
        assertEquals(second, sequence.textureAt(-1, false));
        assertEquals(first, sequence.textureAt(Long.MAX_VALUE, true));
        assertThrows(IllegalArgumentException.class, () -> new Frames(List.of(first), 15));
        assertThrows(IllegalArgumentException.class, () -> new Frames(Collections.nCopies(33, first), 50));
        assertThrows(IllegalArgumentException.class, () -> new Frames(List.of(UiId.parse("test:remote.gif")), 50));
    }

    @Test void sliderUsesTheSameLatticeForPointerAndKeyWithReachableEndpoints() {
        var slider = new Slider("Level", -2, 6, 0, 3);
        assertEquals(1, slider.initial());
        assertEquals(-2, slider.valueAt(-0.1));
        assertEquals(4, slider.valueAt(0.8));
        assertEquals(6, slider.valueAt(0.9));
        assertEquals(6, slider.valueAt(1.1));
        assertEquals(6, slider.stepFrom(4, true));
        assertEquals(4, slider.stepFrom(6, false));
        assertThrows(IllegalArgumentException.class, () -> slider.valueAt(Double.NaN));

        var fullRange = new Slider("Range", Integer.MIN_VALUE, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, fullRange.valueAt(1));
        assertEquals(Integer.MAX_VALUE - 1, fullRange.stepFrom(Integer.MAX_VALUE, false));
        assertEquals(Integer.MAX_VALUE, fullRange.stepFrom(Integer.MAX_VALUE - 1, true));
    }
}
