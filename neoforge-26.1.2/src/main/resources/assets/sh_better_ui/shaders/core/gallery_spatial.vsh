#version 330
// Author: dishanhai

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec4 vertexColor;
out vec2 local;
#ifdef GALLERY_REVEAL
// Vulkan assigns varying locations from the inputs shared with the fragment stage.
out vec2 screenUv;
#endif
flat out vec2 size;
flat out ivec2 parameters;
flat out int metadata;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
#ifdef GALLERY_REVEAL
    screenUv = gl_Position.xy / gl_Position.w * 0.5 + 0.5;
#endif
    vertexColor = Color;
    local = UV0;
    size = vec2(UV1);
    parameters = UV2;
    metadata = int(Position.z);
}
