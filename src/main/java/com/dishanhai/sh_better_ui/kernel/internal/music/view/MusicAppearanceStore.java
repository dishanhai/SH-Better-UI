// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;

/** Stores only the music surface preference; playback and global UI settings are untouched. */
final class MusicAppearanceStore {
    enum Preference {
        AUTOMATIC, CLASSIC, LIQUID_GLASS;

        String id() { return name().toLowerCase(Locale.ROOT); }

        static Preference read(String value) {
            for (var preference : values()) {
                if (preference.id().equalsIgnoreCase(value.strip())) return preference;
            }
            return AUTOMATIC;
        }

        Preference next() {
            return switch (this) {
                case AUTOMATIC -> CLASSIC;
                case CLASSIC -> LIQUID_GLASS;
                case LIQUID_GLASS -> AUTOMATIC;
            };
        }
    }

    private final Path file;
    private Preference value;

    MusicAppearanceStore(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        try {
            value = Files.isRegularFile(this.file) && Files.size(this.file) <= 64
                    ? Preference.read(Files.readString(this.file)) : Preference.AUTOMATIC;
        } catch (IOException | SecurityException unavailable) {
            value = Preference.AUTOMATIC;
        }
    }

    Preference value() { return value; }

    boolean select(Preference next) {
        Objects.requireNonNull(next);
        if (next == value) return true;
        Path temporary = null;
        try {
            Path directory = Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(directory, "music-appearance-", ".tmp");
            Files.writeString(temporary, next.id() + "\n");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            value = next;
            return true;
        } catch (IOException | SecurityException unavailable) {
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException | SecurityException ignored) { }
        }
    }
}
