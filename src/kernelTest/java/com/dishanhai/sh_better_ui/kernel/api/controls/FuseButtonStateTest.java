// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState.CommitOn;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState.Options;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState.Phase;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState.Settle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuseButtonStateTest {
    private final List<String> events = new ArrayList<>();

    @Test
    void immediateActionCanBeUndoneWithoutExpiring() {
        var fuse = create(CommitOn.PRESS, Settle.RESET);
        assertTrue(fuse.arm(0, false, true));
        assertFalse(fuse.arm(1, false, true));
        assertTrue(fuse.undo());
        assertFalse(fuse.undo());
        fuse.advance(10000, false, true);
        assertEquals(List.of("PRESS", "ARMED", "undo", "IDLE"), events);
    }

    @Test
    void deferredActionOnlyCommitsOnceAtDeadline() {
        var fuse = create(CommitOn.FUSE_END, Settle.STAY);
        fuse.arm(10, false, true);
        fuse.advance(1009, false, true);
        assertEquals(Phase.ARMED, fuse.phase());
        fuse.advance(1010, false, true);
        fuse.advance(2000, false, true);
        assertFalse(fuse.undo());
        assertFalse(fuse.arm(2000, false, true));
        assertEquals(List.of("ARMED", "end", "FUSE_END", "SETTLED"), events);
    }

    @Test
    void lifecycleCancellationPreventsDeferredCommit() {
        var fuse = create(CommitOn.FUSE_END, Settle.STAY);
        fuse.arm(0, false, true);
        assertTrue(fuse.undo());
        fuse.advance(10000, false, true);
        assertEquals(List.of("ARMED", "undo", "IDLE"), events);
    }

    @Test
    void onlyReentryPausesHoverAndWindowFocusPausesTime() {
        var fuse = create(CommitOn.PRESS, Settle.RESET);
        fuse.arm(0, true, true);
        fuse.advance(200, true, true);
        assertEquals(0.8f, fuse.fractionRemaining(200), 0.00001f);
        fuse.advance(300, false, true);
        fuse.advance(400, true, true);
        assertEquals(0.6f, fuse.fractionRemaining(1500), 0.00001f);
        fuse.advance(1500, false, true);
        fuse.advance(1600, false, false);
        fuse.advance(4000, false, true);
        assertEquals(0.5f, fuse.fractionRemaining(4000), 0.00001f);
        fuse.advance(4500, false, true);
        assertEquals(List.of("PRESS", "ARMED", "end", "IDLE"), events);
        assertTrue(fuse.arm(4500, false, true));
    }

    private FuseButtonState create(CommitOn commit, Settle settle) {
        return new FuseButtonState(new Options(1000, commit, true, settle),
                new FuseButtonState.Callbacks(reason -> events.add(reason.name()),
                        () -> events.add("undo"), () -> events.add("end"), phase -> events.add(phase.name())));
    }
}
