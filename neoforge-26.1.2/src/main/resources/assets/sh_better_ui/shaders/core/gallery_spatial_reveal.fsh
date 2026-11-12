#version 330
// Author: dishanhai

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 local;
in vec2 screenUv;
flat in vec2 size;
flat in ivec2 parameters;
flat in int metadata;

out vec4 fragColor;

vec3 blurBackdrop(vec2 uv, vec2 stepSize) {
    vec3 samples = texture(Sampler0, uv).rgb * 4.0;
    samples += texture(Sampler0, uv + stepSize * vec2(1.0, 0.0)).rgb * 2.0;
    samples += texture(Sampler0, uv - stepSize * vec2(1.0, 0.0)).rgb * 2.0;
    samples += texture(Sampler0, uv + stepSize * vec2(0.0, 1.0)).rgb * 2.0;
    samples += texture(Sampler0, uv - stepSize * vec2(0.0, 1.0)).rgb * 2.0;
    samples += texture(Sampler0, uv + stepSize).rgb;
    samples += texture(Sampler0, uv - stepSize).rgb;
    samples += texture(Sampler0, uv + stepSize * vec2(-1.0, 1.0)).rgb;
    samples += texture(Sampler0, uv + stepSize * vec2(1.0, -1.0)).rgb;
    return samples / 16.0;
}

void main() {
    bool contrast = (metadata & 32) != 0;
    if ((metadata & 7) == 7) {
        vec3 cover = contrast ? vec3(0.07) : vec3(0.13, 0.18, 0.27);
        if ((metadata & 16) != 0) {
            vec2 stepSize = vec2(length(dFdx(screenUv)), length(dFdy(screenUv))) * 5.0;
            cover = mix(blurBackdrop(screenUv, stepSize), cover, 0.30);
        }
        fragColor = vec4(cover, 1.0) * vertexColor;
        return;
    }
    vec2 center = vec2(parameters);
    float progress = float(metadata >> 6) / 1000.0;
    float radius = length(max(center, size - center)) * progress;
    float reveal = progress >= 1.0 ? 1.0 :
                   1.0 - smoothstep(radius - 3.0, radius + 3.0, length(local - center));
    if (contrast) reveal = step(0.5, reveal);
    vec4 image = texture(Sampler0, local / size);
    fragColor = vec4(image.rgb, reveal * image.a) * vertexColor;
}
