// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native GUI rendering for the shared antigravity simulation and the character rain sampler. */
public final class GalleryFieldParticles {
    private final AntigravityField field = new AntigravityField();
    private AntigravityProfile profile;
    private int[] palette;

    public void restart() { field.restart(); }

    public void cancel() {
        if (profile != null) field.advance(profile, null, 0, false);
    }

    public void burst(AntigravityProfile value, Bounds area) {
        field.resize(area.width(), area.height(), value);
        field.burst(value, 1);
    }

    public void draw(GuiGraphicsExtractor graphics, Bounds area, AntigravityProfile value,
                     UiPointerTracker.Frame pointer, float dt, boolean reducedMotion, boolean highContrast) {
        if (!value.equals(profile)) {
            profile = value;
            palette = AntigravityPalette.build(value.colors(), value.lightMode(),
                    value.colorMode() == AntigravityProfile.ColorMode.CONIC);
        }
        field.resize(area.width(), area.height(), value);
        field.advance(value, pointer, dt, reducedMotion);
        var mesh = new GalleryFieldMesh();
        float unit = area.height() / 31.5f;
        float ringRadius = value.ringRadius() * unit * (1 - .38f * field.charge);
        float band = Math.max(unit * 4, 1);
        for (int i = 0; i < field.x.length; i++) {
            float x = field.x[i], y = field.y[i];
            float dx = x - field.cursorX, dy = y - field.cursorY;
            float distance = (float) Math.hypot(dx, dy);
            float distanceFromRing = (distance - ringRadius) / band;
            float ring = (float) Math.exp(-distanceFromRing * distanceFromRing);
            float grow = value.ambient() + (1 - value.ambient()) * ring;
            float depth = 1 + field.depth[i] * .32f * value.depthFactor();
            float pulse = 1 + (float) Math.sin(field.time * value.pulseSpeed() + field.phase[i])
                    * .22f * value.particleVariance();
            float size = value.particleSize() * unit * .6f * depth * pulse * (.85f + .3f * field.sizeSeed[i])
                    * (1 + field.charge * .25f * ring);
            float colorPosition = switch (value.colorMode()) {
                case CONIC -> (float) (Math.atan2(dy, dx) / (Math.PI * 2)) + field.hue;
                case RADIAL -> distance / Math.max(ringRadius * 2.4f, .001f);
                case RANDOM -> (float) Math.min(value.colors().size() - 1, (int) (field.colorSeed[i] * value.colors().size()))
                        / Math.max(value.colors().size() - 1, 1);
            };
            int base = AntigravityPalette.sample(palette, colorPosition,
                    value.colorMode() == AntigravityProfile.ColorMode.CONIC);
            float visibility = grow * (1 - Math.max(-field.depth[i], 0) * .35f * value.depthFactor())
                    * field.reveal * value.opacity();
            int color = highContrast ? GalleryFieldMesh.alpha(0xFFFFFFFF, .38f + .62f * visibility)
                    : GalleryFieldMesh.alpha(base, visibility);
            if ((color >>> 24) == 0) continue;

            float half = Math.max(.4f, size * .25f * (.6f + .4f * grow));
            switch (value.particleShape()) {
                case SPHERE -> mesh.disc(x, y, half * 1.35f, color, GalleryFieldMesh.alpha(color, .25f));
                case BOX -> mesh.rectangle(x, y, half, half, color);
                case TETRAHEDRON -> {
                    float r = half * 1.5f;
                    mesh.triangle(x, y - r, x - r, y + r, x + r, y + r, color, color,
                            GalleryFieldMesh.alpha(color, .55f));
                }
                case CAPSULE -> {
                    float speed = (float) Math.hypot(field.vx[i], field.vy[i]);
                    float streak = Math.min(2.5f, speed / Math.max(unit * 30, .001f));
                    float angle = distance > .001f ? (float) Math.atan2(dy, dx) : 0;
                    if (speed > .001f && streak > .3f) angle = (float) Math.atan2(field.vy[i], field.vx[i]);
                    float length = Math.max(.55f, size * Math.max(.17f, .5f * grow) * (1 + streak * 1.2f));
                    float sx = (float) Math.cos(angle) * length, sy = (float) Math.sin(angle) * length;
                    mesh.line(x - sx, y - sy, x + sx, y + sy, half * 1.1f, color, color);
                }
            }
        }
        mesh.submit(graphics, area);
    }

    public static void drawRain(GuiGraphicsExtractor graphics, Font font, Bounds area, CodeRainProfile profile,
                                double seconds, UiPointerTracker.Frame pointer, float pulseX, float pulseY,
                                double pulseSeconds, boolean highContrast) {
        Grid grid = Grid.fit(area.width(), area.height(), profile.cellSize());
        String[] glyphs = profile.characters().codePoints().mapToObj(Character::toString).toArray(String[]::new);
        int[] widths = new int[glyphs.length];
        int largest = font.lineHeight;
        for (int i = 0; i < glyphs.length; i++) {
            widths[i] = font.width(glyphs[i]);
            largest = Math.max(largest, widths[i]);
        }
        float scale = .82f * grid.cellSize / Math.max(1, largest);
        float stepX = area.width() / (float) grid.columns;
        float stepY = area.height() / (float) grid.rows;
        double phase = seconds * profile.speed();
        var pose = graphics.pose();
        pose.pushMatrix().translate(area.x(), area.y()).scale(scale);
        try {
            for (int column = 0; column < grid.columns; column++) {
                double head = head(profile, column, grid.rows, phase);
                for (int row = 0; row < grid.rows; row++) {
                    float x = (column + .5f) * stepX;
                    float y = (row + .5f) * stepY;
                    float brightness = brightness(profile, column, row, head);
                    if (pointer != null && pointer.inside()) {
                        double distance = Math.hypot(pointer.x() - x, pointer.y() - y) / 30;
                        brightness = Math.max(brightness, (float) (.56 * Math.exp(-distance * distance)));
                    }
                    if (pulseSeconds >= 0 && pulseSeconds < .8) {
                        double offset = (Math.hypot(pulseX - x, pulseY - y) - 190 * pulseSeconds) / 13;
                        brightness = Math.max(brightness, (float) ((1 - pulseSeconds / .8) * Math.exp(-offset * offset)));
                    }
                    if (brightness <= 0) continue;
                    int glyph = glyphIndex(profile, glyphs.length, column, row, phase);
                    int ink = highContrast ? 0xFFFFFFFF : GuiMotion.lerpColor(profile.color(), 0xFFE9FFF6,
                            Math.clamp((brightness - .65f) / .35f, 0, 1));
                    int color = glyphColor(ink, brightness);
                    if (color != 0) {
                        graphics.text(font, glyphs[glyph], Math.round(x / scale - widths[glyph] * .5f),
                                Math.round(y / scale - font.lineHeight * .5f), color, false);
                    }
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    static int glyphColor(int argb, float opacity) {
        int alpha = Math.round((argb >>> 24) * opacity);
        // 原版 Font 会把 alpha 小于 4 的颜色视为不透明。
        return alpha < 4 ? 0 : alpha << 24 | (argb & 0xFFFFFF);
    }

    static double head(CodeRainProfile profile, int column, int rows, double phase) {
        int cycle = rows + profile.trailLength();
        double travel = cycle * noise(profile.seed(), column, 0, 0)
                + phase * (.65 + .7 * noise(profile.seed(), column, 1, 0));
        return travel % cycle;
    }

    static float brightness(CodeRainProfile profile, int column, int row, double head) {
        float sample = noise(profile.seed(), column, row, 1);
        if (sample >= profile.density()) return 0;
        if (profile.mode() == CodeRainProfile.Mode.ASCII) return .65f + .35f * sample;
        double distance = head - row;
        if (distance <= -1 || distance >= profile.trailLength()) return 0;
        if (distance < 0) return (float) (1 + distance);
        float tail = (float) (1 - distance / profile.trailLength());
        return tail * tail;
    }

    static int glyphIndex(CodeRainProfile profile, int count, int column, int row, double phase) {
        int tick = (int) ((long) (phase + noise(profile.seed(), column, row, 2)));
        return (int) (count * noise(profile.seed(), column, row, tick ^ 0x97531));
    }

    static float noise(int seed, int column, int row, int tick) {
        int hash = seed ^ (column * 0x9E3779B9) ^ (row * 0x85EBCA6B) ^ (tick * 0xC2B2AE35);
        hash = (hash ^ (hash >>> 16)) * 0x7FEB352D;
        hash = (hash ^ (hash >>> 15)) * 0x846CA68B;
        return (hash >>> 8) * 0x1.0p-24f;
    }

    record Grid(int columns, int rows, float cellSize) {
        static Grid fit(int width, int height, int minimumSize) {
            // 大视口自动增大单元，避免粒度设置导致每帧绘制无界增长。
            double size = Math.max(minimumSize, Math.max(Math.max(width, height) / 128.0,
                    Math.sqrt((double) width * height / 3600)));
            return new Grid(Math.max(1, (int) (width / size)), Math.max(1, (int) (height / size)),
                    (float) Math.min(size, Math.min(width, height)));
        }
    }

}
