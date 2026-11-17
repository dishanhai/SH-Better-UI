// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicOutlineControlTest {
    @Test void legacyMusicSymbolsResolveToOriginalOutlineGeometry(){
        assertEquals(MusicControlRenderer.Icon.SEARCH,MusicTextControl.outlineFor(GraphicIcon.SEARCH));
        assertEquals(MusicControlRenderer.Icon.FAVORITE,MusicTextControl.outlineFor(GraphicIcon.FAVORITE));
        assertEquals(MusicControlRenderer.Icon.USER,MusicTextControl.outlineFor(GraphicIcon.USER));
        assertEquals(MusicControlRenderer.Icon.BACK,MusicTextControl.outlineFor(GraphicIcon.BACK));
        assertNull(MusicTextControl.outlineFor(GraphicIcon.INFO));
    }
    @Test void outlineNavigationRetainsFullAccessibleLabelAndCancellableActivation(){
        var calls=new AtomicInteger();var label=Component.literal("My complete music library");
        var c=new MusicTextControl(new Bounds(0,0,32,28),label,UiPreferences.DEFAULT,calls::incrementAndGet,()->{});
        c.setGlyphOnly(MusicControlRenderer.Icon.LIBRARY);
        assertEquals(label,c.getMessage());
        c.mouseClicked(10,10,0);c.setVisible(false);c.setVisible(true);c.mouseReleased(10,10,0);
        assertEquals(0,calls.get());
        c.mouseClicked(10,10,0);c.mouseReleased(10,10,0);assertEquals(1,calls.get());
        c.setLeadingIcon(MusicControlRenderer.Icon.USER);assertEquals(label,c.getMessage());
        c.setGlyphOnly(MusicControlRenderer.Icon.REFRESH);assertEquals(label,c.getMessage());
        c.mouseClicked(10,10,0);c.mouseReleased(10,10,0);assertEquals(2,calls.get());
        c.setGlyphOnly(MusicControlRenderer.Icon.CLOSE);assertEquals(label,c.getMessage());
        c.setAvailable(false);c.onPress();assertEquals(2,calls.get());
    }
}
