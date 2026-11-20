package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.BooleanSupplier;

/** Theme colors consumed by shared widgets. */
public final class WidgetTheme {
    public enum Role { BORDER, INPUT_BACKGROUND, PRIMARY_TEXT, SECONDARY_TEXT }

    private static final ToIntFunction<Role> DEFAULT_COLORS = role -> switch (role) {
        case BORDER -> 0xff454b55;
        case INPUT_BACKGROUND -> 0xff202832;
        case PRIMARY_TEXT -> 0xfff2f4f8;
        case SECONDARY_TEXT -> 0xffaab2bf;
    };
    private static ToIntFunction<Role> colors = DEFAULT_COLORS;
    private static BooleanSupplier showBounds = () -> false;

    private WidgetTheme() {}

    public static void setColors(ToIntFunction<Role> provider) {
        colors = Objects.requireNonNull(provider);
    }

    public static int color(Role role) {
        return colors.applyAsInt(role);
    }

    public static void setShowBounds(BooleanSupplier provider) {
        showBounds = Objects.requireNonNull(provider);
    }

    public static boolean showBounds() {
        return showBounds.getAsBoolean();
    }

    public static void reset() {
        colors = DEFAULT_COLORS;
        showBounds = () -> false;
    }
}
