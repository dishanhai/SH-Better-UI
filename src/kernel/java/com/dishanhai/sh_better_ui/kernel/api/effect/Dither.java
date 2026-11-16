// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.effect;

import java.util.List;
import java.util.Objects;

/** Platform-independent pixel calculation for an image dither background. Colors are ARGB. */
public final class Dither {
    public enum Mode { BAYER, HALFTONE, NOISE, CROSSHATCH }
    public enum ColorMode { ORIGINAL, GRAYSCALE, DUOTONE, CUSTOM }
    public enum Fit { COVER, CONTAIN, FILL, NONE }

    public record Options(int gridSize, Mode mode, ColorMode colorMode, boolean invert, float pixelRatio,
                          int primaryColor, int secondaryColor, List<Integer> colors, float brightness,
                          float contrast, int backgroundColor, Fit fit, float threshold,
                          boolean animated, float animationSpeed) {
        public static final Options DEMO = new Options(2, Mode.BAYER, ColorMode.GRAYSCALE, false, 1,
                0xff000000, 0xfff5f5f5, List.of(0xff000000, 0xffffffff), 0, 1, 0,
                Fit.COVER, .5f, false, .02f);
        public Options {
            Objects.requireNonNull(mode); Objects.requireNonNull(colorMode); Objects.requireNonNull(fit);
            colors = List.copyOf(colors);
            if (colors.isEmpty() || gridSize < 1 || gridSize > 128 || !Float.isFinite(pixelRatio)
                    || pixelRatio < 1 || pixelRatio > 16 || !Float.isFinite(brightness) || brightness < -1
                    || brightness > 1 || !Float.isFinite(contrast) || contrast < 0 || contrast > 2
                    || !Float.isFinite(threshold) || threshold < 0 || threshold > 1
                    || !Float.isFinite(animationSpeed) || animationSpeed < 0) throw new IllegalArgumentException();
        }
    }

    private static final int[][] B4 = {{0,8,2,10},{12,4,14,6},{3,11,1,9},{15,7,13,5}};
    private static final int[][] B8 = {{0,32,8,40,2,34,10,42},{48,16,56,24,50,18,58,26},
            {12,44,4,36,14,46,6,38},{60,28,52,20,62,30,54,22},{3,35,11,43,1,33,9,41},
            {51,19,59,27,49,17,57,25},{15,47,7,39,13,45,5,37},{63,31,55,23,61,29,53,21}};
    private Dither() {}

    /** Process a fitted ARGB source of exactly width × height into a new ARGB image. */
    public static int[] apply(int[] source, int width, int height, Options options, double time) {
        if (width <= 0 || height <= 0 || (long) width * height != source.length) throw new IllegalArgumentException();
        int[] result = new int[source.length];
        java.util.Arrays.fill(result, options.backgroundColor());
        int step = Math.max(1, (int) (options.gridSize() * options.pixelRatio()));
        int size = options.gridSize() <= 4 ? 4 : 8;
        int[][] matrix = size == 4 ? B4 : B8;
        for (int y = 0; y < height; y += step) for (int x = 0; x < width; x += step) {
            int pixel = source[y * width + x];
            if ((pixel >>> 24) < 10) continue;
            double r = channel(pixel >> 16, options), g = channel(pixel >> 8, options), b = channel(pixel, options);
            double lum = (.299 * r + .587 * g + .114 * b) / 255;
            double threshold = switch (options.mode()) {
                case BAYER -> matrix[(y / options.gridSize()) % size][(x / options.gridSize()) % size] / (double)(size * size);
                case HALFTONE -> (Math.sin((x + y) / Math.sqrt(2) / (options.gridSize() * 2))
                        + Math.sin((y - x) / Math.sqrt(2) / (options.gridSize() * 2)) + 2) / 4;
                case NOISE -> { double n = Math.sin(x * 12.9898 + y * 78.233 + time * 100) * 43758.5453; yield n - Math.floor(n); }
                case CROSSHATCH -> (((x + y) % (options.gridSize() * 2) < options.gridSize() ? 1 : 0)
                        + ((x - y + options.gridSize() * 4) % (options.gridSize() * 2) < options.gridSize() ? 1 : 0)) / 2.0;
            };
            threshold = threshold * (1 - options.threshold()) + options.threshold() * .5;
            int color = switch (options.colorMode()) {
                case GRAYSCALE -> lum < threshold ? 0xff000000 : 0xffffffff;
                case DUOTONE -> lum < threshold ? options.primaryColor() : options.secondaryColor();
                case CUSTOM -> options.colors().get(options.colors().size() == 2 ? (lum < threshold ? 0 : 1)
                        : (int)(clamp(lum + (threshold - .5) * .5, 0, 1) * (options.colors().size() - 1)));
                case ORIGINAL -> 0xff000000 | quantize(r, threshold) << 16 | quantize(g, threshold) << 8 | quantize(b, threshold);
            };
            if (options.invert()) color ^= 0x00ffffff;
            color |= 0xff000000;
            for (int yy = y; yy < Math.min(height, y + step); yy++)
                java.util.Arrays.fill(result, yy * width + x, yy * width + Math.min(width, x + step), color);
        }
        return result;
    }
    private static double channel(int color, Options o) { return clamp(((color & 255) - 128) * o.contrast() + 128 + o.brightness() * 255, 0, 255); }
    private static int quantize(double value, double threshold) { return (int)Math.round(Math.round(clamp(value + (threshold - .5) * 64, 0, 255) / 63.75) * 63.75); }
    private static double clamp(double n, double min, double max) { return Math.max(min, Math.min(max, n)); }
}
