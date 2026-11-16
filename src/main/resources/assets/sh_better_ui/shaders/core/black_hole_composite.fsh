#version 150
in vec2 iconUv;
uniform sampler2D Scene;
uniform sampler2D Bloom;
uniform vec4 ContentScale; // scene xy, bloom zw
uniform vec4 Finish; // scene texel xy, glow, opacity
uniform vec4 ColorModulator;
out vec4 fragColor;
void main() {
    vec2 halfTexel = Finish.xy * ContentScale.xy * 0.5;
    vec2 sceneUv = clamp(iconUv * ContentScale.xy, halfTexel, ContentScale.xy - halfTexel);
    // Never blur the scene itself; only the independently filtered bright layer is soft.
    vec4 scene = texture(Scene, sceneUv);
    vec3 halo = vec3(0.0);
    if (Finish.z > 0.0) {
        vec2 bloomHalfTexel = 0.5 / vec2(textureSize(Bloom, 0));
        vec2 bloomUv = clamp(iconUv * ContentScale.zw, bloomHalfTexel, ContentScale.zw - bloomHalfTexel);
        halo = texture(Bloom, bloomUv).rgb * Finish.z;
    }
    vec3 premul = scene.rgb + (vec3(1.0) - scene.rgb) * (vec3(1.0) - exp(-halo));
    float alpha = clamp(scene.a + max(halo.r, max(halo.g, halo.b)), 0.0, 1.0);
    vec3 rgb = alpha > 0.0001 ? premul / alpha : vec3(0.0);
    fragColor = vec4(rgb, alpha * Finish.w) * ColorModulator;
}
