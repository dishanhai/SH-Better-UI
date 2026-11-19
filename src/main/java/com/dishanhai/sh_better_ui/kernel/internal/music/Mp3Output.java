// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSoundEffect;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.*;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

/** Bounded-memory MP3 decoding. Java Sound is touched only after an explicit play command. */
final class Mp3Output implements PlaybackOutput {
    private volatile boolean closed, paused;
    private volatile State state = State.LOADING;
    private final PcmActivity activity = new PcmActivity();
    @Override public float audioActivity() {
        return !closed && !paused && state == State.PLAYING ? activity.sample(System.nanoTime()/1_000_000L) : 0f;
    }
    private volatile SourceDataLine line;
    private volatile long baseMs, endedMs;
    private Thread worker;
    private volatile float gain = 1f;
    private volatile PcmEqualizer.Preset equalizerPreset = PcmEqualizer.Preset.FLAT;

    // Null means this output is not attached to a QQ stream. Avoid allocating delay history for other providers.
    private volatile OnlineMusicSoundEffect soundEffect;
    @Override public boolean supportsSoundEffects() { return true; }
    @Override public void soundEffect(OnlineMusicSoundEffect effect) {
        soundEffect = java.util.Objects.requireNonNull(effect, "effect");
    }

    /** Internal native-output capability only; no effect on externally hosted/DRM playback. */
    @Override public boolean supportsEqualizer() { return true; }
    @Override public void equalizerPreset(com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicEqualizerPreset preset) {
        equalizerPreset = switch (java.util.Objects.requireNonNull(preset, "preset")) {
            case FLAT -> PcmEqualizer.Preset.FLAT;
            case BASS_FOCUS -> PcmEqualizer.Preset.BASS_FOCUS;
            case VOICE_FOCUS -> PcmEqualizer.Preset.VOICE_FOCUS;
            case TREBLE_FOCUS -> PcmEqualizer.Preset.TREBLE_FOCUS;
        };
    }
    @Override public void volume(float value) { gain = PcmGain.normalize(value); }

    @Override public void start(byte[] bytes, long positionMs, boolean playing) {
        paused = !playing;
        baseMs = positionMs;
        worker = new Thread(() -> decode(bytes, positionMs), "shb-online-mp3");
        worker.setDaemon(true);
        worker.start();
    }
    private void decode(byte[] bytes, long targetMs) {
        var bits = new Bitstream(new ByteArrayInputStream(bytes));
        var decoder = new Decoder();
        long skippedFrames = 0;
        SourceDataLine device = null;
        try {
            Header header;
            byte[] pcm = new byte[8192];
            float previousGain = gain;
            PcmEqualizer equalizer = null;
            PcmSoundEffects effects = null;
            int outputRate = 0, outputChannels = 0;
            while (!closed && (header = bits.readFrame()) != null) {
                var samples = (SampleBuffer) decoder.decodeFrame(header, bits);
                int channels = samples.getChannelCount(), rate = samples.getSampleFrequency();
                int frames = samples.getBufferLength() / channels;
                if (equalizer == null) {
                    equalizer = new PcmEqualizer(rate, channels);
                    outputRate = rate;
                    outputChannels = channels;
                } else if (rate != outputRate || channels != outputChannels) {
                    throw new IllegalArgumentException("MP3 format changed during playback");
                }
                long targetFrame = targetMs * rate / 1000;
                if (skippedFrames + frames <= targetFrame) {
                    skippedFrames += frames;
                    bits.closeFrame();
                    continue;
                }
                int skip = (int) Math.max(0, targetFrame - skippedFrames);
                if (device == null) {
                    var format = new AudioFormat(rate, 16, channels, true, false);
                    device = AudioSystem.getSourceDataLine(format);
                    device.open(format, rate * channels * 2 / 10);
                    synchronized (this) {
                        if (closed) return;
                        line = device;
                        baseMs = (skippedFrames + skip) * 1000 / rate;
                        state = paused ? State.PAUSED : State.PLAYING;
                        if (!paused) device.start();
                    }
                }
                synchronized (this) { while (paused && !closed) wait(); }
                if (closed) return;
                short[] source = samples.getBuffer();
                equalizer.preset(equalizerPreset);
                equalizer.process(source, skip * channels, samples.getBufferLength());
                var requestedEffect = soundEffect;
                if (requestedEffect != null) {
                    if (effects == null) effects = new PcmSoundEffects(rate, channels);
                    effects.preset(requestedEffect);
                    effects.process(source, skip * channels, samples.getBufferLength());
                }
                float requestedGain = gain;
                int length = PcmGain.write(source, skip * channels, samples.getBufferLength(), channels,
                        pcm, previousGain, requestedGain);
                previousGain = requestedGain;
                int written = 0;
                while (written < length && !closed) {
                    synchronized (this) { while (paused && !closed) wait(); }
                    if (!closed) written += device.write(pcm, written, length - written);
                }
                if (!closed && !paused) activity.accept(pcm, length, System.nanoTime()/1_000_000L);
                skippedFrames += frames;
                bits.closeFrame();
            }
            if (!closed) {
                if (device == null && targetMs == 0) throw new IllegalArgumentException("No MP3 frames");
                if (device != null) device.drain();
                endedMs = positionMs();
                state = State.ENDED;
            }
        } catch (Exception failure) {
            if (!closed) state = State.FAILED;
        } finally {
            try { bits.close(); } catch (Exception ignored) { }
            if (device != null) { device.stop(); device.close(); }
        }
    }
    @Override public synchronized void pause() {
        paused = true;
        var device = line;
        if (device != null) device.stop();
        if (state == State.PLAYING) state = State.PAUSED;
    }
    @Override public synchronized void resume() {
        paused = false;
        var device = line;
        if (device != null && !closed) device.start();
        if (state == State.PAUSED) state = State.PLAYING;
        notifyAll();
    }
    @Override public State state() { return state; }
    @Override public long positionMs() {
        var device = line;
        return state == State.ENDED ? endedMs : baseMs + (device == null ? 0 : device.getMicrosecondPosition() / 1000);
    }
    @Override public void close() {
        closed = true;
        synchronized (this) { paused = false; notifyAll(); }
        var device = line;
        if (device != null) { device.stop(); device.flush(); device.close(); }
        if (worker != null) worker.interrupt();
    }
}
