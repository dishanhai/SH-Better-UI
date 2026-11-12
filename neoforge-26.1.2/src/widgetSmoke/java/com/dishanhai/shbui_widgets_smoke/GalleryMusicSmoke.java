// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class GalleryMusicSmoke {
    private static final String PREFIX = "sh_better_ui.panel.developer.gallery.music_preview.";
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
    private static final KeyEvent SPACE = new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0);
    private static final KeyEvent ESCAPE = new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0);
    private static final long TIMEOUT_NANOS = 6_000_000_000L;
    private static String sample;
    private static int stage;
    private static long phaseStart;
    private static long paused;
    private static Path fixture;

    private GalleryMusicSmoke() {}

    static boolean frame(Minecraft minecraft, Screen screen, String id) {
        if (!id.equals(sample)) {
            sample = id;
            advance(0);
        }
        Object player = field(screen, "playback");
        String state = call(player, "state").toString();
        long position = number(player, "position");
        try {
            require(System.nanoTime() - phaseStart <= TIMEOUT_NANOS, "timed out in stage " + stage + " (" + state + ")");
            require(!state.equals("ERROR"), "could not open device or fixture");
            switch (stage) {
                case 0 -> {
                    if (state.equals("LOADING") || !minecraft.isWindowActive()) return false;
                    require(state.equals("PAUSED"), "opens paused");
                    require(screen.getFocused() == field(screen, "transport"), "Play has initial focus");
                    require(screen.keyPressed(SPACE), "Space starts playback");
                    advance(1);
                }
                case 1 -> {
                    if (position < 150) return false;
                    require(state.equals("PLAYING"), "device advances while playing");
                    var audio = (AudioFrame) call(player, "audio", new Class<?>[]{long.class, long.class}, position, 0L);
                    require(audio.rms() > 0, "actual playback PCM has measured energy");
                    require(screen.keyPressed(SPACE), "Space pauses playback");
                    paused = number(player, "position");
                    advance(2);
                }
                case 2 -> {
                    if (System.nanoTime() - phaseStart < 110_000_000L) return false;
                    require(state.equals("PAUSED") && position == paused, "pause freezes the device cursor");
                    verifySeek(screen, player, id);
                    verifyPresentation(screen, id);
                    verifyShortLayout(minecraft, screen, id);
                    click(screen, (AbstractWidget) field(screen, "next"));
                    advance(3);
                }
                case 3 -> {
                    if (field(screen, "selection") != null) return false;
                    require(state.equals("PAUSED") && position == 0, "next track stays paused and starts at zero");
                    require(number(player, "duration") < 12_000, "next track loads a different audio clip");
                    if (id.equals("lyrics")) {
                        require(screen.getNarrationMessage().getString().contains(
                                Component.translatable(PREFIX + "no_lyrics").getString()),
                                "an instrumental track exposes no-lyrics status to narration");
                    }
                    if (id.equals("local_music")) {
                        fixture = writeFixture(minecraft);
                        screen.onFilesDrop(List.of(fixture.getRoot(), sibling("Folder.wav"), sibling("Unsupported.txt"),
                                sibling("Corrupt.wav"), fixture));
                        advance(4);
                    } else return pass();
                }
                case 4 -> {
                    if (field(screen, "selection") != null) return false;
                    require(state.equals("PAUSED") && number(player, "duration") == 4000,
                            "batch skips roots, directories, unsupported files and corrupt audio");
                    require(field(screen, "imported").equals(List.of(fixture)), "only opened audio is imported");
                    screen.onFilesDrop(List.of(sibling("Oversized.wav")));
                    advance(5);
                }
                case 5 -> {
                    if (field(screen, "selection") != null) return false;
                    verifyRejection(screen, player, "too_large");
                    var slider = (UiSlider) field(screen, "seek");
                    click(screen, pointer(slider, .40));
                    var line = (LyricLine) call(screen, "currentLine", new Class<?>[]{long.class}, number(player, "position"));
                    require(line != null && line.text().equals("Second smoke cue"), "local LRC sidecar follows a seek");
                    click(screen, (AbstractWidget) field(screen, "transport"));
                    advance(6);
                }
                case 6 -> {
                    if (position < 1750) return false;
                    require(state.equals("PLAYING"), "imported audio plays");
                    paused = position;
                    screen.onFilesDrop(List.of(fixture.getRoot(), sibling("Folder.wav"), sibling("Unsupported.txt")));
                    advance(7);
                }
                case 7 -> {
                    if (field(screen, "selection") != null || position <= paused) return false;
                    verifyRejection(screen, player, "unsupported");
                    require(state.equals("PLAYING"), "unsupported drop preserves active playback");
                    paused = position;
                    screen.onFilesDrop(List.of(sibling("Corrupt.wav")));
                    advance(8);
                }
                case 8 -> {
                    if (field(screen, "selection") != null || position <= paused) return false;
                    verifyRejection(screen, player, "invalid_audio");
                    require(state.equals("PLAYING"), "decode failure preserves active playback");
                    return pass();
                }
                default -> throw new IllegalStateException("Unknown gallery music smoke stage");
            }
        } catch (RuntimeException failure) {
            cleanFixture();
            throw failure;
        }
        return false;
    }

    private static void verifyRejection(Screen screen, Object player, String reason) {
        require(field(screen, "imported").equals(List.of(fixture)) && number(player, "duration") == 4000,
                "rejected drop preserves the playlist and current audio");
        var narration = screen.getNarrationMessage().getString();
        require(narration.contains(Component.translatable(PREFIX + reason).getString())
                        && narration.contains(fixture.getFileName().toString()),
                "narration reports the rejection reason and preserves the selected title");
    }

    private static void verifyShortLayout(Minecraft minecraft, Screen screen, String id) {
        int width = screen.width;
        int height = screen.height;
        screen.resize(180, 145);
        try {
            var canvas = (Bounds) field(screen, "canvas");
            var seek = (UiSlider) field(screen, "seek");
            var transport = (AbstractWidget) field(screen, "transport");
            require(canvas.height() >= 3 * minecraft.font.lineHeight + 4
                            && seek.getY() >= transport.getY() + transport.getHeight(),
                    "short layout reserves room for track, status and instructions above transport");
            if (id.equals("local_music") || id.equals("lyrics")) {
                require(screen.getNarrationMessage().getString().contains(Component.translatable(PREFIX + "import").getString()),
                        "short layout exposes the full drop instructions to narration");
            }
        } finally {
            screen.resize(width, height);
        }
    }

    static void closed(Screen screen, String id) {
        if (!id.equals(sample)) return;
        Object player = field(screen, "playback");
        require((boolean) field(player, "closed") && field(player, "clip") == null
                        && ((ExecutorService) field(player, "decoder")).isShutdown(),
                "leaving the sample closes audio and shuts down decoding");
        System.out.println("SHBUI_GALLERY_MUSIC_RELEASED id=" + sample);
    }

    private static void verifySeek(Screen screen, Object player, String id) {
        var slider = (UiSlider) field(screen, "seek");
        var before = pointer(slider, .25);
        var after = pointer(slider, .50);
        require(screen.mouseClicked(before, false), "seek accepts pointer capture");
        screen.mouseDragged(after, after.x() - before.x(), 0);
        require(slider.isPointerEditing() && Math.abs(slider.value() - 500) < 15
                        && number(player, "position") == paused, "drag previews without seeking the device");
        require(screen.keyPressed(ESCAPE), "Escape cancels the seek");
        screen.mouseReleased(after);
        require(!slider.isPointerEditing() && number(player, "position") == paused, "cancel preserves the device cursor");
        click(screen, after);
        long seekPosition = number(player, "position");
        require(Math.abs(seekPosition - number(player, "duration") / 2) < 150, "seek commits on release");
        if (id.equals("lyrics")) {
            String cue = Component.translatable("sh_better_ui.lyrics.demo.second.0").getString();
            var line = (LyricLine) call(screen, "currentLine", new Class<?>[]{long.class}, seekPosition);
            require(line != null && line.text().startsWith(cue)
                    && screen.getNarrationMessage().getString().contains(line.text()),
                    "seek changes the displayed and narrated lyric");
        }
    }

    private static void verifyPresentation(Screen screen, String id) {
        if (id.equals("music_hud")) {
            var mode = (AbstractWidget) field(screen, "mode");
            click(screen, mode);
            require(!(boolean) field(screen, "hudOverlay"), "switches from HUD to panel view");
        } else if (id.equals("music_island")) {
            var mode = (AbstractWidget) field(screen, "mode");
            var seek = (AbstractWidget) field(screen, "seek");
            click(screen, mode);
            require(!seek.active && !seek.visible, "collapsing the capsule hides and disables transport");
            click(screen, mode);
            require(seek.active && seek.visible, "expanding the capsule restores transport");
        }
    }

    private static Path writeFixture(Minecraft minecraft) {
        try {
            Path directory = Files.createTempDirectory(minecraft.gameDirectory.toPath().toAbsolutePath(), "shb-gallery-music-");
            Path wave = directory.resolve("Smoke.wav");
            fixture = wave;
            int rate = 22_050;
            byte[] pcm = new byte[rate * 4 * 2];
            for (int i = 0; i < pcm.length / 2; i++) {
                int value = (int) (1800 * Math.sin(Math.TAU * 220 * i / rate));
                pcm[2 * i] = (byte) value;
                pcm[2 * i + 1] = (byte) (value >> 8);
            }
            try (var audio = new AudioInputStream(new ByteArrayInputStream(pcm),
                    new AudioFormat(rate, 16, 1, true, false), pcm.length / 2)) {
                AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wave.toFile());
            }
            Files.writeString(directory.resolve("Smoke.lrc"), "[00:00.00]First smoke cue\n[00:01.00]Second smoke cue\n");
            Files.createDirectory(directory.resolve("Folder.wav"));
            Files.writeString(directory.resolve("Unsupported.txt"), "not audio");
            Files.writeString(directory.resolve("Corrupt.wav"), "not a WAV header");
            writeOversizedWave(directory.resolve("Oversized.wav"), rate);
            return wave;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void writeOversizedWave(Path file, int rate) throws IOException {
        int bytes = 32 * 1024 * 1024 + 2;
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(bytes + 36);
        header.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16);
        header.putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2);
        header.putShort((short) 2).putShort((short) 16);
        header.put(new byte[] {'d', 'a', 't', 'a'}).putInt(bytes);
        try (var wave = new RandomAccessFile(file.toFile(), "rw")) {
            wave.write(header.array());
            wave.setLength(44L + bytes);
        }
    }

    private static Path sibling(String name) { return fixture.resolveSibling(name); }

    private static boolean pass() {
        cleanFixture();
        System.out.println("SHBUI_GALLERY_MUSIC_PASS id=" + sample);
        return true;
    }

    private static void cleanFixture() {
        if (fixture == null) return;
        try {
            Files.deleteIfExists(fixture);
            for (String name : List.of("Smoke.lrc", "Folder.wav", "Unsupported.txt", "Corrupt.wav", "Oversized.wav")) {
                Files.deleteIfExists(sibling(name));
            }
            Files.deleteIfExists(fixture.getParent());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } finally {
            fixture = null;
        }
    }

    private static void advance(int nextStage) {
        stage = nextStage;
        phaseStart = System.nanoTime();
    }

    private static MouseButtonEvent pointer(UiSlider slider, double fraction) {
        return new MouseButtonEvent(slider.getX() + 4 + (slider.getWidth() - 8) * fraction,
                slider.getY() + slider.getHeight() / 2.0, LEFT);
    }

    private static void click(Screen screen, AbstractWidget widget) {
        require(widget.active && widget.visible, "control is visible and enabled");
        click(screen, new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0, LEFT));
    }

    private static void click(Screen screen, MouseButtonEvent event) {
        require(screen.mouseClicked(event, false), "control accepted pointer input");
        screen.mouseReleased(event);
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(name, failure);
        }
    }

    private static long number(Object target, String name) {
        return ((Number) call(target, name)).longValue();
    }

    private static Object call(Object target, String name) {
        return call(target, name, new Class<?>[0]);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try {
            var method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(name, failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(name, failure);
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Gallery music " + sample + ": " + description);
    }
}
