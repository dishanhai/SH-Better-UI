#version 150
// Independently authored SHBUI stylization, not a physical lensing simulation.
// One fixed-cost analytic pass: no ray marching, texture fetches or offscreen blur.
in vec2 iconUv;
uniform vec4 ColorModulator;
uniform vec4 Surface; // aspect, size, periodic time, disk tilt (radians)
uniform vec4 Disk; // width, lensing, brightness, glow
uniform float Opacity;
uniform vec4 InkColor;
uniform vec4 BackgroundColor;
out vec4 fragColor;

float band(float distance, float width) {
    float normalized = distance / max(width, 0.0001);
    return exp(-normalized * normalized);
}
void main() {
    if (Disk.z <= 0.0) {
        fragColor = vec4(BackgroundColor.rgb, BackgroundColor.a * Opacity) * ColorModulator;
        return;
    }
    // Normalize by the shorter edge so portrait/narrow panels retain a circular shadow.
    vec2 aspect = vec2(max(Surface.x, 1.0), max(1.0 / Surface.x, 1.0));
    vec2 p = (iconUv - 0.5) * aspect / Surface.y;
    float t = Surface.z;
    float tilt = Surface.w + 0.14 * sin(t);
    float c = cos(tilt), s = sin(tilt);
    vec2 q = vec2(c * p.x + s * p.y, -s * p.x + c * p.y);
    float radius = length(p);
    float pixel = max(length(fwidth(p)), 0.0001);
    float horizon = 0.22;
    float outside = smoothstep(horizon - pixel, horizon + pixel, radius);
    float nearCenter = exp(-q.x * q.x / 0.13);
    // The belt bends toward the shadow, while its two images rise above/below it.
    float warp = Disk.y * 0.027 * nearCenter * sin(q.x * 4.0 + t);
    float width = Disk.x * 0.52 + pixel;
    float disk = band(q.y - warp, width);
    float upperX = q.x - 0.055 * sin(t);
    float lowerX = q.x + 0.07 * sin(t);
    float upper = -0.255 * Disk.y * exp(-upperX * upperX / 0.085)
                * (1.0 + 0.24 * sin(t + q.x * 3.0));
    float lower = 0.27 * Disk.y * exp(-lowerX * lowerX / 0.115)
                * (1.0 + 0.26 * cos(t - q.x * 2.0));
    float arcWidth = width * 0.40 + pixel;
    float upperArc = band(q.y - upper, arcWidth * (1.0 + 0.18 * sin(t)));
    float lowerArc = band(q.y - lower, arcWidth * (0.75 - 0.16 * sin(t)));
    // Smooth material motion around the shadow, with no noisy per-frame sampling.
    // atan(0, 0) is undefined: odd-sized targets can sample the exact center.
    float angle = radius > 0.000001 ? atan(q.y, q.x) : 0.0;
    float streaming = 0.82 + 0.18 * sin(angle * 3.0 - t + radius * 9.0);
    float directional = 0.78 + 0.22 * sin(angle + t);
    float coreLight = disk * 2.8 + Disk.y * (upperArc * 2.0 + lowerArc * 1.5) * nearCenter;
    float halo = 0.0;
    if (Disk.w > 0.0) {
        halo = band(q.y - warp, width * 2.8) * 0.30
             + (band(q.y - upper, arcWidth * 3.0) + band(q.y - lower, arcWidth * 3.0)) * nearCenter * 0.22;
    }
    float rim = band(radius - horizon - pixel, pixel * 1.2 + 0.0015) * (0.06 + 0.08 * directional);
    float energy = ((coreLight * streaming * directional + halo * Disk.w) * outside + rim) * Disk.z;
    float light = 1.0 - exp(-energy);
    // The central silhouette remains black even with a transparent base color.
    float inkAlpha = light * InkColor.a;
    vec3 premul = InkColor.rgb * inkAlpha + BackgroundColor.rgb * BackgroundColor.a * (1.0 - inkAlpha);
    float alpha = inkAlpha + BackgroundColor.a * (1.0 - inkAlpha);
    premul *= outside;
    alpha = (1.0 - outside) + alpha * outside;
    vec3 rgb = alpha > 0.0001 ? premul / alpha : vec3(0.0);
    fragColor = vec4(rgb, alpha * Opacity) * ColorModulator;
}
