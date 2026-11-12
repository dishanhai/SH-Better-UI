package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Horizontal;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.text.TextLayoutMotion;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

/** 山海：共用视觉布局的 Minecraft 客户端适配。 */
public final class LayoutApi {
    LayoutApi() {}

    public AlignmentLayout create(Bounds viewport) { return new AlignmentLayout(new Rect(viewport)); }

    public AlignmentAssets assets(ResourceManager resources) { return new AlignmentAssets(resources); }

    /** GUI 投影使用 getGuiScale，GUI 宽高向上取整后不能用来反推投影倍率。 */
    public PixelAlignment pixels(Window window) { return new PixelAlignment(window.getGuiScale()); }

    /**
     * Fits one logical line of native styled text within a GUI rectangle. Reduces scale first,
     * then uses an ellipsis at the minimum. The original text remains available to the caller for narration.
     */
    public FittedLine fitText(Font font, FormattedText text, int maxWidth, int maxHeight, TextFit.Scale policy) {
        if (maxWidth < 0 || maxHeight < 0) throw new IllegalArgumentException("Negative text bounds");
        var visual = Language.getInstance().getVisualOrder(text);
        int width = font.width(visual);
        float minimum = policy.minimum();
        double heightLimit = (double) maxHeight / font.lineHeight;
        if (maxWidth == 0 || heightLimit < minimum) return FittedLine.hidden(width > 0);

        double widthLimit = width == 0 ? Double.POSITIVE_INFINITY : (double) maxWidth / width;
        float scale = policy.within(Math.min(widthLimit, heightLimit));
        if (scale > 0) {
            return new FittedLine(visual, scale, width * (double) scale, font.lineHeight * (double) scale, false);
        }

        int available = (int) Math.floor(maxWidth / (double) minimum);
        var marker = CommonComponents.ELLIPSIS.getVisualOrderText();
        if (font.width(marker) > available) return FittedLine.hidden(true);
        var shortened = UiFormattedText.ellipsize(font.getSplitter(), text, available);
        visual = Language.getInstance().getVisualOrder(shortened);
        width = font.width(visual);
        if (width > available) {
            visual = marker;
            width = font.width(marker);
        }
        return new FittedLine(visual, minimum, width * (double) minimum, font.lineHeight * (double) minimum, true);
    }

    /**
     * Fits and draws one line with a label-owned transition. The returned line is the final measurement
     * for layout and overflow feedback. Keep the original text for narration.
     */
    public FittedLine drawFittedText(GuiGraphics graphics, Font font, FormattedText text, Bounds area,
                                     TextFit.Scale policy, Horizontal alignment, TextLayoutMotion motion,
                                     int color, boolean shadow, UiPreferences preferences) {
        var line = fitText(font, text, area.width(), area.height(), policy);
        double x = switch (alignment) {
            case LEFT -> 0;
            case CENTER -> (area.width() - line.width()) / 2;
            case RIGHT -> area.width() - line.width();
        };
        double y = (area.height() - line.height()) / 2;
        List<TextLayoutMotion.Line> target = line.scale() == 0 ? List.of()
                : List.of(new TextLayoutMotion.Line(line.glyphs(), (float) x, (float) y, line.scale()));
        var frame = motion.update(text, target, PageTransition.nowMs(),
                preferences.reducedMotion() || preferences.highContrast());
        TextLayoutMotion.draw(graphics, font, frame, area.x(), area.y(), area, color, shadow, preferences);
        return line;
    }

    /** Visual-order glyphs with their final GUI size. A zero scale means no line fits. */
    public record FittedLine(FormattedCharSequence glyphs, float scale, double width, double height, boolean truncated) {
        private static FittedLine hidden(boolean truncated) {
            return new FittedLine(FormattedCharSequence.EMPTY, 0, 0, 0, truncated);
        }
    }

    /** 以现有原生控件的位置和尺寸为初始测量。 */
    public AlignmentFrame widget(AbstractWidget widget) {
        return AlignmentFrame.box(new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
    }

    /**
     * 应用到原生控件的整数位置，原生键盘、旁白及鼠标路由可继续使用。
     * 要求 GUI_ORIGIN 吸附、纯平移、原尺寸点击矩形且无需额外裁剪。
     */
    public void placeWidget(AbstractWidget widget, View view) {
        var t = view.transform();
        if (t.xx() != 1 || t.yy() != 1 || t.xy() != 0 || t.yx() != 0 || view.clip() != null
                || !view.hitTransform().equals(t)
                || !view.localHit().equals(new Rect(0, 0, widget.getWidth(), widget.getHeight()))) {
            throw new IllegalArgumentException("Native widget placement requires an unclipped integer translation");
        }
        int x = integerCoordinate(t.tx()), y = integerCoordinate(t.ty());
        if (widget.getX() != x) widget.setX(x);
        if (widget.getY() != y) widget.setY(y);
    }

    /**
     * 在屏幕 GUI 原点的绘制阶段调用，回调收到局部坐标与保存好的裁剪。
     * 鼠标仍使用屏幕 GUI 坐标，交由同一 View 的 hitTest 判断。
     */
    public void draw(GuiGraphics graphics, View view, Consumer<GuiGraphics> content) {
        Rect clip = view.clip();
        if (clip != null && clip.empty()) return;
        if (clip != null) {
            graphics.enableScissor((int) Math.floor(clip.x()), (int) Math.floor(clip.y()),
                    (int) Math.ceil(clip.right()), (int) Math.ceil(clip.bottom()));
        }
        var pose = graphics.pose();
        pose.pushPose();
        try {
            applyLocalPose(graphics, view);
            content.accept(graphics);
        } finally {
            pose.popPose();
            if (clip != null) graphics.disableScissor();
        }
    }

    /** Compose the published node transform into the current pose. The caller owns the pose stack. */
    public void applyLocalPose(GuiGraphics graphics, View view) {
        var t = view.transform();
        graphics.pose().last().pose().mul(new Matrix4f()
                .m00((float) t.xx()).m10((float) t.xy()).m30((float) t.tx())
                .m01((float) t.yx()).m11((float) t.yy()).m31((float) t.ty()));
    }

    private static int integerCoordinate(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || Math.rint(value) != value) {
            throw new IllegalArgumentException("Expected an integer GUI coordinate: " + value);
        }
        return (int) value;
    }
}
