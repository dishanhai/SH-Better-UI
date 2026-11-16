#version 150
// Original Schwarzschild null geodesics, G=c=M=1. See docs for assumptions/limits.
in vec2 iconUv;
uniform vec4 Surface; // aspect, zoom, unused, screen roll
uniform vec4 Physics; // observer inclination, disk outer radius, unused, unused
uniform vec2 Integration; // RK4 angular step, maximum steps
out vec4 fragColor;
const float CAMERA = 30.0;
const float OBSERVER_F = 1.0 - 2.0 / CAMERA;

vec2 derivative(vec2 state) { return vec2(state.y, 3.0 * state.x * state.x - state.x); }
vec2 rk4(vec2 state, float h) {
    vec2 a = derivative(state);
    vec2 b = derivative(state + h * a * 0.5);
    vec2 c = derivative(state + h * b * 0.5);
    vec2 d = derivative(state + h * c);
    return state + h * (a + 2.0 * b + 2.0 * c + d) / 6.0;
}
vec3 radialAt(float phi, vec3 initial, vec3 tangent) {
    return cos(phi) * initial + sin(phi) * tangent;
}
vec4 trace(vec2 screen) {
    float c = cos(Surface.w), s = sin(Surface.w);
    screen = mat2(c, -s, s, c) * screen;
    vec3 initial = vec3(0.0, sin(Physics.x), cos(Physics.x));
    vec3 up = vec3(0.0, cos(Physics.x), -sin(Physics.x));
    vec3 offset = screen.x * vec3(1.0, 0.0, 0.0) - screen.y * up;
    float offsetLength = length(offset);
    float sineAlpha = offsetLength * 0.82 / sqrt(1.0 + offsetLength * offsetLength * 0.82 * 0.82);
    float cosineAlpha = 1.0 / sqrt(1.0 + offsetLength * offsetLength * 0.82 * 0.82);
    float impact = CAMERA * sineAlpha / sqrt(OBSERVER_F);
    if (impact < 0.0001) return vec4(0.0, 0.0, 0.0, -1.0);
    vec3 tangent = offset / max(offsetLength, 0.000001);
    // Reversing the spatial backtrace momentum reverses L/E for emitted photons.
    float lambda = -impact * cross(initial, tangent).y;
    vec2 state = vec2(1.0 / CAMERA, cosineAlpha / impact);
    float phi = 0.0;
    float previousHeight = initial.y;
    for (int step = 0; step < 512; step++) {
        if (float(step) >= Integration.y) break;
        vec2 next = rk4(state, Integration.x);
        float nextPhi = phi + Integration.x;
        float height = radialAt(nextPhi, initial, tangent).y;
        if (height * previousHeight <= 0.0) {
            // Interpolate the plane crossing, then integrate the fractional step.
            float fraction = clamp(previousHeight / (previousHeight - height), 0.0, 1.0);
            vec2 hitState = rk4(state, Integration.x * fraction);
            float hitPhi = phi + Integration.x * fraction;
            float radius = 1.0 / max(hitState.x, 0.000001);
            if (hitState.x > 0.0 && radius >= 6.0 && radius <= Physics.y) {
                vec3 hit = radialAt(hitPhi, initial, tangent) * radius;
                float omega = 1.0 / pow(radius, 1.5);
                float shift = sqrt((1.0 - 3.0 / radius) / OBSERVER_F) / max(1.0 - omega * lambda, 0.001);
                // Prescribed thin-disk bolometric emissivity, not a GRMHD solution.
                float radialFlux = pow(6.0 / radius, 3.0) * (1.0 - sqrt(6.0 / radius));
                // Half-float data, never filtered: world hit x/z, base radiance, discrete fate.
                return vec4(hit.x, hit.z, 55.0 * radialFlux * pow(shift, 4.0), 1.0);
            }
        }
        if (next.x >= 0.5) return vec4(0.0, 0.0, 0.0, -1.0); // horizon r=2M
        if (next.x <= 0.0) break; // escaped to infinity
        state = next;
        phi = nextPhi;
        previousHeight = height;
    }
    // Escape/budget-exhausted pixels remain background, not falsely captured.
    // Store the optional reference-ring weight separately from the fate code.
    return vec4(0.0, 0.0, exp(-pow((impact - 5.196152423) / 0.045, 2.0)), 0.0);
}
void main() {
    vec2 aspect = vec2(max(Surface.x, 1.0), max(1.0 / Surface.x, 1.0));
    fragColor = trace((iconUv - 0.5) * aspect / Surface.y);
}
