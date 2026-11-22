// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.settings;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Explicitly exposed client settings. Call only on the client thread. */
public final class AssistantSettings {
    private static final String PROVIDER = "sh_common_lib:assistant_settings";
    private AssistantSettings() {}

    /** Human-readable, localized catalog with stable IDs and current values. */
    public static String list() {
        var lines = new ArrayList<String>();
        for (var option : options()) lines.add(describe(option));
        return String.join("\n", lines);
    }

    public static List<String> ids() { return options().stream().map(Option::id).toList(); }

    public static String read(String id) {
        return options().stream().filter(option -> option.id().equals(id)).findFirst()
                .map(AssistantSettings::describe).orElseGet(() -> message(Status.UNKNOWN_SETTING, id));
    }

    public enum Status { OK, INVALID_VALUE, UNKNOWN_SETTING, SAVE_FAILED }
    public record Result(Status status, String detail) {}

    public static String write(String id, String value) {
        var result = writeResult(id, value);
        return message(result.status(), result.detail());
    }

    public static Result writeResult(String id, String value) {
        var option = options().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (option == null) return new Result(Status.UNKNOWN_SETTING, id);
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) return new Result(Status.INVALID_VALUE, id);
        boolean enabled = Boolean.parseBoolean(value);
        return option.write().test(enabled) ? new Result(Status.OK, describe(option)) : new Result(Status.SAVE_FAILED, id);
    }

    private static String message(Status status, String detail) {
        return Component.translatable("sh_better_ui.assistant.setting.result." + status.name().toLowerCase(java.util.Locale.ROOT), detail).getString();
    }

    private static String describe(Option option) {
        return option.id() + " = " + option.read().getAsBoolean() + " | "
                + Component.translatable(option.nameKey()).getString() + " | "
                + Component.translatable(option.descriptionKey()).getString();
    }

    private static List<Option> options() {
        var result = new ArrayList<Option>();
        result.add(builtin("high_contrast", () -> ClientUiSettings.current().highContrast(), value -> {
            var s = ClientUiSettings.current();
            return ShBetterUiApi.settings().save(s.withDisplay(value, s.reducedMotion(), s.widgetBackdropBlur()));
        }));
        result.add(builtin("reduced_motion", () -> ClientUiSettings.current().reducedMotion(), value -> {
            var s = ClientUiSettings.current();
            return ShBetterUiApi.settings().save(s.withDisplay(s.highContrast(), value, s.widgetBackdropBlur()));
        }));
        result.add(builtin("widget_blur", () -> ClientUiSettings.current().widgetBackdropBlur(), value -> {
            var s = ClientUiSettings.current();
            return ShBetterUiApi.settings().save(s.withDisplay(s.highContrast(), s.reducedMotion(), value));
        }));
        if (ShBetterUiApi.extensions().contains(PROVIDER)) {
            try (var context = ShBetterUiApi.context()) {
                Object provider = context.extension(PROVIDER);
                if (provider instanceof Supplier<?> supplier && supplier.get() instanceof List<?> entries) {
                    for (Object entry : entries) {
                        if (entry instanceof Map<?, ?> map && map.get("id") instanceof String id
                                && map.get("nameKey") instanceof String name && map.get("descriptionKey") instanceof String description
                                && map.get("read") instanceof BooleanSupplier read && map.get("write") instanceof Predicate<?> rawWrite) {
                            @SuppressWarnings("unchecked") Predicate<Boolean> write = (Predicate<Boolean>) rawWrite;
                            if (id.startsWith("sh_common_lib:") && result.stream().noneMatch(existing -> existing.id().equals(id)))
                                result.add(new Option(id, name, description, read, write));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Option builtin(String id, BooleanSupplier read, Predicate<Boolean> write) {
        return new Option("sh_better_ui:" + id, "sh_better_ui.assistant.setting." + id,
                "sh_better_ui.assistant.setting." + id + ".description", read, write);
    }

    private record Option(String id, String nameKey, String descriptionKey,
                          BooleanSupplier read, Predicate<Boolean> write) {}
}
