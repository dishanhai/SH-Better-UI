// 作者：山海／dishanhai
package com.dishanhai.sh_better_ui.automation.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** 仅保存客户端呈现偏好；每个模组、原屏幕分别记录，不影响服务端权限。 */
final class AutomationClientPreferences {
    private static final Map<String, Boolean> replacements = new HashMap<>();
    private static boolean loaded;
    private static boolean readable = true;
    private AutomationClientPreferences() {}

    static boolean enabled(String mod, String screen) {
        load();
        return replacements.getOrDefault(mod + ":" + screen, readable);
    }

    static boolean set(String mod, String screen, boolean enabled) {
        load();
        var next = new HashMap<>(replacements);
        next.put(mod + ":" + screen, enabled);
        var values = new JsonObject();
        next.forEach(values::addProperty);
        var json = new JsonObject();
        json.addProperty("version", 1);
        json.add("screens", values);
        Path target = path();
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            replacements.clear(); replacements.putAll(next); readable = true;
            return true;
        } catch (java.io.IOException failure) {
            LoggerFactory.getLogger(AutomationClientPreferences.class).warn("Cannot save terminal UI preferences", failure);
            return false;
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(path())) return;
        try {
            var object = JsonParser.parseString(Files.readString(path(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (object.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown preference version");
            for (var entry : object.getAsJsonObject("screens").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("Invalid replacement preference");
                }
                replacements.put(entry.getKey(), entry.getValue().getAsBoolean());
            }
        } catch (java.io.IOException | RuntimeException failure) {
            replacements.clear(); readable = false;
            LoggerFactory.getLogger(AutomationClientPreferences.class).warn("Cannot read terminal UI preferences; preserving original screens", failure);
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/sh_better_ui/automation-terminal-ui.json");
    }
}
