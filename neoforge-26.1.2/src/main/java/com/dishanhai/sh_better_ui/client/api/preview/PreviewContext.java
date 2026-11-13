// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** Native scene host; bounds are absolute GUI coordinates. */
public interface PreviewContext {
    Screen screen();
    Bounds viewport();
    Font font();
    UiPreferences preferences();
    boolean interactive();
    <T extends AbstractWidget> T add(T widget);
}
