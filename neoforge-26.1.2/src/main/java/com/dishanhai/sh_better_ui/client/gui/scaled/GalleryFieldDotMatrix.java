// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SplittableRandom;

/** Port of the dot matrix sampler to native GUI extraction. */
public final class GalleryFieldDotMatrix {
    private static final int MAX_GRID_CELLS = 16_384;
    private static final int RGB_MASK = 0x00FFFFFF;

    private final DotMatrixLens lens = new DotMatrixLens();
    private DotMatrixProfile profile = DotMatrixProfile.VIOLET;
    private int layoutWidth = -1;
    private int layoutHeight;
    private Dot[] dots = new Dot[0];
    private boolean[] floating = new boolean[0];
    private int[] selected = new int[0];
    private boolean selectionDirty = true;
    private long selectedCycle;

    public void setProfile(DotMatrixProfile value) {
        if (!profile.grid().equals(value.grid())) layoutWidth = -1;
        if (!profile.motion().equals(value.motion())) selectionDirty = true;
        if (!profile.interaction().pointerLens().equals(value.interaction().pointerLens())) lens.reset();
        profile = value;
    }

    public void clearPointer() { lens.reset(); }

    public void draw(GuiGraphicsExtractor graphics, Bounds area, UiPointerTracker.Frame pointer,
                     long nowMs, boolean reducedMotion, boolean highContrast,
                     float pulseX, float pulseY, double pulseSeconds) {
        int width = area.width(), height = area.height();
        if (width == 0 || height == 0 || profile.grid().density() == 0f) return;
        DotMatrixProfile style = profile;
        DotMatrixProfile.Palette palette = style.palette();
        if (layoutWidth != width || layoutHeight != height) {
            lens.reset();
            rebuildGrid(width, height, style.grid());
        }
        GalleryFieldMesh mesh = new GalleryFieldMesh();
        DotMatrixProfile.Motion motion = style.motion();
        DotMatrixProfile.Interaction interaction = style.interaction();
        DotMatrixProfile.Fade fade = style.fade();
        DotMatrixProfile.Gradient gradient = style.gradient();
        boolean canFloat = !reducedMotion && motion.floatProbability() > 0f && motion.maxFloatingDots() > 0
                && motion.floatDistance() > 0f;
        boolean canFade = !reducedMotion && fade.probability() > 0f;
        boolean hasGradient = !gradient.stops().isEmpty();
        float phase = Math.floorMod(nowMs, motion.periodMs()) / (float) motion.periodMs();
        if (canFloat) selectFloating(Math.floorDiv(nowMs, motion.periodMs()), style.grid().seed(), motion);
        long fadeCycle = canFade ? Math.floorDiv(nowMs, fade.periodMs()) : 0L;
        double fadePhase = canFade ? Math.floorMod(nowMs, fade.periodMs()) / (double) fade.periodMs() : 0d;
        float gradientPhase = hasGradient && !reducedMotion
                ? Math.floorMod(nowMs, gradient.periodMs()) / (float) gradient.periodMs() : 0f;

        boolean pointerInside = pointer != null && pointer.inside();
        float localX = pointerInside ? pointer.x() : 0f, localY = pointerInside ? pointer.y() : 0f;
        DotMatrixProfile.PointerLens lensProfile = interaction.pointerLens();
        DotMatrixLens.Frame lensFrame = lens.advance(lensProfile, pointer, nowMs, reducedMotion);
        boolean hovering = pointerInside && interaction.hoverRadius() > 0;
        DotMatrixProfile.PointerWave pointerWave = interaction.pointerWave();
        int waveRadius = pointerWave.radius();
        double waveBand = UiMath.clamp(style.grid().spacing() * 0.75d, waveRadius * 0.3d, waveRadius * 0.75d);
        boolean followPointer = pointerInside && waveRadius > 0;
        float waveLift = reducedMotion ? 0f : pointerWave.lift();
        double progress = Math.clamp(pulseSeconds / .42, 0, 1);
        float pulseOpacity = pulseSeconds < 0 || reducedMotion ? 0
                : (float) (Math.min(1, pulseSeconds / .04) * (1 - progress));
        float crest = (float) ((1 - (1 - progress) * (1 - progress)) * interaction.rippleRadius());
        float half = style.grid().dotSize() * 0.5f;
        float glowAlpha = palette.glow() * 0.22f;
        float glowHalf = half + Math.max(0.65f, half * 0.55f);
        for (int index = 0; index < dots.length; index++) {
            Dot dot = dots[index];
            float wave = followPointer ? ringLight(dot.x() - localX, dot.y() - localY, waveRadius, waveBand) : 0f;
            float opacity = canFade ? visibility(dot, fadeCycle, fadePhase, fade.probability()) : 1f;
            opacity = Math.max(opacity, wave);
            if (opacity == 0f) continue;
            float lift = canFloat && floating[index] ? lift(phase, dot.variation()) : 0f;
            float drift = lift * motion.floatDistance();
            float x = dot.x() + drift * (dot.variation() - 0.5f) * 0.8f;
            float y = dot.y() - drift - wave * waveLift;
            float size = half;
            float pool = 0f;
            DotMatrixLens.Sample warped = lensFrame.sample(x, y);
            if (warped != null) {
                x = warped.x();
                y = warped.y();
                size *= warped.scale();
                pool = GuiMotion.clamp01(warped.glow());
            }
            float anchorX = Math.min(1f, dot.x() / width), anchorY = Math.min(1f, dot.y() / height);
            float animated = reducedMotion ? 0f : motion.effect().brightness(anchorX, anchorY, phase, dot.variation());
            float light = 0.27f * dot.variation() + lift * 0.32f + 0.85f * wave;
            if (Float.isFinite(animated)) light += GuiMotion.clamp01(animated);
            if (hovering) light += 0.64f * localLight(x - localX, y - localY, interaction.hoverRadius());
            if (pulseOpacity > 0f) light += pulseOpacity * ringLight(x - pulseX, y - pulseY, crest, 10);

            int color = hasGradient ? gradientColor(palette, gradient.sample(anchorX, anchorY, gradientPhase), light)
                    : GuiMotion.lerpColor(palette.dotArgb(), palette.highlightArgb(), light);
            if (pool > 0f) color = GuiMotion.lerpColor(color, lensProfile.glowArgb(), pool * 0.8f);
            if (highContrast) {
                mesh.rectangle(x, y, size, size, GalleryFieldMesh.alpha(0xFFFFFFFF, .72f + dot.variation() * .28f));
                continue;
            }
            if (opacity < 1f) color = GalleryFieldMesh.alpha(color, opacity);
            if (warped == null) {
                mesh.rectangle(x, y, glowHalf, glowHalf, GalleryFieldMesh.alpha(color, glowAlpha));
                mesh.rectangle(x, y, size, size, color);
            } else {
                if (pool > 0f) mesh.disc(x, y, size + 2f, GalleryFieldMesh.alpha(color, pool * .42f), color & RGB_MASK);
                if (glowAlpha > 0f) {
                    mesh.disc(x, y, glowHalf + size - half, GalleryFieldMesh.alpha(color, glowAlpha), color & RGB_MASK);
                }
                mesh.disc(x, y, size, color, GalleryFieldMesh.alpha(color, .5f));
            }
        }
        mesh.submit(graphics, area);
    }

    private void rebuildGrid(int width, int height, DotMatrixProfile.Grid grid) {
        int step = gridStep(width, height, grid.spacing());
        int columns = UiMath.ceilDiv(width, step), rows = UiMath.ceilDiv(height, step);
        ArrayList<Dot> cells = new ArrayList<>();
        SplittableRandom layoutRandom = new SplittableRandom(grid.seed());
        for (int row = 0; row < rows; row++) {
            // 每行独立抽样，窗口改变宽度时，保留重叠区域内的图案。
            SplittableRandom random = layoutRandom.split();
            float y = (row + 0.5f) * step;
            for (int column = 0; column < columns; column++) {
                double occupancy = random.nextDouble();
                float variation = (float) random.nextDouble();
                if (occupancy < grid.density()) {
                    float x = (column + 0.5f) * step;
                    long fadeKey = grid.seed() ^ ((long) row << 32) ^ column;
                    cells.add(new Dot(x, y, variation, fadeKey));
                }
            }
        }
        dots = cells.toArray(Dot[]::new);
        floating = new boolean[dots.length];
        selectionDirty = true;
        layoutWidth = width;
        layoutHeight = height;
    }

    private static int gridStep(int width, int height, int spacing) {
        // 大分辨率保持均匀分布，放宽间距，避免只绘制矩形的一角。
        int min = spacing, max = Math.max(spacing, Math.max(width, height));
        while (min < max) {
            int step = min + (max - min) / 2;
            long cells = (long) UiMath.ceilDiv(width, step) * UiMath.ceilDiv(height, step);
            if (cells > MAX_GRID_CELLS) min = step + 1;
            else max = step;
        }
        return min;
    }

    private void selectFloating(long cycle, long seed, DotMatrixProfile.Motion motion) {
        if (!selectionDirty && selectedCycle == cycle) return;
        int limit = Math.min(motion.maxFloatingDots(), dots.length);
        if (selected.length != limit) selected = new int[limit];
        Arrays.fill(floating, false);
        SplittableRandom random = new SplittableRandom(seed ^ cycle);
        SplittableRandom reservoir = random.split();
        int eligible = 0;
        for (int index = 0; index < dots.length; index++) {
            if (random.nextDouble() >= motion.floatProbability()) continue;
            // 蓄水池抽样：候选超过上限时，屏幕各处的候选有相同机会入选。
            int slot = eligible < limit ? eligible : reservoir.nextInt(eligible + 1);
            if (slot < limit) selected[slot] = index;
            eligible++;
        }
        for (int i = 0; i < Math.min(eligible, limit); i++) floating[selected[i]] = true;
        selectedCycle = cycle;
        selectionDirty = false;
    }

    private static float lift(float phase, float variation) {
        float t = GuiMotion.clamp01((phase - variation * 0.42f) / 0.58f);
        float arch = 4f * t * (1f - t);
        return arch * arch;
    }

    private static float visibility(Dot dot, long cycle, double phase, float probability) {
        double localPhase = phase + dot.variation();
        if (localPhase >= 1d) {
            localPhase -= 1d;
            cycle++;
        }
        // 各点错开相位，且在自己的周期边界恢复不透明，重新抽样时没有跳变。
        float visible = GuiMotion.clamp01((float) ((Math.abs(localPhase - 0.5d) - 0.125d) / 0.25d));
        if (visible == 1f) return 1f;
        long hash = dot.fadeKey() ^ (cycle * 0x9E3779B97F4A7C15L);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        double roll = ((hash ^ (hash >>> 31)) >>> 11) * 0x1.0p-53;
        if (roll >= probability) return 1f;
        return visible * visible * (3f - 2f * visible);
    }

    private static int gradientColor(DotMatrixProfile.Palette palette, int sample, float light) {
        float strength = GuiMotion.clamp01(light);
        int dotAlpha = Math.round(GuiMotion.lerp(palette.dotArgb() >>> 24, palette.highlightArgb() >>> 24, strength));
        int alpha = Math.round(dotAlpha * (sample >>> 24) / 255f);
        int rgb = GuiMotion.lerpColor(sample & RGB_MASK, RGB_MASK, strength * 0.45f);
        return (alpha << 24) | rgb;
    }

    private static float localLight(double dx, double dy, double radius) {
        float strength = (float) Math.max(0d, 1d - (dx * dx + dy * dy) / (radius * radius));
        return strength * strength;
    }

    private static float ringLight(double dx, double dy, double crest, double band) {
        double distanceSquared = dx * dx + dy * dy;
        double outer = crest + band;
        if (distanceSquared >= outer * outer) return 0f;
        float strength = (float) Math.max(0d, 1d - Math.abs(Math.sqrt(distanceSquared) - crest) / band);
        return strength * strength;
    }

    private record Dot(float x, float y, float variation, long fadeKey) {}
}
