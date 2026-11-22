// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Recent submitted queries, scoped by source and search mode. */
final class MusicSearchHistory {
    static final int PER_SCOPE_LIMIT = 8;
    private static final int TOTAL_LIMIT = 128;
    private static final int QUERY_LIMIT = 512;
    private static final Gson GSON = new Gson();

    private final Path file;
    private ArrayList<Entry> entries;

    private record Entry(String scope, String query) {}

    MusicSearchHistory(Path file) {
        this.file = file.toAbsolutePath();
    }

    List<String> recent(String scope) {
        load();
        return entries.stream().filter(entry -> entry.scope().equals(scope)).map(Entry::query).toList();
    }

    void record(String scope, String query) {
        load();
        String value = query.strip();
        if (value.isEmpty() || value.length() > QUERY_LIMIT) return;
        entries.removeIf(entry -> entry.scope().equals(scope) && entry.query().equalsIgnoreCase(value));
        entries.add(0, new Entry(scope, value));
        trim(scope);
        save();
    }

    List<String> clear(String scope) {
        List<String> removed = recent(scope);
        if (!removed.isEmpty()) {
            entries.removeIf(entry -> entry.scope().equals(scope));
            save();
        }
        return removed;
    }

    void restore(String scope, List<String> removed) {
        List<String> current = recent(scope);
        for (String query : removed) {
            if (current.stream().noneMatch(query::equalsIgnoreCase)) entries.add(new Entry(scope, query));
        }
        trim(scope);
        save();
    }

    private void trim(String scope) {
        int count = 0;
        for (var iterator = entries.iterator(); iterator.hasNext();) {
            if (iterator.next().scope().equals(scope) && ++count > PER_SCOPE_LIMIT) iterator.remove();
        }
        entries.subList(Math.min(entries.size(), TOTAL_LIMIT), entries.size()).clear();
    }

    private void load() {
        if (entries != null) return;
        entries = new ArrayList<>();
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 512 * 1024) return;
            var data = JsonParser.parseString(Files.readString(file));
            if (!data.isJsonArray()) return;
            for (var element : data.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                var object = element.getAsJsonObject();
                var scope = object.get("scope");
                var query = object.get("query");
                if (scope == null || !scope.isJsonPrimitive() || !scope.getAsJsonPrimitive().isString()
                        || query == null || !query.isJsonPrimitive() || !query.getAsJsonPrimitive().isString()) continue;
                var entry = new Entry(scope.getAsString(), query.getAsString().strip());
                if (entry.scope().isBlank() || entry.scope().length() > 128
                        || entry.query().isBlank() || entry.query().length() > QUERY_LIMIT) continue;
                if (entries.stream().noneMatch(existing -> existing.scope().equals(entry.scope())
                        && existing.query().equalsIgnoreCase(entry.query()))) {
                    entries.add(entry);
                    trim(entry.scope());
                    if (entries.size() >= TOTAL_LIMIT) break;
                }
            }
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger(MusicSearchHistory.class).warn("Could not read music search history", error);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "search-history-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(entries));
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            LoggerFactory.getLogger(MusicSearchHistory.class).warn("Could not save music search history", error);
        }
    }
}
