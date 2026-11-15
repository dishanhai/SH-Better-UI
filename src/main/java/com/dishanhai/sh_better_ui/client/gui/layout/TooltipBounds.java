// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.layout;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.joml.Vector2i;

import java.util.function.DoubleConsumer;

/** Tooltip viewport boundaries in GUI-scaled screen coordinates. */
public final class TooltipBounds {
    private TooltipBounds() {}

    public static int rightEdge(int screenWidth, int rightBoundary) {
        return Math.max(1, Math.min(screenWidth, rightBoundary));
    }

    /** Uses native edge placement with a reserved region to the right. */
    public static ClientTooltipPositioner leftOf(int rightBoundary) {
        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            int right = rightEdge(screenWidth, rightBoundary);
            return DefaultTooltipPositioner.INSTANCE.positionTooltip(right, screenHeight,
                    Math.min(mouseX, right), mouseY, width, height);
        };
    }

    /** Right-aligns the complete tooltip, scaling only if its decorated size exceeds the viewport. */
    public static ClientTooltipPositioner fitLeftOf(int rightBoundary, int padding, DoubleConsumer applyScale) {
        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            int right = rightEdge(screenWidth, rightBoundary);
            int inset = padding + 2;
            float scale = Math.min(1f, Math.min((float) right / (width + 2 * inset),
                    (float) screenHeight / (height + 2 * inset)));
            int localRight = Math.max(width + 2 * inset, (int) (right / scale));
            int localHeight = Math.max(height + 2 * inset, (int) (screenHeight / scale));
            int y = Math.max(inset, Math.min((int) ((mouseY - 12) / scale), localHeight - height - inset));
            applyScale.accept(scale);
            return new Vector2i(localRight - width - inset, y);
        };
    }
}
