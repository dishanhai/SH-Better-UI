// Author: dishanhai
package com.muoniumplayer.core.screens.onboarding.steps;

import com.muoniumplayer.core.screens.onboarding.OnboardingMetrics;
import com.muoniumplayer.core.screens.onboarding.OnboardingUi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceStepLayoutTest {
    @Test
    void noteFollowsLastCardInEveryLayout() {
        for (var tier : OnboardingMetrics.Tier.values()) {
            OnboardingUi.beginMetric(OnboardingMetrics.forTier(tier));
            try {
                for (double width : new double[]{170, 379, 380, 720, 1180}) {
                    double lastCardBottom = SourceStep.cardY(0, width, SourceStep.cardCount() - 1)
                            + SourceStep.cardHeight();
                    assertTrue(SourceStep.noteY(0, width) > lastCardBottom,
                            () -> "The note overlaps a source card in " + tier + " at width " + width);
                }
            } finally {
                OnboardingUi.endMetric();
            }
        }
    }
}
