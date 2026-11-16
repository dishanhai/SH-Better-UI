#version 150
// Author: dishanhai
#moj_import <sh_better_ui:rich_icon_effects.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float IconPhase;
uniform float IconEffect;
in vec2 iconUv;
out vec4 fragColor;
void main() {
    fragColor = richIconEffect(Sampler0, iconUv, IconPhase, int(IconEffect + 0.5)) * ColorModulator;
    if (fragColor.a <= 0.0) discard;
}
