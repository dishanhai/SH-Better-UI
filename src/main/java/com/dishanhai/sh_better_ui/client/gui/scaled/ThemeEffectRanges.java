package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：内置效果的滑杆区间。无固定范围的字段使用原值输入和步进控件。 */
final class ThemeEffectRanges {
    record Range(int min, int max, int scale) {}

    private ThemeEffectRanges() {}

    private static Range range(double min, double max, int decimals) {
        double factor = Math.pow(10, decimals);
        return new Range((int) Math.round(min * factor), (int) Math.round(max * factor), decimals);
    }

    static Range find(String group, ThemeEffectFields.Field descriptor) {
        String field = descriptor.name();
        return switch (group) {
            case "sh_better_ui:soft_aurora", "sh_better_ui:silk_ripples", "sh_better_ui:iridescent_flow",
                    "sh_better_ui:contour_flow", "sh_better_ui:mist_glow" -> switch (field) {
                case "speed" -> range(0, 2, 2);
                case "scale" -> range(0.25, 4, 2);
                case "intensity", "opacity" -> range(0, 1, 2);
                default -> null;
            };
            case "patternWaves" -> switch (field) {
                case "spacing" -> range(4, 120, 2);
                case "markSize", "fadeSize" -> range(0.05, 1, 2);
                case "depth" -> range(0, 1.5, 2);
                case "light" -> range(-90, 90, 2);
                case "shine" -> range(0, 2, 2);
                case "contrast" -> range(0.3, 3, 2);
                case "speed" -> range(0, 10, 2);
                case "scale" -> range(0.2, 5, 2);
                case "opacity", "cursorStrength" -> range(0, 1, 2);
                case "cursorSize" -> range(8, 400, 2);
                case "direction" -> range(-359, 359, 0);
                default -> null;
            };
            case "shapeWaves" -> switch (field) {
                case "fontWeight" -> range(100, 900, 0);
                case "textSize", "dotSize" -> range(0.05, 1, 2);
                case "cellSize" -> range(4, 120, 2);
                case "speed" -> range(0, 10, 2);
                case "scale" -> range(0.2, 5, 2);
                case "contrast" -> range(0, 5, 2);
                case "brightness", "fade", "splashStrength", "glow" -> range(0, 1, 2);
                case "flow" -> range(0, 120, 2);
                case "splashRadius" -> range(8, 400, 2);
                case "introDuration" -> range(0.05, 30, 2);
                case "direction" -> range(-359, 359, 0);
                default -> null;
            };
            case "lightRays" -> switch (field) {
                case "raysSpeed", "glow", "intensity" -> range(0, 10, 2);
                case "lightSpread" -> range(0.001, 10, 3);
                case "rayLength", "fadeDistance" -> range(0.01, 20, 2);
                case "rayDensity" -> range(0.1, 6, 2);
                case "tilt" -> range(-90, 90, 2);
                case "raySharpness", "haze", "atmosphere", "mouseInfluence", "noiseAmount", "opacity" -> range(0, 1, 2);
                case "saturation" -> range(0, 4, 2);
                case "distortion" -> range(-4, 4, 2);
                default -> null;
            };
            case "antigravity" -> switch (field) {
                case "count" -> range(1, 20000, 0);
                case "waveCount" -> range(0, 64, 0);
                case "colorSpeed", "waveSpeed" -> range(-10, 10, 2);
                case "particleSize" -> range(0, 10, 2);
                case "particleVariance", "ambient", "opacity" -> range(0, 1, 2);
                case "magnetRadius", "ringRadius", "fieldStrength" -> range(0, 100, 2);
                case "waveAmplitude" -> range(0, 20, 2);
                case "rotationSpeed", "pulseSpeed" -> range(-20, 20, 2);
                case "depthFactor" -> range(0, 2, 2);
                case "lerpSpeed" -> range(0.001, 1, 3);
                default -> null;
            };
            case "laserFlow" -> switch (field) {
                case "beamPosition", "surfaceLevel" -> range(0, 1, 2);
                case "intensity", "flare", "spread", "spill", "dust", "streaks", "dots", "pulse", "speed" -> range(0, 8, 2);
                case "beamWidth" -> range(0.1, 8, 2);
                case "fog" -> range(0, 4, 2);
                default -> null;
            };
            case "codeRain", "ascii" -> switch (field) {
                case "cellSize" -> range(6, 128, 0);
                case "density" -> range(0, 1, 2);
                case "speed" -> range(0, 64, 2);
                case "trailLength" -> range(1, 128, 0);
                default -> null;
            };
            case "electricLogo" -> switch (field) {
                case "intensity", "glow", "bend", "crackle", "arcs" -> range(0, 4, 2);
                case "fill", "flicker" -> range(0, 1, 2);
                case "scale" -> range(0.01, 1, 2);
                case "thickness" -> range(0.25, 12, 2);
                case "strands" -> range(1, 6, 0);
                case "speed" -> range(0, 10, 2);
                case "cursorIntensity" -> range(0, 2, 2);
                default -> null;
            };
            case "dotMatrix" -> switch (field) {
                case "glow", "density", "floatProbability", "probability" -> range(0, 1, 2);
                case "bulgeStrength" -> range(-1, 1, 2);
                case "twistDegrees" -> range(-360, 360, 0);
                case "glowIntensity" -> range(0, 2, 2);
                default -> null;
            };
            case "glass" -> switch (field) {
                case "blurRadiusScale", "tintScale", "grainScale", "saturationScale", "rimScale", "refractionScale" -> range(0, 2, 2);
                case "strength" -> range(0, 1, 2);
                case "lift" -> range(0, 0.15, 3);
                default -> null;
            };
            case "reveal" -> switch (field) {
                case "maxRadius" -> range(0, 32, 1);
                case "fullBlurUntil" -> range(0, 0.99, 2);
                case "sharpFrom" -> range(0.01, 1, 2);
                default -> null;
            };
            case "feedback" -> switch (field) {
                case "tooltipDelayMs" -> range(0, 2000, 0);
                default -> null;
            };
            case "tabs" -> switch (field) {
                case "scrollStep" -> range(8, 128, 0);
                default -> null;
            };
            default -> {
                if (ThemeEffects.defaults(group) instanceof ThemeEffects.Material && field.equals("opacity")) {
                    yield range(0, 2, 2);
                }
                if (ThemeEffects.defaults(group) instanceof ThemeEffects.Local) {
                    if (field.equals("strength")) yield range(0, 2, 2);
                    if (field.equals("speed")) yield range(0.1, 4, 2);
                }
                yield null;
            }
        };
    }
}
