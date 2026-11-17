package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Native-font counterpart to MP's scrolling label, with no Skija or bundled font dependency.
 * One instance belongs to one visible label. Its full Component remains available for narration.
 */
public final class UiMarqueeLabel {
    private final MarqueeMotion motion;
    private Component text = Component.empty();

    public UiMarqueeLabel() { this(new MarqueeMotion()); }
    public UiMarqueeLabel(MarqueeMotion motion) { this.motion = Objects.requireNonNull(motion); }

    public void setText(Component value) {
        Component next = Objects.requireNonNull(value).copy();
        if (!text.equals(next)) {
            text = next;
            motion.reset();
        }
    }

    /** Full, untruncated semantic label for host tooltip/narration. */
    public Component text() { return text.copy(); }
    public void reset() { motion.reset(); }

    /**
     * Draw within the host's logical coordinate space; nested scissor is restored even on failure.
     * Call with paused=true while hidden (or reset on reopen) to avoid elapsed offscreen animation.
     * The host chooses readable color and supplies its effective accessibility preferences.
     */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width,
                       int color, boolean shadow, long nowMillis, boolean paused,
                       UiPreferences preferences) {
        Objects.requireNonNull(graphics);
        Objects.requireNonNull(font);
        Objects.requireNonNull(preferences);
        if (width <= 0) { motion.reset(); return; }
        MarqueeMotion.Frame frame = motion.sample(text, font.width(text), width, nowMillis,
                paused, preferences.reducedMotion());
        graphics.enableScissor(x, y - 1, x + width, y + font.lineHeight + 2);
        try {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(frame.offset(), 0, 0);
                graphics.drawString(font, text, x, y, color, shadow);
            } finally {
                graphics.pose().popPose();
            }
            if (frame.overflowing() && !preferences.reducedMotion() && frame.repeatOffset() < width) {
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(frame.repeatOffset(), 0, 0);
                    graphics.drawString(font, text, x, y, color, shadow);
                } finally {
                    graphics.pose().popPose();
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
