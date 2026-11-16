// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.appearance;

import com.dishanhai.sh_better_ui.theme.ThemeId;
import java.util.Locale;
import java.util.Objects;

/** A resource-pack image or local PNG/JPEG path, resolved relative to the game directory. */
public record BackgroundImage(String source, Fit fit) {
    public static final String ID = "sh_better_ui:image";
    public static final BackgroundImage DEFAULT = new BackgroundImage("", Fit.COVER);
    public enum Fit { COVER, CONTAIN, STRETCH }

    public BackgroundImage {
        source = Objects.requireNonNull(source).strip();
        Objects.requireNonNull(fit);
        if (source.length() > 1024 || source.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid image path");
        if (!source.isEmpty()) {
            String lower = source.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
                throw new IllegalArgumentException("Expected PNG or JPEG");
            }
            if (resource(source)) ThemeId.require(source);
        }
    }

    public static boolean resource(String source) {
        return source.contains(":") && !source.matches("^[a-zA-Z]:[\\\\/].*");
    }
}
