// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * One tooltip's visual text lines. No global state or matching by text content.
 * Widths and offsets use native GUI units: the host's matrix applies GUI scaling exactly once.
 * Glyph structure is snapshotted; effect markers remain intact and are sampled by the renderer.
 * Recreate this layout when revealing more text, changing font resources, or rewrapping a component.
 */
public final class TooltipTextLayout {
    public enum Alignment { LEFT, CENTER, RIGHT }
    private record Glyph(int index, Style style, int codePoint) {}
    private final List<FormattedCharSequence> lines;
    private final Alignment alignment;
    private final int minimumWidth;

    public TooltipTextLayout(List<FormattedCharSequence> lines, Alignment alignment, int minimumWidth) {
        if (minimumWidth < 0) throw new IllegalArgumentException("minimumWidth must be nonnegative");
        this.alignment = Objects.requireNonNull(alignment);
        this.minimumWidth = minimumWidth;
        var snapshots = new ArrayList<FormattedCharSequence>();
        for (FormattedCharSequence line : lines) {
            var glyphs = new ArrayList<Glyph>();
            Objects.requireNonNull(line).accept((index, style, codePoint) -> {
                glyphs.add(new Glyph(index, style, codePoint)); return true;
            });
            List<Glyph> immutable = List.copyOf(glyphs);
            snapshots.add(sink -> {
                for (Glyph glyph : immutable) {
                    if (!sink.accept(glyph.index(), glyph.style(), glyph.codePoint())) return false;
                }
                return true;
            });
        }
        this.lines = List.copyOf(snapshots);
    }

    public List<FormattedCharSequence> lines() { return lines; }

    /** Native ClientTextTooltip widths are integer GUI units, including their native rounding. */
    public int width(ToIntFunction<FormattedCharSequence> measure) {
        int width = minimumWidth;
        for (FormattedCharSequence line : lines) width = Math.max(width, measure.applyAsInt(line));
        return width;
    }

    public float offset(int line, ToIntFunction<FormattedCharSequence> measure) {
        int remaining = width(measure) - measure.applyAsInt(lines.get(line));
        return switch (alignment) {
            case LEFT -> 0;
            case CENTER -> remaining / 2.0f;
            case RIGHT -> remaining;
        };
    }
}
