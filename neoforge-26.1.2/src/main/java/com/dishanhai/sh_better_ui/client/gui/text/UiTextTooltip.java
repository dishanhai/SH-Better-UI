// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/**
 * Explicit, instance-scoped text tooltip factory. Submit the returned components through the
 * host's native tooltip API. Alignment is within these text rows, not unrelated item/image rows.
 * The host owns positioning and available GUI width; no screen pixels or global matching are used.
 */
public final class UiTextTooltip {
    private UiTextTooltip() {}

    public static List<ClientTooltipComponent> lines(List<FormattedCharSequence> lines,
            TooltipTextLayout.Alignment alignment, int minimumWidth) {
        TooltipTextLayout layout = new TooltipTextLayout(lines, alignment, minimumWidth);
        var result = new ArrayList<ClientTooltipComponent>();
        for (int i = 0; i < layout.lines().size(); i++) result.add(new Row(layout, i));
        return List.copyOf(result);
    }

    /** Native split preserves visual order/styles. Pass a positive width in GUI units, not pixels. */
    public static List<ClientTooltipComponent> wrap(Font font, Component component, int maximumWidth,
            TooltipTextLayout.Alignment alignment) {
        if (maximumWidth < 1) throw new IllegalArgumentException("maximumWidth must be positive");
        var normalized = UiTextEffects.normalize(component);
        List<FormattedCharSequence> rows = font.split(normalized, maximumWidth);
        if (rows.isEmpty()) rows = List.of(normalized.getVisualOrderText());
        return lines(rows, alignment, 0);
    }

    private record Row(TooltipTextLayout layout, int index) implements ClientTooltipComponent {
        @Override public int getWidth(Font font) { return layout.width(font::width); }
        @Override public int getHeight(Font font) { return 10; }
        @Override public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(layout.offset(index, font::width), 0);
                graphics.text(font, layout.lines().get(index), x, y, -1, true);
            } finally {
                graphics.pose().popMatrix();
            }
        }
    }
}
