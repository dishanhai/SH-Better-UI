package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 山海：兼容 GT 山海格式码的色板与逐字效果注册入口。 */
public final class UiTextEffects {
    private static final String MARKER = "shbui:fcs:";
    private static final String LEGACY_MARKER = "fcs:";
    private static final String BUILT_IN_NAMESPACE = "sh_better_ui:";
    static final String SYMBOLS = "#~*%@!^?+>`";
    private static final Map<String, Format> EXTERNAL = new ConcurrentHashMap<>();
    private static final String SCRIPT_NAMESPACE = "kubejs:";

    record Format(TextPalettes.Palette palette, String symbols, Effect effect) {}

    public enum PaletteAnimation { CYCLE, BREATHE, SCAN, STATIC, TWINKLE, DISTORT, WOBBLE }

    /** 0 使用色板默认速度。反向沿相反的字序取样。 */
    public record Timing(int millisPerStep, boolean reversed) {
        public static final Timing DEFAULT = new Timing(0, false);

        public Timing {
            if (millisPerStep < 0) throw new IllegalArgumentException("Palette step must be non-negative");
        }

        long metaTime(long time) {
            return millisPerStep == 0 ? time : (long) (time * (80.0 / millisPerStep));
        }
    }

    /** 字符索引与长度均按当前效果段的 Unicode 码点计数。 */
    public record GlyphContext(int rgb, int index, int length, int codePoint, long timeMillis,
                               boolean reducedMotion) {}

    /** RGB 不含 alpha，位移使用 GUI 逻辑单位。 */
    public record Glyph(int rgb, float x, float y, boolean outline, boolean obfuscated) {
        public Glyph {
            rgb &= 0xFFFFFF;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException("Glyph offsets must be finite");
            }
        }
    }

    /** 实现在渲染线程执行，且可能按每个字形调用。 */
    @FunctionalInterface
    public interface Effect {
        Glyph sample(GlyphContext context);
    }

    private UiTextEffects() {}

    public static void registerPalette(Identifier id, int... rgb) {
        registerPalette(id, false, rgb);
    }

    /** 注册循环色板。正文模式使用较慢的过渡速度。 */
    public static void registerPalette(Identifier id, boolean body, int... rgb) {
        registerPalette(id, body, PaletteAnimation.CYCLE, rgb);
    }

    public static synchronized void registerPalette(Identifier id, boolean body,
                                                    PaletteAnimation animation, int... rgb) {
        registerPalette(id, palette(body, animation, rgb));
    }

    /** 清理函数只撤销本次注册，适用于脚本重载。 */
    public static synchronized Runnable registerScopedPalette(Identifier id, boolean body,
                                                              PaletteAnimation animation, int... rgb) {
        registerPalette(id, body, animation, rgb);
        String key = id.toString();
        Format registered = EXTERNAL.get(key);
        return () -> unregister(key, registered);
    }

    /** 默认生成呼吸、扫描、静态、闪烁和扭曲变体。注册前检查全部 ID。 */
    public static synchronized List<Identifier> registerPaletteVariants(Identifier base, boolean body,
            int[] rgb, PaletteAnimation... animations) {
        Objects.requireNonNull(base, "base");
        if (animations.length == 0) {
            animations = new PaletteAnimation[]{PaletteAnimation.BREATHE, PaletteAnimation.SCAN,
                    PaletteAnimation.STATIC, PaletteAnimation.TWINKLE, PaletteAnimation.DISTORT};
        }
        var variants = new java.util.LinkedHashMap<Identifier, TextPalettes.Palette>();
        for (PaletteAnimation animation : animations) {
            var id = Identifier.tryParse(base + "_" + animation.name().toLowerCase(Locale.ROOT));
            if (find(id.toString()) != null || variants.putIfAbsent(id, palette(body, animation, rgb)) != null) {
                throw new IllegalArgumentException("Text effect already registered: " + id);
            }
        }
        variants.forEach(UiTextEffects::registerPalette);
        return List.copyOf(variants.keySet());
    }

    /** 包含内置名称、旧别名和运行时注册项。 */
    public static synchronized List<String> styleNames() {
        var names = new LinkedHashSet<>(TextPalettes.names());
        EXTERNAL.keySet().stream().sorted().forEach(names::add);
        return List.copyOf(names);
    }

    /** 移除运行时注册项。内置样式和别名一直可用。 */
    public static synchronized boolean unregister(Identifier id) {
        String key = Objects.requireNonNull(id, "id").toString();
        return unregister(key, EXTERNAL.get(key));
    }

    private static synchronized boolean unregister(String key, Format removed) {
        if (removed == null || EXTERNAL.get(key) != removed) return false;
        EXTERNAL.remove(key);
        if (removed.palette() != null && removed.palette().mode() == TextPalettes.Mode.CYCLE) {
            TextPalettes.removeFlowingPalette(removed.palette());
        }
        return true;
    }

    private static TextPalettes.Palette palette(boolean body, PaletteAnimation animation, int[] rgb) {
        int[] stops = rgb.clone();
        if (stops.length == 0) throw new IllegalArgumentException("Palette must contain a color");
        for (int stop : stops) {
            if ((stop & 0xFF000000) != 0) throw new IllegalArgumentException("Palette colors must be RGB");
        }
        return new TextPalettes.Palette(stops, body, TextPalettes.Mode.valueOf(animation.name()), false);
    }

    private static void registerPalette(Identifier id, TextPalettes.Palette palette) {
        register(id, new Format(palette, "", null));
        if (palette.mode() == TextPalettes.Mode.CYCLE) TextPalettes.addFlowingPalette(palette);
    }

    public static synchronized void registerEffect(Identifier id, Effect effect) {
        register(id, new Format(null, "", Objects.requireNonNull(effect, "effect")));
    }

    private static void register(Identifier id, Format format) {
        String key = Objects.requireNonNull(id, "id").toString();
        if (find(key) != null) throw new IllegalArgumentException("Text effect already registered: " + key);
        EXTERNAL.put(key, format);
    }

    public static Component apply(Component text, Identifier id) {
        return apply(text, id, "");
    }

    /** 将注册项与可选的 GT 效果符号写入组件根样式的 insertion，保留其余样式和子组件。 */
    public static Component apply(Component text, Identifier id, String symbols) {
        return applyNamed(text, Objects.requireNonNull(id, "id").toString(), symbols);
    }

    /** 支持旧色板名、中文别名和命名空间 ID。 */
    public static Component applyNamed(Component text, String key, String symbols) {
        return find(key) == null ? text : mark(text, key, checkedSymbols(symbols));
    }

    /** 对原生组件单独应用 GT 效果符号。 */
    public static Component apply(Component text, String symbols) {
        String checked = checkedSymbols(symbols);
        return checked.isEmpty() ? text : mark(text, "", checked);
    }

    public static Component parse(String raw) {
        return parse(raw, Style.EMPTY);
    }

    /** 解析多段旧格式，保留无法识别的标记。 */
    public static Component parse(String raw, Style style) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(style, "style");
        Component parsed = LegacyTextParser.parse(raw, style, false);
        return parsed == null ? Component.literal(raw).setStyle(style) : parsed;
    }

    /**
     * 在量宽或换行之前处理组件各样式段中的旧格式码。
     * 如发生转换，结果是当前语言下的快照，切换语言后请从原组件重新处理。
     */
    public static Component normalize(Component component) {
        return normalize(component, false);
    }

    /** 适用于原版入口，仅识别含明确 FCS 标记的文本。 */
    public static Component normalizeLegacy(Component component) {
        return normalize(component, true);
    }

    private static Component normalize(Component component, boolean requireEffect) {
        boolean hasCode = component.visit((style, text) -> !LegacyTextParser.hasMarkup(text, requireEffect)
                ? Optional.empty() : Optional.of(Boolean.TRUE), Style.EMPTY).isPresent();
        if (!hasCode) return component;
        MutableComponent normalized = Component.empty();
        component.visit((style, text) -> {
            Component parsed = LegacyTextParser.parse(text, style, requireEffect);
            normalized.append(parsed == null ? Component.literal(text).setStyle(style) : parsed);
            return Optional.empty();
        }, Style.EMPTY);
        return normalized;
    }

    static int endOfSymbols(String text, int start) {
        int cursor = start;
        while (cursor < text.length() && SYMBOLS.indexOf(text.charAt(cursor)) >= 0) cursor++;
        return cursor;
    }

    private static String checkedSymbols(String symbols) {
        Objects.requireNonNull(symbols, "symbols");
        if (endOfSymbols(symbols, 0) != symbols.length()) {
            throw new IllegalArgumentException("Unknown text effect symbol: " + symbols);
        }
        return symbols;
    }

    private static Component mark(Component text, String name, String symbols) {
        return text.copy().withStyle(style -> mark(style, name, symbols));
    }

    static Style mark(Style style, String name, String symbols) {
        return style.withInsertion(MARKER + name + "|" + symbols);
    }

    static Format find(String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        Format external = EXTERNAL.get(name);
        if (external != null) return external;
        boolean unqualified = name.indexOf(':') < 0;
        if (name.startsWith(BUILT_IN_NAMESPACE)) name = name.substring(BUILT_IN_NAMESPACE.length());
        TextPalettes.Palette palette = TextPalettes.get(name);
        if (palette != null) return new Format(palette, "", null);
        return unqualified ? EXTERNAL.get(SCRIPT_NAMESPACE + name) : null;
    }

    static Format format(Style style) {
        return format(style, true);
    }

    static Format format(Style style, boolean legacy) {
        String insertion = style.getInsertion();
        if (insertion == null) return null;
        String marker;
        if (insertion.startsWith(MARKER)) marker = MARKER;
        else if (legacy && insertion.startsWith(LEGACY_MARKER)) marker = LEGACY_MARKER;
        else return null;

        int separator = insertion.indexOf('|', marker.length());
        String name = separator < 0 ? insertion.substring(marker.length())
                : insertion.substring(marker.length(), separator);
        Format registered = name.isEmpty() ? null : find(name);
        if (!name.isEmpty() && registered == null) return null;

        StringBuilder symbols = new StringBuilder();
        if (separator >= 0) {
            for (int i = 0; i < SYMBOLS.length(); i++) {
                char symbol = SYMBOLS.charAt(i);
                if (insertion.indexOf(symbol, separator + 1) >= 0) symbols.append(symbol);
            }
        }
        if (registered == null && symbols.isEmpty()) return null;
        return new Format(registered == null ? null : registered.palette(), symbols.toString(),
                registered == null ? null : registered.effect());
    }

    static Glyph sample(Format format, int rgb, int index, int length, int codePoint,
                        long timeMillis, boolean reducedMotion) {
        return sample(format, rgb, index, length, codePoint, timeMillis, reducedMotion, Timing.DEFAULT, false);
    }

    static Glyph sample(Format format, int rgb, int index, int length, int codePoint,
                        long timeMillis, boolean reducedMotion, Timing timing, boolean nativeColor) {
        long time = reducedMotion ? 0 : timeMillis;
        TextPalettes.Palette palette = format.palette();
        TextPalettes.RandomFrame random = null;
        if (palette != null) {
            if (palette.mode() == TextPalettes.Mode.ALL && nativeColor) {
                return new Glyph(rgb, 0, 0, false, false);
            } else if (palette.mode() == TextPalettes.Mode.RANDOM) {
                random = TextPalettes.randomFrame(timing.metaTime(time));
                rgb = TextPalettes.sampleRandom(random, timing.reversed() ? -index : index, length);
            } else {
                rgb = TextPalettes.color(palette, index, length, time, timing);
            }
        }

        Glyph glyph = TextGlyphEffects.sample(format.symbols(), palette, random, rgb, index, length, time,
                reducedMotion);
        if (format.effect() == null) return glyph;
        Glyph custom = Objects.requireNonNull(format.effect().sample(
                new GlyphContext(glyph.rgb(), index, length, codePoint, time, reducedMotion)), "effect result");
        return new Glyph(custom.rgb(), reducedMotion ? 0 : glyph.x() + custom.x(),
                reducedMotion ? 0 : glyph.y() + custom.y(), glyph.outline() || custom.outline(),
                !reducedMotion && (glyph.obfuscated() || custom.obfuscated()));
    }

    /** 取指定码点的色板颜色，可用于首尾色或连接另一段渐变。 */
    public static int paletteColor(String name, int index, int length, long timeMillis, Timing timing) {
        Format format = find(Objects.requireNonNull(name, "name"));
        if (format == null || format.palette() == null || index < 0 || index >= length) {
            throw new IllegalArgumentException("Unknown palette or invalid glyph position: " + name);
        }
        return TextPalettes.color(format.palette(), index, length, timeMillis, timing);
    }
}
