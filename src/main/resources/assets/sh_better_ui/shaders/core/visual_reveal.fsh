#version 150

// 山海：在原分辨率逐像素生成径向渐变与带透明边缘的贴图模糊。
uniform sampler2D TextureSampler;
uniform sampler2D ScratchSampler;
uniform vec4 ColorModulator;
uniform int EffectMode;
uniform vec2 Size;
uniform vec3 Reveal;
uniform vec2 Origin;
uniform int Direction;
uniform float GradientRadius;
uniform vec4 InnerColor;
uniform vec4 OuterColor;
in vec2 localUv;
out vec4 fragColor;

vec4 premultiply(vec4 color) {
    return vec4(color.rgb * color.a, color.a);
}

vec4 straightColor(vec4 color) {
    return color.a > 0.0 ? vec4(color.rgb / color.a, color.a) : vec4(0.0);
}

vec4 fetchBounded(sampler2D image, ivec2 pixel, ivec2 dimensions) {
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, dimensions))) {
        return vec4(0.0);
    }
    return texelFetch(image, pixel, 0);
}

vec4 sampleScratch(vec2 pixel, ivec2 dimensions) {
    vec2 extent = vec2(dimensions);
    // 目标可能大于当前贴图。插值只读有效像素，边界外的部分按透明处理。
    vec2 coverage = clamp(pixel + 1.0, 0.0, 1.0) * clamp(extent - pixel, 0.0, 1.0);
    if (coverage.x == 0.0 || coverage.y == 0.0) return vec4(0.0);
    vec2 samplePoint = clamp(pixel, vec2(0.0), extent - 1.0) + 0.5;
    return texture(ScratchSampler, samplePoint / vec2(textureSize(ScratchSampler, 0))) * coverage.x * coverage.y;
}

float blurRadius() {
    float axis = localUv.x;
    if (Direction == 1) axis = 1.0 - localUv.x;
    else if (Direction == 2) axis = localUv.y;
    else if (Direction == 3) axis = 1.0 - localUv.y;
    return Reveal.x * (1.0 - smoothstep(Reveal.y, Reveal.z, axis));
}

// 每轴逐 texel 取样。核在端点降到零，跨过整数半径时权重连续。
float gaussianWeight(float ratio) {
    return exp2(-6.0 * ratio * ratio) - 0.015625;
}

vec4 blurHorizontal() {
    ivec2 dimensions = textureSize(TextureSampler, 0);
    ivec2 center = clamp(ivec2(floor(localUv * vec2(dimensions))), ivec2(0), dimensions - 1);
    vec4 original = fetchBounded(TextureSampler, center, dimensions);
    float radius = min(blurRadius() / Size.x, 1.0) * float(dimensions.x);
    if (radius <= 1.0) return premultiply(original);

    vec4 sum = premultiply(original) * gaussianWeight(0.0);
    float totalWeight = gaussianWeight(0.0);
    for (int i = 1; float(i) < radius; ++i) {
        float weight = gaussianWeight(float(i) / radius);
        ivec2 delta = ivec2(i, 0);
        sum += (premultiply(fetchBounded(TextureSampler, center - delta, dimensions))
                + premultiply(fetchBounded(TextureSampler, center + delta, dimensions))) * weight;
        totalWeight += 2.0 * weight;
    }
    return sum / totalWeight;
}

vec4 revealTexture() {
    vec4 original = texture(TextureSampler, localUv);
    float radius = blurRadius();
    if (radius <= 0.0) return original;

    ivec2 dimensions = textureSize(TextureSampler, 0);
    // 暂存目标的 texel 原点位于左下，PNG 的 UV 原点位于左上。
    vec2 center = vec2(localUv.x, 1.0 - localUv.y) * vec2(dimensions) - 0.5;
    float pixelRadius = min(radius / Size.y, 1.0) * float(dimensions.y);
    vec4 sum = sampleScratch(center, dimensions) * gaussianWeight(0.0);
    float totalWeight = gaussianWeight(0.0);
    for (int i = 1; float(i) < pixelRadius; ++i) {
        float weight = gaussianWeight(float(i) / pixelRadius);
        vec2 delta = vec2(0.0, float(i));
        sum += (sampleScratch(center - delta, dimensions) + sampleScratch(center + delta, dimensions)) * weight;
        totalWeight += 2.0 * weight;
    }
    return straightColor(mix(premultiply(original), sum / totalWeight, smoothstep(0.0, 1.0, radius)));
}

vec4 radialColor() {
    vec2 farCorner = max(abs(Origin), abs(vec2(1.0) - Origin));
    float reach = max(0.00001, length(farCorner * Size) * GradientRadius);
    float distance = length((localUv - Origin) * Size);
    float progress = smoothstep(0.0, reach, distance);
    return straightColor(mix(premultiply(InnerColor), premultiply(OuterColor), progress));
}

void main() {
    if (EffectMode == 2) {
        fragColor = blurHorizontal();
        return;
    } else {
        vec4 color;
        if (EffectMode == 0) color = revealTexture();
        else if (EffectMode == 1) color = radialColor();
        else color = texture(TextureSampler, localUv);
        fragColor = color * ColorModulator;
    }
    if (fragColor.a <= 0.0) discard;
}
