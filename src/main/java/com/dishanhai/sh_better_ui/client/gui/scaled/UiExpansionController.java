package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/** 山海：局部扩展的几何与状态机。时间采用单调毫秒，所有坐标采用 GUI 单位。 */
public final class UiExpansionController {
    private static final float MENU_BLUR_RADIUS = 10f;
    private static final Morph LINEAR = new Morph() {
        @Override
        public Bounds bounds(Bounds source, Bounds target, float progress) {
            return interpolate(source, target, progress);
        }

        @Override
        public GuiRect outline(Bounds source, Bounds target, float progress) {
            return new GuiRect(GuiMotion.lerp(source.x(), target.x(), progress),
                    GuiMotion.lerp(source.y(), target.y(), progress),
                    GuiMotion.lerp(source.width(), target.width(), progress),
                    GuiMotion.lerp(source.height(), target.height(), progress));
        }
    };
    private static final Morph CENTERED = new Morph() {
        @Override
        public Bounds bounds(Bounds source, Bounds target, float progress) {
            return rounded(outline(source, target, progress));
        }

        @Override
        public GuiRect outline(Bounds source, Bounds target, float progress) {
            float seed = Math.min(12f, Math.min(target.width(), target.height()));
            float width = GuiMotion.lerp(seed, target.width(), progress);
            float height = GuiMotion.lerp(seed, target.height(), progress);
            return new GuiRect(target.x() + (target.width() - width) * 0.5f,
                    target.y() + (target.height() - height) * 0.5f, width, height);
        }
    };

    public enum State { IDLE, ACTIVATING, EXPANDING, ACTIVE, CONFIRMING, CANCELLING, COLLAPSING, INTERRUPTED }

    public record Phase(int durationMs, DoubleUnaryOperator easing) {
        public Phase {
            if (durationMs < 0) throw new IllegalArgumentException("Negative phase duration");
            Objects.requireNonNull(easing);
        }

        float sample(long elapsed, int duration) {
            if (elapsed >= duration) return 1f;
            if (elapsed <= 0) return 0f;
            double result = easing.applyAsDouble((double) elapsed / duration);
            return Double.isFinite(result) ? (float) UiMath.clamp(result, 0d, 1d) : (float) elapsed / duration;
        }
    }

    @FunctionalInterface
    public interface Placement {
        Bounds place(Bounds source, Bounds viewport);
    }

    @FunctionalInterface
    public interface Morph {
        /** 扩展与收缩共用同一条路径。输出须位于源和目标的外接矩形内。 */
        Bounds bounds(Bounds source, Bounds target, float progress);

        /** 可覆盖为浮点轨迹。整数自定义布局仍沿用原有的 bounds 回调。 */
        default GuiRect outline(Bounds source, Bounds target, float progress) {
            return rectangle(bounds(source, target, progress));
        }
    }

    public record Config(Phase activation, Phase expansion, Phase confirmation, Phase cancellation,
                         Phase collapse, Morph morph, float radius) {
        public static final Config DEFAULT = new Config(
                new Phase(100, t -> 1 - (1 - t) * (1 - t)),
                new Phase(220, UiExpansionController::smoothstep),
                new Phase(160, t -> 1 - (1 - t) * (1 - t)),
                new Phase(160, t -> 1 - (1 - t) * (1 - t)),
                new Phase(220, UiExpansionController::smoothstep),
                LINEAR, Float.MAX_VALUE);

        /** 以最终菜单中心为锚点展开与收起，液态玻璃主题使用专用的材质过渡。 */
        public static final Config CENTERED_MENU = new Config(
                new Phase(0, t -> t), new Phase(240, UiExpansionController::easeOut),
                new Phase(0, t -> t), new Phase(0, t -> t),
                new Phase(140, UiExpansionController::easeOut), CENTERED, 10f);

        public Config {
            Objects.requireNonNull(activation);
            Objects.requireNonNull(expansion);
            Objects.requireNonNull(confirmation);
            Objects.requireNonNull(cancellation);
            Objects.requireNonNull(collapse);
            Objects.requireNonNull(morph);
            if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid radius");
        }
    }

    private final Bounds source;
    private final Bounds target;
    private final Bounds envelope;
    private final GuiRect sourceOutline;
    private final GuiRect targetOutline;
    private final Config config;
    private final boolean reducedMotion;
    private final long openingStarted;
    private final int openingDuration;
    private State state = State.ACTIVATING;
    private long started;
    private int duration;
    private float progress;
    private float collapseFrom;
    private float phaseProgress;
    private GuiRect outline;

    public UiExpansionController(Bounds source, Bounds target, Config config, boolean reducedMotion, long now) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.config = Objects.requireNonNull(config);
        this.reducedMotion = reducedMotion;
        envelope = envelope(source, target);
        sourceOutline = centeredMenu() ? CENTERED.outline(source, target, 0f) : rectangle(source);
        targetOutline = rectangle(target);
        outline = sourceOutline;
        openingStarted = now;
        openingDuration = reducedMotion ? 0 : Math.max(config.activation().durationMs(), config.expansion().durationMs());
        enter(State.ACTIVATING, now, config.activation());
    }

    public State state() { return state; }
    public Bounds frame() {
        if (progress == 0f && !centeredMenu()) return source;
        if (progress == 1f) return target;
        return rounded(outline);
    }
    public GuiRect outline() { return outline; }
    public Bounds target() { return target; }
    public Bounds envelope() { return envelope; }
    public float progress() { return progress; }
    public float phaseProgress() { return phaseProgress; }

    boolean centeredMenu() { return config.morph() == CENTERED; }

    float radius() {
        if (!centeredMenu() || progress == 1f) return config.radius();
        float round = Math.min(outline.getW(), outline.getH()) * 0.5f;
        return GuiMotion.lerp(round, Math.min(round, config.radius()), progress);
    }

    static float menuContentOpacity(float progress) {
        return (float) smoothstep(GuiMotion.clamp01((progress - 0.58f) / 0.38f));
    }

    static float menuBloom(float progress, UiPreferences preferences) {
        if (preferences.highContrast() || preferences.reducedMotion()) return 0f;
        float p = GuiMotion.clamp01(progress);
        return 4f * p * (1f - p);
    }

    static float menuBlurRadius(float bloom) {
        return MENU_BLUR_RADIUS + 8f * bloom;
    }

    public Bounds advance(long now) {
        while (true) {
            Phase phase = switch (state) {
                case ACTIVATING -> config.activation();
                case EXPANDING -> config.expansion();
                case CONFIRMING -> config.confirmation();
                case CANCELLING -> config.cancellation();
                case COLLAPSING -> config.collapse();
                default -> null;
            };
            if (phase == null) return frame();
            long elapsed = Math.max(0, now - started);
            phaseProgress = phase.sample(elapsed, duration);
            if (state == State.ACTIVATING) {
                setProgress(config.expansion().sample(Math.max(0, now - openingStarted), openingDuration));
            }
            if (state == State.EXPANDING) setProgress(phaseProgress);
            if (state == State.COLLAPSING) setProgress(collapseFrom * (1f - phaseProgress));
            if (elapsed < duration) return frame();
            long end = started + duration;
            switch (state) {
                case ACTIVATING -> {
                    enter(State.EXPANDING, openingStarted, config.expansion());
                    duration = openingDuration;
                }
                case EXPANDING -> state = State.ACTIVE;
                case CONFIRMING, CANCELLING -> {
                    enter(State.COLLAPSING, end, config.collapse());
                    duration = Math.round(duration * (float) Math.sqrt(collapseFrom));
                }
                case COLLAPSING -> state = State.IDLE;
                default -> throw new IllegalStateException();
            }
        }
    }

    public boolean confirm(long now) {
        if (state != State.ACTIVE) return false;
        collapseFrom = 1f;
        enter(State.CONFIRMING, now, config.confirmation());
        return true;
    }

    public boolean cancel(long now) {
        if (state != State.ACTIVATING && state != State.EXPANDING && state != State.ACTIVE) return false;
        collapseFrom = progress;
        boolean fullyExpanded = state == State.ACTIVE;
        enter(State.CANCELLING, now, config.cancellation());
        if (!fullyExpanded) duration = 0;
        return true;
    }

    public void interrupt() {
        if (state == State.IDLE) return;
        state = State.INTERRUPTED;
        setProgress(0f);
    }

    private void enter(State next, long now, Phase phase) {
        state = next;
        started = now;
        // 保留静态成功反馈的阅读时间。
        duration = reducedMotion && next != State.CONFIRMING ? 0 : phase.durationMs();
        phaseProgress = reducedMotion ? 1f : 0f;
    }

    private void setProgress(float next) {
        if (progress == next) return;
        progress = next;
        outline = next == 0f ? sourceOutline : next == 1f ? targetOutline
                : fit(Objects.requireNonNull(config.morph().outline(source, target, next)), envelope);
    }

    /** 0 固定左/上边，0.5 固定中心，1 固定右/下边。空间不足时将结果移回视口。 */
    public static Placement anchored(int width, int height, float anchorX, float anchorY) {
        if (width <= 0 || height <= 0 || !(anchorX >= 0 && anchorX <= 1 && anchorY >= 0 && anchorY <= 1)) {
            throw new IllegalArgumentException("Invalid expansion placement");
        }
        return (source, viewport) -> fit(new Bounds(
                source.x() + Math.round((source.width() - width) * anchorX),
                source.y() + Math.round((source.height() - height) * anchorY), width, height), viewport);
    }

    public static Bounds interpolate(Bounds from, Bounds to, float progress) {
        return rounded(LINEAR.outline(from, to, progress));
    }

    private static double smoothstep(double t) { return t * t * (3 - 2 * t); }

    private static double easeOut(double t) {
        double remaining = 1 - t;
        return 1 - remaining * remaining * remaining;
    }

    private static Bounds rounded(GuiRect outline) {
        return new Bounds(Math.round(outline.getX()), Math.round(outline.getY()),
                Math.round(outline.getW()), Math.round(outline.getH()));
    }

    private static GuiRect rectangle(Bounds bounds) {
        return new GuiRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static GuiRect fit(GuiRect outline, Bounds limit) {
        if (!Float.isFinite(outline.getX()) || !Float.isFinite(outline.getY())
                || !Float.isFinite(outline.getW()) || !Float.isFinite(outline.getH())) {
            throw new IllegalArgumentException("Non-finite expansion outline");
        }
        float width = UiMath.clamp(outline.getW(), 0, limit.width());
        float height = UiMath.clamp(outline.getH(), 0, limit.height());
        float x = UiMath.clamp(outline.getX(), limit.x(), limit.right() - width);
        float y = UiMath.clamp(outline.getY(), limit.y(), limit.bottom() - height);
        if (x == outline.getX() && y == outline.getY() && width == outline.getW() && height == outline.getH()) {
            return outline;
        }
        return new GuiRect(x, y, width, height);
    }

    static Bounds fit(Bounds bounds, Bounds limit) {
        int width = Math.min(bounds.width(), limit.width());
        int height = Math.min(bounds.height(), limit.height());
        return new Bounds(UiMath.clamp(bounds.x(), limit.x(), limit.right() - width),
                UiMath.clamp(bounds.y(), limit.y(), limit.bottom() - height), width, height);
    }

    static boolean contains(GuiRect outline, float radius, double x, double y) {
        double right = (double) outline.getX() + outline.getW();
        double bottom = (double) outline.getY() + outline.getH();
        if (!(x >= outline.getX() && x < right && y >= outline.getY() && y < bottom)) return false;
        double r = RoundedCorners.fit(outline.getW(), outline.getH(), radius);
        double dx = x - UiMath.clamp(x, outline.getX() + r, right - r);
        double dy = y - UiMath.clamp(y, outline.getY() + r, bottom - r);
        return dx * dx + dy * dy <= r * r;
    }

    static boolean overlaps(Bounds a, Bounds b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static Bounds envelope(Bounds a, Bounds b) {
        int x = Math.min(a.x(), b.x()), y = Math.min(a.y(), b.y());
        return new Bounds(x, y, Math.max(a.right(), b.right()) - x, Math.max(a.bottom(), b.bottom()) - y);
    }
}
