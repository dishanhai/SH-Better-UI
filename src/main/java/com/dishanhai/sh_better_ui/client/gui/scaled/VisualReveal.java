package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.visual.VisualEffectResult;
import net.minecraft.client.Minecraft;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 山海：客户端 GUI 的左糊右清效果与独立径向渐变。 */
public final class VisualReveal {
    private static final int MUTED_WARM_ORANGE = 0xFFD6A37D;
    private static final int MUTED_GREEN = 0xFF778B78;

    private VisualReveal() {}

    /** 在画好内容后调用，坐标使用当前 GUI pose。返回是否执行了后处理。 */
    public static boolean blurRegion(GuiGraphics graphics, int x, int y, int width, int height,
                                     RevealProfile profile) {
        if (profile == RevealProfile.DEFAULT) profile = ThemeEffects.profile("reveal", profile);
        return GlassBlur.revealRegion(graphics, new GuiRect(x, y, width, height),
                profile.maxRadius(), profile.fullBlurUntil(), profile.sharpFrom());
    }

    /** 绘制独立 RGBA 贴图。贴图应为完整资源，透明部分保留背景。 */
    public static void drawTexture(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height, RevealProfile profile) {
        if (width <= 0 || height <= 0) return;
        if (profile == RevealProfile.DEFAULT) profile = ThemeEffects.profile("reveal", profile);
        GlassBackdropSettings settings = ThemeEffects.glass();
        float radius = settings.enabled() && settings.strength() > 0.01f
                ? profile.maxRadius() * settings.blurRadiusScale() * settings.strength() : 0f;
        VisualRevealRenderer.drawTexture(graphics, texture, x, y, width, height, profile, radius);
    }

    /** Checked RGBA rendering in the caller's current GUI pose. Resource IDs are resolved each draw. */
    public static VisualEffectResult drawTextureChecked(GuiGraphics graphics, ResourceLocation texture, Rect area,
                                                        RevealProfile profile, RevealDirection direction,
                                                        GlassBackdropSettings settings, boolean useBlur) {
        Objects.requireNonNull(texture);
        Objects.requireNonNull(profile);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(settings);
        if (area.empty()) return EMPTY_REGION;
        var client = Minecraft.getInstance();
        if (client.getTextureManager().getTexture(texture, null) == null
                && client.getResourceManager().getResource(texture).isEmpty()) return INVALID_TEXTURE;
        float radius = useBlur && settings.enabled() && settings.strength() > 0.01f
                ? profile.maxRadius() * settings.blurRadiusScale() * settings.strength() : 0f;
        int width = (int) Math.ceil(area.width()), height = (int) Math.ceil(area.height());
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(area.x(), area.y(), 0);
            graphics.pose().scale((float) (area.width() / width), (float) (area.height() / height), 1);
            return VisualRevealRenderer.drawTexture(graphics, texture, 0, 0, width, height, profile, radius, direction);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void validateGradient(float originU, float originV, float radius) {
        if (!(originU >= 0f && originU <= 1f && originV >= 0f && originV <= 1f)
                || !Float.isFinite(radius) || radius <= 0f) {
            throw new IllegalArgumentException("Expected gradient origin in [0, 1] and a positive radius");
        }
    }

    /** Radius is relative to the distance to the farthest corner, in the current GUI pose. */
    public static VisualEffectResult drawRadialGradient(GuiGraphics graphics, Rect area, float originU, float originV,
                                                        float radius, int innerArgb, int outerArgb) {
        validateGradient(originU, originV, radius);
        if (area.empty()) return EMPTY_REGION;
        return VisualRevealRenderer.drawRadialGradient(graphics, (float) area.x(), (float) area.y(),
                (float) area.width(), (float) area.height(), originU, originV, radius, innerArgb, outerArgb);
    }

    /** 绘制右下暖橙、向外过渡到灰绿的柔和渐变。 */
    public static void drawRadialGradient(GuiGraphics graphics, int x, int y, int width, int height) {
        drawRadialGradient(graphics, x, y, width, height, 1f, 1f, MUTED_WARM_ORANGE, MUTED_GREEN);
    }

    /** 原点为矩形内归一化坐标，(0,0) 是左上角，色值为 ARGB。 */
    public static void drawRadialGradient(GuiGraphics graphics, int x, int y, int width, int height,
                                          float originU, float originV, int innerArgb, int outerArgb) {
        if (width <= 0 || height <= 0) return;
        if (!(originU >= 0f && originU <= 1f && originV >= 0f && originV <= 1f)) {
            throw new IllegalArgumentException("Gradient origin must be in [0, 1]");
        }
        VisualRevealRenderer.drawRadialGradient(graphics, x, y, width, height, originU, originV, innerArgb, outerArgb);
    }
}
