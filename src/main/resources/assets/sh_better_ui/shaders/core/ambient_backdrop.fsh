#version 150
// Original SHBUI analytic surfaces. No imported React Bits implementation.
// Time occurs only as integer multiples inside trigonometric functions: wrap is seamless.
in vec2 iconUv;
uniform vec4 ColorModulator;
uniform vec4 Surface; // aspect, scale, periodic phase, intensity
uniform float Opacity;
uniform int Style;
uniform vec4 PrimaryColor;
uniform vec4 SecondaryColor;
uniform vec4 BackgroundColor;
out vec4 fragColor;
void main() {
    vec2 p = (iconUv - 0.5) * vec2(Surface.x, 1.0) * Surface.y;
    float t = Surface.z;
    float light;
    float tone;
    if (Style == 0) {
        // Broad luminous curtains, not particles or the existing ray effect.
        float center = 0.13 * sin(p.x * 3.0 + t) + 0.06 * sin(p.x * 7.0 - t);
        float d = p.y - center;
        light = exp(-18.0 * d * d) * (0.65 + 0.2 * sin(p.x * 5.0 + t));
        light += 0.22 * exp(-55.0 * (d + 0.23) * (d + 0.23));
        tone = 0.5 + 0.5 * sin(p.x * 2.0 - t);
    } else if (Style == 1) {
        // Bent diagonal satin folds with a narrow highlight and broad shadow.
        float fold = p.x * 8.0 + p.y * 11.0 + 1.4 * sin(p.x * 2.0 - p.y * 3.0 + t);
        float ridge = 0.5 + 0.5 * sin(fold);
        light = 0.18 + 0.7 * ridge * ridge * ridge;
        tone = 0.35 + 0.35 * sin(fold + 0.8);
    } else if (Style == 2) {
        // Smooth interfering circular fields, tinted rather than hue-flashing.
        vec2 a = p - 0.25 * vec2(cos(t), sin(t));
        vec2 b = p + 0.3 * vec2(sin(t), cos(t));
        // Linear radial phase avoids the quadratic frequency growth/moire on wide panels.
        float field = sin(length(a) * 5.0 - 2.0 * t) + cos(length(b) * 6.0 + t);
        light = 0.35 + 0.3 * sin(field * 1.5);
        tone = 0.5 + 0.5 * sin(field + p.x * 2.0);
    } else if (Style == 3) {
        // Original topographic contours on a gently deforming analytic height field.
        float height = p.y * 3.8 + 0.85 * sin(p.x * 1.7 + t)
                     + 0.45 * cos(p.y * 2.3 - p.x * 0.6 - t);
        float level = height * 6.0;
        float footprint = fwidth(level);
        float line = 1.0 - smoothstep(0.08, 0.08 + max(footprint * 0.8, 0.02), abs(sin(level)));
        // Fade unresolved contours instead of letting subpixel lines shimmer.
        line *= 1.0 - smoothstep(0.8, 2.0, footprint);
        light = 0.07 + 0.31 * line;
        tone = 0.5 + 0.25 * sin(height * 0.5 + t);
    } else if (Style == 5) {
        // Original four-source fluid field, not a port of the QPlayer/AMLL shaders.
        // Work is fixed per pixel, with no noise octaves, texture fetches or blur targets.
        vec2 q = vec2(p.x / max(Surface.x, 0.001), p.y);
        q += 0.10 * vec2(sin(q.y * 4.0 + t), cos(q.x * 4.0 - t));
        vec2 a = q - vec2(-0.28 + 0.18 * cos(t), 0.22 * sin(t));
        vec2 b = q - vec2(0.30 + 0.16 * sin(t), 0.24 * cos(t));
        vec2 c = q - vec2(0.26 * cos(t), -0.30 + 0.12 * sin(2.0 * t));
        vec2 d = q - vec2(0.20 * sin(t), 0.32 + 0.13 * cos(2.0 * t));
        float first = exp(-dot(a, a) * 18.0) + 0.65 * exp(-dot(c, c) * 20.0);
        float second = exp(-dot(b, b) * 18.0) + 0.65 * exp(-dot(d, d) * 20.0);
        light = 0.03 + 0.95 * (1.0 - exp(-1.3 * (first + second)));
        tone = second / max(first + second, 0.0001);
    } else {
        // Two broad drifting mist pools, without particles, light rays or offscreen blur.
        vec2 a = p - vec2(0.32 * sin(t), 0.18 * cos(t));
        vec2 b = p + vec2(0.4 * cos(t), 0.24 * sin(t));
        float footprint = length(fwidth(p));
        float first = exp(-dot(a, a) / (0.24 + footprint));
        float second = exp(-dot(b, b) / (0.46 + footprint));
        light = 0.06 + 0.27 * first + 0.21 * second;
        tone = second / max(first + second, 0.0001);
    }
    vec4 ink = mix(PrimaryColor, SecondaryColor, clamp(tone, 0.0, 1.0));
    float weight = clamp(light * Surface.w, 0.0, 1.0);
    // Straight alpha compositing permits transparent theme colors without halos.
    float a = ink.a * weight;
    float alpha = a + BackgroundColor.a * (1.0 - a);
    vec3 premul = ink.rgb * a + BackgroundColor.rgb * BackgroundColor.a * (1.0 - a);
    vec3 rgb = alpha > 0.0001 ? premul / alpha : vec3(0.0);
    fragColor = vec4(rgb, alpha * Opacity) * ColorModulator;
}
