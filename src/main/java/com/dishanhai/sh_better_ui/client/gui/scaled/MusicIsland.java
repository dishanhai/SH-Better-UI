package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：稳定的音乐胶囊底板与七列像素波形。播放和输入状态由宿主管理。 */
public final class MusicIsland {
    private static final int BAR_COUNT = 7;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_STEP = 4;
    public static final int WAVEFORM_WIDTH = (BAR_COUNT - 1) * BAR_STEP + BAR_WIDTH;
    private static final int PANEL_RADIUS = 10;
    private static final int PALE = 0xFFCAD8E5;

    private MusicIsland() {}

    /** 进度只随展开操作改变。使用同一轮廓连接紧凑态与控制面板。 */
    public static FluidMorph outline(Bounds compact, Bounds expanded, float progress) {
        return FluidMorph.between(
                new GuiRect(compact.x(), compact.y(), compact.width(), compact.height()),
                new GuiRect(expanded.x(), expanded.y(), expanded.width(), expanded.height()),
                PANEL_RADIUS, progress);
    }

    /** 在前景内容之前绘制底板。音频信号只进入波形。 */
    public static void drawShell(GuiGraphics graphics, FluidMorph outline, UiPreferences preferences, boolean hovered) {
        drawShell(graphics, outline, preferences, hovered, IslandShellStyle.CLASSIC);
    }

    public static void drawShell(GuiGraphics graphics, FluidMorph outline, UiPreferences preferences,
                                 boolean hovered, IslandShellStyle style) {
        var rect = outline.rect();
        var shell = style.shell(outline.radius(), rect.getH(), hovered, preferences.highContrast());
        RoundedCorners.draw(graphics, rect.getX(), rect.getY(), rect.getW(), rect.getH(),
                shell.radius(), shell.border(), shell.edge(), shell.fill(), false);
        // Native translucent highlight, intentionally no claim of optical refraction.
        if (style.glass() && !preferences.highContrast()) {
            int inset = Math.max(8, Math.round(shell.radius()));
            graphics.fill(Math.round(rect.getX()) + inset, Math.round(rect.getY()) + 2,
                    Math.round(rect.getX() + rect.getW()) - inset, Math.round(rect.getY()) + 3,
                    style == IslandShellStyle.LIQUID_GLASS ? 0xAADDFAFF : 0x669FC6DF);
        }
        if (style.accentRail()) {
            int railX = Math.round(rect.getX()) + 3;
            graphics.fill(railX, Math.round(rect.getY()) + 9, railX + 2,
                    Math.round(rect.getY() + rect.getH()) - 9,
                    preferences.highContrast() ? 0xFFFFFFFF : style == IslandShellStyle.MUSIC_FOCUS ? 0xFFD3B5FF : 0xFF72DDD0);
        }
    }

    /**
     * 在指定区域居中绘制波形。列边缘对齐 GUI 像素；上下端各用一行透明像素平滑高度变化。
     * accent 是宿主按曲目过渡后的颜色。高对比及减少动效使用静止的基线。
     */
    public static void drawWaveform(GuiGraphics graphics, Bounds area, MusicRhythm.Frame frame,
                                    int accent, UiPreferences preferences) {
        if (area.width() < WAVEFORM_WIDTH || area.height() < 3) return;
        int startX = area.x() + (area.width() - WAVEFORM_WIDTH) / 2;
        int centerY = area.y() + (area.height() - 1) / 2;
        int amplitude = Math.min((area.height() - 1) / 2, 8);
        boolean contrast = preferences.highContrast();
        boolean still = contrast || preferences.reducedMotion();
        var vertices = CapsuleRenderer.colorVertices(graphics, 0f, 0f);
        for (int column = 0; column < BAR_COUNT; column++) {
            int x = startX + column * BAR_STEP;
            float height = still ? 0f : amplitude * level(frame, column);
            int whole = (int) height;
            int color = contrast ? 0xFFFFFFFF : waveColor(frame, accent, column, still);
            quad(vertices, x, centerY - whole, centerY + whole + 1, color);
            int alpha = Math.round(255 * (height - whole));
            if (alpha != 0 && whole < amplitude) {
                int tip = (color & 0xFFFFFF) | (alpha << 24);
                quad(vertices, x, centerY - whole - 1, centerY - whole, tip);
                quad(vertices, x, centerY + whole + 1, centerY + whole + 2, tip);
            }
        }
        graphics.flush();
    }

    static float level(MusicRhythm.Frame frame, int column) {
        // 频段权重从左向右交叠。中心列更突出，边缘保留小幅呼应。
        float position = column / (BAR_COUNT - 1f);
        float low = Math.max(0, 1f - 1.6f * position);
        float middle = 1f - 1.5f * Math.abs(position - .5f);
        float high = Math.max(0, 1.65f * position - .62f);
        float bands = (.9f * frame.bass() * low + frame.mid() * middle + .7f * frame.treble() * high)
                / (low + middle + high);
        double phase = frame.flowPhase() + column * 1.53;
        double detail = frame.shimmerPhase() - column * 1.07;
        float ripple = (float) (.69 + .21 * Math.sin(phase) + .10 * Math.cos(detail));
        float envelope = .62f + .38f * middle;
        return GuiMotion.clamp01(1.65f * bands * ripple * envelope * (.84f + .34f * frame.energy()));
    }

    private static int waveColor(MusicRhythm.Frame frame, int accent, int column, boolean still) {
        float strength = still ? .34f : .36f + .48f * frame.energy() + .13f * frame.sparkle(column * 1.07f);
        return GuiMotion.lerpColor(PALE, accent | 0xFF000000, strength);
    }

    private static void quad(CapsuleMesh.VertexSink vertices, int x, int top, int bottom, int color) {
        vertices.vertex(x, top, color);
        vertices.vertex(x, bottom, color);
        vertices.vertex(x + BAR_WIDTH, bottom, color);
        vertices.vertex(x + BAR_WIDTH, top, color);
    }
}
