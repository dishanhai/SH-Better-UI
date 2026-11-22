// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlinePlaylist;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only import of the user-selected Listen 1 local backup, not a Listen 1 network source. */
public final class Listen1Backup {
    public static final String ID = "listen1";
    static final int MAX_BYTES = 4 * 1024 * 1024;
    static final int MAX_PLAYLISTS = 200, MAX_TRACKS_PER_PLAYLIST = 1000;
    private static final Pattern PLAYLIST_ID = Pattern.compile("myplaylist_[A-Za-z0-9_-]{1,80}");
    private static final Pattern NUMERIC_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern ALPHANUMERIC_ID = Pattern.compile("[A-Za-z0-9]{1,64}");
    private static final Pattern KUGOU_HASH = Pattern.compile("[A-Fa-f0-9]{32}");
    private static final Pattern BILIBILI_VIDEO = Pattern.compile("(BV[A-Za-z0-9]{10})(?:-([1-9][0-9]{0,18}))?");
    private static final Map<String, String> TRACK_SOURCES = Map.of(
            "netrack", "netease", "qqtrack", "qq", "kgtrack", "kugou", "bitrack", "bilibili",
            "kwtrack", "kuwo", "mgtrack", "migu");
    private static final Map<String, String> FAVORITE_SOURCES = Map.of(
            "neplaylist", "netease", "qqplaylist", "qq", "kgplaylist", "kugou");
    private static final Set<String> LEGACY_SOURCES = Set.of("netease", "qq");

    private Listen1Backup() {}

    /** Owned by one player session; stores sanitized records only, with no backing file or settings. */
    public static final class Session {
        private Imported imported;
        public Imported imported() { return imported; }
        /** An empty or incompatible export must not erase a library already available in this session. */
        public boolean replace(Imported value) {
            if (Objects.requireNonNull(value).supportedTracks() == 0) return false;
            imported = value;
            return true;
        }
    }

    public record Imported(List<OnlinePlaylist> playlists, Map<String, List<OnlineTrack>> tracksById,
                           int supportedTracks, int skippedTracks) {
        public Imported {
            playlists = List.copyOf(playlists);
            var copy = new HashMap<String, List<OnlineTrack>>();
            tracksById.forEach((id, tracks) -> copy.put(id, List.copyOf(tracks)));
            tracksById = Map.copyOf(copy);
        }
        public List<OnlineTrack> tracks(OnlinePlaylist playlist) {
            return ID.equals(playlist.source()) ? tracksById.getOrDefault(playlist.id(), List.of()) : List.of();
        }
    }

    public record Library(Imported saved, List<OnlinePlaylist> favorites, int skippedFavorites) {
        public Library {
            favorites = List.copyOf(favorites);
        }
    }

    /** Reads a single bounded file. Never imports backup URLs, artwork, cookies, tokens or other settings. */
    public static Imported read(Path path, Set<String> availableSources) throws IOException {
        return parse(readFile(path), availableSources);
    }

    public static Library readLibrary(Path path) throws IOException {
        JsonObject root = parseRoot(readFile(path));
        var saved = parseSaved(root, TRACK_SOURCES.values());
        var favorites = new ArrayList<OnlinePlaylist>();
        var ids = index(root, "favoriteplayerlists");
        var seen = new HashSet<String>();
        int skipped = 0;
        for (var entry : ids) {
            String key = string(entry);
            if (!seen.add(key)) continue;
            int separator = key.indexOf('_');
            String source = separator < 0 ? null : FAVORITE_SOURCES.get(key.substring(0, separator));
            String id = key.substring(separator + 1);
            if (source == null || !numeric(id)
                    || !(root.get(key) instanceof JsonObject playlist)
                    || !(playlist.get("info") instanceof JsonObject info) || !key.equals(string(info.get("id")))) {
                skipped++;
                continue;
            }
            String name = label(info.get("title"));
            favorites.add(new OnlinePlaylist(source, id, name.isEmpty() ? key : name, 0));
        }
        return new Library(saved, favorites, skipped);
    }

    private static String readFile(Path path) throws IOException {
        String rawPath = path.toString();
        if (!path.isAbsolute() || rawPath.startsWith("//") || rawPath.startsWith("\\\\")
                || path.getFileName() == null
                || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        byte[] bytes;
        try (var input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw invalid();
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    static Imported parse(String text, Set<String> availableSources) throws IOException {
        var sources = new HashSet<>(availableSources);
        sources.retainAll(LEGACY_SOURCES);
        return parseSaved(parseRoot(text), sources);
    }

    private static JsonObject parseRoot(String text) throws IOException {
        verifyDepth(text);
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = new Gson().getAdapter(JsonElement.class).read(reader);
            if (!(value instanceof JsonObject object) || reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
            if (!object.has("playerlists") && !(object.get("favoriteplayerlists") instanceof JsonArray)) {
                throw invalid();
            }
            return object;
        } catch (IOException | JsonParseException | IllegalStateException malformed) {
            // Avoid carrying caller-controlled values or backup data into exceptions.
            throw invalid();
        }
    }

    private static JsonArray index(JsonObject root, String key) throws IOException {
        var value = root.get(key);
        if (value == null) return new JsonArray();
        if (value instanceof JsonArray ids && ids.size() <= MAX_PLAYLISTS) return ids;
        throw invalid();
    }

    private static Imported parseSaved(JsonObject backup, Collection<String> availableSources) throws IOException {
        var ids = index(backup, "playerlists");
        var playlists = new ArrayList<OnlinePlaylist>();
        var byId = new LinkedHashMap<String, List<OnlineTrack>>();
        int skipped = 0, imported = 0;
        for (var value : ids) {
            String id = string(value);
            if (!PLAYLIST_ID.matcher(id).matches() || byId.containsKey(id)
                    || !(backup.get(id) instanceof JsonObject playlist)
                    || !(playlist.get("info") instanceof JsonObject info)
                    || !id.equals(string(info.get("id")))
                    || !(playlist.get("tracks") instanceof JsonArray tracks)) continue;
            if (tracks.size() > MAX_TRACKS_PER_PLAYLIST) throw invalid();
            String name = label(info.get("title"));
            var supported = new ArrayList<OnlineTrack>();
            for (var record : tracks) {
                var track = track(record, availableSources);
                if (track == null) skipped++;
                else supported.add(track);
            }
            byId.put(id, supported);
            playlists.add(new OnlinePlaylist(ID, id, name.isEmpty() ? id : name, supported.size()));
            imported += supported.size();
        }
        if (!ids.isEmpty() && playlists.isEmpty()) throw invalid();
        return new Imported(playlists, byId, imported, skipped);
    }

    private static OnlineTrack track(JsonElement value, Collection<String> sources) {
        if (!(value instanceof JsonObject row)) return null;
        String id = string(row.get("id"));
        int separator = id.indexOf('_');
        if (separator < 0) return null;
        String source = TRACK_SOURCES.get(id.substring(0, separator));
        if (source == null || !sources.contains(source)) return null;
        String providerId = id.substring(separator + 1);
        String declared = string(row.get("source"));
        if (!declared.isEmpty() && !source.equals(declared)) return null;
        switch (source) {
            case "netease" -> { if (!numeric(providerId)) return null; }
            case "qq", "migu" -> { if (!ALPHANUMERIC_ID.matcher(providerId).matches()) return null; }
            case "kuwo" -> {
                if (providerId.startsWith("MUSIC_")) providerId = providerId.substring("MUSIC_".length());
                if (!numeric(providerId)) return null;
            }
            case "kugou" -> {
                if (!KUGOU_HASH.matcher(providerId).matches()) return null;
                providerId = providerId.toLowerCase(Locale.ROOT);
            }
            case "bilibili" -> {
                if (!providerId.startsWith("v_")) return null;
                var video = BILIBILI_VIDEO.matcher(providerId.substring(2));
                if (!video.matches()) return null;
                String cid = video.group(2);
                providerId = cid == null ? video.group(1) : video.group(1) + "_c" + cid;
            }
            default -> throw new IllegalStateException("Unknown Listen 1 provider");
        }
        String title = label(row.get("title"));
        String mediaId = providerId;
        if ("migu".equals(source) && row.get("song_id") instanceof JsonPrimitive songId
                && ALPHANUMERIC_ID.matcher(songId.getAsString()).matches()) {
            mediaId = songId.getAsString();
        }
        return new OnlineTrack(source, providerId, mediaId, title.isEmpty() ? providerId : title,
                label(row.get("artist")), label(row.get("album")), 0);
    }

    private static boolean numeric(String value) {
        return NUMERIC_ID.matcher(value).matches() && (value.length() < 19
                || value.compareTo(Long.toString(Long.MAX_VALUE)) <= 0);
    }

    private static String string(JsonElement value) {
        return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : "";
    }

    private static String label(JsonElement value) {
        var raw = string(value);
        var out = new StringBuilder(Math.min(256, raw.length()));
        raw.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT
                        && Character.getType(c) != Character.SURROGATE && c != 0xa7)
                .limit(256).forEach(out::appendCodePoint);
        return out.toString().strip();
    }

    // Quoted/escaped delimiters are not structure. Limit depth before constructing a JSON tree.
    private static void verifyDepth(String json) throws IOException {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if ((c == '{' || c == '[') && ++depth > 32) throw invalid();
            else if ((c == '}' || c == ']') && --depth < 0) throw invalid();
        }
        if (quoted || depth != 0) throw invalid();
    }

    private static IOException invalid() { return new IOException("Invalid or oversized Listen 1 backup"); }
}
