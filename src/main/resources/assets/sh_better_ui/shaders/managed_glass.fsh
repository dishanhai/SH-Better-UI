// Author: dishanhai
#version 150
// See META-INF/third-party/liquid-glass-optics for the optical references.

uniform sampler2D Backdrop;
uniform vec4 Viewport;
uniform vec4 Geometry;      // framebuffer center and half extents
uniform float Radius;       // framebuffer pixels
uniform vec4 Optics;        // refraction px, lensing, scattering, edge highlight
uniform vec4 Material;      // transmission, thickness px, blur px, light foreground
uniform vec3 Light;         // local light xy (positive up), strength
uniform vec3 FlatColor;
uniform ivec3 Mode;         // geometry, sampling level, high contrast
uniform int OpticalProfile; // 0 legacy, 1 Snell Regular, 2 Snell Clear
uniform vec4 ForegroundBounds; // normalized GUI left, top, right, bottom
uniform float BlurMaxLod;   // 0 when only the original capture is available

uniform float LayerDepth;
out vec4 fragColor;

float distanceToGlass(vec2 p) {
    if (Mode.x == 2) {
        vec2 q = p / Geometry.zw;
        float lengthQ = length(q);
        float gradient = length(q / Geometry.zw);
        return gradient > 0.00001 ? (lengthQ - 1.0) * lengthQ / gradient : -min(Geometry.z, Geometry.w);
    }
    vec2 q = abs(p) - Geometry.zw + Radius;
    vec2 corner = max(q, 0.0);
    float arc = length(corner);
    if (Mode.x == 3) {
        vec2 squared = corner * corner;
        arc = sqrt(sqrt(dot(squared, squared)));
    }
    return arc + min(max(q.x, q.y), 0.0) - Radius;
}

vec3 sceneLod(vec2 pixel, float lod) {
    vec2 uv = (pixel - Viewport.xy) / Viewport.zw;
    vec2 inset = 0.5 / vec2(textureSize(Backdrop, 0));
    return textureLod(Backdrop, clamp(uv, inset, 1.0 - inset), lod).rgb;
}

vec3 scene(vec2 pixel) {
    return sceneLod(pixel, 0.0);
}

vec3 frost(vec2 pixel, float radius) {
    vec2 texel = Viewport.zw / vec2(textureSize(Backdrop, 0));
    float lod = clamp(log2(max(radius / max(texel.x, texel.y), 1.0)) - 1.15, 0.0, BlurMaxLod);
    // Keep taps close at the selected mip level, even for a wide blur footprint.
    vec2 spread = min(vec2(radius * 0.52), texel * exp2(lod) * 2.0);
    vec2 x = vec2(spread.x, 0.0);
    vec2 y = vec2(0.0, spread.y);
    vec3 center = sceneLod(pixel, lod);
    vec3 axes = sceneLod(pixel + x, lod) + sceneLod(pixel - x, lod)
            + sceneLod(pixel + y, lod) + sceneLod(pixel - y, lod);
    if (Mode.y == 2) return center * 0.40 + axes * 0.15;
    vec3 corners = sceneLod(pixel + x + y, lod) + sceneLod(pixel + x - y, lod)
            + sceneLod(pixel - x + y, lod) + sceneLod(pixel - x - y, lod);
    return (center * 4.0 + axes * 2.0 + corners) * (1.0 / 16.0);
}

float luminance(vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

vec3 linearRgb(vec3 srgb) {
    return mix(srgb / 12.92, pow((srgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), srgb));
}

vec3 srgb(vec3 linearColor) {
    return mix(linearColor * 12.92, 1.055 * pow(linearColor, vec3(1.0 / 2.4)) - 0.055,
            step(vec3(0.0031308), linearColor));
}

vec3 protectForeground(vec3 color) {
    vec3 linearColor = linearRgb(clamp(color, 0.0, 1.0));
    float level = luminance(linearColor);
    if (Material.w > 0.5) {
        float ceiling = Mode.z != 0 ? 0.10 : 0.18;
        linearColor *= min(1.0, ceiling / max(level, 0.0001));
    } else {
        float floorValue = Mode.z != 0 ? 0.30 : 0.18;
        linearColor = mix(linearColor, vec3(1.0), max(0.0, floorValue - level) / max(1.0 - level, 0.0001));
    }
    return srgb(linearColor);
}

vec2 bevelNormal(float depth, float width) {
    float u = 1.0 - depth / width;
    float u2 = u * u;
    float height = sqrt(sqrt(max(0.0, 1.0 - u2 * u2)));
    // The slope of the convex profile is u^3 / height^3. Keep the two terms separate at the lip.
    return normalize(vec2(mix(1.25, 2.25, Optics.y) * u2 * u, height * height * height));
}

float raySlope(vec2 normal, float ior) {
    vec2 ray = refract(vec2(0.0, -1.0), normal, 1.0 / ior);
    return ray.x / ray.y;
}

float foregroundMask(vec2 local, float aa) {
    if (OpticalProfile == 0 || Mode.z != 0) return 1.0;
    if (any(lessThanEqual(ForegroundBounds.zw, ForegroundBounds.xy))) return 0.0;
    vec2 gui = vec2(local.x, -local.y) / Geometry.zw * 0.5 + 0.5;
    vec2 surfaceSize = 2.0 * Geometry.zw;
    vec2 boundsSize = (ForegroundBounds.zw - ForegroundBounds.xy) * surfaceSize;
    vec2 outside = max(max(ForegroundBounds.xy - gui, gui - ForegroundBounds.zw), 0.0) * surfaceSize;
    // Scale the falloff with the label so it stays soft as the GUI scale changes.
    float feather = min(0.8 * min(boundsSize.x, boundsSize.y), min(surfaceSize.x, surfaceSize.y) / 3.0);
    return 1.0 - smoothstep(0.0, max(2.0 * aa, feather), length(outside));
}

void main() {
    gl_FragDepth = mix(gl_DepthRange.near, gl_DepthRange.far, LayerDepth * 0.5 + 0.5);
    vec2 pixel = gl_FragCoord.xy;
    vec2 local = pixel - Geometry.xy;
    float d = distanceToGlass(local);
    float aa = clamp(fwidth(d) * 0.75, 0.65, 1.5);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (Mode.y == 0) {
        if (coverage <= 0.0) discard;
        fragColor = vec4(FlatColor, coverage);
        return;
    }

    float halfShort = min(Geometry.z, Geometry.w);
    float thickness = min(Material.y, halfShort);
    float shadowSize = max(1.0, min(18.0, thickness * 0.9 + 1.0));
    float shadow = (1.0 - coverage) * exp(-max(d, 0.0) / shadowSize)
            * (1.0 - smoothstep(shadowSize * 3.0, shadowSize * 4.0, d)) * 0.23;
    if (coverage <= 0.0) {
        if (shadow <= 0.0) discard;
        fragColor = vec4(0.0, 0.0, 0.0, shadow);
        return;
    }

    bool snell = OpticalProfile != 0;
    float depth = max(-d, 0.0);
    float rimWidth = snell ? min(halfShort * 0.8, thickness * 3.0)
            : max(1.0, min(halfShort * 0.75, 1.5 + thickness * 1.7));
    float rim = 1.0 - smoothstep(0.0, max(1.0, rimWidth), depth);
    float highlightWidth = snell ? max(2.0 * aa, 0.38 * thickness) : 0.0;
    vec2 normal = vec2(0.0);
    if (rim > 0.0 || (Optics.w > 0.0 && depth < highlightWidth)) {
        vec2 gradient = vec2(distanceToGlass(local + vec2(0.7, 0.0)) - distanceToGlass(local - vec2(0.7, 0.0)),
                distanceToGlass(local + vec2(0.0, 0.7)) - distanceToGlass(local - vec2(0.0, 0.7)));
        normal = gradient / max(length(gradient), 0.001);
    }
    vec2 bent = pixel;
    vec2 surfaceNormal = vec2(0.0, 1.0);
    vec2 rayTravel = vec2(0.0);
    if (snell && depth < rimWidth && Optics.x > 0.0) {
        surfaceNormal = bevelNormal(depth, rimWidth);
        // Scale the projected ray enough to fold the image at the rim, with a soft limit on small shapes.
        float reach = 4.0 * Optics.x;
        float travel = reach / (1.0 + reach / halfShort) / sqrt(1.5 * 1.5 - 1.0);
        rayTravel = -normal * travel;
        bent += rayTravel * raySlope(surfaceNormal, 1.50);
    } else if (!snell) {
        bent = pixel - normal * Optics.x * rim * (0.55 + 0.45 * rim)
                - local * Optics.y * 0.11 * rim * (1.0 - 0.65 * rim);
    }

    vec3 sharp = scene(bent);
    vec2 displacement = bent - pixel;
    vec2 position = local / Geometry.zw;
    // Keep straight-edge midpoints neutral and fade in channel separation toward the corners.
    float corner = abs(position.x * position.y);
    if (snell && Mode.y > 1 && corner > 0.10 && dot(displacement, displacement) > 0.01) {
        float dispersion = (Mode.y > 2 ? 0.045 : 0.030) * smoothstep(0.10, 0.70, corner);
        sharp.r = scene(pixel + rayTravel * raySlope(surfaceNormal, 1.50 - dispersion)).r;
        sharp.b = scene(pixel + rayTravel * raySlope(surfaceNormal, 1.50 + dispersion)).b;
    }
    float transmission = Material.x;
    vec3 transmitted = sharp;
    if (snell) {
        float scatter = Optics.z * (1.0 - 0.90 * rim);
        if (OpticalProfile == 2) {
            // Clear favors a polished face at low scatter. Tint and frost use independent curves.
            transmission = mix(transmission, 1.0, (1.0 - rim) * smoothstep(0.72, 0.95, transmission));
            scatter *= mix(smoothstep(0.15, 0.45, Optics.z), 1.0, rim);
        }
        if (Mode.y > 1 && scatter > 0.0 && Material.z > 0.0) {
            transmitted = mix(sharp, frost(bent, Material.z), scatter * smoothstep(0.0, 1.0, Material.z));
        }
    } else if (Mode.y > 1 && Optics.z > 0.0 && Material.z > 0.0) {
        vec2 stepSize = vec2(Material.z * 0.78);
        vec3 axis = (scene(bent + vec2(stepSize.x, 0)) + scene(bent - vec2(stepSize.x, 0))
                + scene(bent + vec2(0, stepSize.y)) + scene(bent - vec2(0, stepSize.y))) * 0.25;
        vec3 filtered = mix(sharp, axis, 0.75);
        float complexity = length(sharp - axis);
        if (Mode.y > 2) {
            vec2 diagonal = stepSize * 0.7;
            vec3 diagonals = (scene(bent + diagonal) + scene(bent - diagonal)
                    + scene(bent + vec2(diagonal.x, -diagonal.y))
                    + scene(bent + vec2(-diagonal.x, diagonal.y))) * 0.25;
            filtered = mix(filtered, diagonals, 0.30);
        }
        transmitted = mix(sharp, filtered, Optics.z * mix(0.65, 1.0, smoothstep(0.04, 0.32, complexity)));
    }
    float ambient = luminance(transmitted);
    float tone = smoothstep(0.08, 0.85, ambient);
    vec3 neutral = snell ? mix(vec3(0.12, 0.13, 0.15), vec3(0.63, 0.66, 0.69), tone)
            : mix(vec3(0.09, 0.11, 0.14), vec3(0.83, 0.86, 0.88), tone);
    vec3 color = mix(neutral, transmitted, transmission);
    float direction = dot(normal, vec2(-0.56, 0.83));
    vec2 relative = position - Light.xy;
    float spot = exp(-dot(relative, relative) * 2.4) * Light.z;
    if (snell) {
        // Confine the two-sided specular highlight to a thin stroke inside the contour.
        float lip = 1.0 - smoothstep(0.0, highlightWidth, depth);
        float highlight = lip * (0.10 + 0.42 * direction * direction + 0.18 * spot);
        color += (1.0 - color) * vec3(0.96, 0.98, 1.0) * (Optics.w * highlight);
    } else {
        float fresnel = rim * rim * (0.15 + 0.85 * max(0.0, direction));
        color += vec3(0.92, 0.97, 1.0) * Optics.w * (fresnel * mix(0.11, 0.045, ambient) + spot * rim * 0.10);
        color *= 1.0 - Optics.w * rim * max(0.0, -direction) * (0.06 + ambient * 0.09);
    }
    float protection = foregroundMask(local, aa);
    if (protection >= 1.0) color = protectForeground(color);
    else if (protection > 0.0) color = mix(color, protectForeground(color), protection);
    float alpha = coverage + shadow;
    fragColor = vec4(color * (coverage / max(alpha, 0.001)), alpha);
}
