package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Objects;

/** Touch state supplied by the host to scrollable widgets. */
public interface WidgetTouch {
    WidgetTouch NONE = new WidgetTouch() {};

    default boolean enabled() { return false; }
    default boolean verticalDrag() { return false; }
    default Object claimedBy() { return null; }
    default double dy() { return 0; }
    default boolean pressed() { return false; }
    default double velocityY() { return 0; }
    default double startX() { return 0; }
    default double startY() { return 0; }
    default boolean claim(Object owner) { return false; }
    default double flingMaxDistance() { return 0; }
    default double flingDeceleration() { return 1; }

    final class Host {
        private static WidgetTouch touch = NONE;

        private Host() {}

        public static void set(WidgetTouch value) {
            touch = Objects.requireNonNull(value);
        }

        public static WidgetTouch get() {
            return touch;
        }
    }
}
