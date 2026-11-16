// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

/** Drawing bridge shared by immediate and extracted Minecraft GUI backends. */
interface PillNavCanvas {
    void box(float x, float y, float w, float h, float radius, int stroke, int edge, int fill);
    void mesh(float x, float y, FilledGeometry<Local> mesh, int top, int bottom, float height);
    void clip(int x, int y, int right, int bottom, Runnable draw);
    void text(Font font, FormattedCharSequence text, float x, float y, float scale, int ink);
    void icon(GraphicIcon icon, float x, float y, float size, float rotation, int ink, UiPreferences preferences);
    boolean glass(int x, int y, int width, int height, float radius, int tint, UiPreferences preferences);
}
