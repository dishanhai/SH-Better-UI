// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import static com.dishanhai.sh_better_ui.kernel.internal.music.PlaybackOutput.CrossfadeStatus;
import static org.junit.jupiter.api.Assertions.*;

class Mp3CrossfadeOutputTest {
    @Test void twoActualDecodesMixToOnePcmLineWithConsumedHandoff() throws Exception {
        byte[] outgoing = fixture("outgoing"), incoming = fixture("incoming");
        short[] oldPcm = decode(outgoing), nextPcm = decode(incoming);
        int frames = 8000;
        var line = new TestLine();
        var acquired = new AtomicInteger();
        var output = new Mp3Output(format -> {
            assertEquals(8000, format.getSampleRate());
            assertEquals(1, format.getChannels());
            acquired.incrementAndGet();
            return line.device;
        });
        try {
            output.start(outgoing, 0, true);
            line.allowThrough(512);
            await(() -> output.state() == State.PLAYING && line.frames() == 512);
            assertTrue(output.queueCrossfade(1, incoming, 1, oldPcm.length / 8L));
            assertEquals(CrossfadeStatus.READY, output.crossfadeStatus(1));
            assertEquals(64, output.positionMs());
            line.allowThrough(oldPcm.length - 1);
            await(() -> line.frames() == oldPcm.length - 1);
            assertEquals(CrossfadeStatus.MIXING, output.crossfadeStatus(1));
            assertFalse(output.cancelCrossfade(1), "a committed overlap cannot be unmixed");
            output.pause();
            assertEquals(State.PAUSED, output.state());
            line.allowThrough(oldPcm.length);
            assertEquals(oldPcm.length - 1, line.frames());
            output.resume();
            await(() -> output.crossfadeStatus(1) == CrossfadeStatus.COMPLETE);
            assertEquals(oldPcm.length / 8L, output.positionMs(), "foreground clock stays old until acknowledgement");
            output.acceptCrossfade(1);
            assertEquals(1000, output.positionMs(), "successor is already one mix window in");
            line.allowThrough(Integer.MAX_VALUE);
            await(() -> output.state() == State.ENDED);
            assertEquals(1, acquired.get(), "both decoded streams are mixed on one native line");
            assertEquals(oldPcm.length + nextPcm.length - frames, line.frames());

            var actual = line.samples();
            int mixAt = oldPcm.length - frames;
            assertArrayEquals(Arrays.copyOfRange(oldPcm, 0, mixAt), Arrays.copyOfRange(actual, 0, mixAt));
            for (int i : new int[]{0, 384, 4011, 7031, frames - 1}) {
                double weight = i / (double) (frames - 1);
                var expected = (short) Math.round(oldPcm[mixAt + i] * (1 - weight) + nextPcm[i] * weight);
                assertEquals(expected, actual[mixAt + i]);
            }
            assertArrayEquals(Arrays.copyOfRange(nextPcm, frames, nextPcm.length),
                    Arrays.copyOfRange(actual, oldPcm.length, actual.length));
        } finally { output.close(); }
    }

    @Test void bufferedWritesDoNotCompleteHandoffUntilDeviceConsumptionCrossesBoundary() throws Exception {
        byte[] outgoing = fixture("outgoing");
        short[] oldPcm = decode(outgoing);
        var line = new TestLine(false);
        var output = new Mp3Output(format -> line.device);
        try {
            output.start(outgoing, 0, true);
            line.allowThrough(512);
            await(() -> output.state() == State.PLAYING && line.frames() == 512);
            assertEquals(0, output.positionMs(), "buffered frames have not been heard");
            line.consumeThrough(256);
            assertEquals(32, output.positionMs());
            assertTrue(output.queueCrossfade(1, fixture("incoming"), 1, oldPcm.length / 8L));

            // Queue the whole overlap and some successor audio, but keep the hardware clock behind.
            line.allowThrough(oldPcm.length + 512);
            await(() -> line.frames() == oldPcm.length + 512);
            assertEquals(CrossfadeStatus.MIXING, output.crossfadeStatus(1));
            output.acceptCrossfade(1);
            assertEquals(32, output.positionMs(), "early acknowledgement must not publish successor time");

            line.consumeThrough(oldPcm.length - 1);
            assertEquals(CrossfadeStatus.MIXING, output.crossfadeStatus(1));
            long beforePause = output.positionMs();
            output.pause();
            assertEquals(State.PAUSED, output.state());
            line.consumeThrough(oldPcm.length + 512);
            assertEquals(oldPcm.length - 1, line.consumedFrames(), "a stopped device consumes nothing");
            assertEquals(beforePause, output.positionMs());
            assertEquals(CrossfadeStatus.MIXING, output.crossfadeStatus(1));

            output.resume();
            line.consumeThrough(oldPcm.length);
            assertEquals(CrossfadeStatus.COMPLETE, output.crossfadeStatus(1));
            assertEquals(oldPcm.length / 8L, output.positionMs(), "old clock remains until acknowledgement");
            output.acceptCrossfade(1);
            assertEquals(1000, output.positionMs());
            assertEquals(CrossfadeStatus.NONE, output.crossfadeStatus(1));
            line.consumeThrough(oldPcm.length + 512);
            assertEquals(1064, output.positionMs(), "successor clock follows consumption, not write head");
        } finally { output.close(); }
    }

    @Test void fastPlaybackTracksConsumedSourceTimeAfterSeekAndFreezesOnPause() throws Exception {
        byte[] outgoing = fixture("outgoing");
        short[] oldPcm = decode(outgoing);
        var line = new TestLine(false);
        var output = new Mp3Output(format -> {
            assertEquals(8000, format.getSampleRate(), "no device format change");
            return line.device;
        });
        try {
            assertTrue(output.supportsPlaybackRate());
            output.playbackRate(2);
            assertFalse(output.supportsCrossfade());
            output.start(outgoing, 1000, true);
            line.allowThrough(512);
            await(() -> output.state() == State.PLAYING && line.frames() == 512);
            assertEquals(1000, output.positionMs(), "writes do not advance the media clock");
            line.consumeThrough(256);
            assertEquals(1064, output.positionMs());
            output.pause();
            line.consumeThrough(512);
            assertEquals(1064, output.positionMs());
            assertFalse(output.queueCrossfade(1, fixture("incoming"), 1, oldPcm.length / 8L));
            output.resume();
            line.consumeThrough(512);
            assertEquals(1128, output.positionMs());
            line.allowThrough(Integer.MAX_VALUE);
            await(() -> output.state() == State.ENDED);
            assertEquals(oldPcm.length / 8L, output.positionMs(), "EOF reports the decoded duration");
            assertEquals((oldPcm.length - 8000 + 1) / 2, line.frames());
            assertEquals(oldPcm[8000], line.samples()[0]);
            assertEquals(oldPcm[8006], line.samples()[3]);
        } finally { output.close(); }
    }

    @Test void mismatchAndCancelledTokenNeverTouchOldAudio() throws Exception {
        byte[] outgoing = fixture("outgoing"), mismatch = fixture("mismatch");
        short[] oldPcm = decode(outgoing);
        var line = new TestLine();
        var output = new Mp3Output(format -> line.device);
        try {
            output.start(outgoing, 0, true);
            line.allowThrough(512);
            await(() -> output.state() == State.PLAYING && line.frames() == 512);
            assertFalse(output.queueCrossfade(1, mismatch, 1, oldPcm.length / 8L));
            assertEquals(CrossfadeStatus.REJECTED, output.crossfadeStatus(1));
            assertTrue(output.cancelCrossfade(1));

            // Model a cancelled queue task that wakes up after the main thread has already replaced it.
            assertTrue(output.cancelCrossfade(2));
            assertFalse(output.queueCrossfade(2, fixture("incoming"), 1, oldPcm.length / 8L));
            assertEquals(CrossfadeStatus.NONE, output.crossfadeStatus(2));

            assertTrue(output.queueCrossfade(3, fixture("incoming"), 1, oldPcm.length / 8L));
            assertTrue(output.cancelCrossfade(3));
            line.allowThrough(Integer.MAX_VALUE);
            await(() -> output.state() == State.ENDED);
            assertArrayEquals(oldPcm, line.samples());
        } finally { output.close(); }
    }

    @Test void inconsistentOutgoingMetadataCannotFadeAFalseTail() throws Exception {
        byte[] outgoing = fixture("outgoing");
        short[] oldPcm = decode(outgoing);
        var line = new TestLine();
        var output = new Mp3Output(format -> line.device);
        try {
            output.start(outgoing, 0, true);
            line.allowThrough(512);
            await(() -> output.state() == State.PLAYING && line.frames() == 512);
            assertFalse(output.queueCrossfade(1, fixture("incoming"), 1, 2000),
                    "nominal capture window is not the true outgoing EOF");
            assertTrue(output.cancelCrossfade(1));
            line.allowThrough(Integer.MAX_VALUE);
            await(() -> output.state() == State.ENDED);
            assertArrayEquals(oldPcm, line.samples());
        } finally { output.close(); }
    }

    private static byte[] fixture(String name) throws Exception {
        try (var stream = Objects.requireNonNull(Mp3CrossfadeOutputTest.class.getResourceAsStream(
                "/music/crossfade/" + name + ".mp3"))) {
            return stream.readAllBytes();
        }
    }
    private static short[] decode(byte[] mp3) throws Exception {
        try (var stream = new Mp3PcmStream(mp3)) {
            var span = PcmTrackTransition.capture(stream, 5);
            assertTrue(span.reachesEnd());
            var pcm = new short[span.frames() * span.format().channels()];
            span.read(pcm, 0, 0, span.frames());
            return pcm;
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(1);
        assertTrue(condition.getAsBoolean(), "timed out waiting for the fake line");
    }

    /** A deterministic finite device with an independently advanceable consumed clock. */
    private static final class TestLine {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int budget, consumed;
        private boolean consumeOnWrite;
        TestLine() { this(true); }
        TestLine(boolean consumeOnWrite) { this.consumeOnWrite = consumeOnWrite; }
        private boolean running, closed;
        final SourceDataLine device = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SourceDataLine.class}, (ignored, method, args) -> {
                    synchronized (this) {
                        return switch (method.getName()) {
                            case "open", "drain", "flush" -> null;
                            case "start" -> { running = true; notifyAll(); yield null; }
                            case "stop" -> { running = false; notifyAll(); yield null; }
                            case "close" -> { closed = true; notifyAll(); yield null; }
                            case "getLongFramePosition" -> (long) consumed;
                            case "write" -> {
                                while (!closed && running && frames() >= budget) wait();
                                if (closed || !running) yield 0;
                                int count = Math.min((int) args[2] / 2, budget - frames()) * 2;
                                bytes.write((byte[]) args[0], (int) args[1], count);
                                if (consumeOnWrite) consumed = frames();
                                yield count;
                            }
                            default -> throw new AssertionError("Unexpected device method: " + method.getName());
                        };
                    }
                });
        synchronized int frames() { return bytes.size() / 2; }
        synchronized int consumedFrames() { return consumed; }
        synchronized void consumeThrough(int frame) {
            if (running && !closed) consumed = Math.max(consumed, Math.min(frame, frames()));
            notifyAll();
        }
        synchronized void allowThrough(int frame) { budget = frame; notifyAll(); }
        synchronized short[] samples() {
            byte[] raw = bytes.toByteArray();
            short[] pcm = new short[raw.length / 2];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (short) ((raw[2 * i] & 255) | raw[2 * i + 1] << 8);
            return pcm;
        }
    }
}
