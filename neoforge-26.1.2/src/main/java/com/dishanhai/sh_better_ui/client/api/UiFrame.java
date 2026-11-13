// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One native GUI extraction snapshot; coordinates use GUI logical pixels. */
public record UiFrame(GuiGraphicsExtractor graphics, Bounds viewport, int mouseX, int mouseY,
                      UiPreferences preferences, long nowMs, float partialTick) {}
