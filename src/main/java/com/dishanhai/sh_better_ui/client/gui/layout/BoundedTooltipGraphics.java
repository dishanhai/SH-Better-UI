// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Matrix4f;

/** A temporary viewport for native tooltip rendering, including custom image components. */
public final class BoundedTooltipGraphics extends GuiGraphics {
    private final GuiGraphics parent;
    private final int right;
    private final int height;
    private float scale = 1f;

    public BoundedTooltipGraphics(GuiGraphics parent, int right) {
        super(Minecraft.getInstance(), parent.bufferSource());
        this.parent = parent;
        this.right = right;
        this.height = parent.guiHeight();
        pose().last().pose().set(parent.pose().last().pose());
        pose().last().normal().set(parent.pose().last().normal());
    }

    @Override public int guiWidth() { return right; }
    @Override public int guiHeight() { return height; }

    @Override
    public void enableScissor(int left, int top, int right, int bottom) {
        parent.enableScissor((int) Math.floor(left * scale), (int) Math.floor(top * scale),
                (int) Math.ceil(right * scale), (int) Math.ceil(bottom * scale));
    }

    @Override public void disableScissor() { parent.disableScissor(); }

    public ClientTooltipPositioner positioner() {
        Matrix4f original = new Matrix4f(pose().last().pose());
        // Recent tooltip sprites extend 12 GUI pixels outside the content rectangle.
        return TooltipBounds.fitLeftOf(right, 12, factor -> {
            scale = (float) factor;
            pose().last().pose().set(original).scale(scale, scale, 1f);
        });
    }
}
