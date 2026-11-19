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
    private static final Pattern NETEASE_ID = Pattern.compile("netrack_([1-9][0-9]{0,18})");
    private static final Pattern QQ_ID = Pattern.compile("qqtrack_([A-Za-z0-9]{1,64})");

    private Listen1Backup() {}

    /** Owned by one player session; stores sanitized records only, with no backing file or settings. */
    public static final class Session {
        private Imported imported;
        public Imported imported() { return imported; }
        public void replace(Imported value) { imported = Objects.requireNonNull(value); }
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

    /** Reads a single bounded file. Never imports backup URLs, artwork, cookies, tokens or other settings. */
    public static Imported read(Path path, Set<String> availableSources) throws IOException {
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
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        return parse(text, availableSources);
    }

    static Imported parse(String text, Set<String> availableSources) throws IOException {
        verifyDepth(text);
        JsonObject backup;
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = new Gson().getAdapter(JsonElement.class).read(reader);
            if (!(value instanceof JsonObject object) || reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
            backup = object;
        } catch (IOException | JsonParseException | IllegalStateException malformed) {
            // Avoid carrying caller-controlled values or backup data into exceptions.
            throw invalid();
        }
        var index = backup.get("playerlists");
        if (index == null) {
            if (backup.get("favoriteplayerlists") instanceof JsonArray) return new Imported(List.of(), Map.of(), 0, 0);
            throw invalid();
        }
        if (!(index instanceof JsonArray ids) || ids.size() > MAX_PLAYLISTS) throw invalid();

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

    private static OnlineTrack track(JsonElement value, Set<String> sources) {
        if (!(value instanceof JsonObject row)) return null;
        String id = string(row.get("id"));
        var qq = QQ_ID.matcher(id);
        var netease = NETEASE_ID.matcher(id);
        String source, providerId;
        if (qq.matches()) { source = "qq"; providerId = qq.group(1); }
        else if (netease.matches()) { source = "netease"; providerId = netease.group(1); }
        else return null;
        String declared = string(row.get("source"));
        if (!sources.contains(source) || (!declared.isEmpty() && !source.equals(declared))) return null;
        String title = label(row.get("title"));
        return new OnlineTrack(source, providerId, providerId, title.isEmpty() ? providerId : title,
                label(row.get("artist")), label(row.get("album")), 0);
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
