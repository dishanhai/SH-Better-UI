#version 150

// SHBUI original black-hole background, following the native GUI pose.
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 iconUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    iconUv = UV0;
}
