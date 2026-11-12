#version 330
// Author: dishanhai

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec2 screenUv;
out vec2 localUv;
flat out vec2 size;
flat out ivec2 parameters;
flat out int effect;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    screenUv = gl_Position.xy / gl_Position.w * 0.5 + 0.5;
    localUv = UV0;
    size = vec2(UV1);
    parameters = UV2;
    effect = int(Position.z);
}
