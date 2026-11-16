#version 150
// Original SHBUI stylized faceted crystal. No imported component/shader source.
uniform vec4 ColorModulator;
uniform vec4 ClipRect;
uniform float Chrome;
uniform float Glow;
in vec2 crystalUv;
in vec2 clipPosition;
in vec4 crystalColor;
out vec4 fragColor;
void main() {
    if (any(lessThan(clipPosition, ClipRect.xy)) || any(greaterThanEqual(clipPosition, ClipRect.zw))) discard;
    vec2 p = crystalUv;
    // A pointed diamond with an off-centre keel separates the four visible facets.
    float edgeDistance = abs(p.x) + abs(p.y) - 1.0;
    float feather = max(fwidth(edgeDistance), 0.012);
    float body = 1.0 - smoothstep(-feather, feather, edgeDistance);
    float rim = 1.0 - smoothstep(0.025, 0.10 + feather, abs(edgeDistance));
    float keelDistance = abs(p.y + p.x * 0.12);
    float keel = 1.0 - smoothstep(0.0, 0.035 + feather, keelDistance);
    float face = p.y + p.x * 0.12 > 0.0 ? 0.52 : 0.86;
    face *= p.x > 0.0 ? 1.0 : 0.76;
    float band = pow(max(0.0, 1.0 - abs(p.y - p.x * 0.28 - 0.14) * 3.2), 3.0);
    vec3 tint = crystalColor.rgb;
    vec3 glass = tint * face + vec3(rim * 0.27 + keel * 0.19 + band * 0.13);
    vec3 chrome = tint * (0.22 + face * 0.38) + vec3(band * 0.62 + keel * 0.29 + rim * 0.35);
    float halo = (1.0 - smoothstep(0.0, 0.18, max(edgeDistance, 0.0))) * (1.0 - body) * Glow * 0.12;
    float alpha = body * mix(0.45 + rim * 0.3 + keel * 0.12, 0.92, Chrome) + halo;
    vec3 rgb = mix(glass, chrome, Chrome) + tint * halo;
    fragColor = vec4(clamp(rgb, 0.0, 1.0), clamp(alpha, 0.0, 1.0) * crystalColor.a) * ColorModulator;
    if (fragColor.a <= 0.001) discard;
}
