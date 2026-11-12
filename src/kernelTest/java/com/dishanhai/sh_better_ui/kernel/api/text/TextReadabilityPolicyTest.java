// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy.Classification.FRAMEBUFFER_SIZE;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy.Classification.USAGE;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy.Usage.BODY;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy.Usage.DISPLAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextReadabilityPolicyTest {
    @Test
    void defaultsPreserveExistingRenderingAndClassifyByUsage() {
        var policy = TextReadabilityPolicy.inherit().resolve();
        assertEquals(TextReadabilityPolicy.defaults().resolve(), policy);
        assertFalse(policy.adaptiveColor());
        assertFalse(policy.foregroundProtection());
        assertEquals(USAGE, policy.classification());
        assertEquals(4.5, policy.targetContrast(BODY, 80));
        assertEquals(3.0, policy.targetContrast(DISPLAY, 8));
    }

    @Test
    void switchesCanBeEnabledIndependentlyAndExplicitlyDisabled() {
        var adaptive = new TextReadabilityPolicy(true, null, null, null, null);
        assertTrue(adaptive.resolve().adaptiveColor());
        assertFalse(adaptive.resolve().foregroundProtection());

        var protect = new TextReadabilityPolicy(false, true, null, null, null);
        var resolved = adaptive.overlay(protect).resolve();
        assertFalse(resolved.adaptiveColor());
        assertTrue(resolved.foregroundProtection());
    }

    @Test
    void samplingAndStyleFieldsInheritIndependently() {
        var page = TextReadabilityPolicy.inherit()
                .withSamplingMode(TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY).withLockHue(true);
        var control = TextReadabilityPolicy.inherit().withLockHue(false).withLocalColor(true);
        var result = page.overlay(control).resolve();
        assertEquals(TextReadabilityPolicy.SamplingMode.EXPLICIT_ONLY, result.samplingMode());
        assertEquals(Boolean.FALSE, result.lockHue());
        assertTrue(result.localColor());
    }

    @Test
    void fixedRatioTakesPriorityUntilAChildSelectsClassification() {
        var page = new TextReadabilityPolicy(true, null, new TextReadabilityPolicy.FixedContrast(7),
                USAGE, 30.0);
        var child = new TextReadabilityPolicy(null, true, null, FRAMEBUFFER_SIZE, null);
        var resolved = page.overlay(child).resolve();
        assertTrue(resolved.adaptiveColor());
        assertTrue(resolved.foregroundProtection());
        assertEquals(FRAMEBUFFER_SIZE, resolved.classification());
        assertEquals(30.0, resolved.largeTextFramebufferPixels());
        assertEquals(7.0, resolved.targetContrast(DISPLAY, 80));
        assertEquals(7.0, resolved.targetContrast(BODY, 8));

        var clear = new TextReadabilityPolicy(null, null, TextReadabilityPolicy.ClassifiedContrast.INSTANCE,
                null, null);
        var classified = page.overlay(child).overlay(clear).resolve();
        assertEquals(4.5, classified.targetContrast(DISPLAY, 29.999));
        assertEquals(3.0, classified.targetContrast(BODY, 30));
    }

    @Test
    void framebufferHeightUsesEffectiveGuiScaleAndRejectsInvalidGeometry() {
        assertEquals(27.0, TextReadabilityPolicy.framebufferHeight(12, 2.25));
        assertEquals(0.0, TextReadabilityPolicy.framebufferHeight(0, 2));
        assertThrows(IllegalArgumentException.class, () -> TextReadabilityPolicy.framebufferHeight(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> TextReadabilityPolicy.framebufferHeight(Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> TextReadabilityPolicy.framebufferHeight(1, 0));
        assertThrows(IllegalArgumentException.class, () -> TextReadabilityPolicy.framebufferHeight(1, -2));
        assertThrows(IllegalArgumentException.class,
                () -> TextReadabilityPolicy.framebufferHeight(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> TextReadabilityPolicy.framebufferHeight(Double.MAX_VALUE, 2));
    }

    @Test
    void invalidPolicyNumbersAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new TextReadabilityPolicy.FixedContrast(0.9));
        assertThrows(IllegalArgumentException.class, () -> new TextReadabilityPolicy.FixedContrast(21.1));
        assertThrows(IllegalArgumentException.class, () -> new TextReadabilityPolicy.FixedContrast(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new TextReadabilityPolicy(null, null, null, FRAMEBUFFER_SIZE, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TextReadabilityPolicy(null, null, null, null, Double.POSITIVE_INFINITY));
    }

    @Test
    void stableIdsDistinguishRowsAndFragments() {
        var text = UiId.parse("test:label");
        var rowA = UiId.parse("test:row/a");
        var rowB = UiId.parse("test:row/b");
        var fragmentA = UiId.parse("test:fragment/a");
        var fragmentB = UiId.parse("test:fragment/b");
        var first = new TextId(text, rowA, fragmentA);

        assertEquals(first, new TextId(text, rowA, fragmentA));
        assertNotEquals(first, new TextId(text, rowB, fragmentA));
        assertNotEquals(first, new TextId(text, rowA, fragmentB));
        assertNotEquals(first, new TextId(text));
        assertEquals(new TextId(text), new TextId(text, null, null));
        assertThrows(NullPointerException.class, () -> new TextId(null));
    }
}
