package com.dishanhai.sh_better_ui.client.gui.search;

import com.ibm.icu.text.Transliterator;
import com.dishanhai.sh_better_ui.kernel.api.search.PhoneticText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 山海：保留汉字片段的音节，供核心匹配全拼、首字母和混合前缀。 */
final class PinyinSearch {
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}+");
    private static final Pattern TONE_MARKS = Pattern.compile("[\\u0300\\u0301\\u0304\\u030c]");

    private PinyinSearch() {}

    static List<PhoneticText> readings(String text) {
        var han = HAN.matcher(text);
        if (!han.find()) return List.of();

        List<PhoneticText> readings = new ArrayList<>();
        do {
            String reading = fold(HanLatin.INSTANCE.transliterate(han.group()));
            List<String> syllables = java.util.Arrays.stream(reading.split(" "))
                    .filter(syllable -> !syllable.isEmpty()).toList();
            readings.add(new PhoneticText(syllables));
        } while (han.find());
        return readings;
    }

    static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return TONE_MARKS.matcher(decomposed.replace("u\u0308", "v")).replaceAll("");
    }

    private static final class HanLatin {
        private static final Transliterator INSTANCE = Transliterator.getInstance("Han-Latin");
    }
}
