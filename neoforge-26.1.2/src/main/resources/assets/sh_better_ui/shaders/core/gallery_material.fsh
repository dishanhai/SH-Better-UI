#version 330
// Author: dishanhai

uniform sampler2D Sampler0;

in vec2 screenUv;
in vec2 localUv;
flat in vec2 size;
flat in ivec2 parameters;
flat in int effect;

out vec4 fragColor;

const float TAU = 6.2831853;

vec4 sampleLocal(vec2 position, mat2 localToUv, vec2 offset) {
    vec2 clamped = clamp(position + offset, vec2(0.5), size - vec2(0.5));
    return texture(Sampler0, screenUv + localToUv * (clamped - position));
}

vec3 directionalBlur(vec2 position, mat2 localToUv, float intensity) {
    float angle = radians(float(parameters.y));
    vec2 direction = vec2(cos(angle), -sin(angle)) * intensity * 2.0;
    vec3 rgb = vec3(0.0);
    float totalWeight = 0.0;
    for (int i = -6; i <= 6; i++) {
        float weight = exp(-float(i * i) / 9.0);
        rgb += sampleLocal(position, localToUv, direction * float(i)).rgb * weight;
        totalWeight += weight;
    }
    return rgb / totalWeight;
}

vec3 refractionWave(vec2 position, mat2 localToUv, float intensity, float phase, vec3 original) {
    vec2 delta = position - size * 0.5;
    float distanceFromCenter = length(delta);
    float waveRadius = mix(3.0, min(size.x, size.y) * 0.52, phase);
    float displacement = distanceFromCenter - waveRadius;
    float envelope = 1.0 - smoothstep(0.0, 9.0, abs(displacement));
    vec2 normal = delta / max(1.0, distanceFromCenter);
    vec2 bend = normal * sin(displacement * 0.48) * envelope * 6.0 * intensity;
    vec3 warped = sampleLocal(position, localToUv, bend).rgb;
    vec3 blurred = (sampleLocal(position, localToUv, bend - normal * 2.0).rgb
        + sampleLocal(position, localToUv, bend + normal * 2.0).rgb) * 0.5;
    vec3 refracted = mix(warped, blurred, 0.35 * intensity);
    refracted = mix(refracted, vec3(0.78, 0.92, 1.0), 0.26 * intensity * envelope * envelope);
    return mix(original, refracted, envelope);
}

vec3 shadeIcon(float phase) {
    if (effect == 2) {
        float diagonal = dot(localUv, vec2(0.68, 0.32));
        float sweep = 1.0 - smoothstep(0.0, 0.16, abs(diagonal - mix(-0.18, 1.18, phase)));
        vec3 gradient = mix(vec3(0.48, 0.74, 1.0), vec3(0.76, 1.0, 0.92), localUv.y);
        return mix(gradient, vec3(1.0), sweep * 0.92);
    }
    if (effect == 3) {
        float hue = phase + dot(localUv, vec2(0.34, 0.22));
        vec3 channels = 0.5 + 0.5 * cos(TAU * (hue + vec3(0.0, 0.6666667, 0.3333333)));
        return mix(vec3(0.42), vec3(1.0), channels);
    }
    if (effect == 4) {
        float coordinate = dot(localUv, vec2(0.70, 0.46)) + 0.12 * sin(TAU * localUv.y);
        float wave = pow(0.5 + 0.5 * cos(TAU * (coordinate - phase)), 10.0);
        vec3 base = mix(vec3(0.32, 0.54, 0.84), vec3(0.28, 0.85, 0.72), localUv.y);
        return mix(base, vec3(1.0), wave);
    }
    return vec3(mix(0.58, 1.0, 0.5 - 0.5 * cos(TAU * phase)));
}

void main() {
    float phase = float(parameters.y) / 1000.0;
    if (effect >= 2) {
        vec4 icon = texture(Sampler0, localUv);
        if (icon.a <= 0.0) discard;
        fragColor = vec4(icon.rgb * shadeIcon(phase), icon.a);
        return;
    }

    // Derivatives convert offsets in GUI units to the actual framebuffer scale on either backend.
    vec2 position = localUv * size;
    mat2 pixelToLocal = mat2(dFdx(position), dFdy(position));
    mat2 pixelToUv = mat2(dFdx(screenUv), dFdy(screenUv));
    mat2 localToUv = abs(determinant(pixelToLocal)) > 1.0e-10 ? pixelToUv * inverse(pixelToLocal) : mat2(0.0);
    vec4 original = texture(Sampler0, screenUv);
    if (original.a == 0.0) {
        fragColor = vec4(0.08, 0.10, 0.14, 0.88);
        return;
    }
    float intensity = float(parameters.x) / 100.0;
    vec3 color = effect == 0 ? directionalBlur(position, localToUv, intensity)
        : refractionWave(position, localToUv, intensity, phase, original.rgb);
    fragColor = vec4(color, 1.0);
}
