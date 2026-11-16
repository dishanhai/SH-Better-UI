#version 150
// Cheap per-frame emission shading over a stationary Schwarzschild hit map.
in vec2 iconUv;
uniform sampler2D Geometry;
uniform vec2 ContentScale;
uniform vec4 Motion; // phase, brightness, optional outline, unused
uniform vec4 InkColor;
uniform vec4 BackgroundColor;
out vec4 fragColor;
void main() {
    vec4 base = vec4(BackgroundColor.rgb * BackgroundColor.a, BackgroundColor.a);
    if (Motion.y <= 0.0) { fragColor = base; return; }
    // Geometry has NEAREST filtering; shade at exactly its own pixel grid.
    // Discrete fate and unrelated world hits must never interpolate at silhouettes.
    vec4 hit = texture(Geometry, iconUv * ContentScale);
    // Evaluate derivatives outside fate branches; unwrap angular derivatives at +/-pi.
    float radius = length(hit.xy);
    float angle = radius > 0.000001 ? atan(hit.y, hit.x) : 0.0;
    vec2 angularDelta = vec2(dFdx(angle), dFdy(angle));
    angularDelta = atan(sin(angularDelta), cos(angularDelta));
    vec2 radialDelta = vec2(dFdx(radius), dFdy(radius));
    float armFootprint = dot(abs(6.0 * angularDelta + 2.2 * radialDelta), vec2(1.0));
    float detailFootprint = dot(abs(13.0 * angularDelta - 0.75 * radialDelta), vec2(1.0));
    float armContrast = 1.0 - smoothstep(1.2, 3.14, armFootprint);
    float detailContrast = 1.0 - smoothstep(1.0, 2.4, detailFootprint);
    if (hit.a < -0.5) { fragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
    float light;
    if (hit.a > 0.5) {
        // Prescribed rotating emissivity filaments, not changes to the metric or GRMHD.
        // Integer phase harmonics make the 2pi clock wrap exactly continuous.
        float arm = 0.5 + 0.5 * armContrast * sin(6.0 * angle + radius * 2.2 - 8.0 * Motion.x);
        float detail = 0.5 + 0.5 * detailContrast * sin(13.0 * angle - radius * 0.75 - 16.0 * Motion.x);
        float pattern = 0.045 + arm * arm * (1.25 + 0.35 * detail);
        float emission = hit.b * pattern * Motion.y;
        light = emission / (1.0 + emission);
        float alpha = InkColor.a + BackgroundColor.a * (1.0 - InkColor.a);
        fragColor = vec4(InkColor.rgb * light * InkColor.a + base.rgb * (1.0 - InkColor.a), alpha);
    } else {
        light = hit.b * Motion.z;
        float alpha = light * InkColor.a;
        fragColor = vec4(InkColor.rgb * alpha + base.rgb * (1.0 - alpha), alpha + base.a * (1.0 - alpha));
    }
}
