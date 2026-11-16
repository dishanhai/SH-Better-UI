#version 150
in vec2 iconUv;
uniform sampler2D Source;
uniform vec2 ContentScale;
uniform vec4 Filter; // UV direction x/y, threshold, unused
uniform vec2 Texel;
out vec4 fragColor;
vec3 sampleBright(vec2 uv) {
    vec2 halfTexel = Texel * ContentScale * 0.5;
    vec2 at = clamp(uv * ContentScale, halfTexel, ContentScale - halfTexel);
    return max(texture(Source, at).rgb - vec3(Filter.z), vec3(0.0));
}
void main() {
    vec3 result = sampleBright(iconUv) * 0.227027;
    result += (sampleBright(iconUv + Filter.xy) + sampleBright(iconUv - Filter.xy)) * 0.194595;
    result += (sampleBright(iconUv + Filter.xy * 2.0) + sampleBright(iconUv - Filter.xy * 2.0)) * 0.121622;
    result += (sampleBright(iconUv + Filter.xy * 3.0) + sampleBright(iconUv - Filter.xy * 3.0)) * 0.054054;
    result += (sampleBright(iconUv + Filter.xy * 4.0) + sampleBright(iconUv - Filter.xy * 4.0)) * 0.016216;
    fragColor = vec4(result, 1.0);
}
