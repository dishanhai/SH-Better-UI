// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Runs the real screen narrator and, when configured, verifies Windows process-loopback audio. */
final class NarratorSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("shbui.widgetSmoke.narrator");
    private static Session session;
    private static boolean complete;

    private NarratorSmoke() {}

    static boolean frame(Minecraft minecraft, Screen screen) {
        if (!ENABLED || complete) return true;
        if (session == null) {
            session = new Session(minecraft, screen);
        }
        try {
            complete = session.frame(screen);
            if (complete) session.close();
            return complete;
        } catch (IOException failure) {
            session.close();
            throw new UncheckedIOException("Narrator smoke", failure);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    private static final class Session {
        private static final Duration SETTLE = Duration.ofSeconds(4);
        private static final Duration BASELINE = Duration.ofMillis(1500);
        private static final Duration AUDIO_WINDOW = Duration.ofSeconds(16);
        private static final Duration TIMEOUT = Duration.ofSeconds(45);

        private final Minecraft minecraft;
        private final Screen screen;
        private final NarratableEntry widget;
        private final NarratorStatus previousStatus;
        private final double previousMaster;
        private final double previousVoice;
        private final double previousMusic;
        private final long started = System.nanoTime();
        private final Path directory;
        private final Path audioRecorder;
        private Process recorder;
        private long readyAt;
        private long submittedAt;
        private boolean changedOptions;

        Session(Minecraft minecraft, Screen screen) {
            this.minecraft = minecraft;
            this.screen = screen;
            if (!(screen.getFocused() instanceof NarratableEntry focused)) {
                throw new IllegalStateException("Narrator smoke: gallery has no focused narratable widget");
            }
            this.widget = focused;
            if (!minecraft.getNarrator().isActive()) {
                throw new IllegalStateException("Narrator smoke: native text-to-speech is unavailable");
            }
            previousStatus = minecraft.options.narrator().get();
            previousMaster = volume(SoundSource.MASTER);
            previousVoice = volume(SoundSource.VOICE);
            previousMusic = volume(SoundSource.MUSIC);
            directory = minecraft.gameDirectory.toPath().toAbsolutePath();
            String recorderPath = System.getProperty("shbui.widgetSmoke.narratorAudio");
            audioRecorder = recorderPath == null ? null : Path.of(recorderPath).toAbsolutePath();
        }

        boolean frame(Screen activeScreen) throws IOException {
            if (activeScreen != screen || WidgetSmokePlatform.screen(minecraft) != screen
                    || screen.getFocused() != widget
                    || widget.narrationPriority() != NarratableEntry.NarrationPriority.FOCUSED) {
                throw new IllegalStateException("Narrator smoke: gallery widget lost focus");
            }
            long now = System.nanoTime();
            if (now - started >= TIMEOUT.toNanos()) {
                throw new IllegalStateException("Narrator smoke timed out");
            }
            if (!changedOptions) {
                changedOptions = true;
                setVolume(SoundSource.MUSIC, 0);
                setVolume(SoundSource.MASTER, 1);
                setVolume(SoundSource.VOICE, 1);
                minecraft.getMusicManager().stopPlaying();
                minecraft.getSoundManager().stop();
                minecraft.options.narrator().set(NarratorStatus.SYSTEM);
                return false;
            }
            if (now - started < SETTLE.toNanos()) return false;

            if (audioRecorder != null && recorder == null) {
                startRecorder();
                return false;
            }
            if (recorder != null && recorder.isAlive() && readyAt == 0
                    && Files.exists(directory.resolve("narrator-audio.ready"))) {
                readyAt = now;
            }
            if (recorder != null && !recorder.isAlive()) {
                if (submittedAt == 0 || recorder.exitValue() != 0) {
                    throw new IllegalStateException("Narrator smoke: process loopback failed with exit "
                            + recorder.exitValue() + "; see " + directory.resolve("narrator-audio.log"));
                }
                System.out.println("SHBUI_NARRATOR_AUDIO_PASS backend=" + WidgetSmokePlatform.backend()
                        + " evidence=" + directory.resolve("narrator-audio.log"));
                return true;
            }
            if (recorder != null && (readyAt == 0 || now - readyAt < BASELINE.toNanos())) return false;
            if (submittedAt == 0) {
                submit(now);
                return false;
            }
            return recorder == null && now - submittedAt >= AUDIO_WINDOW.toNanos();
        }

        private void startRecorder() throws IOException {
            Files.deleteIfExists(directory.resolve("narrator-audio.ready"));
            Files.deleteIfExists(directory.resolve("narrator-audio.start"));
            Files.deleteIfExists(directory.resolve("narrator-audio.wav"));
            recorder = new ProcessBuilder("dotnet", audioRecorder.toString(),
                    Long.toString(ProcessHandle.current().pid()), directory.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(directory.resolve("narrator-audio.log").toFile())
                    .start();
        }

        private void submit(long now) throws IOException {
            var narration = new ScreenNarrationCollector();
            narration.update(widget::updateNarration);
            String widgetText = narration.collectNarrationText(true);
            if (widgetText.isBlank() || !minecraft.getNarrator().isActive()
                    || !minecraft.options.narrator().get().shouldNarrateSystem()
                    || minecraft.options.getFinalSoundSourceVolume(SoundSource.VOICE) <= 0) {
                throw new IllegalStateException("Narrator smoke: native narration cannot read the focused widget");
            }
            if (recorder != null) Files.createFile(directory.resolve("narrator-audio.start"));
            screen.triggerImmediateNarration(false);
            submittedAt = now;
            System.out.println("SHBUI_NARRATOR_SUBMITTED backend=" + WidgetSmokePlatform.backend()
                    + " pid=" + ProcessHandle.current().pid()
                    + " screen=\"" + screen.getNarrationMessage().getString() + "\""
                    + " widget=\"" + widgetText + "\"");
        }

        private double volume(SoundSource source) {
            return minecraft.options.getSoundSourceOptionInstance(source).get();
        }

        private void setVolume(SoundSource source, double value) {
            minecraft.options.getSoundSourceOptionInstance(source).set(value);
        }

        void close() {
            if (recorder != null && recorder.isAlive()) recorder.destroyForcibly();
            if (changedOptions) {
                minecraft.options.narrator().set(previousStatus);
                setVolume(SoundSource.VOICE, previousVoice);
                setVolume(SoundSource.MASTER, previousMaster);
                setVolume(SoundSource.MUSIC, previousMusic);
            }
        }
    }
}
