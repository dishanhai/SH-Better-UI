// Author: dishanhai
package com.dishanhai.sh_better_ui.automation;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Reads the same translation resources used by clients without loading client classes. */
public final class AutomationText {
    private static final Map<String, Map<String, String>> LANGUAGES = load();

    private AutomationText() {}

    public static String language(String requested) {
        return LANGUAGES.containsKey(requested) ? requested : "en_us";
    }

    public static String get(String language, String key, Object... arguments) {
        String fullKey = key.startsWith("sh_better_ui.") ? key : "sh_better_ui.automation." + key;
        String value = LANGUAGES.get(language(language)).getOrDefault(fullKey,
                LANGUAGES.get("en_us").getOrDefault(fullKey, fullKey));
        return arguments.length == 0 ? value : value.formatted(arguments);
    }

    private static Map<String, Map<String, String>> load() {
        var result = new HashMap<String, Map<String, String>>();
        for (String language : new String[]{"en_us", "zh_cn", "zh_tw"}) {
            var entries = new HashMap<String, String>();
            try (var stream = AutomationText.class.getResourceAsStream(
                    "/assets/sh_better_ui/lang/" + language + ".json")) {
                if (stream == null) throw new IllegalStateException("Missing language resource: " + language);
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
                        if (entry.getKey().startsWith("sh_better_ui.automation.")) {
                            entries.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    });
                }
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Unable to read automation translations", failure);
            }
            result.put(language, Map.copyOf(entries));
        }
        return Map.copyOf(result);
    }
}
