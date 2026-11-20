package com.dishanhai.sh_better_ui.kernel.api.text;

import java.io.File;
import java.util.Objects;

/** Host services for optional font assets and font-change notifications. */
public interface FontServices {
    FontServices NONE = new FontServices() {};

    default long pinnedBytes(String fileName) { return 0; }
    default File cachedFile(String fileName) { return null; }
    default void fontChanged() {}

    final class Host {
        private static FontServices services = NONE;

        private Host() {}

        public static void set(FontServices value) {
            services = Objects.requireNonNull(value);
        }

        public static FontServices get() {
            return services;
        }
    }
}
