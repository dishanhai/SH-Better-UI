// Adapted from MuoniumPlayer-multiversion c845678; see META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core.input;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WheelCallbackGuardTest {
    @Test void screenOnlyWheelAccumulatesButSynchronousForwardingDoesNotDoubleCount() {
        var guard = new WheelCallbackGuard();
        var notches = new AtomicInteger();
        Runnable screen = () -> { if (guard.shouldAccumulateScreenFallback()) notches.incrementAndGet(); };

        screen.run(); // No GLFW callback (e.g. injected screen input).
        assertEquals(1, notches.get());
        notches.incrementAndGet(); // GLFW has already accumulated this notch.
        guard.forwardToScreen(screen);
        assertEquals(2, notches.get());
        screen.run();
        assertEquals(3, notches.get());
    }

    @Test void nestedForwardingAndExceptionsRestoreTheOuterGuard() {
        var guard = new WheelCallbackGuard();
        guard.forwardToScreen(() -> {
            assertFalse(guard.shouldAccumulateScreenFallback());
            assertThrows(IllegalStateException.class, () -> guard.forwardToScreen(() -> {
                assertFalse(guard.shouldAccumulateScreenFallback());
                throw new IllegalStateException();
            }));
            assertFalse(guard.shouldAccumulateScreenFallback());
        });
        assertTrue(guard.shouldAccumulateScreenFallback());
    }

    @Test void forwardingDoesNotSuppressUnrelatedThreads() {
        var guard = new WheelCallbackGuard();
        guard.forwardToScreen(() -> {
            var otherThread = new FutureTask<>(guard::shouldAccumulateScreenFallback);
            new Thread(otherThread).start();
            assertDoesNotThrow(() -> assertTrue(otherThread.get(2, TimeUnit.SECONDS)));
            assertFalse(guard.shouldAccumulateScreenFallback());
        });
    }
}
