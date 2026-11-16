package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.dishanhai.sh_better_ui.client.gui.scaled.UiKeyframeAnimation.*;
import static org.junit.jupiter.api.Assertions.*;

class UiKeyframeAnimationTest {
    private UiKeyframeAnimation track() {
        return new UiKeyframeAnimation(List.of(new Keyframe(0, 0), new Keyframe(100, 10), new Keyframe(200, -10)));
    }

    @Test void samplesContinuouslyThroughMultipleStopsAndCompletesOnce() {
        var a = track();
        assertEquals(State.READY, a.state());
        assertEquals(0, a.sample(10_000));
        long token = a.restart(1000);
        assertEquals(2.5f, a.sample(1025));
        assertEquals(10, a.sample(1100));
        assertEquals(0, a.sample(1150));
        assertFalse(a.takeCompletion(token));
        assertEquals(-10, a.sample(9999));
        assertTrue(a.takeCompletion(token));
        assertFalse(a.takeCompletion(token));
    }

    @Test void pauseAndResumeExcludePausedTimeAndDoNotRestart() {
        var a = track();
        a.restart(1000);
        a.pause(1040);
        a.pause(9000);
        assertEquals(4, a.sample(12000));
        a.resume(20000);
        a.resume(21000);
        assertEquals(5, a.sample(20010));
    }

    @Test void backwardAndDuplicateTimestampsCannotAdvanceOrRewind() {
        var a = track();
        a.restart(1000);
        assertEquals(5, a.sample(1050));
        assertEquals(5, a.sample(1040));
        assertEquals(5, a.sample(1050));
        a.pause(1030);
        a.resume(1010);
        assertEquals(6, a.sample(1060));
    }

    @Test void restartInvalidatesEarlierCompletionAndBeginsAtFirstStop() {
        var a = track();
        long old = a.restart(1000);
        a.sample(1050);
        long current = a.restart(1050);
        assertEquals(0, a.value());
        a.finish();
        assertFalse(a.takeCompletion(old));
        assertTrue(a.takeCompletion(current));
    }

    @Test void cancellationSamplesThenFreezesAndCannotResume() {
        var a = track();
        long token = a.restart(1000);
        a.cancel(1050);
        a.resume(1100);
        a.finish();
        assertEquals(5, a.sample(1200, true));
        assertEquals(State.CANCELED, a.state());
        assertFalse(a.takeCompletion(token));
    }

    @Test void reducedMotionFinishesPausedTrackButDoesNotStartReadyTrack() {
        var a = track();
        assertEquals(0, a.sample(1000, true));
        long token = a.restart(1000);
        a.pause(1010);
        assertEquals(-10, a.sample(1010, true));
        assertTrue(a.takeCompletion(token));
    }

    @Test void closedTrackRejectsCommandsAndInvalidatesCompletion() {
        var a = track();
        long token = a.restart(1000);
        a.sample(1050);
        a.close();
        a.close();
        assertEquals(0, a.restart(2000));
        a.pause(2000);
        a.resume(2000);
        a.cancel(2000);
        a.finish();
        assertEquals(5, a.sample(5000, true));
        assertEquals(State.CLOSED, a.state());
        assertFalse(a.takeCompletion(token));
    }

    @Test void interpolationSupportsFiniteExtremeEndpointsAndLargeClocks() {
        var a = new UiKeyframeAnimation(List.of(new Keyframe(0, -Float.MAX_VALUE), new Keyframe(100, Float.MAX_VALUE)));
        a.restart(Long.MAX_VALUE - 100);
        assertEquals(0, a.sample(Long.MAX_VALUE - 50));
        assertEquals(Float.MAX_VALUE, a.sample(Long.MAX_VALUE));
        a.restart(Long.MIN_VALUE);
        assertEquals(Float.MAX_VALUE, a.sample(Long.MAX_VALUE));
    }

    @Test void adjacentStopsRemainDistinctAtMaximumDuration() {
        var a = new UiKeyframeAnimation(List.of(new Keyframe(0, 0),
                new Keyframe(Long.MAX_VALUE - 1, 1), new Keyframe(Long.MAX_VALUE, 2)));
        long token = a.restart(0);
        assertEquals(1, a.sample(Long.MAX_VALUE - 1));
        assertEquals(State.PLAYING, a.state());
        assertFalse(a.takeCompletion(token));
        assertEquals(2, a.sample(Long.MAX_VALUE));
        assertTrue(a.takeCompletion(token));
    }

    @Test void easingAppliesToArrivingSegment() {
        var a = new UiKeyframeAnimation(List.of(new Keyframe(0, 0), new Keyframe(100, 1, Easing.EASE_OUT_CUBIC),
                new Keyframe(200, 2, Easing.SMOOTHSTEP)));
        a.restart(0);
        assertEquals(0.875f, a.sample(50));
        assertEquals(1.15625f, a.sample(125));
    }

    @Test void invalidStopsAreRejectedAndInputIsDefensivelyCopied() {
        assertThrows(IllegalArgumentException.class, () -> new Keyframe(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe(0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe(0, Float.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> new Keyframe(0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new UiKeyframeAnimation(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new UiKeyframeAnimation(List.of(new Keyframe(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new UiKeyframeAnimation(List.of(new Keyframe(1, 0), new Keyframe(2, 1))));
        assertThrows(IllegalArgumentException.class, () -> new UiKeyframeAnimation(List.of(new Keyframe(0, 0), new Keyframe(0, 1))));
        assertThrows(IllegalArgumentException.class, () -> new UiKeyframeAnimation(List.of(new Keyframe(0, 0), new Keyframe(2, 1), new Keyframe(1, 1))));
        var input = new ArrayList<>(List.of(new Keyframe(0, 0), new Keyframe(10, 1)));
        var a = new UiKeyframeAnimation(input);
        input.clear();
        assertEquals(10, a.durationMs());
        a.restart(0);
        assertEquals(1, a.sample(10));
    }
}
