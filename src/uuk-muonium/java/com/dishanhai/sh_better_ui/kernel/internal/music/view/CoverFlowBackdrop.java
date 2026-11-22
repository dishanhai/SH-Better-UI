// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.RuntimeEffectBuilder;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;

import java.util.Arrays;

/** UUK cover background. Holds only the two sources needed for a crossfade. */
public final class CoverFlowBackdrop implements AutoCloseable {
    private static final long FADE_NANOS = 640_000_000L;
    private static final long COVER_WAIT_NANOS = 750_000_000L;
    private static final Rect UNIT = Rect.makeWH(1f, 1f);
    private static final String FLOW_SKSL = """
            uniform shader cover;
            uniform shader outgoing;
            uniform float phase;
            uniform float swell;
            uniform float fade;
            uniform float contrast;

            float2 rotate(float2 p, float angle) {
                float s = sin(angle), c = cos(angle);
                return float2(c * p.x - s * p.y, s * p.x + c * p.y);
            }

            float2 curl(float2 p, float2 center, float amount) {
                float2 d = p - center;
                float falloff = max(0.0, 1.0 - dot(d, d) * 1.35);
                return center + rotate(d, amount * falloff * falloff);
            }

            half4 main(float2 uv) {
                float2 drift = float2(sin(phase), cos(phase * 0.73));
                float2 p = uv - 0.5;
                float2 a = curl(p, -0.20 * drift, 0.85 + 0.52 * sin(phase * 0.91) + swell);
                float2 b = curl(p, 0.22 * drift.yx, -0.95 + 0.50 * cos(phase * 0.64) - swell);
                a = 0.5 + rotate(a, 0.3 + 0.48 * drift.x) * 0.84 + 0.13 * drift;
                b = 0.5 + rotate(b, 2.4 + 0.49 * drift.y) * 0.81 - 0.12 * drift.yx;

                // Protect the lyric column and transport area while leaving color near the cover.
                half shade = half(1.12 + 0.035 * swell
                    - 0.24 * smoothstep(0.35, 0.85, uv.x)
                    - 0.38 * smoothstep(0.40, 1.00, uv.y));
                half spread = half(0.24 + 0.14 * smoothstep(0.2, 0.85, uv.y));
                half3 color = mix(cover.eval(a).rgb, cover.eval(b).rgb, spread);
                half3 dark = half3(0.035), light = half3(0.45);
                color = clamp(color * shade, dark, light);
                if (fade < 1.0) {
                    half3 previous = mix(outgoing.eval(a).rgb, outgoing.eval(b).rgb, spread);
                    color = mix(clamp(previous * shade, dark, light), color, half(fade));
                }
                return half4(mix(color, color * 0.42 + 0.018, half(contrast)), 1.0);
            }
            """;

    private Shader current;
    private Shader previous;
    private byte[] mountedArtwork;
    private final int[] mountedPalette = new int[3];
    private boolean usingArtwork;
    private String mountedKey;
    private String requestedKey;
    private long requestedAt;
    private long fadeStart;

    /** The fallback colors are opaque, contrast-adjusted colors in highlight-to-shadow order. */
    public void draw(Canvas canvas, Rect bounds, float alpha, String coverKey, int[] fallbackColors,
                     long now, boolean still, boolean highContrast, float phase, float swell) {
        updateSource(coverKey, fallbackColors, now, still);
        float fade = previous == null ? 1f : (float) (now - fadeStart) / FADE_NANOS;
        fade = fade * fade * (3f - 2f * fade);

        int saved = canvas.save();
        try (Paint paint = new Paint().setAlphaf(alpha)) {
            canvas.translate(bounds.getLeft(), bounds.getTop());
            canvas.scale(bounds.getWidth(), bounds.getHeight());
            RuntimeEffect effect = FlowEffect.INSTANCE;
            if (effect == null) {
                paint.setShader(current);
                canvas.drawRect(UNIT, paint);
                return;
            }
            try (RuntimeEffectBuilder builder = new RuntimeEffectBuilder(effect)) {
                builder.setChild("cover", current);
                builder.setChild("outgoing", previous == null ? current : previous);
                builder.setUniform("phase", phase);
                builder.setUniform("swell", swell);
                builder.setUniform("fade", fade);
                builder.setUniform("contrast", highContrast ? 1f : 0f);
                try (Shader flow = builder.makeShader()) {
                    paint.setShader(flow);
                    canvas.drawRect(UNIT, paint);
                }
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    private void updateSource(String key, int[] palette, long now, boolean still) {
        if (!key.equals(requestedKey)) {
            requestedKey = key;
            requestedAt = now;
        }
        if (previous != null) {
            if (!still && now - fadeStart < FADE_NANOS) return;
            previous.close();
            previous = null;
        }

        byte[] artwork = CoverBackdropArtwork.find(key);
        if (artwork == null && usingArtwork && key.equals(mountedKey)) return;
        if (artwork == null && usingArtwork && !key.isEmpty() && !still
                && now - requestedAt < COVER_WAIT_NANOS) return;
        if (current != null && artwork == mountedArtwork
                && (usingArtwork || Arrays.equals(palette, mountedPalette))) return;

        Shader incoming = artwork != null && artwork != mountedArtwork ? makeArtworkShader(artwork) : null;
        usingArtwork = incoming != null;
        if (!usingArtwork) {
            incoming = Shader.makeLinearGradient(0.08f, 0.10f, 0.91f, 0.93f, palette);
            System.arraycopy(palette, 0, mountedPalette, 0, mountedPalette.length);
        }
        if (still && current != null) current.close();
        previous = still ? null : current;
        current = incoming;
        mountedArtwork = artwork;
        mountedKey = key;
        fadeStart = now;
    }

    private static Shader makeArtworkShader(byte[] artwork) {
        try (Image image = Image.makeDeferredFromEncodedBytes(artwork)) {
            Matrix33 toUnit = Matrix33.makeScale(1f / image.getWidth(), 1f / image.getHeight());
            return image.makeShader(FilterTileMode.MIRROR, FilterTileMode.MIRROR, SamplingMode.LINEAR, toUnit);
        } catch (IllegalArgumentException invalidArtwork) {
            return null;
        }
    }

    @Override
    public void close() {
        if (previous != null) previous.close();
        if (current != null) current.close();
        previous = current = null;
        mountedArtwork = null;
        usingArtwork = false;
        mountedKey = null;
        requestedKey = null;
    }

    private static final class FlowEffect {
        private static final RuntimeEffect INSTANCE = compile();

        private static RuntimeEffect compile() {
            try {
                return RuntimeEffect.makeForShader(FLOW_SKSL);
            } catch (IllegalArgumentException invalidShader) {
                System.getLogger(CoverFlowBackdrop.class.getName())
                        .log(System.Logger.Level.WARNING, "Could not compile the cover background shader", invalidShader);
                return null;
            }
        }
    }
}
