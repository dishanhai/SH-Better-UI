// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Bounded, session-owned index of tracks already returned by a source. */
final class OnlineMusicSearch {
    private static final int DEFAULT_CAPACITY = 500;
    private record Key(String source, String id) {}

    private final int capacity;
    private final Map<Key, OnlineTrack> tracks = new LinkedHashMap<>();
    private SearchIndex<OnlineTrack> index;

    OnlineMusicSearch() { this(DEFAULT_CAPACITY); }

    OnlineMusicSearch(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Invalid music index capacity");
        this.capacity = capacity;
    }

    synchronized void addAll(List<OnlineTrack> incoming) {
        Objects.requireNonNull(incoming);
        boolean changed = false;
        for (var track : incoming) {
            Objects.requireNonNull(track);
            if (track.title().isBlank()) continue;
            var key = new Key(track.source(), track.id());
            tracks.remove(key);
            tracks.put(key, track);
            while (tracks.size() > capacity) tracks.remove(tracks.keySet().iterator().next());
            changed = true;
        }
        if (changed) index = null;
    }

    synchronized void clear(String source) {
        Objects.requireNonNull(source);
        if (tracks.keySet().removeIf(key -> key.source().equals(source))) index = null;
    }

    synchronized void clear() {
        tracks.clear();
        index = null;
    }

    synchronized List<OnlineTrack> search(String source, String query, int limit) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(query);
        if (query.isBlank() || limit <= 0) return List.of();
        return index().searchPlainText(query, track -> track.source().equals(source)).stream()
                .limit(limit).map(SearchIndex.Entry::value).toList();
    }

    synchronized List<String> suggest(String source, String query, int limit) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(query);
        if (query.isBlank() || limit <= 0) return List.of();
        var candidates = tracks.values().stream().filter(track -> track.source().equals(source)).toList();
        var unique = new LinkedHashMap<String, String>();
        for (var suggestion : index().suggestPlainText(query, candidates, candidates.size())) {
            String title = suggestion.entry().value().title().strip();
            unique.putIfAbsent(title.toLowerCase(Locale.ROOT), title);
            if (unique.size() >= limit) break;
        }
        return List.copyOf(unique.values());
    }

    private SearchIndex<OnlineTrack> index() {
        if (index == null) {
            var entries = new ArrayList<SearchIndex.Entry<OnlineTrack>>(tracks.size());
            for (var track : tracks.values()) {
                var aliases = new ArrayList<String>(2);
                if (!track.artist().isBlank()) aliases.add(track.artist());
                if (!track.album().isBlank()) aliases.add(track.album());
                entries.add(new SearchIndex.Entry<>(track, track.title(), List.of(), aliases));
            }
            index = new SearchIndex<>(entries);
        }
        return index;
    }
}
