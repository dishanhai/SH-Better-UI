// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicNativeInputTest {
    private static MouseButtonEvent mouse(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
    }
    @Test void transportNativeEventsActivateOnReleaseAndCancelOnFocusLoss() {
        var fired = new AtomicInteger();
        var control = new MusicTransportControl(new Bounds(0,0,48,48), Component.literal("Play"),
                MusicTransportControl.Glyph.PLAY, UiPreferences.DEFAULT, fired::incrementAndGet, () -> {});
        assertTrue(control.mouseClicked(mouse(20,20), false));
        assertEquals(0, fired.get());
        assertTrue(control.mouseReleased(mouse(20,20)));
        assertEquals(1, fired.get());
        var space = new KeyEvent(32,0,0);
        assertTrue(control.keyPressed(space));
        assertTrue(control.keyPressed(space));
        assertTrue(control.keyReleased(space));
        assertEquals(2, fired.get());
        control.mouseClicked(mouse(20,20), false);
        control.setFocused(false);
        assertFalse(control.mouseReleased(mouse(20,20)));
        assertEquals(2, fired.get());
    }
    @Test void seekNativeDragAndShiftKeyboardKeepTheSharedModel() {
        var commits = new ArrayList<MusicSeekModel.Edit>();
        var cancels = new ArrayList<MusicSeekModel.Edit>();
        var control = new MusicSeekControl(new Bounds(10,10,108,20), Component.literal("Seek"),
                UiPreferences.DEFAULT, e -> {}, commits::add, cancels::add);
        control.sync("track",100,1000,true);
        assertTrue(control.mouseClicked(mouse(39,20),false));
        assertTrue(control.mouseDragged(mouse(89,20),50,0));
        assertTrue(control.mouseReleased(mouse(89,20)));
        assertEquals(750, commits.getFirst().positionMs());
        assertFalse(control.mouseReleased(mouse(89,20)));
        control.setKeyboardStep(50,10);
        assertTrue(control.keyPressed(new KeyEvent(262,0,1)));
        assertEquals(760, commits.getLast().positionMs());
        control.mouseClicked(mouse(39,20),false);
        assertTrue(control.keyPressed(new KeyEvent(256,0,0)));
        assertEquals(1,cancels.size());
    }
    @Test void segmentedAndTextControlsUseNativeEventsWithoutPrematureCommit() {
        var selected = new AtomicInteger();
        var segmented = new MusicSegmentedControl(new Bounds(0,0,100,24), Component.literal("Source"),
                List.of(Component.literal("One"),Component.literal("Two")),0,UiPreferences.DEFAULT,
                selected::set, () -> {});
        assertTrue(segmented.mouseClicked(mouse(75,10),false));
        assertEquals(0,selected.get());
        assertTrue(segmented.mouseReleased(mouse(75,10)));
        assertEquals(1,selected.get());
        var fired = new AtomicInteger();
        var text = new MusicTextControl(new Bounds(0,0,100,24),Component.literal("Library"),
                UiPreferences.DEFAULT,fired::incrementAndGet,() -> {});
        text.setFocused(true);
        assertTrue(text.keyPressed(new KeyEvent(257,0,0)));
        assertEquals(0,fired.get());
        assertTrue(text.keyReleased(new KeyEvent(257,0,0)));
        assertEquals(1,fired.get());
    }
}
