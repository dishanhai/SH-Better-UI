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
            assertTrue(job.ready()); // Wait for finish to return the cancelled decoder's slot.
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
        for (var job : jobs) assertTrue(job.ready());
        var replacement = RichMediaJobs.submit(() -> () -> {}, () -> {}, true);
        assertNotNull(replacement);
        replacement.cancel();
    }
    @Test void cancelledRunningDecodersKeepTheirBudgetUntilTheyActuallyExit() throws InterruptedException {
        var jobs = new ArrayList<RichMediaJobs.Job<AutoCloseable>>();
        var started = new CountDownLatch(2);
        var finish = new CountDownLatch(1);
        var disposed = new CountDownLatch(2);
        try {
            for (int i = 0; i < 2; i++) {
                var job = RichMediaJobs.<AutoCloseable>submit(() -> {
                    started.countDown();
                    while (true) {
                        try { finish.await(); break; }
                        catch (InterruptedException ignored) { /* Resource IO can ignore interruption. */ }
                    }
                    return disposed::countDown;
                }, () -> {}, false);
                assertNotNull(job);
                jobs.add(job);
            }
            assertTrue(started.await(3, TimeUnit.SECONDS));
            jobs.forEach(RichMediaJobs.Job::cancel);
            for (int i = 0; i < 6; i++) {
                var job = RichMediaJobs.<AutoCloseable>submit(() -> () -> {}, () -> {}, false);
                assertNotNull(job);
                jobs.add(job);
            }
            var overflow = RichMediaJobs.<AutoCloseable>submit(() -> () -> {}, () -> {}, false);
            if (overflow != null) { jobs.add(overflow); }
            assertNull(overflow, "Cancelled decoders still own memory until their work returns");
            jobs.forEach(RichMediaJobs.Job::cancel);
            for (int i = 0; i < 6; i++) {
                var replacement = RichMediaJobs.<AutoCloseable>submit(() -> () -> {}, () -> {}, false);
                assertNotNull(replacement, "Queued cancellation must return its slot immediately");
                jobs.add(replacement);
            }
        } finally {
            jobs.forEach(RichMediaJobs.Job::cancel);
            finish.countDown();
            assertTrue(disposed.await(3, TimeUnit.SECONDS));
            assertTrue(jobs.get(0).ready());
            assertTrue(jobs.get(1).ready());
        }
    }

    @Test void cancellingCompletedResultsAndTransferringOwnershipEachReturnOneSlot() throws Exception {
        var closed = new AtomicInteger();
        var job = RichMediaJobs.<AutoCloseable>submit(() -> closed::incrementAndGet, () -> {}, false);
        assertNotNull(job);
        awaitReady(job);
        job.cancel();
        job.cancel();
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, job::take);

        var transferred = RichMediaJobs.<AutoCloseable>submit(() -> closed::incrementAndGet, () -> {}, false);
        assertNotNull(transferred);
        awaitReady(transferred);
        var result = transferred.take();
        transferred.cancel();
        assertEquals(1, closed.get(), "Taken results belong to the consumer, not cancellation");
        result.close();
        assertEquals(2, closed.get());
    }

    private static void awaitReady(RichMediaJobs.Job<?> job) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!job.ready() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(job.ready());
    }

}
