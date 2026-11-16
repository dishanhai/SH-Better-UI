#version 150
in vec2 iconUv;
uniform sampler2D Scene;
uniform vec4 Finish; // texel x/y, glow, opacity
uniform vec4 ColorModulator;
uniform vec2 ContentScale; // used viewport / reusable texture capacity
vec2 sceneUv(vec2 uv) {
    vec2 halfTexel = Finish.xy * ContentScale * 0.5;
    return clamp(uv * ContentScale, halfTexel, ContentScale - halfTexel);
}
out vec4 fragColor;
vec3 bright(vec2 uv) {
    vec4 sampleColor = texture(Scene, sceneUv(uv));
    return max(sampleColor.rgb - vec3(0.35), vec3(0.0));
}
vec3 kernel(vec2 uv, vec2 stride) {
    // A bounded 7x7 Gaussian convolution, rather than shifted copies of thin rings.
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    for (int y = -3; y <= 3; y++) {
        for (int x = -3; x <= 3; x++) {
            float w = exp(-0.32 * float(x * x + y * y));
            sum += bright(uv + vec2(float(x), float(y)) * stride) * w;
            weight += w;
        }
    }
    return sum / weight;
}
void main() {
    // Small reconstruction low-pass removes hard stair steps from the bounded trace grid.
    vec2 dx = vec2(Finish.x, 0.0), dy = vec2(0.0, Finish.y);
    vec4 scene = texture(Scene, sceneUv(iconUv)) * 0.4;
    scene += (texture(Scene, sceneUv(iconUv + dx)) + texture(Scene, sceneUv(iconUv - dx))
            + texture(Scene, sceneUv(iconUv + dy)) + texture(Scene, sceneUv(iconUv - dy))) * 0.15;
    vec3 halo = vec3(0.0);
    if (Finish.z > 0.0) {
        halo = kernel(iconUv, Finish.xy * (2.0 + Finish.z * 1.5)) * Finish.z;
    }
    vec3 premul = scene.rgb + (vec3(1.0) - scene.rgb) * (vec3(1.0) - exp(-halo));
    float alpha = clamp(scene.a + max(halo.r, max(halo.g, halo.b)), 0.0, 1.0);
    vec3 rgb = alpha > 0.0001 ? premul / alpha : vec3(0.0);
    fragColor = vec4(rgb, clamp(alpha, 0.0, 1.0) * Finish.w) * ColorModulator;
}
