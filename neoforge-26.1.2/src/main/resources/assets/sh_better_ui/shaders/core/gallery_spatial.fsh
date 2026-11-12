#version 330
// Author: dishanhai

in vec4 vertexColor;
in vec2 local;
flat in vec2 size;
flat in ivec2 parameters;
flat in int metadata;

out vec4 fragColor;

float shapeDistance(vec2 point) {
    vec2 halfSize = size * 0.5;
    vec2 centered = point - halfSize;
    if ((metadata & 8) != 0) {
        return (length(centered / halfSize) - 1.0) * min(halfSize.x, halfSize.y);
    }
    float radius = float((metadata >> 4) & 255) * 0.25;
    vec2 q = abs(centered) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

// Same Gaussian edge approximation as the legacy spatial shader.
float gaussianEdge(float z) {
    float x = abs(z) * 0.70710678;
    float t = 1.0 / (1.0 + 0.3275911 * x);
    float erfc = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * exp(-x * x);
    return z < 0.0 ? 1.0 - 0.5 * erfc : 0.5 * erfc;
}

vec2 unpackBytes(int bits) {
    return vec2(bits & 255, (bits >> 8) & 255) / 255.0;
}

vec3 linearize(vec3 srgb) {
    return mix(srgb / 12.92, pow((srgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), srgb));
}

vec3 encodeSrgb(vec3 linear) {
    return mix(linear * 12.92, 1.055 * pow(linear, vec3(1.0 / 2.4)) - 0.055,
               step(vec3(0.0031308), linear));
}

vec4 spatial(int mode) {
    float base = shapeDistance(local);
    float aa = max(fwidth(base) * 0.5, 0.0001);
    float mask = 1.0 - smoothstep(-aa, aa, base);
    if (mode == 0) {
        vec2 gradient = local / size * 2.0 - 1.0;
        vec2 lightDirection = unpackBytes(parameters.y) * 2.0 - 1.0;
        vec2 highlights = unpackBytes(parameters.x);
        float roughness = float((metadata >> 12) & 63) / 63.0;
        float depth = float((metadata >> 18) & 63) / 31.5 - 1.0;
        float light = dot(gradient, lightDirection) * 0.5 + 0.5;
        float shine = pow(clamp(light, 0.0, 1.0), mix(8.0, 1.0, roughness));
        float edge = 1.0 - smoothstep(0.0, max(1.0, aa), abs(base));
        vec3 color = linearize(vertexColor.rgb) * (1.0 + depth * (light - 0.5) * 0.65);
        color = mix(color, vec3(1.0), clamp(highlights.x * shine * 0.32 + highlights.y * edge * shine, 0.0, 1.0));
        return vec4(encodeSrgb(clamp(color, 0.0, 1.0)), mask * vertexColor.a);
    }
    bool inner = mode == 2;
    bool soft = (metadata & (1 << 23)) != 0;
    float spread = (float((metadata >> 18) & 31) - 16.0) * 0.5;
    float blur = float((metadata >> 12) & 63) * 0.25;
    float d = shapeDistance(local - vec2(parameters) / 16.0) + (inner ? spread : -spread);
    float shiftedAA = max(fwidth(d) * 0.5, 0.0001);
    float softened = soft && blur > 0.0
            ? gaussianEdge(d / max(blur, shiftedAA))
            : 1.0 - smoothstep(-shiftedAA, max(blur, shiftedAA), d);
    float alpha = inner ? mask * (1.0 - softened) : softened * (mode == 3 ? mask : 1.0 - mask);
    return vec4(vertexColor.rgb, alpha * vertexColor.a);
}

vec4 radialGradient() {
    float radius = float(metadata >> 4);
    float t = smoothstep(0.0, radius, length(local - vec2(parameters)));
    vec3 center = mix(vertexColor.rgb, vec3(1.0), 0.85);
    return vec4(mix(center, vertexColor.rgb, sqrt(t)), vertexColor.a * (1.0 - t));
}

vec4 waves() {
    vec2 cell = floor(local / 10.0);
    vec2 center = (cell + 0.5) * 10.0;
    float phase = float(metadata >> 6) * (6.2831853 / 4096.0);
    float wave = 0.5 + 0.5 * sin(cell.x * 0.50 + cell.y * 0.63 - phase);
    wave *= 0.58 + 0.42 * sin(cell.y * 0.59 - cell.x * 0.21 + phase);
    float distanceToPointer = length(center - vec2(parameters));
    float ripple = parameters.x < 0 ? 0.0 :
            exp(-distanceToPointer * 0.026) * (0.5 + 0.5 * cos(distanceToPointer * 0.19 - phase * 2.0));
    vec2 p = local - center;
    float scale = 1.0 + 3.2 * clamp(wave + ripple, 0.0, 1.0);
    int shape = (metadata >> 4) & 3;
    if (shape == 0) shape = 1 + int(mod(cell.x * 5.0 + cell.y, 3.0));
    float d = shape == 1 ? max(abs(p.x), abs(p.y)) - scale :
              shape == 2 ? length(p) - scale :
              max(abs(p.x) * 0.866 + p.y * 0.5, -p.y) - scale * 0.62;
    float mask = 1.0 - smoothstep(-0.4, 0.4, d);
    float brightness = clamp(0.37 + 0.6 * wave + ripple, 0.0, 1.0);
    return vec4(mix(vertexColor.rgb * 0.46, vertexColor.rgb, brightness), mask * vertexColor.a);
}

void main() {
    int mode = metadata & 7;
    fragColor = mode == 4 ? radialGradient() : mode == 5 ? waves() : spatial(mode);
}
