package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 山海：点阵背景的配色、网格、动画与指针反馈设置。尺寸均使用 GUI 单位。 */
public record DotMatrixProfile(Palette palette, Grid grid, Motion motion, Interaction interaction,
                               Fade fade, Gradient gradient) {
    public static final DotMatrixProfile VIOLET = new DotMatrixProfile(
            new Palette(0xFF050208, 0xB06D249A, 0xF0D267FA, 0.7f),
            new Grid(8, 3, 0.36f, 27L),
            new Motion(Effect.TWINKLE, 2800, 0.09f, 36, 9f),
            new Interaction(38, 96));

    public DotMatrixProfile {
        Objects.requireNonNull(palette);
        Objects.requireNonNull(grid);
        Objects.requireNonNull(motion);
        Objects.requireNonNull(interaction);
        Objects.requireNonNull(fade);
        Objects.requireNonNull(gradient);
    }

    public DotMatrixProfile(Palette palette, Grid grid, Motion motion, Interaction interaction) {
        this(palette, grid, motion, interaction, Fade.NONE, Gradient.NONE);
    }

    public DotMatrixProfile withPalette(Palette value) {
        return new DotMatrixProfile(value, grid, motion, interaction, fade, gradient);
    }

    DotMatrixProfile withTheme(UiTheme theme) {
        return withPalette(new Palette(theme.background(palette.backgroundArgb()), theme.text(palette.dotArgb()),
                theme.text(palette.highlightArgb()), palette.glow()));
    }

    public DotMatrixProfile withGrid(Grid value) {
        return new DotMatrixProfile(palette, value, motion, interaction, fade, gradient);
    }

    public DotMatrixProfile withMotion(Motion value) {
        return new DotMatrixProfile(palette, grid, value, interaction, fade, gradient);
    }

    public DotMatrixProfile withInteraction(Interaction value) {
        return new DotMatrixProfile(palette, grid, motion, value, fade, gradient);
    }

    public DotMatrixProfile withPointerWave(PointerWave value) {
        return withInteraction(new Interaction(interaction.hoverRadius(), interaction.rippleRadius(), value,
                interaction.pointerLens()));
    }

    public DotMatrixProfile withPointerLens(PointerLens value) {
        return withInteraction(new Interaction(interaction.hoverRadius(), interaction.rippleRadius(),
                interaction.pointerWave(), value));
    }

    public DotMatrixProfile withFade(Fade value) {
        return new DotMatrixProfile(palette, grid, motion, interaction, value, gradient);
    }

    public DotMatrixProfile withGradient(Gradient value) {
        return new DotMatrixProfile(palette, grid, motion, interaction, fade, value);
    }

    /** 颜色为 ARGB；底色可透明。glow 为光晕强度，取值 0..1。 */
    public record Palette(int backgroundArgb, int dotArgb, int highlightArgb, float glow) {
        public Palette {
            if (!(glow >= 0f && glow <= 1f)) throw new IllegalArgumentException();
        }
    }

    /** spacing 和 dotSize 为正整数，且 dotSize 不超过 spacing。density 取值 0..1。 */
    public record Grid(int spacing, int dotSize, float density, long seed) {
        public Grid {
            if (dotSize <= 0 || dotSize > spacing || !(density >= 0f && density <= 1f)) {
                throw new IllegalArgumentException();
            }
        }
    }

    /** 每轮为每个点抽签一次，再从中选出至多 maxFloatingDots 个点。浮动参数为零时关闭位移。 */
    public record Motion(Effect effect, int periodMs, float floatProbability, int maxFloatingDots,
                         float floatDistance) {
        public Motion {
            Objects.requireNonNull(effect);
            if (periodMs <= 0 || !(floatProbability >= 0f && floatProbability <= 1f) || maxFloatingDots < 0
                    || !Float.isFinite(floatDistance) || floatDistance < 0f) {
                throw new IllegalArgumentException();
            }
        }
    }

    /** 各半径为非负整数。设为零可分别关闭悬停高亮和点击波纹。 */
    public record Interaction(int hoverRadius, int rippleRadius, PointerWave pointerWave, PointerLens pointerLens) {
        public Interaction {
            if (hoverRadius < 0 || rippleRadius < 0) throw new IllegalArgumentException();
            Objects.requireNonNull(pointerWave);
            Objects.requireNonNull(pointerLens);
        }

        public Interaction(int hoverRadius, int rippleRadius, PointerWave pointerWave) {
            this(hoverRadius, rippleRadius, pointerWave, PointerLens.NONE);
        }

        public Interaction(int hoverRadius, int rippleRadius) {
            this(hoverRadius, rippleRadius, PointerWave.NONE);
        }
    }

    /**
     * 光标透镜。半径使用 GUI 单位，radius 为零关闭透镜，glowRadius 为零关闭柔光。
     * 鼓胀强度 -1..1，负值向内收缩。
     * twistDegrees 为扭转角度（-360..360），glowIntensity 取 0..2。
     */
    public record PointerLens(int radius, float bulgeStrength, float twistDegrees, int glowRadius,
                              int glowArgb, float glowIntensity, boolean holdToZoom) {
        public static final PointerLens NONE = new PointerLens(0, 0f, 0f, 0, 0, 0f, false);
        public static final PointerLens DEFAULT = new PointerLens(96, 0.6f, 0f, 64, 0xFFFFFFFF, 0.8f, true);

        public PointerLens {
            if (radius < 0 || glowRadius < 0 || !(bulgeStrength >= -1f && bulgeStrength <= 1f)
                    || !(twistDegrees >= -360f && twistDegrees <= 360f)
                    || !(glowIntensity >= 0f && glowIntensity <= 2f)) throw new IllegalArgumentException();
        }
    }

    /** 以光标为圆心的点阵波。半径为零时关闭，抬升为零时仅高亮。 */
    public record PointerWave(int radius, float lift) {
        public static final PointerWave NONE = new PointerWave(0, 0f);
        public static final PointerWave DEFAULT = new PointerWave(18, 3.5f);

        public PointerWave {
            if (radius < 0 || !Float.isFinite(lift) || lift < 0f) throw new IllegalArgumentException();
        }
    }

    /** 每个点独立抽签，入选后淡出、短暂消失并在同一格重新浮现。概率取值 0..1，周期为正毫秒数。 */
    public record Fade(float probability, int periodMs) {
        public static final Fade NONE = new Fade(0f, 1);

        public Fade {
            if (!(probability >= 0f && probability <= 1f) || periodMs <= 0) {
                throw new IllegalArgumentException();
            }
        }
    }

    /** ARGB 色标在循环中的位置，取值 [0, 1)。 */
    public record ColorStop(float position, int argb) {
        public ColorStop {
            if (!(position >= 0f && position < 1f)) throw new IllegalArgumentException();
        }
    }

    /** 首个色标必须在 0，其余严格递增。末色渐变回首色。色标透明度乘以原点色透明度。空色板关闭渐变。 */
    public record Gradient(List<ColorStop> stops, int periodMs, Flow flow) {
        public static final Gradient NONE = new Gradient(List.of(), 1, Flow.RIGHT);
        public static final Gradient RAINBOW = new Gradient(List.of(
                new ColorStop(0f, 0xFFFF476B), new ColorStop(1f / 7f, 0xFFFF9C42),
                new ColorStop(2f / 7f, 0xFFFFE773), new ColorStop(3f / 7f, 0xFF57E58D),
                new ColorStop(4f / 7f, 0xFF41D9F5), new ColorStop(5f / 7f, 0xFF627BFF),
                new ColorStop(6f / 7f, 0xFFC567FF)), 5000, Flow.RIGHT);
        public static final Gradient REVERSE_RAINBOW = reverseRainbow();
        public static final Gradient AURORA = new Gradient(List.of(
                new ColorStop(0f, 0xFF65E3C4), new ColorStop(0.24f, 0xFF4896F3),
                new ColorStop(0.52f, 0xFF9C6DF5), new ColorStop(0.76f, 0xFFF695CF)), 5000, Flow.RIGHT);

        public Gradient {
            stops = List.copyOf(stops);
            Objects.requireNonNull(flow);
            if (periodMs <= 0 || (!stops.isEmpty() && stops.get(0).position() != 0f)) {
                throw new IllegalArgumentException();
            }
            for (int i = 1; i < stops.size(); i++) {
                if (stops.get(i - 1).position() >= stops.get(i).position()) throw new IllegalArgumentException();
            }
        }

        private static Gradient reverseRainbow() {
            var rainbow = RAINBOW.stops();
            var reversed = new ArrayList<ColorStop>(rainbow.size());
            reversed.add(rainbow.get(0));
            for (int i = rainbow.size() - 1; i > 0; i--) {
                var stop = rainbow.get(i);
                reversed.add(new ColorStop(1f - stop.position(), stop.argb()));
            }
            return new Gradient(reversed, RAINBOW.periodMs(), RAINBOW.flow());
        }

        int sample(float x, float y, float phase) {
            float value = flow.position(x, y, phase);
            double position = Float.isFinite(value) ? value - Math.floor(value) : 0d;
            int low = 0, high = stops.size();
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (position < stops.get(middle).position()) high = middle;
                else low = middle;
            }
            ColorStop from = stops.get(low);
            boolean wrap = high == stops.size();
            ColorStop to = wrap ? stops.get(0) : stops.get(high);
            float end = wrap ? 1f : to.position();
            return GuiMotion.lerpColor(from.argb(), to.argb(), (float) ((position - from.position())
                    / (end - from.position())));
        }
    }

    @FunctionalInterface
    public interface Flow {
        Flow RIGHT = (x, y, phase) -> x - phase;
        Flow DOWN = (x, y, phase) -> y - phase;
        Flow DIAGONAL = (x, y, phase) -> (x + y) * 0.5f - phase;
        Flow OUTWARD = (x, y, phase) -> {
            float dx = x - 0.5f, dy = y - 0.5f;
            return (float) Math.sqrt(2f * (dx * dx + dy * dy)) - phase;
        };

        /** 以网格锚点和周期进度采样，三者归一化。返回色标位置，按 1 循环，非有限结果取首色。 */
        float position(float x, float y, float phase);
    }

    @FunctionalInterface
    public interface Effect {
        Effect STEADY = (x, y, phase, variation) -> 0f;
        Effect TWINKLE = (x, y, phase, variation) -> {
            float light = crest(phase + variation);
            return 0.44f * light * light;
        };
        Effect WAVE = (x, y, phase, variation) -> 0.63f * crest(phase - x - y * 0.65f);

        /** 在渲染线程采样。坐标和周期进度均为 0..1，variation 是该点固定的随机值。返回额外的高亮强度 0..1。 */
        float brightness(float x, float y, float phase, float variation);

        private static float crest(float phase) {
            return 0.5f + 0.5f * (float) Math.cos(2d * Math.PI * phase);
        }
    }
}
