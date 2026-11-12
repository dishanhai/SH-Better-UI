#version 330
// Author: dishanhai

uniform sampler2D Sampler0;

in vec2 screenUv;
in vec2 localPosition;
flat in vec4 tint;
flat in vec2 halfSize;
flat in float radius;
flat in vec4 refractionBlurTransmissionLensing;
flat in vec3 scatteringRimThickness;
flat in float opacity;

out vec4 fragColor;

vec4 sampleBackdrop(vec2 uv, mat2 localToUv, float blurRadius) {
    if (blurRadius < 0.06) return texture(Sampler0, uv);

    // Two sampling distances keep periodic textures from lining up with every tap.
    vec2 dx = localToUv[0] * blurRadius * 0.5;
    vec2 dy = localToUv[1] * blurRadius * 0.5;
    vec2 diagonalX = localToUv[0] * blurRadius * 0.70710678;
    vec2 diagonalY = localToUv[1] * blurRadius * 0.70710678;
    return texture(Sampler0, uv) * 0.25
        + (texture(Sampler0, uv - dx) + texture(Sampler0, uv + dx)
        + texture(Sampler0, uv - dy) + texture(Sampler0, uv + dy)) * 0.125
        + (texture(Sampler0, uv - diagonalX - diagonalY) + texture(Sampler0, uv + diagonalX - diagonalY)
        + texture(Sampler0, uv - diagonalX + diagonalY) + texture(Sampler0, uv + diagonalX + diagonalY)) * 0.0625;
}

// Reuse the managed-glass convex bevel and Snell ray model in local GUI units.
vec2 refractedOffset(vec2 outward, float depth, float bevelWidth, float halfShort, float refraction, float lensing) {
    if (depth >= bevelWidth || refraction <= 0.0) return vec2(0.0);
    float u = 1.0 - depth / bevelWidth;
    float u2 = u * u;
    float height = sqrt(sqrt(max(0.0, 1.0 - u2 * u2)));
    vec2 bevelNormal = normalize(vec2(mix(1.25, 2.25, lensing) * u2 * u, height * height * height));
    vec2 ray = refract(vec2(0.0, -1.0), bevelNormal, 1.0 / 1.5);
    float reach = 4.0 * refraction;
    float travel = reach / (1.0 + reach / halfShort) / sqrt(1.5 * 1.5 - 1.0);
    return -outward * travel * (ray.x / ray.y);
}

void main() {
    vec2 corner = abs(localPosition) - (halfSize - radius);
    vec2 outside = max(corner, 0.0);
    float distanceToEdge = length(outside) + min(max(corner.x, corner.y), 0.0) - radius;
    float pixelWidth = max(fwidth(distanceToEdge), 0.001);
    float coverage = 1.0 - smoothstep(-pixelWidth * 0.5, pixelWidth * 0.5, distanceToEdge);
    mat2 pixelToLocal = mat2(dFdx(localPosition), dFdy(localPosition));
    mat2 pixelToUv = mat2(dFdx(screenUv), dFdy(screenUv));
    if (coverage <= 0.0) discard;

    vec2 outward = (outside.x > 0.0 || outside.y > 0.0)
        ? normalize(outside) : corner.x > corner.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    outward *= sign(localPosition);
    float inwardDistance = max(0.0, -distanceToEdge);
    float halfShort = min(halfSize.x, halfSize.y);
    float thickness = min(scatteringRimThickness.z, halfShort);
    float bevelWidth = min(halfShort * 0.8, thickness * 3.0);
    float highlightWidth = max(2.0 * pixelWidth, thickness * 0.38);
    float rim = 1.0 - smoothstep(0.0, highlightWidth, inwardDistance);

    mat2 localToUv = abs(determinant(pixelToLocal)) > 1.0e-10 ? pixelToUv * inverse(pixelToLocal) : mat2(0.0);
    float refraction = refractionBlurTransmissionLensing.x;
    float blurRadius = refractionBlurTransmissionLensing.y;
    float transmission = refractionBlurTransmissionLensing.z;
    float lensing = refractionBlurTransmissionLensing.w;
    vec2 bend = refractedOffset(outward, inwardDistance, bevelWidth, halfShort, refraction, lensing);
    vec4 scene = sampleBackdrop(screenUv + localToUv * bend, localToUv, blurRadius);
    if (scene.a == 0.0) {
        fragColor = vec4(tint.rgb, tint.a * coverage * opacity);
        return;
    }

    float luma = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 scattered = mix(scene.rgb, vec3(luma), scatteringRimThickness.x * 0.25);
    vec3 material = mix(tint.rgb, scattered, transmission * (1.0 - tint.a));
    float incidentLight = 0.35 + 0.65 * max(0.0, dot(outward, normalize(vec2(-0.6, -1.0))));
    float highlight = incidentLight * rim * scatteringRimThickness.y * 0.55;
    fragColor = vec4(mix(material, vec3(0.88, 0.94, 1.0), highlight), coverage * opacity);
}
