// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicAnalyzer;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LocalLyricFiles;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A single screen's PCM transport. Decoder work never owns the UI thread. */
final class GalleryMusicPlayback implements AutoCloseable {
    static final int DEMO_COUNT = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int WINDOW = MusicAnalyzer.WINDOW_SIZE;
    private static final int DEMO_RATE = 22_050;
    private static final int[][] NOTES = {
            {60, 64, 67, 72, 69, 67, 64, 62},
            {57, 60, 64, 69, 67, 64, 62, 60},
            {55, 59, 62, 67, 64, 62, 59, 57}
    };
    private static final int[] BEAT_MS = {540, 460, 600};

    enum State { LOADING, PAUSED, PLAYING, ENDED, ERROR }

    enum Failure { UNSUPPORTED, INVALID_AUDIO, TOO_LARGE, DEVICE }

    record LoadResult(Path file, Failure failure) {}

    private final ExecutorService decoder = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "shb-gallery-audio");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> loading;
    private Prepared prepared;
    private Clip clip;
    private Pcm track;
    private boolean playWhenReady;
    private float volume = 1;
    private State state = State.LOADING;
    private boolean closed;
    private int requestId;

    void load(int demo, Path file, boolean play) {
        load(demo, file == null ? List.of() : List.of(file), play);
    }

    void loadFiles(List<Path> files) {
        load(0, List.copyOf(files), false);
    }

    private synchronized void load(int demo, List<Path> files, boolean play) {
        if (closed) return;
        discardPending();
        if (clip == null) state = State.LOADING;
        playWhenReady = play;
        int request = ++requestId;
        loading = decoder.submit(() -> prepare(request, demo, files));
    }

    private void prepare(int request, int demo, List<Path> files) {
        Prepared result;
        try {
            if (files.isEmpty()) {
                result = prepareTrack(demo, null);
            } else {
                result = new Prepared(null, null, null, Failure.UNSUPPORTED);
                for (Path file : files) {
                    if (Thread.interrupted()) return;
                    if (!supportedFile(file)) continue;
                    var attempt = prepareTrack(demo, file);
                    if (attempt.failure() == null || result.failure() == Failure.UNSUPPORTED) result = attempt;
                    if (attempt.failure() == null) break;
                }
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (this) {
            if (closed || request != requestId) result.close();
            else prepared = result;
        }
    }

    private static Prepared prepareTrack(int demo, Path file) throws InterruptedException {
        Clip opened = null;
        try {
            Pcm pcm = file == null ? synthesize(demo) : decode(file);
            if (Thread.interrupted()) throw new InterruptedException();
            opened = AudioSystem.getClip();
            opened.open(pcm.format(), pcm.bytes(), 0, pcm.bytes().length);
            var result = new Prepared(file, opened, pcm, null);
            opened = null;
            return result;
        } catch (OversizedAudio failure) {
            return new Prepared(file, null, null, Failure.TOO_LARGE);
        } catch (UnsupportedAudioFileException | IOException failure) {
            return new Prepared(file, null, null, Failure.INVALID_AUDIO);
        } catch (InterruptedException cancelled) {
            throw cancelled;
        } catch (Exception | LinkageError failure) {
            LOGGER.warn("Gallery audio preview could not open its output device", failure);
            return new Prepared(file, null, null, Failure.DEVICE);
        } finally {
            if (opened != null) opened.close();
        }
    }

    synchronized LoadResult completeLoad() {
        if (prepared == null) return null;
        var candidate = prepared;
        prepared = null;
        loading = null;
        Failure failure = candidate.failure();
        if (failure == null) {
            try {
                applyVolume(candidate.clip(), volume);
                if (playWhenReady) candidate.clip().start();
            } catch (RuntimeException | LinkageError unavailable) {
                LOGGER.warn("Gallery audio preview could not start its output device", unavailable);
                candidate.close();
                failure = Failure.DEVICE;
            }
        }
        if (failure == null) {
            releaseClip();
            clip = candidate.clip();
            track = candidate.pcm();
            state = playWhenReady ? State.PLAYING : State.PAUSED;
        } else if (clip == null) {
            state = State.ERROR;
        }
        return new LoadResult(candidate.file(), failure);
    }

    synchronized void toggle() {
        if (clip == null && loading != null) playWhenReady = !playWhenReady;
        else if (state() == State.PLAYING) pause();
        else if (clip != null) {
            playWhenReady = true;
            play();
        }
    }

    synchronized boolean wantsPlayback() {
        return state == State.LOADING ? playWhenReady : state() == State.PLAYING;
    }

    private void play() {
        if (position() >= duration() - 1) clip.setFramePosition(0);
        state = State.PLAYING;
        clip.start();
    }

    synchronized void pause() {
        playWhenReady = false;
        if (state() == State.PLAYING) {
            clip.stop();
            state = State.PAUSED;
        }
    }

    synchronized void seek(long time) {
        if (clip == null) return;
        long target = Math.clamp(time, 0, duration());
        if (target == duration()) {
            clip.stop();
            state = State.ENDED;
        } else if (state == State.ENDED) {
            state = State.PAUSED;
        }
        clip.setMicrosecondPosition(target * 1000);
    }

    synchronized State state() {
        if (state == State.PLAYING && position() >= duration() - 1 && !clip.isRunning()) state = State.ENDED;
        return state;
    }

    synchronized void setVolume(float volume) {
        if (this.volume == volume) return;
        this.volume = volume;
        if (clip != null) applyVolume(clip, volume);
    }

    private static void applyVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        var gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volume <= 0 ? gain.getMinimum() : (float) (20 * Math.log10(volume) - 8);
        gain.setValue(Math.clamp(decibels, gain.getMinimum(), gain.getMaximum()));
        if (clip.isControlSupported(javax.sound.sampled.BooleanControl.Type.MUTE)) {
            ((javax.sound.sampled.BooleanControl) clip.getControl(javax.sound.sampled.BooleanControl.Type.MUTE))
                    .setValue(volume <= 0);
        }
    }

    synchronized long position() { return clip == null ? 0 : clip.getMicrosecondPosition() / 1000; }

    synchronized long duration() { return clip == null ? 0 : clip.getMicrosecondLength() / 1000; }

    synchronized AudioFrame audio(long position, long now) {
        if (track == null || state != State.PLAYING) return AudioFrame.silence(now);
        var frame = track.frames()[(int) Math.clamp(position * track.rate() / (WINDOW * 1000L),
                0, track.frames().length - 1)];
        return new AudioFrame(now, frame.rms(), frame.peak(), frame.bass(), frame.mid(), frame.treble(),
                frame.beat(), frame.beatCount());
    }

    synchronized LyricTimeline lyrics() { return track == null ? LyricTimeline.empty() : track.lyrics(); }

    static int beatMs(int demo) { return BEAT_MS[demo]; }

    private void releaseClip() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    private void discardPending() {
        if (loading != null) loading.cancel(true);
        loading = null;
        if (prepared != null) prepared.close();
        prepared = null;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        discardPending();
        decoder.shutdownNow();
        releaseClip();
        track = null;
    }

    private static boolean supportedFile(Path file) {
        var name = file.getFileName();
        if (name == null) return false;
        String lower = name.toString().toLowerCase(Locale.ROOT);
        return (lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".aif") || lower.endsWith(".au"))
                && Files.isRegularFile(file);
    }

    private static Pcm decode(Path file) throws UnsupportedAudioFileException, IOException, InterruptedException {
        String name = file.getFileName().toString();
        try (var original = AudioSystem.getAudioInputStream(file.toFile())) {
            int rate = Math.round(original.getFormat().getSampleRate());
            int channels = original.getFormat().getChannels();
            if (rate < 8_000 || rate > 96_000 || channels < 1 || channels > 2) {
                throw new UnsupportedAudioFileException("Unsupported preview sample rate or channel count");
            }
            var format = new AudioFormat(rate, 16, channels, true, false);
            if (!AudioSystem.isConversionSupported(format, original.getFormat())) {
                throw new UnsupportedAudioFileException("Unsupported PCM conversion");
            }
            try (var converted = AudioSystem.getAudioInputStream(format, original)) {
                // Keep reads frame-aligned and cap the decoded data even for invalid file headers.
                if (converted.getFrameLength() > MAX_BYTES / format.getFrameSize()) throw new OversizedAudio();
                byte[] pcm = converted.readNBytes(MAX_BYTES + format.getFrameSize());
                if (pcm.length > MAX_BYTES) throw new OversizedAudio();
                if (pcm.length == 0 || pcm.length % format.getFrameSize() != 0) {
                    throw new UnsupportedAudioFileException("Invalid PCM frames");
                }
                long duration = pcm.length * 1000L / (rate * format.getFrameSize());
                var identity = TrackIdentity.of(name.substring(0, name.lastIndexOf('.')), "", duration);
                return analyzed(format, pcm, new LocalLyricFiles(file.toAbsolutePath().getParent()).load(identity));
            }
        }
    }

    private static Pcm synthesize(int demo) throws InterruptedException {
        int beatMs = BEAT_MS[demo];
        int sampleCount = DEMO_RATE * beatMs * 24 / 1000;
        byte[] bytes = new byte[sampleCount * 2];
        int[] notes = NOTES[demo];
        for (int i = 0; i < sampleCount; i++) {
            if ((i & 8191) == 0 && Thread.interrupted()) throw new InterruptedException();
            double seconds = i / (double) DEMO_RATE;
            double beat = seconds * 1000 / beatMs;
            int step = (int) beat;
            double phase = (beat - step) * beatMs / 1000;
            double note = 440 * Math.pow(2, (notes[step % notes.length] - 69) / 12.0);
            double lead = Math.sin(phase * note * Math.TAU) + .24 * Math.sin(phase * note * 2 * Math.TAU);
            double bass = Math.sin(phase * note * .25 * Math.TAU);
            double percussion = Math.sin(phase * (53 + 32 * Math.exp(-phase * 12)) * Math.TAU);
            double hat = Math.sin(phase * 5970 * Math.TAU) * Math.sin(phase * 4153 * Math.TAU);
            double attack = Math.min(1, phase * 150);
            double fade = Math.min(1, (sampleCount - i) / (DEMO_RATE * .12));
            double signal = (.21 * lead * Math.exp(-phase * 5) + .15 * bass * Math.exp(-phase * 3)
                    + (step % 4 == 0 ? .42 : .16) * percussion * Math.exp(-phase * 20)
                    + .07 * hat * Math.exp(-phase * 42)) * attack * fade;
            int value = (int) Math.round(32_767 * signal);
            bytes[2 * i] = (byte) value;
            bytes[2 * i + 1] = (byte) (value >> 8);
        }
        return analyzed(new AudioFormat(DEMO_RATE, 16, 1, true, false), bytes, LyricTimeline.empty());
    }

    private static Pcm analyzed(AudioFormat format, byte[] pcm, LyricTimeline lyrics) throws InterruptedException {
        var analyzer = new MusicAnalyzer();
        int channels = format.getChannels();
        int rate = Math.round(format.getSampleRate());
        float[] samples = new float[WINDOW * channels];
        int stride = samples.length * 2;
        var frames = new AudioFrame[Math.ceilDiv(pcm.length, stride)];
        for (int i = 0; i < frames.length; i++) {
            if (Thread.interrupted()) throw new InterruptedException();
            int start = i * stride;
            for (int sample = 0; sample < samples.length; sample++) {
                int offset = start + sample * 2;
                samples[sample] = offset < pcm.length
                        ? (short) ((pcm[offset] & 0xff) | pcm[offset + 1] << 8) / 32768f : 0;
            }
            long timestamp = i * WINDOW * 1000L / rate;
            analyzer.acceptInterleaved(samples, channels, rate, timestamp);
            frames[i] = analyzer.sample(timestamp);
        }
        return new Pcm(format, pcm, frames, lyrics, rate);
    }

    private record Pcm(AudioFormat format, byte[] bytes, AudioFrame[] frames, LyricTimeline lyrics, int rate) {}

    private record Prepared(Path file, Clip clip, Pcm pcm, Failure failure) {
        void close() {
            if (clip != null) clip.close();
        }
    }

    private static final class OversizedAudio extends IOException {}
}
