package com.dishanhai.sh_better_ui.client.lyrics.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import org.joml.Matrix4f;

/** 山海：当前 Minecraft 版本的歌词字形顶点写入。 */
public final class LyricGlyphPlatform {
    private LyricGlyphPlatform() {}

    public static float top(SheetGlyphInfo glyph) { return glyph.getTop(); }
    public static float bottom(SheetGlyphInfo glyph) { return glyph.getBottom(); }

    public static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y,
                              float u, float v, int argb, int light) {
        buffer.addVertex(pose, x, y, 0f).setColor(argb).setUv(u, v).setLight(light);
    }
}
