// Adapted by dishanhai from MuoniumPlayer MuNeteaseApi. Copyright 2026 MuoniumPlayer Team.
// Apache-2.0; see META-INF/third-party/muonium-player.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import static com.dishanhai.sh_better_ui.kernel.internal.music.SourceJson.*;

final class NeteaseSource implements OnlineMusicSource {
    @FunctionalInterface interface Transport {
        JsonObject json(String url, String body, String type, String referer, String cookie) throws IOException;
    }
    private final Transport http;
    NeteaseSource() { this(new MusicHttp()::json); }
    NeteaseSource(Transport http) { this.http = Objects.requireNonNull(http); }
    private volatile String cookie = "";
    @Override public void credentials(String value) { cookie = MusicHttp.cookie(value); }
    @Override public OnlineMusicSource requestSnapshot() {
        var snapshot = new NeteaseSource(http); snapshot.cookie = cookie; return snapshot;
    }
    @Override public boolean supportsArtistTracks() { return true; }
    @Override public boolean supportsLikes() { return true; }
    @Override public String id() { return "netease"; }
    @Override public QrChallenge qrLogin() throws Exception { return new NeteaseQrLogin(); }
    @Override public List<String> suggest(String query, int limit) throws Exception {
        var data = call("/weapi/search/suggest/keyword", Map.of("s", query));
        var matches = array(object(data, "result"), "allMatch");
        var result = new LinkedHashSet<String>();
        for (var entry : matches) {
            if (!entry.isJsonObject()) continue;
            String value = text(entry.getAsJsonObject(), "keyword").strip();
            if (!value.isEmpty()) result.add(value);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }
    @Override public OnlineMusicAccount account() throws Exception { return account(credentialsSnapshot()); }
    private OnlineMusicAccount account(String session) throws Exception {
        var data = accountCall("/weapi/w/nuser/account/get", Map.of(), session);
        String uid = text(object(data, "account"), "id");
        if (!uid.matches("[0-9]{1,24}") || uid.equals("0")) throw new LoginRequired();
        return new OnlineMusicAccount(id(), uid, text(object(data, "profile"), "nickname"));
    }
    @Override public List<OnlinePlaylist> playlists(int page, int limit) throws Exception {
        checkPage(page, limit);
        String session = credentialsSnapshot();
        String uid = account(session).id();
        var data = accountCall("/weapi/user/playlist", Map.of("uid", uid, "limit", "" + limit,
                "offset", "" + ((page - 1L) * limit), "includeVideo", "true"), session);
        requireArray(data, "playlist");
        var result = new ArrayList<OnlinePlaylist>();
        for (var item : array(data, "playlist")) {
            if (!item.isJsonObject() || result.size() >= limit) continue;
            var playlist = item.getAsJsonObject();
            String playlistId = text(playlist, "id");
            if (!playlistId.matches("[0-9]{1,24}")) continue;
            result.add(new OnlinePlaylist(id(), playlistId, text(playlist, "name"), (int) Math.min(Integer.MAX_VALUE, number(playlist, "trackCount"))));
        }
        return List.copyOf(result);
    }
    @Override public List<OnlineTrack> playlist(OnlinePlaylist playlist, int page, int limit) throws Exception {
        checkPage(page, limit);
        if (!id().equals(playlist.source()) || !playlist.id().matches("[0-9]{1,24}")) throw new IllegalArgumentException("Invalid playlist");
        String session = credentialsSnapshot();
        var data = accountCall("/weapi/v6/playlist/detail", Map.of("id", playlist.id(), "n", "1", "s", "8"), session);
        var playlistData = object(data, "playlist");
        requireArray(playlistData, "trackIds");
        var ids = array(playlistData, "trackIds");
        int start = (int) Math.min(ids.size(), (page - 1L) * limit);
        var selected = new ArrayList<String>();
        for (int i = start, end = Math.min(ids.size(), start + limit); i < end; i++) {
            if (!ids.get(i).isJsonObject()) continue;
            String songId = text(ids.get(i).getAsJsonObject(), "id");
            if (songId.matches("[0-9]{1,24}")) selected.add(songId);
        }
        return songDetails(selected, session);
    }
    @Override public boolean supportsPlaylistDetail() { return true; }
    @Override public OnlinePlaylistDetail playlistDetail(String playlistId) throws Exception {
        if (!playlistId.matches("[1-9][0-9]{0,23}")) throw new IllegalArgumentException("Invalid playlist");
        String session = credentialsSnapshot();
        var response = accountCall("/weapi/v6/playlist/detail", Map.of("id", playlistId, "n", "1", "s", "8"), session);
        var detail = object(response, "playlist");
        if (!playlistId.equals(text(detail, "id"))) throw new IOException("Playlist identity changed");
        requireArray(detail, "trackIds");
        var ids = array(detail, "trackIds");
        if (ids.size() > OnlinePlaylistDetail.MAX_TRACKS || !detail.has("trackCount")
                || !Integer.toString(ids.size()).equals(text(detail, "trackCount"))) {
            throw new IOException("NetEase playlist order is incomplete");
        }
        var tracks = new ArrayList<OnlineTrack>(ids.size());
        for (int start = 0; start < ids.size(); start += 100) {
            checkInterrupted();
            var selected = new ArrayList<String>(100);
            for (int i = start; i < Math.min(start + 100, ids.size()); i++) {
                String songId = ids.get(i).isJsonObject() ? text(ids.get(i).getAsJsonObject(), "id") : "";
                if (!songId.matches("[0-9]{1,24}")) throw new IOException("Invalid playlist track identity");
                selected.add(songId);
            }
            var page = songDetails(selected, session);
            if (page.size() != selected.size()) throw new IOException("NetEase playlist song details are incomplete");
            tracks.addAll(page);
        }
        return new OnlinePlaylistDetail(new OnlinePlaylist(id(), playlistId, text(detail, "name"), ids.size()), tracks);
    }
    @Override public List<OnlineTrack> liked(int page, int limit) throws Exception {
        checkPage(page, limit);
        String session = credentialsSnapshot();
        var data = accountCall("/weapi/song/like/get", Map.of("uid", account(session).id()), session);
        requireArray(data, "ids");
        var ids = array(data, "ids");
        int start = (int) Math.min(ids.size(), (page - 1L) * limit);
        var selected = new ArrayList<String>();
        for (int i = start, end = Math.min(ids.size(), start + limit); i < end; i++) {
            var item = ids.get(i);
            if (!item.isJsonPrimitive()) continue;
            String songId = item.getAsString();
            if (songId.matches("[0-9]{1,24}")) selected.add(songId);
        }
        return songDetails(selected, session);
    }
    @Override public void like(OnlineTrack track, boolean liked) throws Exception {
        if (!id().equals(track.source()) || !track.id().matches("[0-9]{1,24}")) throw new IllegalArgumentException("Invalid track");
        String session = credentialsSnapshot();
        accountCall("/weapi/radio/like", Map.of("alg", "itembased", "trackId", track.id(),
                "like", Boolean.toString(liked), "time", "3"), session);
    }
    private List<OnlineTrack> songDetails(List<String> ids, String session) throws Exception {
        if (ids.isEmpty()) return List.of();
        var request = new ArrayList<Map<String, java.math.BigInteger>>();
        for (var songId : ids) request.add(Map.of("id", new java.math.BigInteger(songId)));
        var data = accountCall("/weapi/v3/song/detail", Map.of("c", new Gson().toJson(request)), session);
        requireArray(data, "songs");
        var wrapped = new JsonObject(); var result = new JsonObject();
        // Ignore unrelated/duplicate records before applying the requested-count bound.
        // Otherwise an unexpected first record can crowd a legitimate requested song out.
        var requested = new HashSet<>(ids);
        var seen = new HashSet<String>();
        var selectedSongs = new JsonArray();
        for (var song : array(data, "songs")) {
            if (!song.isJsonObject()) continue;
            String songId = text(song.getAsJsonObject(), "id");
            if (requested.contains(songId) && seen.add(songId)) selectedSongs.add(song);
        }
        result.add("songs", selectedSongs); wrapped.add("result", result);
        var byId = new HashMap<String, OnlineTrack>();
        for (var track : parseSearch(wrapped, ids.size())) byId.put(track.id(), track);
        var ordered = new ArrayList<OnlineTrack>();
        for (var songId : ids) if (byId.containsKey(songId)) ordered.add(byId.get(songId));
        return List.copyOf(ordered);
    }
    private static void checkPage(int page, int limit) {
        if (page < 1 || page > 1000 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page");
    }
    private static void requireArray(JsonObject data, String key) throws IOException {
        if (!data.has(key) || !data.get(key).isJsonArray()) throw new IOException("NetEase response missing " + key);
    }
    private String credentialsSnapshot() throws LoginRequired {
        String session = cookie;
        if (session.isBlank()) throw new LoginRequired();
        return session;
    }
    private JsonObject accountCall(String path, Map<String, String> parameters, String session) throws Exception {
        String csrf = "";
        for (var part : session.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).strip().equals("__csrf")) csrf = part.substring(equals + 1).strip();
        }
        var payload = new LinkedHashMap<>(parameters); payload.put("csrf_token", csrf);
        var encrypted = MuWeapi.weapi(new Gson().toJson(payload));
        var data = http.json("https://music.163.com" + path + "?" + MusicHttp.form(Map.of("csrf_token", csrf)),
                MusicHttp.form(Map.of("params", encrypted.getParams(), "encSecKey", encrypted.getEncSecKey())),
                "application/x-www-form-urlencoded", "https://music.163.com/", "os=pc; appver=8.9.70; " + session);
        long code = number(data, "code");
        if (code == 301 || code == 401) throw new LoginRequired();
        if (code >= 400 && code < 500 && path.equals("/weapi/radio/like")) throw new MusicWriteRejected("NetEase favorite write rejected");
        if (code != 200) throw new IOException("NetEase account request rejected: " + code);
        return data;
    }
    @Override public List<OnlineTrack> search(String query, int page, int limit) throws Exception {
        checkPage(page, limit);
        checkInterrupted();
        String session = cookie;
        var data = call("/weapi/cloudsearch/get/web", Map.of("s", query, "type", "1", "limit", "" + limit,
                "offset", "" + ((page - 1L) * limit), "total", "true"), session);
        return completeSearchArtwork(parseSearch(data, limit), session);
    }

    /** Public search omits album.picUrl. Enrich only this page, once, without touching playback access. */
    private List<OnlineTrack> completeSearchArtwork(List<OnlineTrack> tracks, String session) throws IOException {
        checkInterrupted();
        var ids = new LinkedHashSet<String>();
        for (var track : tracks) if (track.artworkUrl().isBlank()) ids.add(track.id());
        if (ids.isEmpty()) return tracks;
        try {
            // Public metadata endpoint verified to return songs[].album.picUrl, not audio URLs.
            var data = http.json("https://music.163.com/api/song/detail/?" + MusicHttp.form(Map.of(
                            "ids", "[" + String.join(",", ids) + "]", "id", ids.iterator().next())),
                    null, null, "https://music.163.com/", "os=pc; appver=8.9.70; " + session);
            checkInterrupted();
            if (number(data, "code") != 200) return tracks;
            var covers = new HashMap<String, String>();
            for (var item : array(data, "songs")) {
                if (!item.isJsonObject()) continue;
                var song = item.getAsJsonObject();
                String id = text(song, "id");
                if (!ids.contains(id) || covers.containsKey(id)) continue;
                var album = song.has("al") ? object(song, "al") : object(song, "album");
                String cover = text(album, "picUrl");
                if (cover.isBlank()) continue;
                try { covers.put(id, artworkUrl(OnlineArtwork.validated(cover).toString())); }
                catch (IOException invalidCover) { /* Keep the search record; do not invent a URL. */ }
            }
            var completed = new ArrayList<OnlineTrack>(tracks.size());
            for (var track : tracks) {
                String cover = track.artworkUrl().isBlank() ? covers.get(track.id()) : null;
                completed.add(cover == null ? track : new OnlineTrack(track.source(), track.id(), track.mediaId(),
                        track.title(), track.artist(), track.album(), track.durationMs(), cover, track.artists()));
            }
            return List.copyOf(completed);
        } catch (java.util.concurrent.CancellationException cancelled) {
            throw cancelled;
        } catch (java.io.InterruptedIOException interrupted) {
            checkInterrupted();
            if (!(interrupted instanceof java.net.SocketTimeoutException)) throw interrupted;
            return tracks; // Optional metadata timeout must not discard a successful search.
        } catch (IOException | RuntimeException unavailable) {
            checkInterrupted();
            return tracks;
        }
    }
    private static void checkInterrupted() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("NetEase request cancelled");
    }
    private static String artworkUrl(String url) {
        // Provider thumbnail sizing avoids downloading a multi-megapixel album original.
        return url.isBlank() || url.contains("?") ? url : url + "?param=512y512";
    }

    static List<OnlineTrack> parseSearch(JsonObject data, int limit) {
        var result = new ArrayList<OnlineTrack>();
        for (var element : array(object(data, "result"), "songs")) {
            if (!element.isJsonObject() || result.size() >= limit) continue;
            var song = element.getAsJsonObject();
            String id = text(song, "id");
            if (!id.matches("[0-9]{1,24}")) continue;
            var album = song.has("al") ? object(song, "al") : object(song, "album");
            var singers = song.has("ar") ? array(song, "ar") : array(song, "artists");
            result.add(new OnlineTrack("netease", id, id, text(song, "name"), artists(singers), text(album, "name"),
                    song.has("dt") ? number(song, "dt") : number(song, "duration"), artworkUrl(text(album, "picUrl")),
                    artistIdentities(singers, "netease", artist -> {
                        String artistId = text(artist, "id");
                        return artistId.matches("[1-9][0-9]{0,23}") ? artistId : "";
                    })));
        }
        return List.copyOf(result);
    }
    @Override public Stream resolve(OnlineTrack track) throws Exception {
        var data = call("/weapi/song/enhance/player/url/v1", Map.of("ids", "[" + track.id() + "]",
                "level", "standard", "encodeType", "mp3"));
        var entries = array(data, "data");
        if (entries.isEmpty() || !entries.get(0).isJsonObject()) return null;
        return parseStream(entries.get(0).getAsJsonObject(), track.durationMs());
    }
    static Stream parseStream(JsonObject item, long duration) {
        String url = text(item, "url"), type = text(item, "type");
        if (url.isBlank() || (!type.isBlank() && !type.equalsIgnoreCase("mp3"))) return null;
        var trial = object(item, "freeTrialInfo");
        long start = number(trial, "start") * 1000, end = number(trial, "end") * 1000;
        return new Stream(MusicHttp.https(url), start, end > start ? end - start : duration, end > start);
    }
    @Override public LyricTimeline lyrics(OnlineTrack track) throws Exception {
        var data = call("/weapi/song/lyric", Map.of("id", track.id(), "lv", "-1", "kv", "-1", "tv", "-1", "yv", "-1"));
        return OnlineLyrics.parse(text(object(data, "lrc"), "lyric"), text(object(data, "tlyric"), "lyric"),
                text(object(data, "yrc"), "lyric"), track.durationMs());
    }
    private JsonObject call(String path, Map<String, String> parameters) throws Exception {
        return call(path, parameters, cookie);
    }
    private JsonObject call(String path, Map<String, String> parameters, String session) throws Exception {
        var publicParameters = new LinkedHashMap<>(parameters);
        String publicPath = path.replace("/weapi/", "/api/")
                .replace("/cloudsearch/get/web", "/search/get/web")
                .replace("/song/enhance/player/url/v1", "/song/enhance/player/url");
        if (path.contains("/player/url")) publicParameters.put("br", "128000");
        publicParameters.put("os", "pc");
        var publicResult = http.json("https://music.163.com" + publicPath + "?" + MusicHttp.form(publicParameters),
                null, null, "https://music.163.com/", "os=pc; appver=8.9.70; " + session);
        if (number(publicResult, "code") == 200) return publicResult;
        var encrypted = MuWeapi.weapi(new Gson().toJson(parameters));
        var json = http.json("https://music.163.com" + path,
                MusicHttp.form(Map.of("params", encrypted.getParams(), "encSecKey", encrypted.getEncSecKey())),
                "application/x-www-form-urlencoded", "https://music.163.com/", "os=pc; appver=8.9.70; " + session);
        if (number(json, "code") == 301 || number(json, "code") == 401) throw new LoginRequired();
        if (number(json, "code") != 200) throw new IOException("NetEase rejected request");
        return json;
    }
}
