// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;
import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GallerySampleInputTest {
    private static class Sample extends GallerySampleScreen {
        Sample(){super(null,"input_fixture");}
        protected void initSample(){}
        <T extends AbstractWidget> T add(T widget){if(initialFocus==null)initialFocus=widget;return addRenderableWidget(widget);}
    }
    private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
    private static AbstractWidget canvas(AtomicInteger rail) {
        return new AbstractWidget(0,0,100,100,Component.literal("scroll canvas")) {
            @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice) {
                if(event.x()<92||!isMouseOver(event.x(),event.y()))return false;
                rail.incrementAndGet();return true;
            }
            protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float delta){}
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out){}
        };
    }
    @Test void canvasPassThroughCapturesButtonReleaseAndRetainsRepeatedFocus() throws Exception {
        try(var client=NativeTestClient.install()) {
            var screen=new Sample();client.screen(screen);
            var hits=new AtomicInteger();var rail=new AtomicInteger();screen.add(canvas(rail));
            var button=screen.add(new MusicTextControl(new Bounds(10,10,70,24),Component.literal("Move down"),UiPreferences.DEFAULT,hits::incrementAndGet,()->{}));
            for(int i=0;i<2;i++) {
                assertTrue(screen.mouseClicked(mouse(20,20),false));assertSame(button,screen.getFocused());
                assertTrue(button.isArmed());assertEquals(i,hits.get());
                assertTrue(screen.mouseReleased(mouse(20,20)));assertEquals(i+1,hits.get());
            }
            assertTrue(screen.keyPressed(new KeyEvent(32,0,0)));assertTrue(screen.keyReleased(new KeyEvent(32,0,0)));assertEquals(3,hits.get());
            assertTrue(screen.mouseClicked(mouse(96,20),false));assertEquals(1,rail.get());assertEquals(3,hits.get());
        }
    }
    @Test void releaseOutsideCancelsAndHiddenRowsDoNotStealTheClick() throws Exception {
        try(var client=NativeTestClient.install()) {
            var screen=new Sample();client.screen(screen);screen.add(canvas(new AtomicInteger()));
            var hits=new AtomicInteger();var button=screen.add(new MusicTextControl(new Bounds(10,10,70,24),Component.literal("Move"),UiPreferences.DEFAULT,hits::incrementAndGet,()->{}));
            assertTrue(screen.mouseClicked(mouse(20,20),false));assertTrue(screen.mouseReleased(mouse(120,120)));assertEquals(0,hits.get());
            button.setVisible(false);assertFalse(screen.mouseClicked(mouse(20,20),false));assertEquals(0,hits.get());
        }
    }
}
