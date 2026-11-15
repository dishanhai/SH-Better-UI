// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayeredScreenTest {
    @Test
    void backgroundCompletesBeforeClearForegroundAndWidgets() {
        var passes = new ArrayList<String>();
        class Probe extends LayeredScreen {
            Probe() {
                super(Component.empty());
                addRenderableOnly((graphics, x, y, partialTick) -> passes.add("widget"));
            }

            @Override protected void renderBackdrop(GuiGraphics graphics, int x, int y, float partialTick) {
                passes.add("backdrop");
            }

            @Override protected void renderForeground(GuiGraphics graphics, int x, int y, float partialTick) {
                passes.add("foreground");
            }
        }
        new Probe().render(null, 0, 0, 0f);
        assertEquals(List.of("backdrop", "foreground", "widget"), passes);
    }
}
