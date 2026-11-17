package com.dishanhai.sh_better_ui.kernel.api.music.control;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class MusicControlInputTest {
    @Test void seekCommitsOnceOnReleaseAndFocusLossCancels(){
        var commits=new ArrayList<MusicSeekModel.Edit>();var cancels=new ArrayList<MusicSeekModel.Edit>();
        var c=new MusicSeekControl(new Bounds(10,10,108,20),Component.literal("Position"),UiPreferences.DEFAULT,e->{},commits::add,cancels::add);
        c.sync("a",100,1000,true);
        assertTrue(c.mouseClicked(39,20,0));assertTrue(commits.isEmpty());
        assertTrue(c.mouseDragged(89,20,0,50,0));assertTrue(c.mouseReleased(89,20,0));
        assertEquals(750,commits.get(0).positionMs());assertFalse(c.mouseReleased(89,20,0));
        c.mouseClicked(39,20,0);c.setFocused(false);assertEquals(1,cancels.size());assertFalse(c.mouseReleased(89,20,0));
    }
    @Test void seekIdentityHideAndResizeInvalidateCapture(){
        var commits=new ArrayList<MusicSeekModel.Edit>();var cancels=new ArrayList<MusicSeekModel.Edit>();
        var c=new MusicSeekControl(new Bounds(0,0,108,20),Component.literal("Position"),UiPreferences.DEFAULT,e->{},commits::add,cancels::add);
        c.sync("a",100,1000,true);c.mouseClicked(25,10,0);c.sync("b",0,1000,true);
        assertFalse(c.mouseReleased(90,10,0));assertTrue(commits.isEmpty());
        c.mouseClicked(25,10,0);c.visible=false;c.sync("b",0,1000,true);c.visible=true;
        assertFalse(c.mouseReleased(90,10,0));c.mouseClicked(25,10,0);c.setWidth(120);
        assertFalse(c.mouseReleased(90,10,0));assertEquals(3,cancels.size());
    }
    @Test void volumeUsesConfiguredKeyboardStepsAndTruthfulValueFormatter(){
        var commits=new ArrayList<MusicSeekModel.Edit>();
        var c=new MusicSeekControl(new Bounds(0,0,100,20),Component.literal("Volume"),UiPreferences.DEFAULT,e->{},commits::add,e->{});
        c.setKeyboardStep(5,1);c.setValueFormatter((p,d)->Component.literal(p+"%"));
        c.sync("volume",50,100,true);c.setFocused(true);
        assertTrue(c.keyPressed(262,0,0));assertEquals(55,commits.get(0).positionMs());
        assertTrue(c.keyPressed(263,0,1));assertEquals(54,commits.get(1).positionMs());
        assertThrows(IllegalArgumentException.class,()->c.setKeyboardStep(0,1));
    }
    @Test void transportReleaseKeyboardRepeatAndCancelNeedNoSoundManager(){
        var fired=new AtomicInteger();var feedback=new AtomicInteger();
        var c=new MusicTransportControl(new Bounds(0,0,48,48),Component.literal("Play"),MusicTransportControl.Glyph.PLAY,UiPreferences.DEFAULT,fired::incrementAndGet,feedback::incrementAndGet);
        assertTrue(c.mouseClicked(20,20,0));assertEquals(0,fired.get());c.mouseReleased(60,20,0);assertEquals(0,fired.get());
        c.mouseClicked(20,20,0);c.mouseReleased(20,20,0);assertEquals(1,fired.get());
        c.keyPressed(32,0,0);c.keyPressed(32,0,0);c.keyReleased(32,0,0);assertEquals(2,fired.get());
        c.mouseClicked(20,20,0);c.setGlyph(MusicTransportControl.Glyph.PAUSE);c.mouseReleased(20,20,0);assertEquals(2,fired.get());
        c.keyPressed(32,0,0);c.setFocused(false);c.keyReleased(32,0,0);assertEquals(2,fired.get());
        c.mouseClicked(20,20,0);c.setAvailable(false);c.mouseReleased(20,20,0);assertEquals(2,feedback.get());
    }
}
