// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CallChipStateTest {
    private long now;
    private final CallChipState chip = new CallChipState(1_000, () -> now);

    @Test
    void failureFreezesEstimatedProgressAndTimeUntilRetry() {
        assertEquals(Status.IDLE, chip.status());
        assertEquals(0, chip.sample(false).fill());
        chip.setStatus(Status.RUNNING);
        now = 500;
        assertEquals(.45f, chip.sample(false).fill(), .001f);
        chip.setStatus(Status.RUNNING);
        assertEquals(500, chip.sample(false).elapsedMs());
        chip.setStatus(Status.ERROR);
        now = 2_000;
        assertEquals(500, chip.sample(false).elapsedMs());
        assertEquals(.45f, chip.sample(false).fill(), .001f);
        assertEquals(0, chip.sample(false).shake());
        chip.setStatus(Status.RUNNING);
        assertEquals(0, chip.sample(false).elapsedMs());
        assertEquals(0, chip.sample(false).fill());
        now = 4_000;
        assertEquals(.9f, chip.sample(false).fill(), .001f);
        assertEquals(2_000, chip.sample(false).elapsedMs());
    }

    @Test
    void successCompletesProgressAndReducedMotionKeepsAStaticWash() {
        chip.setStatus(Status.RUNNING);
        now = 200;
        chip.setStatus(Status.DONE);
        assertEquals(Status.RUNNING, chip.sample(false).previousStatus());
        assertEquals(.18f, chip.sample(false).fill(), .001f);
        assertEquals(0, chip.sample(false).glyphRoll());
        assertEquals(1, chip.sample(true).glyphRoll());
        assertEquals(1, chip.sample(true).fill());
        now = 400;
        assertEquals(1, chip.sample(false).fill());
        now = 900;
        assertEquals(0, chip.sample(false).wash());
        assertEquals(1, chip.sample(true).wash());
        assertEquals(200, chip.sample(false).elapsedMs());
        chip.setStatus(Status.IDLE);
        assertEquals(0, chip.sample(false).elapsedMs());
    }

    @Test
    void invalidExpectedDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CallChipState(0));
    }
}
