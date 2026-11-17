package com.dishanhai.sh_better_ui.kernel.api.music.control;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class MusicTextControlTest {
    private MusicTextControl control(AtomicInteger calls){return new MusicTextControl(new Bounds(0,0,120,36),Component.literal("Track"),UiPreferences.DEFAULT,calls::incrementAndGet,()->{});}
    @Test void compactRowsKeepSecondLineAtActualRenderingThreshold(){
        var c=control(new AtomicInteger());c.setVariant(MusicTextControl.Variant.ROW);
        c.setSecondary(Component.literal("Artist · 3:45"));
        c.setHeight(28);assertTrue(c.secondaryVisible());
        c.setHeight(27);assertFalse(c.secondaryVisible());
        c.setHeight(32);assertTrue(c.secondaryVisible());
        c.setVariant(MusicTextControl.Variant.ACTION);assertFalse(c.secondaryVisible());
    }
    @Test void releaseActivatesOnceWhileFlyoutAndEscapeCancel(){
        var calls=new AtomicInteger();var c=control(calls);
        c.mouseClicked(20,10,0);assertTrue(c.isArmed());assertEquals(0,calls.get());
        c.mouseReleased(121,10,0);assertEquals(0,calls.get());
        c.mouseClicked(20,10,0);assertTrue(c.keyPressed(256,0,0));assertFalse(c.mouseReleased(20,10,0));
        c.mouseClicked(20,10,0);c.mouseReleased(20,10,0);assertEquals(1,calls.get());
        assertFalse(c.mouseReleased(20,10,0));
    }
    @Test void rebindRelayoutDisableAndFocusLossCancelOldActivation(){
        var calls=new AtomicInteger();var c=control(calls);
        c.mouseClicked(20,10,0);c.rebind(Component.literal("Another track"));c.mouseReleased(20,10,0);
        c.mouseClicked(20,10,0);c.setWidth(140);c.mouseReleased(20,10,0);
        c.mouseClicked(20,10,0);c.setAvailable(false);c.setAvailable(true);c.mouseReleased(20,10,0);
        c.mouseClicked(20,10,0);c.setFocused(false);c.mouseReleased(20,10,0);
        assertEquals(0,calls.get());
    }
    @Test void keyboardRepeatIsSingleReleaseAndHiddenControlDoesNotActivate(){
        var calls=new AtomicInteger();var c=control(calls);c.setFocused(true);
        c.keyPressed(32,0,0);c.keyPressed(32,0,0);c.keyReleased(32,0,0);assertEquals(1,calls.get());
        c.keyPressed(257,0,0);c.setVisible(false);c.setVisible(true);c.keyReleased(257,0,0);assertEquals(1,calls.get());
        c.active=false;c.onPress();assertEquals(1,calls.get());
    }
}
