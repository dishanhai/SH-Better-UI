// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class RichMediaJobsTest {
    @Test void cancellationDisposesAnInFlightResultEvenIfWorkReturnsAfterInterrupt() throws InterruptedException {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var disposed = new CountDownLatch(1);
        var aborts = new AtomicInteger();
        var job = RichMediaJobs.<AutoCloseable>submit(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
                // Simulate resource IO or a decoder returning a result after cancellation.
            }
            return disposed::countDown;
        }, aborts::incrementAndGet, false);
        assertNotNull(job);
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            job.cancel();
            job.cancel();
            assertTrue(disposed.await(3, TimeUnit.SECONDS));
            assertEquals(1, aborts.get());
            assertThrows(IllegalStateException.class, job::take);
        } finally {
            release.countDown();
            job.cancel();
        }
    }

    @Test void boundsQueuedAndCompletedWorkAndKeepsLocalCapacityAvailable() throws InterruptedException {
        var jobs = new ArrayList<RichMediaJobs.Job<AutoCloseable>>();
        var releases = new AtomicInteger();
        var completed = new CountDownLatch(4);
        try {
            for (int i = 0; i < 4; i++) {
                var job = RichMediaJobs.<AutoCloseable>submit(() -> {
                    completed.countDown();
                    return releases::incrementAndGet;
                }, () -> {}, true);
                assertNotNull(job);
                jobs.add(job);
            }
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertNull(RichMediaJobs.submit(() -> () -> {}, () -> {}, true));
            var local = RichMediaJobs.submit(() -> () -> {}, () -> {}, false);
            assertNotNull(local);
            local.cancel();
        } finally {
            jobs.forEach(RichMediaJobs.Job::cancel);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (releases.get() != 4 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(4, releases.get());
        var replacement = RichMediaJobs.submit(() -> () -> {}, () -> {}, true);
        assertNotNull(replacement);
        replacement.cancel();
    }
}
