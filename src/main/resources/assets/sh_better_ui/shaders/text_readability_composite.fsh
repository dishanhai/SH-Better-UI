#version 150
// Author: dishanhai
uniform sampler2D Backdrop;
uniform sampler2D Mask;
uniform sampler2D NativeOriginal;
uniform usampler2D Analysis;
uniform uint Original;
uniform ivec2 Origin;
uniform ivec2 MaskSize;
uniform ivec4 OutlineOffsets;
uniform ivec2 ShadowOffset;
uniform bool Shadow;
uniform bool HasNativeOriginal;
uniform mat2 PixelToLocal;
uniform vec2 PixelOffset;
uniform vec4 BackingRect;
uniform float Opacity;
out vec4 color;

vec3 toLinear(vec3 rgb) {
    return mix(rgb / 12.92, pow((rgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), rgb));
}
vec3 toSrgb(vec3 rgb) {
    return mix(12.92 * rgb, 1.055 * pow(rgb, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), rgb));
}
vec3 rgb(uint argb) { return vec3((uvec3(argb) >> uvec3(16u, 8u, 0u)) & uvec3(255u)) / 255.0; }

float coverage(ivec2 p) {
    if (any(lessThan(p, ivec2(0))) || any(greaterThanEqual(p, MaskSize))) return 0.0;
    return texelFetch(Mask, p, 0).a;
}

void over(inout vec4 dst, vec3 ink, float coverage) {
    dst.rgb = mix(dst.rgb, ink, coverage);
    dst.a = mix(dst.a, 1.0, coverage);
}

void main() {
    uvec4 decision = texelFetch(Analysis, ivec2(0), 0);
    uint kind = (decision.w >> 2u) & 3u;
    ivec2 p = ivec2(gl_FragCoord.xy) - Origin;
    vec4 mask = texelFetch(Mask, p, 0);
    float fillAlpha = float(decision.x >> 24u) / 255.0;
    vec3 chosen = rgb(decision.x);
    bool nativePaint = HasNativeOriginal && Original == decision.x;
    vec4 glyph = nativePaint ? texelFetch(NativeOriginal, p, 0) : mask;
    float outline = 0.0;
    if (kind == 1u) {
        ivec2 dx = OutlineOffsets.xy, dy = OutlineOffsets.zw;
        outline = max(max(coverage(p + dx), coverage(p - dx)), max(coverage(p + dy), coverage(p - dy)));
        outline *= 1.0 - mask.a;
    }
    float shadow = Shadow && !nativePaint ? coverage(p - ShadowOffset) * fillAlpha : 0.0;
    vec2 local = PixelToLocal * (vec2(p) + 0.5) + PixelOffset;
    bool backing = kind == 2u && all(greaterThanEqual(local, BackingRect.xy)) && all(lessThan(local, BackingRect.zw));
    if (!backing && outline == 0.0 && shadow == 0.0 && glyph.a == 0.0) discard;

    vec4 before = texelFetch(Backdrop, p, 0);
    vec4 background = vec4(toLinear(before.rgb), before.a);
    vec4 after = background;
    if (backing || outline > 0.0) over(after, toLinear(rgb(decision.y)), backing ? 1.0 : outline);
    if (shadow > 0.0) over(after, toLinear(chosen * 0.25), shadow);
    if (glyph.a > 0.0) {
        vec3 unassociated = clamp(glyph.rgb / glyph.a, 0.0, 1.0);
        vec3 ink = nativePaint ? unassociated : chosen * unassociated;
        over(after, toLinear(ink), glyph.a * fillAlpha);
    }
    vec4 mixed = mix(background, after, Opacity);
    color = vec4(toSrgb(mixed.rgb), mixed.a);
}
