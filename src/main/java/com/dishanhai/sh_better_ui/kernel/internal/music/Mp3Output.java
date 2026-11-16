// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.*;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

/** Bounded-memory MP3 decoding. Java Sound is touched only after an explicit play command. */
final class Mp3Output implements PlaybackOutput {
    private volatile boolean closed, paused;
    private volatile State state = State.LOADING;
    private volatile SourceDataLine line;
    private volatile long baseMs, endedMs;
    private Thread worker;

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
            while (!closed && (header = bits.readFrame()) != null) {
                var samples = (SampleBuffer) decoder.decodeFrame(header, bits);
                int channels = samples.getChannelCount(), rate = samples.getSampleFrequency();
                int frames = samples.getBufferLength() / channels;
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
                int length = 0;
                for (int i = skip * channels; i < samples.getBufferLength(); i++) {
                    short value = source[i];
                    pcm[length++] = (byte) value;
                    pcm[length++] = (byte) (value >> 8);
                }
                int written = 0;
                while (written < length && !closed) {
                    synchronized (this) { while (paused && !closed) wait(); }
                    if (!closed) written += device.write(pcm, written, length - written);
                }
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
