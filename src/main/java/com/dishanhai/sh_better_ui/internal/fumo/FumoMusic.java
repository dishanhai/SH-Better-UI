// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import com.dishanhai.sh_better_ui.internal.fumo.DishanhaiFumoBlock.DanceEntity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** One positional music stream per audible dancing plush. */
final class FumoMusic extends AbstractTickableSoundInstance {
    private static final int RANGE = 16;
    private static final Map<DanceEntity, FumoMusic> PLAYING = new WeakHashMap<>();
    private final WeakReference<DanceEntity> plush;

    private FumoMusic(DanceEntity plush, SoundEvent sound) {
        super(sound, SoundSource.RECORDS, RandomSource.create());
        this.plush = new WeakReference<>(plush);
        var pos = plush.getBlockPos();
        x = pos.getX() + 0.5;
        y = pos.getY() + 0.5;
        z = pos.getZ() + 0.5;
        volume = 0.5F;
        looping = true;
    }

    static void playNearby(DanceEntity plush) {
        var client = Minecraft.getInstance();
        if (!isNearby(client, plush) || client.options.getSoundSourceVolume(SoundSource.RECORDS) <= 0
                || client.options.getSoundSourceVolume(SoundSource.MASTER) <= 0) {
            return;
        }
        var sounds = client.getSoundManager();
        var playing = PLAYING.get(plush);
        if (playing != null && !playing.isStopped() && sounds.isActive(playing)) return;

        var id = BuiltInRegistries.BLOCK.getKey(plush.getBlockState().getBlock());
        if (id == null || sounds.getSoundEvent(id) == null) return;
        var music = new FumoMusic(plush, SoundEvent.createVariableRangeEvent(id));
        PLAYING.put(plush, music);
        sounds.play(music);
    }

    @Override
    public void tick() {
        var source = plush.get();
        if (source == null || source.isRemoved() || !source.getBlockState().getValue(DishanhaiFumoBlock.DANCING)
                || !isNearby(Minecraft.getInstance(), source)) {
            stop();
        }
    }

    private static boolean isNearby(Minecraft client, DanceEntity plush) {
        var camera = client.getCameraEntity();
        return plush.getLevel() == client.level && camera != null
                && plush.getBlockPos().distToCenterSqr(camera.getX(), camera.getY(), camera.getZ()) < RANGE * RANGE;
    }
}
