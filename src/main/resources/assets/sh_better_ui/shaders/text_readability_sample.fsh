#version 150
// Author: dishanhai
uniform sampler2D Backdrop;
uniform sampler2D Mask;
uniform ivec2 MaskSize;
uniform ivec2 BackdropOffset;
uniform ivec4 SampleBox;
uniform ivec2 Grid;
uniform ivec4 OutlineOffsets;
uniform float InteriorCoverage;
out vec4 Sample;

float coverage(ivec2 p) {
    if (any(lessThan(p, ivec2(0))) || any(greaterThanEqual(p, MaskSize))) return 0.0;
    return texelFetch(Mask, p, 0).a;
}

vec3 toLinear(vec3 rgb) {
    return mix(rgb / 12.92, pow((rgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), rgb));
}

void main() {
    int slot = int(gl_FragCoord.x);
    int row = slot % Grid.y;
    int col = slot / Grid.y;
    int cols = (Grid.x + Grid.y - 1 - row) / Grid.y;
    ivec2 low = SampleBox.xy + ivec2(col * SampleBox.z / cols, row * SampleBox.w / Grid.y);
    ivec2 high = SampleBox.xy + ivec2((col + 1) * SampleBox.z / cols, (row + 1) * SampleBox.w / Grid.y);
    ivec2 span = high - low;
    Sample = vec4(-1.0);
    if (any(lessThanEqual(span, ivec2(0)))) return;

    // One backdrop read per tile. Test fixed center-first mask probes inside that tile to find solid ink.
    const ivec2 probes[13] = ivec2[13](ivec2(3, 3), ivec2(1, 3), ivec2(5, 3), ivec2(3, 1), ivec2(3, 5),
            ivec2(1, 1), ivec2(5, 1), ivec2(1, 5), ivec2(5, 5), ivec2(0, 2), ivec2(6, 4), ivec2(2, 0), ivec2(4, 6));
    ivec2 p = low;
    float ink = 0.0;
    for (int i = 0; i < 13; ++i) {
        p = low + ((span - 1) * probes[i] + 3) / 6;
        ink = coverage(p);
        if (ink >= InteriorCoverage) break;
    }
    if (ink < InteriorCoverage) return;
    vec4 backdrop = texelFetch(Backdrop, p + BackdropOffset, 0);
    if (backdrop.a < 1.0) return;
    vec2 dx = vec2(OutlineOffsets.xy);
    vec2 dy = vec2(OutlineOffsets.zw);
    float neighboringInk = min(min(coverage(p + ivec2(dx)), coverage(p - ivec2(dx))),
                               min(coverage(p + ivec2(dy)), coverage(p - ivec2(dy))));
    float luminance = dot(toLinear(backdrop.rgb), vec3(0.2126, 0.7152, 0.0722));
    Sample = vec4(luminance, ink * (1.0 - neighboringInk), vec2(p));
}
