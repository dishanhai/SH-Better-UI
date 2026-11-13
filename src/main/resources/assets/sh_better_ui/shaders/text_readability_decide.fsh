#version 150
// Author: dishanhai
uniform sampler2D Samples;
uniform usampler2D State;
uniform ivec2 Slot;
uniform uint Generation;
uniform uvec2 DeltaNanos;
uniform uvec2 StabilityNanos;
uniform ivec2 TransitionTiming; // duration and maximum advance per frame, in nanoseconds
uniform uint Original;
uniform uint InitialFill;
uniform uint Declared;
uniform ivec4 Rules; // sample budget, minimum valid, lock hue, outline/backing bit flags
uniform vec2 Thresholds; // contrast, group opacity
out uvec4 NextState;
out uvec4 Decision;
out vec4 Metrics;
out uvec4 NextTiming;

const uint ACTUAL = 0u, DECLARED = 1u, UNKNOWN = 2u;
const uint NONE = 0u, OUTLINE = 1u, BACKING = 2u;
const uint BLACK = 0xFF000000u, WHITE = 0xFFFFFFFFu;
const uint INSUFFICIENT = 1u, UNKNOWN_BACKGROUND = 2u, LOW_CONTRAST = 4u, INDIVISIBLE = 8u,
        HUE_LOCKED = 16u, NO_CANDIDATE = 32u, WAITING = 64u, NO_ID = 128u, LIMITED = 256u, OPACITY = 512u;
const int MAX_SAMPLES = 32;
vec2 points[MAX_SAMPLES]; // backdrop luminance and adjacent outline coverage
int count = 0;
uint source = ACTUAL;
float alpha;

uvec3 channels(uint color) { return (uvec3(color) >> uvec3(16u, 8u, 0u)) & uvec3(255u); }
float luminance(uint argb) {
    vec3 rgb = vec3(channels(argb)) / 255.0;
    vec3 linear = mix(rgb / 12.92, pow((rgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), rgb));
    return dot(linear, vec3(0.2126, 0.7152, 0.0722));
}
float ratio(float a, float b) { return (max(a, b) + 0.05) / (min(a, b) + 0.05); }
bool meets(float value) { return value + 0.000001 >= Thresholds.x; }

float contrastAt(float ink, vec2 samplePoint, uint kind, float protection, float opacity) {
    float bg = samplePoint.x;
    if (kind == BACKING) {
        return ratio(mix(bg, mix(protection, ink, alpha), opacity), mix(bg, protection, opacity));
    }
    float glyph = mix(bg, ink, alpha * opacity);
    float edge = kind == OUTLINE ? mix(bg, protection, samplePoint.y * opacity) : bg;
    return ratio(glyph, edge);
}

float minimum(float ink, uint kind, float protection) {
    float worst = 21.0;
    for (int i = 0; i < count; ++i) worst = min(worst, contrastAt(ink, points[i], kind, protection, 1.0));
    return worst;
}

vec2 assess(float ink, uint kind, float protection, float opacity) {
    float ordered[MAX_SAMPLES];
    for (int i = 0; i < count; ++i) {
        float value = contrastAt(ink, points[i], kind, protection, opacity);
        int j = i;
        while (j > 0) {
            if (value >= ordered[j - 1]) break;
            ordered[j] = ordered[j - 1];
            --j;
        }
        ordered[j] = value;
    }
    return vec2(ordered[(count - 1) / 10], ordered[0]);
}

bool passes(uint argb, bool allowBacking) {
    float ink = luminance(argb);
    if (source == UNKNOWN) {
        if (!allowBacking || meets(1.0)) return true;
        return meets(max(contrastAt(ink, vec2(1.0), BACKING, 0.0, 1.0),
                         contrastAt(ink, vec2(1.0), BACKING, 1.0, 1.0)));
    }
    int permitted = (count - 1) / 10;
    int failures = 0;
    for (int i = 0; i < count; ++i) {
        if (!meets(contrastAt(ink, points[i], NONE, 0.0, 1.0)) && ++failures > permitted) return false;
    }
    return true;
}

ivec2 hue(uvec3 rgb) {
    ivec3 c = ivec3(rgb);
    int hi = max(c.r, max(c.g, c.b));
    int delta = hi - min(c.r, min(c.g, c.b));
    int sector = hi == c.r ? c.g - c.b : hi == c.g ? 2 * delta + c.b - c.r : 4 * delta + c.r - c.g;
    return ivec2(sector < 0 ? sector + 6 * delta : sector, delta);
}

uint atLightness(uvec3 rgb, uint endpoint, uint step) {
    uvec3 c = (rgb * (32u - step) + endpoint * step + 16u) / 32u;
    return (Original & 0xFF000000u) | (c.r << 16u) | (c.g << 8u) | c.b;
}

bool sameHue(ivec2 originalHue, uint argb) {
    ivec2 candidate = hue(channels(argb));
    if (candidate.y == 0) return false;
    int divisor = originalHue.y * candidate.y;
    int distance = abs(originalHue.x * candidate.y - candidate.x * originalHue.y);
    // Hue uses six sectors per turn. An inclusive two-degree tolerance is 1/30 of a sector.
    return 30 * min(distance, 6 * divisor - distance) <= divisor;
}

uint choose(bool allowBacking) {
    if (passes(Original, allowBacking)) return Original;
    uvec3 rgb = channels(Original);
    ivec2 originalHue = hue(rgb);
    if (originalHue.y != 0) {
        for (uint step = 1u; step < 32u; ++step) {
            uint darker = atLightness(rgb, 0u, step);
            if (sameHue(originalHue, darker) && passes(darker, allowBacking)) return darker;
            uint lighter = atLightness(rgb, 255u, step);
            if (sameHue(originalHue, lighter) && passes(lighter, allowBacking)) return lighter;
        }
    }
    if (Rules.z != 0) return 0u;
    uint black = Original & 0xFF000000u;
    if (passes(black, allowBacking)) return black;
    uint white = Original | 0x00FFFFFFu;
    return passes(white, allowBacking) ? white : 0u;
}

bool atLeast(uvec2 a, uvec2 b) { return a.y > b.y || (a.y == b.y && a.x >= b.x); }

uint transitionFill(uint from, uint target, uint progress) {
    if (from == target || progress == 0u) return from;
    uint duration = uint(TransitionTiming.x) / 1000u;
    uint micros = progress / 1000u;
    uvec3 rgb = (channels(from) * (duration - micros) + channels(target) * micros + duration / 2u) / duration;
    return (target & 0xFF000000u) | (rgb.r << 16u) | (rgb.g << 8u) | rgb.b;
}

void main() {
    for (int i = 0; i < Rules.x; ++i) {
        vec2 point = texelFetch(Samples, ivec2(i, 0), 0).xy;
        if (point.x >= 0.0) points[count++] = point;
    }
    int actualCount = count;
    uint reasons = INDIVISIBLE;
    if (count < Rules.y) {
        reasons = INSUFFICIENT;
        source = Declared == 0u ? UNKNOWN : DECLARED;
        count = 1;
        points[0] = vec2(Declared == 0u ? 1.0 : luminance(Declared), 1.0);
        if (source == UNKNOWN) reasons |= UNKNOWN_BACKGROUND;
    }

    alpha = float(Original >> 24u) / 255.0;
    bool allowBacking = (Rules.w & 2) != 0;
    uint candidate = choose(allowBacking);
    if (candidate == 0u) {
        reasons |= NO_CANDIDATE;
        if (Rules.z != 0) reasons |= HUE_LOCKED;
    }
    uint from = (Original & 0xff000000u) | (InitialFill & 0x00ffffffu);
    uint target = from;
    uint progress = 0u;
    uint pending = 0u;
    uvec2 elapsed = uvec2(0u);
    if (Generation == 0u) {
        if (candidate != 0u && candidate != Original) reasons |= NO_ID;
    } else {
        uvec4 previous = texelFetch(State, Slot, 0);
        uvec4 timing = uvec4(0u);
        bool sameSource = previous.x == Generation && previous.w == source;
        if (previous.x == Generation) {
            timing = texelFetch(State, Slot + ivec2(32, 0), 0);
            if (sameSource) {
                from = previous.y;
                target = timing.z;
                progress = timing.w;
            } else {
                from = transitionFill(previous.y, timing.z, timing.w);
                target = from;
            }
        }
        if (candidate != 0u && candidate == target) {
            if (sameSource && previous.z == 0u) {
                uint stepNanos = DeltaNanos.y == 0u ? min(DeltaNanos.x, uint(TransitionTiming.y))
                        : uint(TransitionTiming.y);
                progress = min(progress + stepNanos, uint(TransitionTiming.x));
            }
        } else if (candidate != 0u) {
            if (sameSource && previous.z == candidate) {
                elapsed = timing.xy + DeltaNanos;
                if (elapsed.x < timing.x) ++elapsed.y;
            }
            if (atLeast(elapsed, StabilityNanos)) {
                from = transitionFill(from, target, progress);
                target = candidate;
                progress = 0u;
                elapsed = uvec2(0u);
            } else {
                pending = candidate;
                reasons |= WAITING;
            }
        }
    }

    uint fill = transitionFill(from, target, progress);
    float ink = luminance(fill);
    uint kind = NONE;
    float protect = 0.0;
    vec2 bare = assess(ink, NONE, 0.0, 1.0);
    vec2 protectedRatio = bare;
    vec2 finalRatio;
    if (source == UNKNOWN) {
        bare = vec2(-1.0);
        if (allowBacking && !meets(1.0)) {
            kind = BACKING;
            protect = minimum(ink, BACKING, 1.0) > minimum(ink, BACKING, 0.0) ? 1.0 : 0.0;
            protectedRatio = assess(ink, BACKING, protect, 1.0);
            finalRatio = assess(ink, BACKING, protect, Thresholds.y);
        } else {
            protectedRatio = vec2(1.0);
            finalRatio = protectedRatio;
        }
    } else {
        if (!meets(bare.y)) {
            reasons |= LOW_CONTRAST;
            if ((meets(bare.x) || !allowBacking) && (Rules.w & 1) != 0) {
                kind = OUTLINE;
                protect = minimum(ink, OUTLINE, 1.0) > minimum(ink, OUTLINE, 0.0) ? 1.0 : 0.0;
            }
            if (allowBacking && (kind == NONE || !meets(minimum(ink, kind, protect)))) {
                kind = BACKING;
                protect = minimum(ink, BACKING, 1.0) > minimum(ink, BACKING, 0.0) ? 1.0 : 0.0;
            }
            protectedRatio = assess(ink, kind, protect, 1.0);
        }
        finalRatio = assess(ink, kind, protect, Thresholds.y);
    }
    if (!meets(protectedRatio.y)) reasons |= LIMITED;
    if (Thresholds.y < 1.0 && meets(protectedRatio.y) && !meets(finalRatio.y)) reasons |= OPACITY;

    NextState = uvec4(Generation, from, pending, source);
    NextTiming = uvec4(elapsed, target, progress);
    Decision = uvec4(fill, kind == NONE ? 0u : (protect == 1.0 ? WHITE : BLACK),
                     candidate == fill ? 0u : candidate, source | (kind << 2u) | (reasons << 4u));
    Metrics = vec4(bare.x, protectedRatio.x, finalRatio.x, float(actualCount));
}
