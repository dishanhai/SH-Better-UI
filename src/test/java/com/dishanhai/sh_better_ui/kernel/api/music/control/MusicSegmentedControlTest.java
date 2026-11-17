// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MusicSegmentedControlTest {
    private MusicSegmentedControl control(List<Integer> calls){
        return new MusicSegmentedControl(new Bounds(0,0,150,26),Component.literal("Source"),
                List.of(Component.literal("Local"),Component.literal("Cloud"),Component.literal("Other")),0,
                UiPreferences.DEFAULT,calls::add,()->{});
    }
    @Test void releaseCommitsSameCellExactlyOnceAndCurrentChoiceIsNoop(){
        var calls=new ArrayList<Integer>();var c=control(calls);
        assertTrue(c.mouseClicked(75,13,0));assertTrue(c.isArmed());assertTrue(calls.isEmpty());
        assertTrue(c.mouseReleased(75,13,0));assertEquals(List.of(1),calls);assertEquals(1,c.selectedIndex());
        assertFalse(c.mouseReleased(75,13,0));c.mouseClicked(75,13,0);c.mouseReleased(75,13,0);
        assertEquals(List.of(1),calls);
        c.mouseClicked(125,13,0);c.mouseReleased(25,13,0);assertEquals(1,c.selectedIndex());
    }
    @Test void cancelLifecycleCannotResurrectOldPress(){
        var calls=new ArrayList<Integer>();var c=control(calls);
        c.mouseClicked(75,13,0);assertTrue(c.keyPressed(256,0,0));c.mouseReleased(75,13,0);
        c.mouseClicked(75,13,0);c.setVisible(false);c.setVisible(true);c.mouseReleased(75,13,0);
        c.mouseClicked(75,13,0);c.setAvailable(false);c.setAvailable(true);c.mouseReleased(75,13,0);
        c.mouseClicked(75,13,0);c.setFocused(false);c.mouseReleased(75,13,0);
        c.mouseClicked(75,13,0);c.setWidth(160);c.mouseReleased(75,13,0);
        c.mouseClicked(75,13,0);c.setSelectedIndex(2);c.mouseReleased(75,13,0);
        assertTrue(calls.isEmpty());assertEquals(2,c.selectedIndex());
    }
    @Test void keyboardUsesReleaseAndClampedHomeEndWithoutRepeatOrMixedInput(){
        var calls=new ArrayList<Integer>();var c=control(calls);c.setFocused(true);
        c.keyPressed(262,0,0);c.keyPressed(262,0,0);assertEquals(0,c.selectedIndex());
        c.keyReleased(262,0,0);assertEquals(List.of(1),calls);
        c.keyPressed(269,0,0);c.keyReleased(269,0,0);assertEquals(2,c.selectedIndex());
        c.keyPressed(262,0,0);c.keyReleased(262,0,0);assertEquals(List.of(1,2),calls);
        c.keyPressed(268,0,0);c.keyPressed(256,0,0);c.keyReleased(268,0,0);assertEquals(2,c.selectedIndex());
        c.mouseClicked(25,13,0);assertFalse(c.keyPressed(262,0,0));c.mouseReleased(25,13,0);
        assertEquals(List.of(1,2,0),calls);
    }
    @Test void invalidSelectionAndTooSmallGroupAreRejected(){
        var c=control(new ArrayList<>());assertThrows(IllegalArgumentException.class,()->c.setSelectedIndex(3));
        assertThrows(IllegalArgumentException.class,()->new MusicSegmentedControl(new Bounds(0,0,20,26),
                Component.empty(),List.of(Component.literal("a"),Component.literal("b")),0,UiPreferences.DEFAULT,i->{},()->{}));
    }
}
