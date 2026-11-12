package com.dishanhai.sh_better_ui.client.audio;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：后台采集生命周期与失败清理；单元测试不打开真实设备。 */
class SystemAudioCaptureTest {
    @Test
    void globalPulseKeepsOneCaptureAcrossFramesAndRestartsAfterSuspension() throws Exception {
        var first = new FakeSource();
        var second = new FakeSource();
        var firstCapture = new SystemAudioCapture(first);
        var captures = new java.util.ArrayDeque<>(java.util.List.of(firstCapture, new SystemAudioCapture(second)));
        try (var pulse = new ClientMusicPulse(captures::removeFirst)) {
            pulse.update(0L, true);
            assertEquals(2, captures.size());
            pulse.setEnabled(true);
            pulse.update(16L, true);
            assertTrue(first.polled.await(2, TimeUnit.SECONDS));
            pulse.update(32L, true);
            assertEquals(SystemAudioCapture.Status.RUNNING, pulse.status());
            assertEquals(1, captures.size());

            pulse.update(48L, false);
            assertEquals(0f, pulse.level());
            assertTrue(pulse.enabled());
            assertTrue(first.released.await(2, TimeUnit.SECONDS));
            firstCapture.close();
            pulse.update(64L, true);
            assertTrue(second.polled.await(2, TimeUnit.SECONDS));

            pulse.setEnabled(false);
            assertFalse(pulse.enabled());
            assertEquals(0f, pulse.level());
            assertTrue(second.released.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void captureIsOptInAndAllNativeOperationsStayOnOneWorker() throws Exception {
        var source = new FakeSource();
        var capture = new SystemAudioCapture(source);
        assertEquals(SystemAudioCapture.Status.READY, capture.status());
        assertNull(source.owner);
        assertTrue(capture.start());
        assertTrue(source.polled.await(2, TimeUnit.SECONDS));
        assertFalse(capture.start());
        capture.close();
        assertEquals(SystemAudioCapture.Status.CLOSED, capture.status());
        assertTrue(source.closed);
        assertNotEquals(Thread.currentThread(), source.owner);
        assertEquals(0, capture.sample().rms());
        capture.close();
        assertFalse(capture.start());
    }

    @Test
    void partialOpenFailureStillClosesSourceAndPublishesNoSamples() throws Exception {
        var source = new FakeSource() {
            @Override public void open() {
                super.open();
                throw new UnsupportedOperationException();
            }
        };
        var capture = new SystemAudioCapture(source);
        capture.start();
        assertTrue(source.released.await(2, TimeUnit.SECONDS));
        assertEquals(SystemAudioCapture.Status.UNSUPPORTED, capture.status());
        assertTrue(capture.failure().isPresent());
        assertTrue(source.closed);
        assertEquals(0, capture.sample().peak());
        capture.close();
    }

    @Test
    void discontinuityDropsAnOldPartialWindow() throws Exception {
        var source = new FakeSource() {
            int reads;
            @Override public Block poll() {
                assertEquals(owner, Thread.currentThread());
                if (++reads == 1) return new Block(new float[1024], 48000, false);
                if (reads == 2) return new Block(new float[2048], 48000, true);
                polled.countDown();
                return null;
            }
        };
        try (var capture = new SystemAudioCapture(source)) {
            capture.start();
            assertTrue(source.polled.await(2, TimeUnit.SECONDS));
            assertEquals(0, capture.sample().rms());
        }
    }

    @Test
    void requestStopReturnsWhileWorkerIsStillInsideDevicePoll() throws Exception {
        var unblock = new CountDownLatch(1);
        var source = new FakeSource() {
            @Override public Block poll() {
                super.poll();
                try {
                    if (!unblock.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("poll was not released");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return new Block(new float[2048], 48000, false);
            }
        };
        try (var capture = new SystemAudioCapture(source)) {
            capture.start();
            try {
                assertTrue(source.polled.await(2, TimeUnit.SECONDS));
                assertTimeoutPreemptively(java.time.Duration.ofMillis(500), capture::requestStop);
                assertEquals(SystemAudioCapture.Status.STOPPING, capture.status());
                assertEquals(0, capture.sample().rms());
                assertFalse(capture.start());
            } finally {
                unblock.countDown();
            }
            capture.close();
            assertTrue(source.closed);
            assertEquals(SystemAudioCapture.Status.CLOSED, capture.status());
        }
    }

    static class FakeSource implements LoopbackSource {
        final CountDownLatch polled = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        Thread owner;
        boolean closed;
        @Override public void open() { owner = Thread.currentThread(); }
        @Override public Block poll() {
            assertEquals(owner, Thread.currentThread());
            polled.countDown();
            return null;
        }
        @Override public void close() {
            assertEquals(owner, Thread.currentThread());
            closed = true;
            released.countDown();
        }
    }
}
