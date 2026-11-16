// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OnlineLyricInteractionTest {
    @Test void hostAssigningFocusAfterMouseClickMustNotFreezeFollowing() {
        var row = new OnlineLyricButton(0,0,100,24, () -> {}, UiPreferences.DEFAULT, ignored -> {});
        row.setFocused(true);
        row.onClick(12,12);
        row.setFocused(false); // host may clear the previously focused child before reassigning it
        // DeveloperGalleryScreen.mouseClicked assigns child focus AFTER its callback.
        row.setFocused(true);
        assertFalse(row.keyboardFocused());
        row.setFocused(false); row.setFocused(true);
        assertTrue(row.keyboardFocused());
    }
    @Test void smallViewportKeepsActiveRowVisibleAcrossEveryTransitionFrame() {
        int height = 34;
        var viewport = new LyricViewport(); viewport.sample(0,10,0,!LyricViewport.canAnimate(height),false);
        for (int ms=1;ms<=200;ms++) {
            double center=viewport.sample(1,10,ms,!LyricViewport.canAnimate(height),false);
            int top=height/2-12+(int)Math.round((1-center)*24);
            assertTrue(top>=0 && top+24<=height);
        }
    }
}
