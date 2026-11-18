// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplaySuitePreviewLayoutTest {
    @Test void shortWindowsPageAllFiveSamplesWithoutOverlappingNavigation() {
        for(int height:new int[]{122,150,180,200,240,320}) {
            var pages=DisplaySuitePreviewScreen.SamplePages.at(180,height);
            assertTrue(pages.capacity()>0);
            assertTrue(pages.capacity()*pages.count()>=5);
            assertTrue(40+(pages.capacity()-1)*27+22<=height-54);
            assertEquals(0,pages.clamp(-1));
            assertEquals(pages.count()-1,pages.clamp(99));
        }
    }
    @Test void tinyViewportsKeepBackOnlyInsteadOfOverlappingSamples() {
        assertEquals(0,DisplaySuitePreviewScreen.SamplePages.at(60,200).capacity());
        assertEquals(0,DisplaySuitePreviewScreen.SamplePages.at(200,100).capacity());
    }
}
