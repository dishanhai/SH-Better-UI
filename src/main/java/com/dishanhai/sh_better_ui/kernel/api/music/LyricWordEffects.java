// Author: dishanhai. Original native presentation math; see docs/music-lyric-motion.md.
package com.dishanhai.sh_better_ui.kernel.api.music;

final class LyricWordEffects {
    private LyricWordEffects() {}

    static float smooth(float t) {
        float x = Math.max(0, Math.min(1, t));
        return x * x * (3 - 2 * x);
    }

    /** A feather across measured advances, not an integer scissor or a new lyric timestamp. */
    static float coverage(float fraction, float wordWidth, float glyphCenter, float glyphWidth) {
        if (fraction <= 0 || !(wordWidth > 0)) return 0;
        if (fraction >= 1) return 1;
        float feather = Math.min(wordWidth * .75f, Math.max(2, glyphWidth * 1.15f));
        float front = fraction * (wordWidth + feather) - feather * .5f;
        return smooth(.5f + (front - glyphCenter) / feather);
    }

    /** Words return to their baseline before the next word takes over. */
    static float lift(float fraction, float scale) {
        return Math.min(2.0f, scale * .85f) * envelope(fraction);
    }

    static float rise(float fraction, float width, float scale) {
        return Math.min(scale * .48f, width * .022f) * envelope(fraction);
    }

    /** Ink-only expansion: keep advances fixed and anchor the bottom of the glyph cell.
     * The same timestamp envelope as lift/tilt makes seeks deterministic, without a spring
     * or an extra animation clock. Browsing and discontinuities retain static geometry. */
    static float verticalScale(float fraction, boolean motion) {
        return motion ? 1 + .025f * envelope(fraction) : 1;
    }

    private static float envelope(float fraction) {
        return smooth(fraction / .32f) * smooth((1 - fraction) / .38f);
    }

    /** Continuous ink hierarchy on both sides of the actual on-screen reading anchor. */
    static float focusDistance(double rowY, double anchorY, double rowPitch) {
        if (!(rowPitch > 0)) return 0;
        return smooth((float) ((Math.abs(rowY - anchorY) / rowPitch - .12) / 2.1));
    }

    static float distanceInkFade(double rowY, double anchorY, double rowPitch) {
        return .36f + .40f * focusDistance(rowY, anchorY, rowPitch);
    }

    /** Suppress finished lines a little more strongly than the upcoming words. */
    static float stageInkFade(float regular, double y, double anchorY, double pitch) {
        float lead = pitch > 0 ? smooth((float)((anchorY - y) / pitch)) : 0;
        return Math.min(.96f, regular + .24f + .11f * lead);
    }

    /** Paint-only correction; keep the measured rows and moving scroll at full precision. */
    static double restingBaseline(double y, double physicalScale, boolean still) {
        return still && physicalScale > 0 && Double.isFinite(physicalScale)
                ? Math.rint(y * physicalScale) / physicalScale : y;
    }

    /** Only peripheral rows fade into the clip edge; reading/focused rows keep full ink. */
    static float rowOpacity(boolean peripheral, double y, double height, double fadeHeight) {
        if (!peripheral || !(fadeHeight > 0)) return 1;
        return smooth((float) Math.min(y / fadeHeight, (height - y) / fadeHeight));
    }
}
