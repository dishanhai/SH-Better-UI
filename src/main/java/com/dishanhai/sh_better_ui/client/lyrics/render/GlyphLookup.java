// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.render;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.lang.invoke.MethodHandle;

/**
 * 从 {@link Font} 取单字的 {@link BakedGlyph}。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>{@code Font.getFontSet(ResourceLocation)} 是**包级可见**，
 * 外部无法直接调用；而 {@code FontSet.getGlyph(int)} 是 public。
 * 差的只是拿到 {@code FontSet} 这一步。
 *
 * <p>用 {@code setAccessible} 打开这个包级方法，而不是写 Mixin：
 * 需要的只是「调用一个已存在的方法」，不改变任何行为。
 * Mixin 适合改逻辑，读一个方法用反射更轻，也不用维护 mixins.json。
 *
 * <h2>字体回退</h2>
 *
 * <p>{@code Style.EMPTY} 用的是 {@code minecraft:default} 字体。
 * 中文在 1.21.1 由 default 字体内部的 unifont 提供者覆盖，
 * 因此取 default 字体集就能拿到中文字形，不需要额外遍历 fallback。
 * 取不到字形时返回 null，调用方退回原版路径，不会崩。
 */
public final class GlyphLookup {

    private static final ResourceLocation DEFAULT_FONT =
            ResourceLocation.withDefaultNamespace("default");

    private static volatile boolean initialised;

    private static MethodHandle getFontSet;


    private GlyphLookup() {
    }

    public static boolean available() {
        if (!initialised) {
            initialise();
        }
        return getFontSet != null;
    }

    private static synchronized void initialise() {
        if (initialised) {
            return;
        }
        try {
            getFontSet = LyricGlyphAccess.fontSet();
        } catch (ReflectiveOperationException | RuntimeException e) {
            getFontSet = null;
        } finally {
            initialised = true;
        }
    }

    /**
     * 取单字字形。
     *
     * @param font 客户端字体
     * @param cp   码点
     * @return 字形，取不到时返回 null
     */
    public static BakedGlyph glyph(Font font, int cp) {
        return glyph(font,cp,null);
    }
    public static BakedGlyph glyph(Font font, int cp, ResourceLocation fontId) {
        FontSet set = fontSet(font,fontId);
        if (set == null) {
            return null;
        }
        try {
            // getGlyph 返回 missingGlyph 而不是 null，因此缺失字符
            // 仍会画出方框；这与原版行为一致，不需要额外处理。
            return set.getGlyph(cp);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 单字的步进宽度。
     *
     * <p>必须取自字体度量而非字符串测量：逐顶点绘制时我们逐个字符
     * 推进坐标，若宽度来自别处（例如整串测量的平均值），
     * 字距会逐字累积误差，行尾偏移可达数像素。
     *
     * <p>1.21.1 的 {@code BakedGlyph} 不暴露 {@code info()}，
     * 宽度要从 {@code FontSet.getGlyphInfo} 取。
     */
    public static float advance(Font font, int cp) {
        FontSet set = fontSet(font);
        if (set == null) {
            return 0f;
        }
        try {
            GlyphInfo info = set.getGlyphInfo(cp, false);
            // 不用 getAdvance(bold)：加粗已移除，推进量一律按常规计算。
            return info == null ? 0f : info.getAdvance(false);
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    private static FontSet fontSet(Font font) { return fontSet(font,null); }
    private static FontSet fontSet(Font font, ResourceLocation fontId) {
        if (!available()) {
            return null;
        }
        try {
            return (FontSet) getFontSet.invoke(font, fontId==null?DEFAULT_FONT:fontId);
        } catch (Throwable t) {
            return null;
        }
    }
}