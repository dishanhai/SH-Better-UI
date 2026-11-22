// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** A client-local sound bed controlled independently of the music queue. */
public final class AmbientSoundPlayer {
    public static final String RESOURCE_NAMESPACE = "shbui_ambient";
    private static final String TRANSLATION_PREFIX = "sh_better_ui.muonium.ambient_sound.";

    public enum Mode {
        OFF("off"), WITH_MUSIC("with_music"), SOLO("solo");

        private final String key;

        Mode(String key) { this.key = key; }

        public Component label() { return Component.translatable(TRANSLATION_PREFIX + "mode." + key); }
    }

    public enum Soundscape {
        PINK_NOISE("pink_noise"), WHITE_NOISE("white_noise"), BROWN_NOISE("brown_noise"),
        WAVES("waves"), RAIN("rain"), STREAM("stream"), SUMMER_NIGHT("summer_night"), FIREPLACE("fireplace"),
        CITY("city"), AIRPLANE("airplane"), BOAT("boat");

        private final String id;

        Soundscape(String id) { this.id = id; }

        public Component label() { return Component.translatable(TRANSLATION_PREFIX + "sound." + id); }
    }

    public enum Status {
        OFF("off"), WAITING_FOR_MUSIC("waiting_for_music"), WAITING_FOR_SILENCE("waiting_for_silence"),
        MUTED("muted"), PLAYING("playing"), UNAVAILABLE("unavailable");

        private final String key;

        Status(String key) { this.key = key; }

        public Component label() { return Component.translatable(TRANSLATION_PREFIX + "state." + key); }
    }

    private static final int RETRY_TICKS = 20;
    private static Mode mode = Mode.OFF;
    private static Soundscape selected = Soundscape.RAIN;
    private static int volume = 20;
    private static Status status = Status.OFF;
    private static Loop loop;
    private static int retryTicks;

    private AmbientSoundPlayer() {}

    public static Mode mode() { return mode; }
    public static Soundscape soundscape() { return selected; }
    public static int volumePercent() { return volume; }
    public static Status status() { return status; }

    public static void setMode(Mode next) {
        mode = Objects.requireNonNull(next);
        retryTicks = 0;
    }

    public static void select(Soundscape soundscape) {
        selected = Objects.requireNonNull(soundscape);
        retryTicks = 0;
    }

    public static void setVolumePercent(int percent) {
        volume = Math.max(0, Math.min(100, percent));
    }

    /**
     * Runs on the client thread. The event factory adapts the resource ID type used by each loader.
     * The music predicate is evaluated only while an ambient mode is enabled.
     */
    public static void tick(Minecraft client, BooleanSupplier musicPlaying, Function<String, SoundEvent> eventFactory) {
        Status inactive = inactiveStatus(client, musicPlaying);
        var sounds = client.getSoundManager();
        if (loop != null && (inactive != null || loop.soundscape != selected)) {
            sounds.stop(loop);
            loop = null;
        }
        if (inactive != null) {
            status = inactive;
            retryTicks = 0;
            return;
        }
        if (loop != null) {
            if (sounds.isActive(loop)) {
                loop.updateVolume();
                status = Status.PLAYING;
                return;
            }
            sounds.stop(loop);
            loop = null;
        }
        if (retryTicks > 0) {
            retryTicks--;
            return;
        }

        var next = new Loop(eventFactory.apply(selected.id), selected);
        if (next.resolve(sounds) != null) {
            sounds.play(next);
            if (sounds.isActive(next)) {
                loop = next;
                status = Status.PLAYING;
                return;
            }
            sounds.stop(next);
        }
        status = Status.UNAVAILABLE;
        retryTicks = RETRY_TICKS;
    }

    private static Status inactiveStatus(Minecraft client, BooleanSupplier musicPlaying) {
        if (mode == Mode.OFF) return Status.OFF;
        boolean playing = musicPlaying.getAsBoolean();
        if (mode == Mode.WITH_MUSIC && !playing) return Status.WAITING_FOR_MUSIC;
        if (mode == Mode.SOLO && playing) return Status.WAITING_FOR_SILENCE;
        if (volume == 0 || client.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || client.options.getSoundSourceVolume(SoundSource.AMBIENT) <= 0) return Status.MUTED;
        return null;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Soundscape soundscape;

        private Loop(SoundEvent event, Soundscape soundscape) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.soundscape = soundscape;
            relative = true;
            attenuation = Attenuation.NONE;
            looping = true;
            updateVolume();
        }

        private void updateVolume() { volume = AmbientSoundPlayer.volume / 100f; }

        @Override
        public void tick() {}
    }
}
