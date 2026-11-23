package com.muoniumplayer.core.screens.ncm;

import java.util.ArrayList;
import java.util.List;

/** A view-only choice for provider-supplied lyrics aligned to the original line. */
enum LyricLanguage {
    ORIGINAL("original"), TRANSLATION("translation"), PRONUNCIATION("pronunciation");

    final String key;

    LyricLanguage(String key) {
        this.key = "sh_better_ui.muonium.lyric_language." + key;
    }

    String sidecar(String translation, String pronunciation) {
        return switch (this) {
            case ORIGINAL -> null;
            case TRANSLATION -> nonBlank(translation);
            case PRONUNCIATION -> nonBlank(pronunciation);
        };
    }

    static List<LyricLanguage> options(boolean translation, boolean pronunciation) {
        List<LyricLanguage> modes = new ArrayList<>(3);
        modes.add(ORIGINAL);
        if (translation) modes.add(TRANSLATION);
        if (pronunciation) modes.add(PRONUNCIATION);
        return List.copyOf(modes);
    }

    static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
