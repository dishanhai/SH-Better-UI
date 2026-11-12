#version 150

in vec4 vertexColor;
in vec2 local;
out vec4 fragColor;

uniform vec4 SizeMode;
uniform vec4 Radii;
uniform vec4 Shadow;
uniform vec4 Surface;
uniform vec4 Light;
uniform float PathVertices[149];

vec2 pathPoint(int i) {
    return vec2(PathVertices[i], PathVertices[i + 1]);
}

// Contours have implicit closure. A horizontal ray applies even-odd fill across all contours.
float distanceToPath(vec2 p, int length) {
    float nearest = 1e30;
    bool inside = false;
    int cursor = 0;
    while (cursor < length) {
        int end = cursor + 1 + 2 * int(PathVertices[cursor]);
        vec2 a = pathPoint(end - 2);
        for (int i = cursor + 1; i < end; i += 2) {
            vec2 b = pathPoint(i);
            vec2 edge = b - a;
            vec2 delta = p - a;
            vec2 offset = delta - edge * clamp(dot(delta, edge) / max(dot(edge, edge), 1e-30), 0.0, 1.0);
            nearest = min(nearest, dot(offset, offset));
            if ((a.y > p.y) != (b.y > p.y)) {
                inside = inside != (p.x < a.x + edge.x * (delta.y / edge.y));
            }
            a = b;
        }
        cursor = end;
    }
    return sqrt(nearest) * (inside ? -1.0 : 1.0);
}

float distanceToShape(vec2 p) {
    if (Light.w > 0.0) return distanceToPath(p, int(Light.w));
    vec2 halfSize = SizeMode.xy * 0.5;
    p -= halfSize;
    if (SizeMode.w > 0.5) {
        return (length(p / halfSize) - 1.0) * min(halfSize.x, halfSize.y);
    }
    float radius = p.y < 0.0 ? (p.x < 0.0 ? Radii.x : Radii.y)
                             : (p.x < 0.0 ? Radii.w : Radii.z);
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

// Approximation to the integral of a Gaussian across an infinite straight edge.
float gaussianEdge(float z) {
    float x = abs(z) * 0.70710678;
    float t = 1.0 / (1.0 + 0.3275911 * x);
    float erfc = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * exp(-x * x);
    return z < 0.0 ? 1.0 - 0.5 * erfc : 0.5 * erfc;
}

float coverage(float d, float aa) {
    return 1.0 - smoothstep(-aa, aa, d);
}

vec3 linearize(vec3 srgb) {
    return mix(srgb / 12.92, pow((srgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), srgb));
}

vec3 encodeSrgb(vec3 linear) {
    return mix(linear * 12.92, 1.055 * pow(linear, vec3(1.0 / 2.4)) - 0.055,
               step(vec3(0.0031308), linear));
}

void main() {
    float base = distanceToShape(local);
    float aa = max(fwidth(base) * 0.5, 0.00001);
    float mask = coverage(base, aa);
    float mode = SizeMode.z;
    float alpha;
    vec3 color = vertexColor.rgb;
    if (mode < 0.5) {
        vec2 gradient = local / SizeMode.xy * 2.0 - 1.0;
        float light = dot(gradient, Light.xy) * 0.5 + 0.5;
        float shine = pow(clamp(light, 0.0, 1.0), mix(8.0, 1.0, Surface.w));
        float edge = 1.0 - smoothstep(0.0, max(1.0, aa), abs(base));
        vec3 linear = linearize(color);
        linear *= 1.0 + Surface.z * (light - 0.5) * 0.65;
        linear = mix(linear, vec3(1.0), clamp(Surface.x * shine * 0.32 + Surface.y * edge * shine, 0.0, 1.0));
        color = encodeSrgb(clamp(linear, 0.0, 1.0));
        alpha = mask;
    } else {
        bool inner = mode > 1.5 && mode < 2.5;
        float shifted = all(equal(Shadow.xy, vec2(0.0))) ? base : distanceToShape(local - Shadow.xy);
        float d = shifted + (inner ? Shadow.w : -Shadow.w);
        float blur = Shadow.z;
        float shiftedAA = max(fwidth(d) * 0.5, 0.00001);
        float softMask = Light.z > 0.5 && blur > 0.0
                ? gaussianEdge(d / max(blur, shiftedAA))
                : 1.0 - smoothstep(-shiftedAA, max(blur, shiftedAA), d);
        alpha = inner ? mask * (1.0 - softMask) : softMask * (mode > 2.5 ? mask : 1.0 - mask);
    }
    fragColor = vec4(color, alpha * vertexColor.a);
}
