// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkStageMotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicCoverHandoffTest {
    private static final ArtworkPalette A = ArtworkPalette.solid(0xff442233);
    private static final ArtworkPalette B = ArtworkPalette.solid(0xff234458);
    private static final ArtworkPalette C = ArtworkPalette.solid(0xff4a3822);
    private static final class Slot implements MusicCoverHandoff.Slot<ArtworkPalette> {
        final HashMap<String, MusicCoverHandoff.Result<ArtworkPalette>> available = new HashMap<>();
        final List<String> requests = new ArrayList<>();
        String key;
        ArtworkPalette current;
        public MusicCoverHandoff.Result<ArtworkPalette> poll(String next) {
            if (!next.equals(key)) requests.add(next);
            key = next;
            var result = available.getOrDefault(key, new MusicCoverHandoff.Result<>(null, true));
            current = result.image();
            return result;
        }
        public void clear() { key = null; current = null; }
        public boolean owns(ArtworkPalette value) { return current == value; }
        void ready(String id, ArtworkPalette value) { available.put(id, new MusicCoverHandoff.Result<>(value, false)); }
    }
    private final Slot first = new Slot(), second = new Slot();
    private final MusicCoverHandoff<ArtworkPalette> cover = new MusicCoverHandoff<>(first, second, a -> a);

    @Test void slowReplacementHoldsArtworkAndPaletteThenSettlesInOrder() {
        first.ready("A", A);
        cover.sample("A", 1000, true);
        var pending = cover.sample("B", 1050, false);
        assertSame(A, pending.base());
        assertSame(A, pending.background());
        assertNull(pending.incoming());
        second.ready("B", B);
        assertEquals(0f, cover.sample("B", 1200, false).mix());
        var middle = cover.sample("B", 1290, false);
        assertSame(A, middle.base());
        assertSame(B, middle.incoming());
        assertSame(A, middle.background());
        assertEquals(.5f, middle.mix(), .0001f);
        var completed = cover.sample("B", 1380, false);
        assertSame(B, completed.base());
        assertSame(B, completed.background());
        assertNull(first.current);
    }

    @Test void supersededDownloadNeverPaintsEvenIfItCompletedJustBeforeTheSwitch() {
        first.ready("A", A);
        cover.sample("A", 1000, true);
        cover.sample("B", 1010, false);
        second.ready("B", B);
        var next = cover.sample("C", 1020, false);
        assertSame(A, next.base());
        assertNull(next.incoming());
        second.ready("C", C);
        assertSame(C, cover.sample("C", 1030, false).incoming());
        assertEquals(List.of("B", "C"), second.requests);
    }

    @Test void backToOutgoingCoverReversesAtTheVisibleWeightWithoutReloading() {
        first.ready("A", A);
        second.ready("B", B);
        cover.sample("A", 1000, true);
        cover.sample("B", 1100, false);
        var middle = cover.sample("B", 1190, false);
        assertEquals(middle.mix(), cover.sample("A", 1190, false).mix());
        assertEquals(0f, cover.sample("A", 1280, false).mix());
        assertSame(A, cover.sample("A", 1300, false).base());
        assertNull(second.current);
        assertEquals(List.of("A"), first.requests);
    }

    @Test void failureFadesToFallbackAndDoesNotRepollUntilKeyChanges() {
        first.ready("A", A);
        second.ready("B", null);
        cover.sample("A", 1000, true);
        cover.sample("B", 1100, false);
        var middle = cover.sample("B", 1190, false);
        assertSame(A, middle.background());
        assertNull(middle.incoming());
        assertEquals(.5f, middle.mix(), .0001f);
        var dropped = cover.sample("B", 1280, false);
        assertNull(dropped.background());
        assertNull(dropped.base());
        assertNull(first.current);
        assertNull(cover.sample("B", 1400, false).base());
        assertEquals(List.of("B"), second.requests);
    }

    @Test void threeRapidSelectionsKeepTwoSlotsAndReducedMotionSnapsWhenReady() {
        first.ready("A", A);
        first.ready("C", C);
        second.ready("B", B);
        cover.sample("A", 1000, true);
        cover.sample("B", 1100, false);
        var coasting = cover.sample("C", 1190, false);
        assertSame(B, coasting.incoming());
        assertEquals(List.of("A"), first.requests);
        var snapped = cover.sample("C", 1200, true);
        assertSame(C, snapped.base());
        assertSame(C, snapped.background());
        assertNull(snapped.incoming());
        assertNull(second.current);
        assertEquals(0f, snapped.mix());
    }

    @Test void backdropInterpolatesWithoutFlashingWhenRetargeted() {
        var stage = new ArtworkStageMotion();
        assertSame(A, stage.sample(A, 1000, false, false).palette());
        assertEquals(A, stage.sample(B, 1010, false, false).palette());
        ArtworkPalette shown = A;
        for (int i = 1; i <= 4; i++) shown = stage.sample(B, 1010 + 50 * i, false, false).palette();
        assertNotEquals(A, shown);
        assertNotEquals(B, shown);
        assertEquals(shown, stage.sample(C, 1210, false, false).palette());
        for (int i = 1; i <= 14; i++) shown = stage.sample(C, 1210 + 50 * i, false, false).palette();
        assertEquals(C, shown);
    }

    @Test void replacementWaitFollowsActiveRequestsWithoutRepaintingTheCover() {
        first.ready("A", A);
        cover.sample("A", 1000, true);
        cover.sample("B", 1010, true);
        var badge = cover.sample("B", 1150, true);
        assertEquals(MusicCoverWait.Mode.BADGE, badge.cue().mode());
        assertSame(A, badge.base());
        assertSame(A, badge.background());
        second.ready("B", B);
        var newer = cover.sample("C", 1160, true);
        assertEquals(badge.cue(), newer.cue());
        assertNull(newer.incoming());
        var unavailable = cover.sample("", 1170, true);
        assertEquals(MusicCoverWait.Mode.NONE, unavailable.cue().mode());
        assertNull(unavailable.base());
    }

    @Test void failureNeverLeavesTheInitialPlaceholderAnimating() {
        cover.sample("A", 1000, true);
        assertEquals(MusicCoverWait.Mode.SKELETON, cover.sample("A", 1140, true).cue().mode());
        first.ready("A", null);
        var failed = cover.sample("A", 1150, true);
        assertEquals(MusicCoverWait.Mode.NONE, failed.cue().mode());
        assertNull(failed.base());
        assertEquals(failed, cover.sample("A", 1190, true));
    }

    @Test void invalidatedGpuLeaseIsNotSentToPainter() {
        first.ready("A", A);
        cover.sample("A", 1000, true);
        first.available.clear();
        first.clear();
        var restarted = cover.sample("A", 1100, false);
        assertNull(restarted.base());
        assertNull(restarted.incoming());
        assertEquals(List.of("A", "A"), first.requests);
    }
}
