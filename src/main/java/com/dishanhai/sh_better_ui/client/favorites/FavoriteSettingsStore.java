// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.favorites;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local pinned setting IDs, never provider song/account favorites or credentials. */
public final class FavoriteSettingsStore {
    public static final List<String> AVAILABLE = List.of("hud", "island", "ambient", "tasks", "scale");
    public static final List<String> DEFAULT = List.of("hud", "island", "scale");
    private final Path file;
    private List<String> ids = DEFAULT;
    public FavoriteSettingsStore(Path file) { this.file = Objects.requireNonNull(file); }
    public void load() throws IOException {
        if (!Files.exists(file)) { ids = DEFAULT; return; }
        if (Files.size(file) > 16384) throw new IOException("Favorites file exceeds size limit");
        ids = normalize(Files.readAllLines(file, StandardCharsets.UTF_8));
    }
    public List<String> ids() { return ids; }
    public void save(Collection<String> value) throws IOException {
        var safe = normalize(value);
        Path destination = file.toAbsolutePath(), directory = destination.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory,"favorites-", ".tmp");
        try {
            Files.write(temporary,safe,StandardCharsets.UTF_8);
            try { Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING); }
            ids = safe;
        } finally { Files.deleteIfExists(temporary); }
    }
    private static List<String> normalize(Collection<String> value) {
        var result = new LinkedHashSet<String>();
        for (String id : value) if (AVAILABLE.contains(id)) result.add(id);
        return List.copyOf(result);
    }
}
