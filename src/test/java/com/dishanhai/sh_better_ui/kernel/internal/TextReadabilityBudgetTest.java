// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextReadabilityBudgetTest {
    @Test
    void generalRunsLeaveFocusAndHoverReservationsAvailable() {
        var budget = new TextReadabilityBudget(TextReadabilityBudget.PROVISIONAL);
        for (int i = 0; i < 32; i++) {
            var grant = budget.offer(0, 64);
            assertNotNull(grant);
            assertEquals(8, grant.samples());
            budget.spend(grant, 64);
        }
        assertNull(budget.offer(0, 64));
        assertEquals(16, budget.offer(1, 64).samples());
        assertEquals(32, budget.offer(2, 64).samples());
        assertEquals(32, budget.offer(3, 64).samples());
    }

    @Test
    void importantRunsCanBorrowAtTheRemainingDensityAndResetNextFrame() {
        var budget = new TextReadabilityBudget(TextReadabilityBudget.PROVISIONAL);
        for (int i = 0; i < 48; i++) {
            var grant = budget.offer(1, 32);
            assertNotNull(grant);
            assertEquals(i < 16 ? 16 : 8, grant.samples());
            budget.spend(grant, 32);
        }
        assertNull(budget.offer(1, 32));
        assertNotNull(budget.offer(3, 32));
        budget.reset();
        assertEquals(16, budget.offer(1, 32).samples());
    }

    @Test
    void copyPixelsAndSampleRunsAreBothBounded() {
        var budget = new TextReadabilityBudget(TextReadabilityBudget.PROVISIONAL);
        var grant = budget.offer(0, 1);
        budget.spend(grant, grant.pixelAllowance());
        assertNull(budget.offer(0, 1));
        assertEquals(32, budget.offer(3, 1).samples());
    }
}
