#version 330
// Author: dishanhai

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec2 screenUv;
out vec2 localPosition;
flat out vec4 tint;
flat out vec2 halfSize;
flat out float radius;
flat out vec4 refractionBlurTransmissionLensing;
flat out vec3 scatteringRimThickness;
flat out float opacity;

vec2 unpackPair(int packedValue) {
    return vec2(packedValue & 255, (packedValue >> 8) & 255) / 255.0;
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    screenUv = gl_Position.xy / gl_Position.w * 0.5 + 0.5;
    halfSize = vec2(UV1) * 0.5;
    localPosition = (2.0 * UV0 - 1.0) * halfSize;
    tint = Color;
    radius = Position.z;
    refractionBlurTransmissionLensing = vec4(unpackPair(UV2.x) * 32.0, unpackPair(UV2.y));
    scatteringRimThickness = Normal * vec3(1.0, 1.0, 32.0);
    opacity = LineWidth;
}
