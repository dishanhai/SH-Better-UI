// Author: dishanhai
package com.muoniumplayer.core.ncm.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.muoniumplayer.core.ncm.OptionsUtil;
import com.muoniumplayer.core.ncm.RequestUtil;
import com.muoniumplayer.core.ncm.api.CloudMusicApi;
import com.muoniumplayer.core.ncm.music.dto.Artist;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.utils.json.JsonUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Loads catalog recommendations and falls back to other tracks by the seed artist. */
final class QueueAutoplay {
    private QueueAutoplay() {}

    static String artist(Music track) {
        if (track.getArtists() == null) return null;
        for (Artist artist : track.getArtists()) {
            if (artist != null && artist.getName() != null) {
                String name = artist.getName().strip();
                if (!name.isEmpty() && !name.equalsIgnoreCase("Unknown")) return name;
            }
        }
        return null;
    }

    static List<Music> find(Music seed) {
        if (seed.isNetease()) {
            try {
                List<Music> similar = similarNeteaseTracks(seed.getId());
                if (!similar.isEmpty()) return similar;
            } catch (RuntimeException unavailable) {
                // Keep the same-artist fallback when the recommendation service is unavailable.
            }
        }
        String artist = artist(seed);
        if (artist == null) return List.of();
        var hits = NeteaseMusicService.search(seed.getSource(), artist, 40);
        return hits.stream()
                .filter(track -> track.getSource() == seed.getSource() && track.getArtists() != null
                        && track.getArtists().stream().anyMatch(entry -> entry != null && entry.getName() != null
                                && artist.equalsIgnoreCase(entry.getName().strip())))
                .filter(QueueAutoplay::hasAccess)
                .toList();
    }

    private static List<Music> similarNeteaseTracks(long seed) {
        var request = RequestUtil.createRequest("/api/v1/discovery/simiSong",
                Map.of("songid", seed, "limit", 20, "offset", 0, "total", true),
                OptionsUtil.createOptions("weapi"));
        var ids = new LinkedHashSet<Long>();
        for (JsonElement entry : array(request.toJsonObject(), "songs")) {
            if (ids.size() == 20) break;
            Long id = songId(entry);
            if (id != null && id > 0 && id != seed) ids.add(id);
        }
        if (ids.isEmpty()) return List.of();

        JsonObject details = CloudMusicApi.songDetail(List.copyOf(ids)).toJsonObject();
        var privileges = new HashMap<Long, JsonObject>();
        for (JsonElement entry : array(details, "privileges")) {
            Long id = songId(entry);
            if (id != null) privileges.put(id, entry.getAsJsonObject());
        }
        var tracks = new HashMap<Long, Music>();
        for (JsonElement entry : array(details, "songs")) {
            Long id = songId(entry);
            if (!ids.contains(id)) continue;
            try {
                JsonObject song = entry.getAsJsonObject();
                Music track = JsonUtils.parse(song, Music.class);
                if (track == null) continue;
                track.applyNeteaseMetadata(song, privileges.get(id));
                if (hasAccess(track)) tracks.put(id, track);
            } catch (RuntimeException invalidSong) {
                // Continue with the remaining catalog results.
            }
        }
        return ids.stream().filter(tracks::containsKey).map(tracks::get).toList();
    }

    private static JsonArray array(JsonObject root, String field) {
        return root != null && root.get(field) instanceof JsonArray items ? items : new JsonArray();
    }

    private static Long songId(JsonElement item) {
        if (!(item instanceof JsonObject song) || !song.has("id")) return null;
        try {
            return song.get("id").getAsLong();
        } catch (RuntimeException invalidId) {
            return null;
        }
    }

    private static boolean hasAccess(Music track) {
        return !track.hasVipRestriction() || CrossSourceSwitcher.hasVipAccess(track.getSource());
    }

    static Music nextUnplayed(List<Music> queue, List<Music> candidates) {
        var queued = new HashSet<String>();
        for (Music track : queue) queued.add(track.getStableKey());
        for (Music candidate : candidates) {
            if (!queued.contains(candidate.getStableKey())) return candidate;
        }
        return null;
    }
}
