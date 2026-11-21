package com.muoniumplayer.core.screens.ncm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LyricLanguageTest {
    @Test
    void onlyOffersPresentSidecars() {
        assertEquals(List.of(LyricLanguage.ORIGINAL), LyricLanguage.options(false, false));
        assertEquals(List.of(LyricLanguage.ORIGINAL, LyricLanguage.TRANSLATION), LyricLanguage.options(true, false));
        assertEquals(List.of(LyricLanguage.ORIGINAL, LyricLanguage.PRONUNCIATION), LyricLanguage.options(false, true));
        assertEquals(List.of(LyricLanguage.ORIGINAL, LyricLanguage.TRANSLATION, LyricLanguage.PRONUNCIATION),
                LyricLanguage.options(true, true));
    }

    @Test
    void doesNotSubstituteAnUnrequestedLanguageOnPartialSidecars() {
        assertNull(LyricLanguage.ORIGINAL.sidecar("translated", "pronounced"));
        assertNull(LyricLanguage.TRANSLATION.sidecar("  ", "pronounced"));
        assertNull(LyricLanguage.PRONUNCIATION.sidecar("translated", null));
        assertEquals("翻译", LyricLanguage.TRANSLATION.sidecar(" 翻译 ", "pronounced"));
        assertEquals("kana", LyricLanguage.PRONUNCIATION.sidecar("translated", " kana "));
    }
}
