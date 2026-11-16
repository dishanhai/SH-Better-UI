// Author: dishanhai. YRC timing adapted from MuoniumPlayer (Apache-2.0).
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.*;
import java.util.ArrayList;
import java.util.regex.Pattern;

final class OnlineLyrics {
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)");
    static LyricTimeline parse(String lrc, String translation, String yrc, long duration) {
        var fallback = LrcParser.parse(lrc, translation, duration);
        if (yrc == null || yrc.isBlank()) return fallback;
        var lines = new ArrayList<LyricLine>();
        var translations = LrcParser.parse(translation, null, duration);
        for (String raw : yrc.split("\\R")) {
            var line = LINE.matcher(raw);
            if (!line.matches()) continue;
            try {
                long start = Long.parseLong(line.group(1)), length = Long.parseLong(line.group(2));
                if (start > 86_400_000 || length > 86_400_000) continue;
                var words = new ArrayList<LyricWord>();
                var text = new StringBuilder();
                var word = WORD.matcher(line.group(3));
                while (word.find()) {
                    long time = Long.parseLong(word.group(1)), span = Long.parseLong(word.group(2));
                    if (time > 86_400_000 || span > 86_400_000) continue;
                    text.append(word.group(3));
                    words.add(new LyricWord(word.group(3), time, time + span));
                }
                var translated = translations.lineAt(start + 100);
                String trans = translated != null && Math.abs(translated.startMs() - start) <= 100 ? translated.text() : "";
                if (!words.isEmpty()) lines.add(new LyricLine(text.toString(), trans, start, start + length, words, ""));
            } catch (NumberFormatException ignored) { /* One malformed row does not discard valid LRC. */ }
        }
        return lines.isEmpty() ? fallback : new LyricTimeline(lines);
    }
}
