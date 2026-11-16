#version 150
// SHBUI native GUI vertex adapter. Sprite UVs carry crystal-local coordinates.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InversePose;
out vec2 crystalUv;
out vec2 clipPosition;
out vec4 crystalColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    crystalUv = UV0;
    clipPosition = (InversePose * vec4(Position, 1.0)).xy;
    crystalColor = Color;
}
