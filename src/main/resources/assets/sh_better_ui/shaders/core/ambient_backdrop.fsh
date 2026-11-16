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
    } else {
        // Smooth interfering circular fields, tinted rather than hue-flashing.
        vec2 a = p - 0.25 * vec2(cos(t), sin(t));
        vec2 b = p + 0.3 * vec2(sin(t), cos(t));
        // Linear radial phase avoids the quadratic frequency growth/moire on wide panels.
        float field = sin(length(a) * 5.0 - 2.0 * t) + cos(length(b) * 6.0 + t);
        light = 0.35 + 0.3 * sin(field * 1.5);
        tone = 0.5 + 0.5 * sin(field + p.x * 2.0);
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
