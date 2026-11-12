// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

/** Persist applied preview parameters separately from the control panel. */
final class GalleryPreviewSettings {
    private static final System.Logger LOGGER = System.getLogger(GalleryPreviewSettings.class.getName());

    private GalleryPreviewSettings() {}

    static Properties read() {
        try {
            return readFile(file());
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read gallery preview preferences.", error);
            return new Properties();
        }
    }

    static boolean write(Consumer<Properties> update) {
        Path file = file();
        Path temporary = null;
        try {
            Properties properties = readFile(file);
            update.accept(properties);
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "gallery-previews-", ".tmp");
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "SH-Better-UI developer gallery previews");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save gallery preview preferences.", error);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException error) {
                    LOGGER.log(System.Logger.Level.WARNING, "Could not remove gallery preview temporary file.", error);
                }
            }
        }
    }

    static int number(Properties properties, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, ""));
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("sh_better_ui").resolve("gallery-previews.properties");
    }

    private static Properties readFile(Path file) throws IOException {
        Properties result = new Properties();
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) {
                result.load(input);
            }
        }
        return result;
    }
}
