package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicLocaleParityTest {
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z%])|§[0-9a-fk-or]");

    @Test
    void chineseLocalesCoverMusicLabelsAndPreserveTheirArguments() throws IOException {
        var reference = readLocale("en_us");
        // Include music-related keys in every namespace, not just the online player.
        var keys = reference.keySet().stream().filter(key -> key.contains("music")).sorted().toList();
        assertFalse(keys.isEmpty(), "Expected music keys in the English language asset");
        for (var language : List.of("zh_cn", "zh_tw")) {
            var translation = readLocale(language);
            var missing = keys.stream().filter(key -> !translation.has(key)).toList();
            assertTrue(missing.isEmpty(), () -> language + " is missing " + missing);
            for (var key : keys) {
                var text = translation.get(key).getAsString();
                assertFalse(text.isBlank(), () -> language + ": " + key + " is blank");
                assertEquals(format(reference.get(key).getAsString()), format(text), language + ": " + key);
            }
        }
    }

    private static JsonObject readLocale(String language) throws IOException {
        var path = "/assets/sh_better_ui/lang/" + language + ".json";
        var resource = MusicLocaleParityTest.class.getResourceAsStream(path);
        assertNotNull(resource, path);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<String> format(String text) {
        var formats = new ArrayList<String>();
        var matcher = FORMAT.matcher(text);
        int nextArgument = 1;
        while (matcher.find()) {
            var conversion = matcher.group(2);
            if (conversion == null || conversion.equals("%")) {
                formats.add(matcher.group());
            } else {
                var index = matcher.group(1);
                formats.add((index == null ? Integer.toString(nextArgument++) : index) + "$" + conversion);
            }
        }
        // Translators can change word order with explicit argument indices.
        Collections.sort(formats);
        return formats;
    }
}
