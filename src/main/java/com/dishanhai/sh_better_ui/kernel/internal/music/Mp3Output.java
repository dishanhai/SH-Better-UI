// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicEqualizerPreset;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSoundEffect;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import javax.sound.sampled.*;
import java.io.IOException;
import java.util.Objects;

/** One device line, with bounded decoder-only prefetch and sample-aligned PCM transitions. */
final class Mp3Output implements PlaybackOutput {
    private static final int IO_FRAMES = 512;

    /** A short monitor protects the reservation/claim; device writes and decoding are never under it. */
    private static final class Transition {
        final long token, captureStart;
        CrossfadeStatus phase = CrossfadeStatus.PREPARING;
        Mp3PcmStream incoming;
        PcmTrackTransition.Blend blend;
        int successorPreparedFrames;
        byte[] nextBytes;
        long handoff = Long.MAX_VALUE, successorOrigin;

        Transition(long token, long captureStart) {
            this.token = token; this.captureStart = captureStart;
        }
    }

    private volatile boolean closed, paused;
    private volatile State state = State.LOADING;
    private final PcmActivity activity = new PcmActivity();
    private volatile SourceDataLine line;
    private volatile PcmTrackTransition.Format format;
    private volatile long publishedOrigin, baseMs, endedFrame;
    private Thread worker;
    private volatile float gain = 1f;
    private volatile PcmEqualizer.Preset equalizerPreset = PcmEqualizer.Preset.FLAT;
    @FunctionalInterface interface LineFactory { SourceDataLine acquire(AudioFormat format) throws LineUnavailableException; }
    // Only QQ sessions set this; automatic overlap is limited to the same provider.
    private volatile OnlineMusicSoundEffect soundEffect;
    @Override public boolean supportsSoundEffects() { return true; }
    @Override public void soundEffect(OnlineMusicSoundEffect effect) { soundEffect=Objects.requireNonNull(effect); }

    private final LineFactory lines;
    private byte[] activeMedia;
    private Transition transition;

    Mp3Output() { this(AudioSystem::getSourceDataLine); }
    Mp3Output(LineFactory lines) { this.lines = Objects.requireNonNull(lines); }
    private long cancelledToken;

    @Override public float audioActivity() {
        return !closed && !paused && state == State.PLAYING ? activity.sample(System.nanoTime() / 1_000_000L) : 0f;
    }
    @Override public boolean supportsEqualizer() { return true; }
    @Override public void equalizerPreset(OnlineMusicEqualizerPreset preset) {
        equalizerPreset = switch (Objects.requireNonNull(preset, "preset")) {
            case FLAT -> PcmEqualizer.Preset.FLAT;
            case BASS_FOCUS -> PcmEqualizer.Preset.BASS_FOCUS;
            case VOICE_FOCUS -> PcmEqualizer.Preset.VOICE_FOCUS;
            case TREBLE_FOCUS -> PcmEqualizer.Preset.TREBLE_FOCUS;
        };
    }
    @Override public void volume(float value) { gain = PcmGain.normalize(value); }

    @Override public boolean supportsCrossfade() { return true; }
    @Override public boolean queueCrossfade(long token, byte[] mp3, int seconds, long outgoingDurationMs) throws IOException {
        if (token <= 0 || seconds < 1 || seconds > PcmTrackTransition.MAX_SECONDS
                || outgoingDurationMs < 2_000L * seconds) return false;
        var currentFormat = format;
        if (currentFormat == null || outgoingDurationMs > Long.MAX_VALUE / currentFormat.sampleRate()) return false;
        int frames = seconds * currentFormat.sampleRate();
        long duration = outgoingDurationMs * currentFormat.sampleRate() / 1000;
        // Seek the true final tail from inside its small metadata tolerance. We only mix if EOF is confirmed.
        long captureStart = Math.max(0, duration - frames - currentFormat.sampleRate() / 2L);
        var offer = new Transition(token, captureStart);
        byte[] outgoing;
        synchronized (this) {
            if (closed || state != State.PLAYING && state != State.PAUSED || token <= cancelledToken || transition != null)
                return false;
            outgoing = activeMedia;
            if (outgoing == null) return false;
            transition = offer;
        }
        Mp3PcmStream reader = null;
        try {
            reader = new Mp3PcmStream(mp3);
            if (!currentFormat.equals(reader.format())) return false;
            var head = PcmTrackTransition.capture(reader, seconds);
            if (head.frames() < frames) return false;
            // Decode the old tail independently: never hold up live device delivery to verify EOF.
            PcmTrackTransition.Span tail;
            try (var outgoingReader = new Mp3PcmStream(outgoing)) {
                if (!currentFormat.equals(outgoingReader.format())) return false;
                var scratch = new short[4096 * currentFormat.channels()];
                long skipped = 0;
                while (skipped < captureStart) {
                    if (!stillPreparing(offer) || Thread.currentThread().isInterrupted()) return false;
                    int n = outgoingReader.read(scratch, 0, (int) Math.min(4096, captureStart - skipped));
                    if (n == 0) return false;
                    skipped += n;
                }
                tail = PcmTrackTransition.captureFrames(outgoingReader, frames + currentFormat.sampleRate());
            }
            var blend = PcmTrackTransition.prepare(tail, head, frames);
            if (blend == null) return false;
            synchronized (this) {
                if (transition != offer || closed) return false;
                offer.incoming = reader;
                offer.blend = blend;
                offer.successorPreparedFrames = head.frames();
                offer.nextBytes = mp3;
                offer.phase = CrossfadeStatus.READY;
                reader = null; // Worker now owns the decoder via the reservation.
                return true;
            }
        } finally {
            closeReader(reader);
            synchronized (this) {
                if (transition == offer && offer.phase == CrossfadeStatus.PREPARING)
                    offer.phase = CrossfadeStatus.REJECTED;
            }
        }
    }
    private synchronized boolean stillPreparing(Transition offer) {
        return !closed && transition == offer && offer.phase == CrossfadeStatus.PREPARING;
    }
    @Override public synchronized CrossfadeStatus crossfadeStatus(long token) {
        var offer = transition;
        if (offer == null || offer.token != token) return CrossfadeStatus.NONE;
        if (offer.phase == CrossfadeStatus.MIXING && consumedFrames() >= offer.handoff) return CrossfadeStatus.COMPLETE;
        return offer.phase;
    }
    @Override public boolean cancelCrossfade(long token) {
        Mp3PcmStream abandoned = null;
        synchronized (this) {
            if (transition != null && transition.token == token) {
                if (transition.phase == CrossfadeStatus.MIXING) return false;
                abandoned = transition.incoming;
                transition = null;
            }
            cancelledToken = Math.max(cancelledToken, token);
        }
        closeReader(abandoned);
        return true;
    }
    @Override public synchronized void acceptCrossfade(long token) {
        if (crossfadeStatus(token) != CrossfadeStatus.COMPLETE) return;
        publishedOrigin = transition.successorOrigin;
        cancelledToken = Math.max(cancelledToken, token);
        transition = null;
    }

    @Override public void start(byte[] bytes, long positionMs, boolean playing) {
        paused = !playing;
        baseMs = positionMs;
        worker = new Thread(() -> decode(bytes, positionMs), "shb-online-mp3");
        worker.setDaemon(true);
        worker.start();
    }

    private void decode(byte[] bytes, long targetMs) {
        Mp3PcmStream reader = null;
        SourceDataLine device = null;
        long submitted = 0, at = 0;
        try {
            reader = new Mp3PcmStream(bytes);
            var actual = reader.format();
            var scratch = new short[IO_FRAMES * actual.channels()];
            long desired = targetMs > Long.MAX_VALUE / actual.sampleRate()
                    ? Long.MAX_VALUE : targetMs * actual.sampleRate() / 1000;
            while (at < desired && !closed) {
                int skip = reader.read(scratch, 0, (int) Math.min(IO_FRAMES, desired - at));
                if (skip == 0) break;
                at += skip;
            }
            if (closed) return;
            if (!reader.hasMore()) {
                baseMs = at * 1000L / actual.sampleRate();
                state = State.ENDED;
                return;
            }
            var audioFormat = new AudioFormat(actual.sampleRate(), 16, actual.channels(), true, false);
            device = lines.acquire(audioFormat);
            device.open(audioFormat, (actual.sampleRate() / 10) * actual.channels() * 2);
            synchronized (this) {
                if (closed) return;
                publishedOrigin = -at;
                line = device;
                activeMedia = bytes;
                format = actual;
                state = paused ? State.PAUSED : State.PLAYING;
                if (!paused) device.start();
            }
            var writer = new LineWriter(device, actual, scratch);
            while (awaitPlaying()) {
                var offer = ready();
                int readLimit = IO_FRAMES;
                if (offer != null) {
                    if (at >= offer.captureStart) {
                        if (at == offer.captureStart) {
                            var blend = offer.blend;
                            Mp3PcmStream next = claim(offer, blend, submitted);
                            if (next != null) {
                                closeReader(reader);
                                reader = next;
                                // Both tracks were predecoded. No speculative work can interrupt output.
                                while (blend.remaining() != 0 && !closed) {
                                    int count = blend.read(scratch, 0, IO_FRAMES);
                                    if (!writer.write(count)) break;
                                    submitted += count;
                                }
                                at = offer.successorPreparedFrames;
                                continue;
                            }
                        }
                        reject(offer); // Late/cancelled offers leave the live old decoder untouched.
                    } else {
                        readLimit = (int) Math.min(readLimit, offer.captureStart - at);
                    }
                }
                int count = reader.read(scratch, 0, readLimit);
                if (count == 0 || !writer.write(count)) break;
                at += count;
                submitted += count;
            }
            if (!closed) {
                device.drain();
                endedFrame = device.getLongFramePosition();
                state = State.ENDED;
            }
        } catch (Exception failure) {
            if (!closed) state = State.FAILED;
        } finally {
            closeReader(reader);
            if (device != null) { device.stop(); device.close(); }
        }
    }

    private synchronized Transition ready() {
        return transition != null && transition.phase == CrossfadeStatus.READY ? transition : null;
    }
    private synchronized Mp3PcmStream claim(Transition offer, PcmTrackTransition.Blend blend, long submitted) {
        if (closed || transition != offer || offer.phase != CrossfadeStatus.READY) return null;
        offer.successorOrigin = submitted + blend.handoffFrame() - blend.successorHandoffFrame();
        offer.handoff = submitted + blend.handoffFrame();
        offer.phase = CrossfadeStatus.MIXING;
        activeMedia = offer.nextBytes;
        offer.nextBytes = null;
        var next = offer.incoming;
        offer.incoming = null;
        return next;
    }
    private void reject(Transition offer) {
        Mp3PcmStream abandoned;
        synchronized (this) {
            if (transition != offer || offer.phase != CrossfadeStatus.READY) return;
            offer.phase = CrossfadeStatus.REJECTED;
            abandoned = offer.incoming;
            offer.incoming = null;
            offer.blend = null;
            offer.nextBytes = null;
        }
        closeReader(abandoned);
    }
    private static void closeReader(Mp3PcmStream reader) {
        if (reader != null) try { reader.close(); } catch (IOException ignored) { }
    }

    private final class LineWriter {
        private final SourceDataLine device;
        private final short[] scratch;
        private final byte[] pcm;
        private final int channels, rate;
        private PcmSoundEffects effects;
        private final PcmEqualizer equalizer;
        private float previousGain = gain;

        LineWriter(SourceDataLine device, PcmTrackTransition.Format format, short[] scratch) {
            this.device = device;
            this.scratch = scratch;
            this.channels = format.channels();
            this.rate = format.sampleRate();
            pcm = new byte[scratch.length * 2];
            equalizer = new PcmEqualizer(format.sampleRate(), channels);
        }

        boolean write(int frames) throws InterruptedException {
            equalizer.preset(equalizerPreset);
            int samples = frames * channels;
            equalizer.process(scratch, 0, samples);
            var requestedEffect=soundEffect;
            if(requestedEffect!=null){
                if(effects==null)effects=new PcmSoundEffects(rate,channels);
                effects.preset(requestedEffect);
                effects.process(scratch,0,samples);
            }
            float requested = gain;
            int length = PcmGain.write(scratch, 0, samples, channels, pcm, previousGain, requested);
            previousGain = requested;
            int written = 0;
            while (written < length && awaitPlaying()) written += device.write(pcm, written, length - written);
            if (!closed && !paused) activity.accept(pcm, length, System.nanoTime() / 1_000_000L);
            return !closed;
        }
    }

    private synchronized boolean awaitPlaying() throws InterruptedException {
        while (paused && !closed) wait();
        return !closed;
    }
    @Override public synchronized void pause() {
        paused = true;
        var device = line;
        if (device != null && state == State.PLAYING) device.stop();
        if (state == State.PLAYING) state = State.PAUSED;
    }
    @Override public synchronized void resume() {
        paused = false;
        var device = line;
        if (device != null && !closed && state == State.PAUSED) device.start();
        if (state == State.PAUSED) state = State.PLAYING;
        notifyAll();
    }
    @Override public State state() { return state; }
    private long consumedFrames() {
        var device = line;
        return state == State.ENDED ? endedFrame : device == null ? 0 : device.getLongFramePosition();
    }
    @Override public long positionMs() {
        var currentFormat = format;
        return currentFormat == null ? baseMs : Math.max(0, consumedFrames() - publishedOrigin) * 1000 / currentFormat.sampleRate();
    }
    @Override public void close() {
        Mp3PcmStream abandoned = null;
        synchronized (this) {
            closed = true;
            paused = false;
            if (transition != null) abandoned = transition.incoming;
            transition = null;
            activeMedia = null;
            notifyAll();
        }
        closeReader(abandoned);
        var device = line;
        if (device != null) { device.stop(); device.flush(); device.close(); }
        if (worker != null) worker.interrupt();
    }
}
