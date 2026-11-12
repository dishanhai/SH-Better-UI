// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One owner-scoped animation for changes to the layout of the same text. */
public final class TextLayoutMotion {
    public static final long DURATION_MS = 160;

    private Object source;
    private Layout target;
    private List<Layer> outgoing = List.of();
    private int matchingLayer = -1;
    private boolean moving;
    private boolean morph;
    private long startedAt;

    /** Positions are local to the label. Replacing the logical source applies the new text immediately. */
    public Frame update(Object sourceKey, List<Line> lines, long nowMs, boolean instant) {
        if (target == null || !Objects.equals(source, sourceKey)) {
            source = sourceKey;
            target = Layout.of(lines);
            stop();
        } else if (instant) {
            if (!target.matches(lines)) target = Layout.of(lines);
            stop();
        } else if (!target.matches(lines)) {
            var next = Layout.of(lines);
            var visible = sample(nowMs).layers();
            outgoing = visible.size() <= 2 ? visible
                    : visible.stream().sorted(Comparator.comparingDouble(Layer::opacity).reversed()).limit(2).toList();
            morph = outgoing.size() == 1 && Layout.of(outgoing.get(0).lines()).glyphs().equals(next.glyphs());
            matchingLayer = -1;
            if (!morph) {
                for (int i = 0; i < outgoing.size(); i++) {
                    if (Layout.of(outgoing.get(i).lines()).sameLayout(next)) matchingLayer = i;
                }
            }
            target = next;
            startedAt = nowMs;
            moving = true;
        }
        return sample(nowMs);
    }

    public void reset() {
        source = null;
        target = null;
        stop();
    }

    private void stop() {
        moving = false;
        outgoing = List.of();
    }

    private Frame sample(long nowMs) {
        if (!moving || nowMs - startedAt >= DURATION_MS) {
            stop();
            return new Frame(target.lines().isEmpty() ? List.of() : List.of(new Layer(target.lines(), 1)), false);
        }
        float t = GuiMotion.clamp01((nowMs - startedAt) / (float) DURATION_MS);
        if (t == 0) return new Frame(outgoing, true);
        float progress = t * t * (3 - 2 * t);
        if (morph) {
            var previous = outgoing.get(0);
            var lines = new ArrayList<Line>(target.lines().size());
            for (int i = 0; i < target.lines().size(); i++) {
                Line from = previous.lines().get(i);
                Line to = target.lines().get(i);
                lines.add(new Line(to.glyphs(), GuiMotion.lerp(from.x(), to.x(), progress),
                        GuiMotion.lerp(from.y(), to.y(), progress), GuiMotion.lerp(from.scale(), to.scale(), progress)));
            }
            return new Frame(List.of(new Layer(lines, GuiMotion.lerp(previous.opacity(), 1, progress))), true);
        }

        var layers = new ArrayList<Layer>(outgoing.size() + 1);
        for (int i = 0; i < outgoing.size(); i++) {
            var layer = outgoing.get(i);
            float opacity = layer.opacity() * (1 - progress);
            if (i == matchingLayer) opacity += progress;
            if (opacity > 0) layers.add(new Layer(layer.lines(), opacity));
        }
        if (matchingLayer < 0 && !target.lines().isEmpty()) layers.add(new Layer(target.lines(), progress));
        return new Frame(layers, true);
    }

    /**
     * Draws an animated layout inside the current GUI clip. The origin and clip use the current pose's
     * coordinates, so an outgoing label stays inside the latest bounds even under a scaled button.
     */
    public static void draw(GuiGraphics graphics, Font font, Frame frame, int originX, int originY, Bounds clip,
                            int color, boolean shadow, UiPreferences preferences) {
        if (frame.layers().isEmpty() || (frame.active() && (clip.width() <= 0 || clip.height() <= 0))) return;
        if (frame.active()) enableClip(graphics, clip);
        try {
            for (var layer : frame.layers()) {
                int argb = color;
                if (layer.opacity() < 1) {
                    int base = color >>> 24;
                    int alpha = Math.round((base == 0 ? 255 : base) * layer.opacity());
                    // Native Font treats alpha 0–3 as an opaque RGB color.
                    if (alpha < 4) continue;
                    argb = (color & 0xFFFFFF) | (alpha << 24);
                }
                for (var line : layer.lines()) {
                    float x = originX + line.x();
                    float y = originY + line.y();
                    if (line.scale() == 1) {
                        UiFormattedText.draw(graphics, font, line.glyphs(), x, y, argb, shadow, preferences);
                    } else {
                        var pose = graphics.pose();
                        pose.pushPose();
                        try {
                            pose.translate(x, y, 0);
                            pose.scale(line.scale(), line.scale(), 1);
                            UiFormattedText.draw(graphics, font, line.glyphs(), 0, 0, argb, shadow, preferences);
                        } finally {
                            pose.popPose();
                        }
                    }
                }
            }
        } finally {
            if (frame.active()) graphics.disableScissor();
        }
    }

    private static void enableClip(GuiGraphics graphics, Bounds clip) {
        var pose = graphics.pose();
        var screen = screenClip(pose.last().pose(), clip);
        pose.pushPose();
        try {
            // Forge takes screen coordinates, while NeoForge applies the pose in enableScissor.
            pose.setIdentity();
            graphics.enableScissor(screen.x(), screen.y(), screen.right(), screen.bottom());
        } finally {
            pose.popPose();
        }
    }

    static Bounds screenClip(Matrix4fc matrix, Bounds clip) {
        var a = matrix.transformPosition(clip.x(), clip.y(), 0, new Vector3f());
        var b = matrix.transformPosition(clip.right(), clip.y(), 0, new Vector3f());
        var c = matrix.transformPosition(clip.x(), clip.bottom(), 0, new Vector3f());
        var d = matrix.transformPosition(clip.right(), clip.bottom(), 0, new Vector3f());
        int left = (int) Math.floor(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)));
        int top = (int) Math.floor(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)));
        int right = (int) Math.ceil(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)));
        int bottom = (int) Math.ceil(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));
        return new Bounds(left, top, right - left, bottom - top);
    }

    public record Line(FormattedCharSequence glyphs, float x, float y, float scale) {
        public Line {
            Objects.requireNonNull(glyphs, "glyphs");
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Invalid animated text geometry");
            }
        }
    }

    public record Layer(List<Line> lines, float opacity) {
        public Layer { lines = List.copyOf(lines); }
    }

    public record Frame(List<Layer> layers, boolean active) {
        public Frame { layers = List.copyOf(layers); }
    }

    private record Glyph(int codePoint, Style style) {}

    private record Layout(List<Line> lines, List<List<Glyph>> glyphs) {
        static Layout of(List<Line> lines) {
            return new Layout(List.copyOf(lines), lines.stream().map(line -> {
                var key = new ArrayList<Glyph>();
                line.glyphs().accept((index, style, codePoint) -> {
                    key.add(new Glyph(codePoint, style));
                    return true;
                });
                return List.copyOf(key);
            }).toList());
        }

        boolean matches(List<Line> sample) {
            if (lines.size() != sample.size()) return false;
            for (int i = 0; i < lines.size(); i++) {
                var before = lines.get(i);
                var after = sample.get(i);
                if (before.x() != after.x() || before.y() != after.y() || before.scale() != after.scale()) return false;
                if (before.glyphs() == after.glyphs()) continue;
                var expected = glyphs.get(i);
                int[] index = {0};
                boolean equal = after.glyphs().accept((ignored, style, codePoint) -> {
                    if (index[0] >= expected.size()) return false;
                    var glyph = expected.get(index[0]++);
                    return glyph.codePoint() == codePoint && glyph.style().equals(style);
                });
                if (!equal || index[0] != expected.size()) return false;
            }
            return true;
        }

        boolean sameLayout(Layout other) {
            if (!glyphs.equals(other.glyphs)) return false;
            for (int i = 0; i < lines.size(); i++) {
                var a = lines.get(i);
                var b = other.lines.get(i);
                if (a.x() != b.x() || a.y() != b.y() || a.scale() != b.scale()) return false;
            }
            return true;
        }
    }
}
