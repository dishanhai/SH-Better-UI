// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Sample renderers that emit native GUI vertices and glyphs on both 26.x backends. */
public final class GalleryFieldOptics {
    private static final float[][] BOLT = {{-.02f, -.47f, .24f, -.47f, .08f, -.14f, .39f, -.14f,
            -.21f, .48f, -.07f, .08f, -.34f, .08f}};
    private static final float[][] STAR = {star()};
    private static final float[][] CREEPER = {
            {-.47f, -.47f, .47f, -.47f, .47f, .47f, -.47f, .47f},
            {-.33f, -.24f, -.12f, -.24f, -.12f, -.04f, -.33f, -.04f},
            {.12f, -.24f, .33f, -.24f, .33f, -.04f, .12f, -.04f},
            {-.11f, -.03f, .11f, -.03f, .11f, .09f, .22f, .09f, .22f, .35f, .1f, .35f,
                    .1f, .23f, -.1f, .23f, -.1f, .35f, -.22f, .35f, -.22f, .09f, -.11f, .09f}
    };

    private final LightRaysMotion rays = new LightRaysMotion();

    public void restartRays() { rays.phase = rays.opening = 0; }
    public void clearPointer() { rays.clearInteraction(); }
    public void pulseRays() { rays.pulse(); }

    public static void drawWaves(GuiGraphicsExtractor graphics, Font font, Bounds area, PatternWavesProfile profile,
                                 double seconds, UiPointerTracker.Frame pointer, float pulseX, float pulseY,
                                 double pulseSeconds, boolean reduced, boolean highContrast) {
        var mesh = new GalleryFieldMesh();
        float step = profile.spacing();
        float centerX = area.width() * .5f, centerY = area.height() * .5f;
        double direction = Math.toRadians(profile.direction());
        double headingX = Math.cos(direction), headingY = Math.sin(direction);
        double coordinateScale = 140 * profile.scale();
        double time = seconds * profile.speed();
        float rise = profile.intro() && !reduced ? smooth(seconds / 1.1) : 1;
        String[] glyphs = profile.pattern() == PatternWavesProfile.Pattern.GLYPH
                ? profile.characters().codePoints().mapToObj(Character::toString).toArray(String[]::new)
                : new String[0];
        int ink = highContrast ? 0xFFFFFFFF : profile.color();
        int columns = Math.max(1, (int) Math.ceil(area.width() / step));
        int rows = Math.max(1, (int) Math.ceil(area.height() / step));
        for (int row = 0; row < rows; row++) {
            float y = centerY + (row - (rows - 1) * .5f) * step;
            float lastX = 0, lastY = 0;
            int lastColor = 0;
            for (int column = 0; column < columns; column++) {
                float x = centerX + (column - (columns - 1) * .5f) * step;
                double dx = (x - centerX) / coordinateScale, dy = (y - centerY) / coordinateScale;
                double a = dx * headingX + dy * headingY;
                double b = -dx * headingY + dy * headingX;
                double h = wave(profile.wave(), a, b, time) * rise;
                double slope = (wave(profile.wave(), a + .06, b, time) * rise - h) * profile.depth() * 5;
                if (pointer != null && pointer.inside() && profile.interactive()) {
                    double range = Math.hypot(x - pointer.x(), y - pointer.y()) / profile.cursorSize();
                    h -= Math.exp(-range * range * 1.8) * profile.cursorStrength();
                }
                if (pulseSeconds >= 0 && pulseSeconds < 1) {
                    double front = Math.hypot(x - pulseX, y - pulseY) / profile.cursorSize() - 2.4 * pulseSeconds;
                    h -= Math.cos(front * 7) * (1 - smooth(Math.abs(front) / 1.1)) * (1 - pulseSeconds)
                            * profile.cursorStrength();
                }
                double tone = Math.clamp(.6 + (.1 - slope * .28) * profile.contrast(), .12, 1);
                double level = tone * tone;
                float displayY = y + Math.clamp((float) (-h * profile.depth() * .37), -.46f, .46f) * step;
                float edge = Math.max(.05f, fade(profile, x / area.width(), y / area.height()));
                float alpha = edge * profile.opacity() * rise * (float) (.18 + .82 * level);
                int highlight = GuiMotion.lerpColor(ink, 0xFFFFFFFF, (float) Math.clamp(
                        profile.shine() * Math.pow(level, 5) * .4, 0, 1));
                int color = GalleryFieldMesh.alpha(highlight, alpha);
                float half = step * profile.markSize() * (float) (.12 + .88 * Math.sqrt(level)) * .5f;
                switch (profile.pattern()) {
                    case DOT -> mesh.disc(x, displayY, half, color, GalleryFieldMesh.alpha(color, .4f));
                    case SQUARE -> mesh.rectangle(x, displayY, half, half, color);
                    case PLUS -> {
                        float stroke = Math.min(half, Math.max(.5f, step * .055f));
                        mesh.rectangle(x, displayY, half, stroke, color);
                        mesh.rectangle(x, displayY, stroke, half, color);
                    }
                    case LINE -> {
                        if (column > 0) mesh.line(lastX, lastY, x, displayY, Math.max(1, half * 1.15f), lastColor, color);
                        lastX = x;
                        lastY = displayY;
                        lastColor = color;
                    }
                    case GLYPH -> {
                        String glyph = glyphs[Math.min(glyphs.length - 1, (int) (level * glyphs.length))];
                        if ((color >>> 24) >= 4) graphics.text(font, glyph, area.x() + Math.round(x - font.width(glyph) * .5f),
                                area.y() + Math.round(displayY - font.lineHeight * .5f), color, false);
                    }
                }
            }
        }
        mesh.submit(graphics, area);
    }

    private static double wave(PatternWavesProfile.Wave shape, double x, double y, double time) {
        return switch (shape) {
            case SWELL -> {
                double crest = 3 * x + .7 * Math.sin(y * .95 + time) - time * 2;
                yield (Math.cos(crest) + .25 * Math.cos(2 * crest - .5)) * .78;
            }
            case RIPPLE -> {
                double distance = Math.hypot(x + 3.4, y + .4);
                yield Math.cos(distance * 2.7 - time * 2) / (1 + .12 * distance);
            }
            case SILK -> {
                double drift = 1.45 * Math.sin(y * .9 + time) + .55 * Math.sin(y * 1.8 - x * .72 - time);
                double fold = x * 2.7 + drift - time;
                yield (Math.sin(fold) + .28 * Math.cos(2 * fold)) * (.7 + .24 * Math.cos(y + x * .4 - time));
            }
        };
    }

    private static float fade(PatternWavesProfile profile, float x, float y) {
        double distance = Math.hypot(2 * x - 1, 2 * y - 1);
        float size = profile.fadeSize();
        return switch (profile.fade()) {
            case NONE -> 1;
            case EDGES -> 1 - smooth((distance - (1 - size)) / (size + .05));
            case CENTER -> .05f + .95f * smooth(distance / size);
            case BOTTOM -> 1 - smooth((y - (1 - size)) / size);
            case TOP -> smooth(y / size);
        };
    }

    public void drawRays(GuiGraphicsExtractor graphics, Bounds area, LightRaysProfile profile,
                         UiPointerTracker.Frame pointer, float dt, boolean reduced, boolean highContrast) {
        if (reduced) {
            rays.clearInteraction();
        } else {
            rays.advance(profile, pointer, area.width(), area.height(), dt);
        }
        LightRaysProfile.Origin origin = profile.raysOrigin();
        float x = origin.x * area.width(), y = origin.y * area.height();
        float span = (float) Math.hypot(area.width(), area.height());
        double baseAngle = Math.atan2(origin.directionY, origin.directionX) + Math.toRadians(profile.tilt());
        if (!reduced && rays.aim > .001f) {
            double target = Math.atan2(rays.pointerY * area.height() - y, rays.pointerX * area.width() - x);
            baseAngle += Math.IEEEremainder(target - baseAngle, Math.PI * 2) * rays.aim * profile.mouseInfluence();
        }
        float open = profile.intro() && !reduced ? smooth(rays.opening * 2) : 1;
        float phase = reduced ? 0 : rays.phase;
        var mesh = new GalleryFieldMesh();
        int color = highContrast ? 0xFFFFFFFF : profile.raysColor();
        float radius = span * Math.min(1.5f, profile.rayLength()) * (.3f + .7f * open);
        int shafts = Math.clamp(Math.round(31 * profile.rayDensity()), 8, 85);
        double cone = Math.min(2.9, profile.lightSpread() * 2.5);
        for (int index = 0; index < shafts; index++) {
            double angle = baseAngle + (index / (double) (shafts - 1) - .5) * cone * 2;
            double crest = .52 + .26 * Math.cos(index * 2.41 + phase * 1.25)
                    + .22 * Math.cos(index * 4.17 - phase * .9 + .8);
            double spread = Math.pow(Math.max(0, Math.cos(angle - baseAngle)), 1.0 / profile.lightSpread());
            double width = cone / shafts * (.55 + profile.raySharpness() * .42);
            double shadow = 1;
            if (!reduced && rays.shadow > .001f) {
                double shadeAngle = Math.atan2(rays.pointerY * area.height() - y, rays.pointerX * area.width() - x);
                double offset = Math.IEEEremainder(angle - shadeAngle, Math.PI * 2) / .15;
                shadow -= .65 * rays.shadow * Math.exp(-offset * offset);
            }
            double energy = (crest + profile.glow() * .15) * spread * shadow * profile.intensity() * open;
            for (int band = 0; band < 5; band++) {
                float start = radius * band / 5, end = radius * (band + 1) / 5;
                int inner = rayColor(color, energy, start / span, profile, reduced);
                int outer = rayColor(color, energy, end / span, profile, reduced);
                double a = angle - width, b = angle + width;
                mesh.quad(x + (float) Math.cos(a) * start, y + (float) Math.sin(a) * start,
                        x + (float) Math.cos(b) * start, y + (float) Math.sin(b) * start,
                        x + (float) Math.cos(b) * end, y + (float) Math.sin(b) * end,
                        x + (float) Math.cos(a) * end, y + (float) Math.sin(a) * end,
                        inner, inner, outer, outer);
            }
        }
        mesh.submit(graphics, area);
    }

    private int rayColor(int tint, double energy, float depth, LightRaysProfile profile, boolean reduced) {
        double waves = 0;
        if (!reduced) {
            for (float age : rays.pulseAges) {
                if (age < 0) continue;
                double band = (depth - age * 1.1) / (.09 + age * .16);
                waves += Math.exp(-band * band) * smooth(age / .16) * (1 - smooth((age - .45) / 1.55));
            }
        }
        double attenuation = (1 - smooth(depth / profile.rayLength())) * (1 - .5 * smooth(depth / profile.fadeDistance()));
        double level = 1 - Math.exp(-energy * attenuation * (1 + waves));
        int color = profile.lightMode() ? GuiMotion.lerpColor(0xFF223D78, tint, .45f)
                : GuiMotion.lerpColor(tint, 0xFF416DC7, profile.atmosphere() * smooth(depth));
        return GalleryFieldMesh.alpha(color, level * profile.opacity() * (profile.lightMode() ? .7 : 1));
    }

    public static void drawLogo(GuiGraphicsExtractor graphics, Bounds area, ElectricLogoProfile profile, int image,
                                double seconds, float charge, double pulseSeconds, boolean reduced, boolean highContrast) {
        float scale = Math.min(area.width(), area.height()) * profile.lines().scale();
        float x = area.width() * .5f, y = area.height() * .5f;
        float power = 1 + charge * profile.interaction().cursorIntensity();
        var mesh = new GalleryFieldMesh();
        int core = highContrast ? 0xFFFFFFFF : profile.palette().coreArgb();
        int glow = profile.palette().glowArgb();
        float line = profile.lines().thickness();
        double phase = seconds * profile.motion().speed();
        boolean animated = !reduced && profile.motion().speed() > 0;
        float[][] outlines = switch (image) { case 1 -> STAR; case 2 -> CREEPER; default -> BOLT; };
        for (float[] points : outlines) {
            int pointCount = points.length / 2;
            for (int edge = 0; edge < pointCount; edge++) {
                int next = (edge + 1) % pointCount;
                float ax = x + points[edge * 2] * scale, ay = y + points[edge * 2 + 1] * scale;
                float bx = x + points[next * 2] * scale, by = y + points[next * 2 + 1] * scale;
                for (int strand = 0; strand < (animated ? profile.lines().strands() : 1); strand++) {
                    float lastX = ax, lastY = ay;
                    int parts = Math.clamp((int) (Math.hypot(bx - ax, by - ay) / 5), 2, 14);
                    for (int segment = 1; segment <= parts; segment++) {
                        float t = segment / (float) parts;
                        float offset = 0;
                        if (animated) {
                            double state = edge * 1.19 + segment * 2.89 + strand * 5.33;
                            offset = (float) (Math.sin(t * Math.PI) * power
                                    * (Math.sin(state + phase * 2.8) * profile.motion().bend()
                                    + Math.sin(state * 2.4 - phase * 5.2) * profile.motion().crackle() * .5));
                        }
                        float nx = GuiMotion.lerp(ax, bx, t) + offset;
                        float ny = GuiMotion.lerp(ay, by, t) - offset * .7f;
                        double perimeter = (edge + t) / pointCount;
                        double front = pulseSeconds >= 0 && pulseSeconds < .8
                                ? 1 - smooth(Math.abs(perimeter - pulseSeconds / .8) * 8) : 0;
                        double flicker = animated ? 1 - .35 * profile.motion().flicker()
                                * (1 + Math.sin(phase * 3 + perimeter * 20)) : 1;
                        double energy = (flicker * .52 + front * .45) * profile.palette().intensity() * power
                                / (strand == 0 ? 1 : 2.6);
                        int halo = GalleryFieldMesh.alpha(glow, energy * profile.palette().glow() * .27);
                        if (!highContrast) mesh.line(lastX, lastY, nx, ny, line * 4, halo, halo);
                        int color = GalleryFieldMesh.alpha(core, energy);
                        mesh.line(lastX, lastY, nx, ny, line * (strand == 0 ? 1 : .65f), color, color);
                        lastX = nx;
                        lastY = ny;
                    }
                }
            }
        }
        mesh.submit(graphics, area);
    }

    private static float[] star() {
        float[] points = new float[20];
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5 - Math.PI / 2;
            double radius = i % 2 == 0 ? .49 : .21;
            points[i * 2] = (float) (radius * Math.cos(angle));
            points[i * 2 + 1] = (float) (radius * Math.sin(angle));
        }
        return points;
    }

    public static void drawLaser(GuiGraphicsExtractor graphics, Bounds area, LaserFlowProfile profile,
                                 double seconds, UiPointerTracker.Frame pointer, double pulseSeconds,
                                 boolean reduced, boolean highContrast) {
        var mesh = new GalleryFieldMesh();
        float x = area.width() * profile.beamPosition();
        float y = area.height() * profile.surfaceLevel();
        float unit = Math.max(.28f, Math.min(area.width(), area.height()) / 360f);
        float progress = profile.intro() && !reduced ? smooth(seconds / .7) : 1;
        float phase = (float) (seconds * profile.speed());
        double breathing = 1 + (reduced ? 0 : .18 * Math.sin(phase * 2.3) * (1 - Math.exp(-profile.pulse() * .5)));
        int beam = highContrast ? 0xFFFFFFFF : profile.color();
        float width = Math.max(.8f, profile.beamWidth() * 1.5f * unit);
        float frontY = y * progress;
        float reach = profile.spread() * 58 * unit * progress;
        double pointerGain = 0;
        if (pointer != null && pointer.inside() && profile.mouseInteraction()) {
            double distance = (pointer.x() - x) / (62 * unit);
            pointerGain = .75 * Math.exp(-distance * distance);
        }
        double energy = (.38 + profile.intensity() * .22) * breathing * (1 + pointerGain);
        if (pulseSeconds >= 0 && pulseSeconds < .55) energy *= 1 + .7 * (1 - pulseSeconds / .55);
        for (int lane = 0; lane < 3; lane++) {
            float half = width + (lane == 0 ? 28 : lane == 1 ? 8 : 0) * unit;
            int tint = lane == 2 ? GuiMotion.lerpColor(beam, 0xFFFFFFFF, .8f) : beam;
            int middle = GalleryFieldMesh.alpha(tint, energy * (lane == 0 ? .16 : lane == 1 ? .43 : .95));
            int clear = tint & 0xFFFFFF;
            mesh.quad(x - half, 0, x - half, frontY, x, frontY, x, 0, clear, clear, middle, middle);
            mesh.quad(x, 0, x, frontY, x + half, frontY, x + half, 0, middle, middle, clear, clear);
        }
        if (progress > .6f) {
            float flare = profile.flare() * 19 * unit;
            int color = GalleryFieldMesh.alpha(beam, energy * .8 * progress);
            mesh.disc(x, y, flare, color, beam & 0xFFFFFF);
            int rim = GalleryFieldMesh.alpha(GuiMotion.lerpColor(beam, 0xFFFFFFFF, .45f), energy);
            mesh.line(x - reach, y, x, y, 2 * unit, beam & 0xFFFFFF, rim);
            mesh.line(x, y, x + reach, y, 2 * unit, rim, beam & 0xFFFFFF);
            for (int i = 1; i <= 4; i++) {
                float offset = (i - 2.5f) * unit * 4;
                float length = reach * (.5f + .08f * i);
                int streak = GalleryFieldMesh.alpha(beam, energy * profile.streaks() * .11
                        * (.6 + .4 * Math.cos(i * 2.4 + phase)));
                mesh.line(x - length, y + offset, x, y + offset, unit, beam & 0xFFFFFF, streak);
                mesh.line(x, y + offset, x + length, y + offset, unit, streak, beam & 0xFFFFFF);
            }
        }
        int count = Math.min(84, (int) (profile.dust() * 42));
        for (int i = 0; i < count; i++) {
            double column = GalleryFieldParticles.noise(profile.seed(), i, 0, 0) - .5;
            double row = (GalleryFieldParticles.noise(profile.seed(), i, 1, 0) + phase * .045) % 1;
            float sx = x + (float) column * (80 * unit), sy = (float) row * frontY;
            double sparkle = (.4 + .6 * Math.sin(i * 7.33 + phase * 3)) * (1 - 2 * Math.abs(column));
            int tint = GalleryFieldMesh.alpha(beam, energy * Math.max(0, sparkle) * .55 * progress);
            mesh.rectangle(sx, sy, .5f, .6f + unit, tint);
        }
        mesh.submit(graphics, area);
    }

    private static float smooth(double value) {
        float t = (float) Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
