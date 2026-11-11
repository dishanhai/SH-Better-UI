package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame.VisualBounds;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricGlyphAccess;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/**
 * 山海：页面持有的客户端度量缓存。仅在测量阶段访问资源或字体。
 * 资源重载后调用 invalidate，再更新引用旧度量的节点。
 */
public final class AlignmentAssets {
    private record TextureKey(ResourceLocation id, int threshold) {}
    private record GlyphKey(Font font, ResourceLocation styleFont, int codePoint, boolean bold, boolean italic) {}
    private record Glyph(Rect ink, double advance) {}

    /** 缓存计数仅包含纹理查询。 */
    public record Stats(long hits, long misses) {}

    private final ResourceManager resources;
    private final Map<TextureKey, Optional<TextureMetrics>> textures = new HashMap<>();
    private final Map<ResourceLocation, TextureMetrics> authored = new HashMap<>();
    private final Map<GlyphKey, Glyph> glyphs = new HashMap<>();
    private GlyphAccess glyphAccess;
    private boolean checkedGlyphAccess;
    private long hits;
    private long misses;

    private final Runnable cacheHit;
    private final Runnable cacheMiss;

    public AlignmentAssets(ResourceManager resources) { this(resources, () -> {}, () -> {}); }

    public AlignmentAssets(ResourceManager resources, Runnable cacheHit, Runnable cacheMiss) {
        this.resources = Objects.requireNonNull(resources);
        this.cacheHit = Objects.requireNonNull(cacheHit);
        this.cacheMiss = Objects.requireNonNull(cacheMiss);
    }

    /** 登记由图标作者标注的归一化边界与中心，优先于 alpha 扫描。 */
    public void textureMetadata(ResourceLocation id, TextureMetrics metrics) {
        authored.put(Objects.requireNonNull(id), Objects.requireNonNull(metrics));
    }

    /** 读取指定 PNG 资源的一次性度量，缺失或损坏的资源返回空。 */
    public Optional<TextureMetrics> texture(ResourceLocation id, int threshold) {
        Objects.requireNonNull(id);
        if (threshold < 1 || threshold > 255) throw new IllegalArgumentException("Invalid alpha threshold: " + threshold);
        TextureMetrics override = authored.get(id);
        if (override != null) {
            hits++;
            cacheHit.run();
            return Optional.of(override);
        }
        var key = new TextureKey(id, threshold);
        var cached = textures.get(key);
        if (cached != null) {
            hits++;
            cacheHit.run();
            return cached;
        }
        misses++;
        cacheMiss.run();
        return textures.computeIfAbsent(key, this::load);
    }

    /** 用现有 GraphicIcon 资源，缺失时使用宿主给定的绘制矩形。 */
    public VisualBounds icon(GraphicIcon icon, Rect region) {
        return texture(icon.texture(), 1).map(metrics -> metrics.in(region)).orElseGet(() -> new VisualBounds(region));
    }

    /**
     * 测量一行已经整形、排好视觉顺序的 Minecraft 文字。以文字的绘制原点为 (0,0)。
     * 包含字体样式、字形包围盒和装饰线。混淆字使用原字符作为稳定度量。
     */
    public VisualBounds text(Font font, FormattedCharSequence text) {
        Objects.requireNonNull(font);
        Objects.requireNonNull(text);
        var measure = new TextMeasure();
        if (!checkedGlyphAccess) {
            glyphAccess = GlyphAccess.open();
            checkedGlyphAccess = true;
        }
        if (glyphAccess == null) return fallback(font, text);
        try {
            text.accept((index, style, codePoint) -> {
                var key = new GlyphKey(font, style.getFont(), codePoint, style.isBold(), style.isItalic());
                Glyph glyph = glyphs.get(key);
                if (glyph == null) {
                    cacheMiss.run();
                    glyph = glyphAccess.glyph(key);
                    glyphs.put(key, glyph);
                } else {
                    cacheHit.run();
                }
                Rect ink = glyph.ink();
                measure.add(new Rect(measure.pen + ink.x(), ink.y(), ink.width(), ink.height()));
                if (style.isStrikethrough()) measure.add(new Rect(measure.pen - 1, 3.5, glyph.advance() + 1, 1));
                if (style.isUnderlined()) measure.add(new Rect(measure.pen - 1, 8, glyph.advance() + 1, 1));
                measure.pen += glyph.advance();
                return true;
            });
            Anchor center = measure.weight == 0 ? measure.ink.center()
                    : new Anchor(measure.xWeight / measure.weight, measure.yWeight / measure.weight);
            return new VisualBounds(measure.ink, center, new Anchor(0, 0), Insets.NONE, font.lineHeight - 2.0);
        } catch (IllegalStateException unavailable) {
            return fallback(font, text);
        }
    }

    public Stats stats() { return new Stats(hits, misses); }

    /** 失效当前资源包的扫描与字形缓存。作者手工登记的元数据仍可复用。 */
    public void invalidate() {
        textures.clear();
        glyphs.clear();
    }

    private Optional<TextureMetrics> load(TextureKey key) {
        var resource = resources.getResource(key.id());
        if (resource.isEmpty()) return Optional.empty();
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            return Optional.of(TextureMetrics.scan(image.getWidth(), image.getHeight(), key.threshold(),
                    (x, y) -> image.getPixelRGBA(x, y) >>> 24));
        } catch (IOException | IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private static VisualBounds fallback(Font font, FormattedCharSequence text) {
        return new VisualBounds(new Rect(0, 0, font.width(text), font.lineHeight)).withBaseline(font.lineHeight - 2.0);
    }

    private static final class TextMeasure {
        private Rect ink = new Rect(0, 0, 0, 0);
        private double pen;
        private double weight;
        private double xWeight;
        private double yWeight;

        private void add(Rect rect) {
            if (rect.empty()) return;
            ink = ink.union(rect);
            double area = rect.width() * rect.height();
            weight += area;
            xWeight += rect.center().x() * area;
            yWeight += rect.center().y() * area;
        }
    }

    private record GlyphAccess(MethodHandle fontSet, Field left, Field right, Field up, Field down, int yOffset) {
        static GlyphAccess open() {
            try {
                return new GlyphAccess(LyricGlyphAccess.fontSet(),
                        LyricGlyphAccess.field(BakedGlyph.class, "left", "f_95205_"),
                        LyricGlyphAccess.field(BakedGlyph.class, "right", "f_95206_"),
                        LyricGlyphAccess.field(BakedGlyph.class, "up", "f_95207_"),
                        LyricGlyphAccess.field(BakedGlyph.class, "down", "f_95208_"),
                        SharedConstants.getCurrentVersion().getName().equals("1.20.1") ? -3 : 0);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return null;
            }
        }

        Glyph glyph(GlyphKey key) {
            try {
                var set = (FontSet) fontSet.invokeExact(key.font(), key.styleFont());
                var info = set.getGlyphInfo(key.codePoint(), false);
                BakedGlyph glyph = set.getGlyph(key.codePoint());
                double advance = info.getAdvance(key.bold());
                if (glyph instanceof EmptyGlyph) return new Glyph(new Rect(0, 0, 0, 0), advance);
                double top = up.getFloat(glyph) + yOffset, bottom = down.getFloat(glyph) + yOffset;
                double topShear = key.italic() ? 1 - top / 4 : 0;
                double bottomShear = key.italic() ? 1 - bottom / 4 : 0;
                double start = left.getFloat(glyph) + Math.min(topShear, bottomShear);
                double end = right.getFloat(glyph) + Math.max(topShear, bottomShear)
                        + (key.bold() ? info.getBoldOffset() : 0);
                return new Glyph(Rect.edges(start, top, end, bottom), advance);
            } catch (Error error) {
                throw error;
            } catch (Throwable failure) {
                throw new IllegalStateException("Minecraft glyph metrics unavailable", failure);
            }
        }
    }
}
