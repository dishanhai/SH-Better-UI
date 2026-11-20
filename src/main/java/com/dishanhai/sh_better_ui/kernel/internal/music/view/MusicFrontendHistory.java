// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Observational only: never attaches to, retries, or mutates a session operation. */
final class MusicFrontendHistory {
    record Key(long id, long epoch) {}
    record Entry(Key key, SessionMusicOperations.Snapshot snapshot) {}
    record Detail(Entry entry, String caption) {}
    private final SessionMusicOperations registry;

    MusicFrontendHistory(SessionMusicOperations registry) { this.registry = registry; }
    boolean available() { return registry != null; }

    List<Entry> entries() {
        if (registry == null) return List.of();
        synchronized (registry) {
            var result = new ArrayList<Entry>();
            for (var snapshot : registry.snapshots()) registry.find(snapshot.id()).ifPresent(handle ->
                    result.add(new Entry(new Key(snapshot.id(), handle.accountVersion()), snapshot)));
            Collections.reverse(result);
            return List.copyOf(result);
        }
    }

    Key notification(MusicOperationNavigation.Target target) {
        if (registry == null) return null;
        synchronized (registry) {
            if (!target.validFor(registry)) return null;
            var handle = registry.find(target.id()).orElse(null);
            return handle == null ? null : new Key(handle.id(), handle.accountVersion());
        }
    }

    Entry lookup(Key key) {
        if (registry == null || key == null) return null;
        synchronized (registry) {
            var handle = registry.find(key.id()).orElse(null);
            if (handle == null || handle.accountVersion() != key.epoch() || !handle.valid()) return null;
            var snapshot = handle.snapshot();
            if (snapshot.state() == SessionMusicOperations.State.CANCELLED
                    || snapshot.state() == SessionMusicOperations.State.SUPERSEDED) return null;
            return new Entry(key, snapshot);
        }
    }

    Detail details(Key key) {
        if (registry == null) return null;
        synchronized (registry) {
            var entry = lookup(key);
            if (entry == null) return null;
            var handle = registry.find(key.id()).orElse(null);
            if (handle == null) return null;
            var request = handle.request();
            String caption = switch (request.kind()) {
                case SEARCH -> request.query();
                case PLAYLIST -> request.playlist() == null ? "" : request.playlist().name();
                default -> "";
            };
            return new Detail(entry, caption);
        }
    }

    static MusicFrontendRoutes.Route detail(Key key) {
        Objects.requireNonNull(key);
        return new MusicFrontendRoutes.Route(MusicFrontendRoutes.Page.HISTORY, "", key.id() + ":" + key.epoch());
    }

    static Key parse(MusicFrontendRoutes.Route route) {
        if (route.page() != MusicFrontendRoutes.Page.HISTORY || route.id().isEmpty()) return null;
        int separator = route.id().indexOf(':');
        if (separator <= 0) return null;
        try {
            return new Key(Long.parseLong(route.id().substring(0, separator)),
                    Long.parseLong(route.id().substring(separator + 1)));
        } catch (NumberFormatException invalid) { return null; }
    }
}
