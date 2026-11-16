// Author: dishanhai. Original transaction model; HSV conversion reuses the existing licensed HsvColor utility.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.lyrics.common.style.HsvColor;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Host-owned color transaction. Preview callbacks are reversible; only commit callbacks accept a change. */
public final class ColorEditorState {
    private record Channels(float h, float s, float v, float a) {}
    private Channels current, committed;
    private final int defaultArgb;
    private final boolean alphaEditable;
    private final IntConsumer onPreview, onCommit;
    private boolean editing;
    public ColorEditorState(int initialArgb, int defaultArgb, IntConsumer onPreview, IntConsumer onCommit) {
        this(initialArgb, defaultArgb, true, onPreview, onCommit);
    }
    public ColorEditorState(int initialArgb, int defaultArgb, boolean alphaEditable,
                            IntConsumer onPreview, IntConsumer onCommit) {
        this.alphaEditable = alphaEditable;
        this.defaultArgb = alphaEditable ? defaultArgb : defaultArgb | 0xff000000;
        this.onPreview = Objects.requireNonNull(onPreview); this.onCommit = Objects.requireNonNull(onCommit);
        setValue(initialArgb);
    }
    public boolean alphaEditable() { return alphaEditable; }
    public int color() { return argb(current); }
    public int committedColor() { return argb(committed); }
    public int defaultColor() { return defaultArgb; }
    public boolean editing() { return editing; }
    public float channel(int channel) {
        return switch (channel) { case 0 -> current.h / 360f; case 1 -> current.s; case 2 -> current.v; case 3 -> current.a; default -> throw new IllegalArgumentException("Channel must be 0..3"); };
    }
    /** Replaces host state without echoing callbacks. Preserves hue when the supplied color is achromatic. */
    public void setValue(int argb) {
        if (!alphaEditable) argb |= 0xff000000;
        float[] hsv = HsvColor.toHsv(argb);
        float hue = hsv[1] == 0 && current != null ? current.h : hsv[0];
        current = committed = new Channels(hue, hsv[1], hsv[2], (argb >>> 24) / 255f); editing = false;
    }
    public void preview(int channel, float value) {
        if (channel == 3 && !alphaEditable) throw new IllegalArgumentException("Alpha is not editable in RGB mode");
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Channel value must be finite");
        float next = Math.max(0, Math.min(1, value));
        Channels changed = switch (channel) {
            case 0 -> new Channels(next * 360f, current.s, current.v, current.a);
            case 1 -> new Channels(current.h, next, current.v, current.a);
            case 2 -> new Channels(current.h, current.s, next, current.a);
            case 3 -> new Channels(current.h, current.s, current.v, next);
            default -> throw new IllegalArgumentException("Channel must be 0..3");
        };
        if (changed.equals(current)) return;
        editing = true; current = changed; onPreview.accept(color());
    }
    public void commit() {
        if (!editing) return;
        committed = current; editing = false; onCommit.accept(color());
    }
    public void cancel() {
        if (!editing) return;
        current = committed; editing = false; onPreview.accept(color());
    }
    public void reset() {
        int before = color(), oldCommitted = committedColor();
        float[] hsv = HsvColor.toHsv(defaultArgb);
        current = committed = new Channels(hsv[0], hsv[1], hsv[2], (defaultArgb >>> 24) / 255f);
        editing = false;
        if (before != defaultArgb) onPreview.accept(defaultArgb);
        if (oldCommitted != defaultArgb) onCommit.accept(defaultArgb);
    }
    public int rampColor(int channel, float position) {
        if (channel == 3 && !alphaEditable) throw new IllegalArgumentException("Alpha is not editable in RGB mode");
        float t = Math.max(0, Math.min(1, position));
        return switch (channel) {
            case 0 -> HsvColor.toArgb(t * 360f, 1, 1, 255);
            case 1 -> HsvColor.toArgb(current.h, t, current.v, 255);
            case 2 -> HsvColor.toArgb(current.h, current.s, t, 255);
            case 3 -> HsvColor.toArgb(current.h, current.s, current.v, Math.round(t * 255));
            default -> throw new IllegalArgumentException("Channel must be 0..3");
        };
    }
    private static int argb(Channels c) { return HsvColor.toArgb(c.h, c.s, c.v, Math.round(c.a * 255)); }
}
