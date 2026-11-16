// Author: dishanhai. Algorithms and source references: docs/RICH_ICON_EFFECTS.md.
float richHash(vec2 cell) {
    vec3 p = fract(vec3(cell.x, cell.y, cell.x) * vec3(0.1037, 0.1099, 0.0973));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.z) * p.y);
}

vec4 richSample(sampler2D source, vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec4(0.0);
    return texture(source, uv);
}

// Straight-alpha output. Only the outline expands the source mask, within the declared icon box.
vec4 richIconEffect(sampler2D source, vec2 uv, float phase, int kind) {
    const float circle = 6.2831853;
    vec4 pixel = texture(source, uv);
    float cycle = 0.5 - 0.5 * cos(circle * phase);
    if (kind == 0) {
        float lines = 0.76 + 0.24 * cos(circle * (uv.y * 7.0 - phase));
        vec3 tint = mix(vec3(0.25, 0.95, 1.0), vec3(0.75, 0.48, 1.0),
                0.5 + 0.5 * sin(circle * (uv.y * 0.65 + phase)));
        return vec4(pixel.rgb * tint * lines, pixel.a);
    }
    if (kind == 1) {
        float band = floor(uv.y * 10.0);
        float tick = floor(phase * 8.0);
        float shift = (richHash(vec2(band, tick)) - 0.5) * 0.14
                * step(0.68, richHash(vec2(tick, band + 41.0)));
        vec2 displaced = uv + vec2(shift, 0.0);
        vec3 channels = vec3(richSample(source, displaced + vec2(0.025, 0.0)).r,
                richSample(source, displaced).g, richSample(source, displaced - vec2(0.025, 0.0)).b);
        return vec4(channels, pixel.a);
    }
    if (kind == 2) {
        float scan = 1.0 - smoothstep(0.0, 0.16, abs(uv.y - phase));
        float lines = 0.75 + 0.25 * cos(circle * uv.y * 7.0);
        return vec4(pixel.rgb * mix(vec3(0.35, 0.70, 0.82) * lines, vec3(1.0), scan), pixel.a);
    }
    if (kind == 3) {
        vec2 cell = floor(uv * 7.0);
        float sparkle = pow(max(0.0, cos(circle * (phase + richHash(cell)))), 14.0);
        vec2 within = abs(fract(uv * 7.0) - 0.5);
        float star = (1.0 - smoothstep(0.02, 0.14, min(within.x, within.y)))
                * (1.0 - smoothstep(0.1, 0.5, max(within.x, within.y)));
        return vec4(mix(pixel.rgb * vec3(0.65, 0.78, 1.0), vec3(1.0), star * sparkle), pixel.a);
    }
    if (kind == 4) {
        float noise = richHash(floor(uv * 12.0));
        float threshold = cycle * 0.65;
        float visible = smoothstep(threshold - 0.06, threshold + 0.06, noise);
        float rim = (1.0 - smoothstep(0.02, 0.15, abs(noise - threshold))) * cycle;
        return vec4(mix(pixel.rgb, vec3(1.0, 0.65, 0.20), rim), pixel.a * visible);
    }
    vec2 delta = max(vec2(1.0) / vec2(textureSize(source, 0)), fwidth(uv) * 0.7);
    float adjacent = max(max(richSample(source, uv + vec2(delta.x, 0.0)).a,
            richSample(source, uv - vec2(delta.x, 0.0)).a),
            max(richSample(source, uv + vec2(0.0, delta.y)).a, richSample(source, uv - vec2(0.0, delta.y)).a));
    float edge = max(0.0, adjacent - pixel.a) * (0.65 + 0.35 * cycle);
    float alpha = pixel.a + edge * (1.0 - pixel.a);
    vec3 rgb = (pixel.rgb * pixel.a + vec3(0.35, 0.85, 1.0) * edge * (1.0 - pixel.a)) / max(alpha, 0.0001);
    return vec4(rgb, alpha);
}
