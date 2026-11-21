// Author: dishanhai
package com.muoniumplayer.core.viper;

import com.muoniumplayer.core.viper.dsp.WavReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Real-room matrix responses selected by the output clock. Presets keep a single stable filename. */
final class ViperRoomKernels {
    static final List<String> NAMES = List.of("Surrey-Room-A.wav", "Surrey-Room-B.wav");
    private static final String ROOT = "/assets/sh_better_ui/audio/rooms/";

    private ViperRoomKernels() {}

    record Pair(WavReader.WavData at44100, WavReader.WavData at48000) {
        WavReader.WavData at(int rate) {
            return switch (rate) {
                case 44100 -> at44100;
                case 48000 -> at48000;
                default -> null;
            };
        }
    }

    static boolean contains(String name) {
        return name != null && NAMES.contains(name);
    }

    static Pair load(String name) {
        if (!contains(name)) return null;
        WavReader.WavData at44100 = read(name.substring(0, name.length() - 4) + "-44100.wav", 44100);
        WavReader.WavData at48000 = read(name, 48000);
        return at44100 == null || at48000 == null ? null : new Pair(at44100, at48000);
    }

    private static WavReader.WavData read(String name, int rate) {
        try (InputStream input = ViperRoomKernels.class.getResourceAsStream(ROOT + name)) {
            if (input == null) return null;
            WavReader.WavData response = new WavReader.WavData();
            if (WavReader.decode(input.readAllBytes(), response)
                    && response.channels == 4 && response.sampleRate == rate && response.frameCount >= 16) {
                return response;
            }
        } catch (IOException unreadable) {
            return null;
        }
        return null;
    }
}
