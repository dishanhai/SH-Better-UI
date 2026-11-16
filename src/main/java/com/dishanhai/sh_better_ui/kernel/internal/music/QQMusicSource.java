// Adapted by dishanhai from MuoniumPlayer MuQQApi. Copyright 2026 MuoniumPlayer Team.
// Apache-2.0; see META-INF/third-party/muonium-player.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LrcParser;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import static com.dishanhai.sh_better_ui.kernel.internal.music.SourceJson.*;

final class QQMusicSource implements OnlineMusicSource {
    private final MusicHttp http = new MusicHttp();
    private final QQFavorites favorites;
    QQMusicSource() { this(new QQFavorites()); }
    QQMusicSource(QQFavorites favorites) { this.favorites = favorites; }
    private record Account(String id, String key) {}
    private volatile Account account = new Account("0", "");
    @Override public OnlineMusicSource requestSnapshot() {
        var snapshot = new QQMusicSource(favorites); snapshot.account = account; return snapshot;
    }
    @Override public void credentials(String value) {
        String valid = MusicHttp.cookie(value);
        var fields = new HashMap<String, String>();
        for (var part : valid.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0) fields.put(part.substring(0, equals).strip(), part.substring(equals + 1).strip());
        }
        String id = fields.getOrDefault("uin", fields.getOrDefault("qqmusic_uin", "0"));
        String key = fields.getOrDefault("qm_keyst", fields.getOrDefault("qqmusic_key", ""));
        if (!id.matches("[0-9]{1,24}") || (!valid.isEmpty() && (id.equals("0") || key.isBlank())))
            throw new IllegalArgumentException("Missing QQ Music account cookies");
        account = new Account(id, key);
    }
    private String cookies() {
        return cookies(account);
    }
    private static String cookies(Account current) {
        return current.key().isEmpty() ? "" : "uin=" + current.id() + "; qqmusic_uin=" + current.id()
                + "; qm_keyst=" + current.key() + "; qqmusic_key=" + current.key();
    }
    private final String guid = Long.toString(1_000_000_000L + new java.security.SecureRandom().nextInt(Integer.MAX_VALUE));
    @Override public String id() { return "qq"; }
    @Override public QrChallenge qrLogin() throws Exception { return new QQQrLogin(); }
    @Override public List<String> suggest(String query, int limit) throws Exception {
        var json = http.json("https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?" +
                MusicHttp.form(Map.of("key", query, "format", "json")), null, null, "https://y.qq.com/");
        if (json.has("code") && number(json, "code") != 0) throw new IOException("QQ suggestions rejected request");
        var data = object(json, "data");
        if (data.has("data")) data = object(data, "data");
        var results = new LinkedHashSet<String>();
        for (var group : List.of("song", "singer", "album")) {
            for (var entry : array(object(data, group), "itemlist")) {
                if (!entry.isJsonObject()) continue;
                var item = entry.getAsJsonObject();
                String value = first(item, "name", "songname", "singername", "albumname").strip();
                if (!value.isEmpty()) results.add(value);
                if (results.size() >= limit) return List.copyOf(results);
            }
        }
        return List.copyOf(results);
    }
    @Override public OnlineMusicAccount account() throws Exception {
        var current = credentialsSnapshot();
        var json = http.json("https://c6.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?" +
                MusicHttp.form(Map.of("ct", "20", "cv", "4747474", "cid", "205360838", "userid", current.id(), "format", "json")),
                null, null, "https://y.qq.com/", cookies(current));
        if (json.has("code") && number(json, "code") != 0) {
            long code = number(json, "code");
            if (code == 1000 || code == 104009 || code == 401) throw new LoginRequired();
            throw new IOException("QQ profile request rejected: " + code);
        }
        var creator = object(object(json, "data"), "creator");
        if (creator.size() == 0) throw new IOException("QQ profile response missing creator");
        return new OnlineMusicAccount(id(), current.id(), nickname(text(creator, "nick")));
    }
    @Override public List<OnlinePlaylist> playlists(int page, int limit) throws Exception {
        checkPage(page, limit);
        var current = credentialsSnapshot();
        var data = rpc("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin", Map.of("uin", current.id()),
                Map.of("ct", "24", "cv", "0"), current);
        var merged = new LinkedHashMap<String, OnlinePlaylist>();
        appendPlaylists(merged, requireArray(data, "v_playlist", "disslist", "list"));
        long end = (long) page * limit;
        var subscribed = http.json("https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg?" +
                MusicHttp.form(Map.of("ct", "20", "cv", "4747474", "cid", "205360956", "userid", current.id(),
                        "reqtype", "3", "sin", "0", "ein", Long.toString(Math.min(end + merged.size() - 1, 99999)), "format", "json")),
                null, null, "https://y.qq.com/", cookies(current));
        if (subscribed.has("code") && number(subscribed, "code") != 0) throw new IOException("QQ subscribed playlists rejected request");
        appendPlaylists(merged, requireArray(subscribed, "cdlist", "disslist", "list", "playlist"));
        var all = new ArrayList<>(merged.values());
        int start = (int) Math.min(all.size(), (page - 1L) * limit);
        return List.copyOf(all.subList(start, Math.min(all.size(), start + limit)));
    }
    private void appendPlaylists(Map<String, OnlinePlaylist> result, JsonArray entries) {
        for (var entry : entries) {
            if (!entry.isJsonObject()) continue;
            var item = entry.getAsJsonObject();
            String playlistId = first(item, "tid", "dissid", "id");
            if (!playlistId.matches("[0-9]{1,18}")) continue;
            String name = first(item, "diss_name", "dissname", "name", "title", "dirName", "dirname");
            long songCount = 0;
            for (var field : List.of("songNum", "song_cnt", "songnum", "song_count", "trackCount")) {
                if (item.has(field)) { songCount = number(item, field); break; }
            }
            int count = (int) Math.min(Integer.MAX_VALUE, Math.max(0, songCount));
            result.putIfAbsent(playlistId, new OnlinePlaylist(id(), playlistId, name, count));
        }
    }
    @Override public List<OnlineTrack> playlist(OnlinePlaylist playlist, int page, int limit) throws Exception {
        checkPage(page, limit);
        if (!id().equals(playlist.source()) || !playlist.id().matches("[0-9]{1,18}")) throw new IllegalArgumentException("Invalid playlist");
        var current = credentialsSnapshot();
        var data = rpc("music.srfDissInfo.DissInfo", "CgiGetDiss", Map.of("disstid", Long.parseLong(playlist.id()),
                "onlysong", 0, "song_begin", (page - 1) * limit, "song_num", limit), Map.of("ct", "24", "cv", "0"), current);
        return parseSongList(data, limit);
    }
    @Override public List<OnlineTrack> liked(int page, int limit) throws Exception {
        checkPage(page, limit);
        var current = credentialsSnapshot();
        var data = rpc("music.srfDissInfo.DissInfo", "CgiGetDiss", Map.of("disstid", 0, "dirid", 201,
                "song_begin", (page - 1) * limit, "song_num", limit, "userinfo", true, "orderlist", true),
                Map.of("ct", "24", "cv", "0"), current);
        return parseSongList(data, limit);
    }
    @Override public boolean supportsLikes() { return true; }
    @Override public void like(OnlineTrack track, boolean liked) throws Exception {
        if (track == null || !id().equals(track.source()) || !track.id().matches("[A-Za-z0-9]{1,40}"))
            throw new IllegalArgumentException("Invalid QQ track");
        var current = credentialsSnapshot();
        favorites.like(track, liked, current.id(), current.key());
    }
    private Account credentialsSnapshot() throws LoginRequired {
        var current = account;
        if (current.key().isEmpty()) throw new LoginRequired();
        return current;
    }
    private static void checkPage(int page, int limit) {
        if (page < 1 || page > 1000 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page");
    }
    static List<OnlineTrack> parseSongList(JsonObject data, int limit) throws IOException {
        if (!data.has("songlist") || !data.get("songlist").isJsonArray()) throw new IOException("QQ song list missing");
        var body = new JsonObject(); var song = new JsonObject(); var root = new JsonObject();
        song.add("list", array(data, "songlist")); body.add("song", song); root.add("body", body);
        return parseSearch(root, limit);
    }
    private static String first(JsonObject object, String... keys) {
        for (var key : keys) { String value = text(object, key); if (!value.isBlank()) return value; }
        return "";
    }
    private static String nickname(String value) {
        if (!value.matches("[A-Za-z0-9+/]+={0,2}") || value.length() % 4 != 0) return value;
        try {
            var decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
            String decoded = decoder.decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(value))).toString();
            return decoded.isBlank() || decoded.codePoints().anyMatch(Character::isISOControl) ? value : decoded;
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException ignored) { return value; }
    }
    static JsonArray requireArray(JsonObject object, String... keys) throws IOException {
        var result = findArray(object, 0, keys);
        if (result == null) throw new IOException("QQ response missing playlist array");
        return result;
    }
    private static JsonArray findArray(JsonObject object, int depth, String... keys) {
        if (depth > 6) return null;
        for (var key : keys) if (object.has(key) && object.get(key).isJsonArray()) return object.getAsJsonArray(key);
        for (var entry : object.entrySet()) if (entry.getValue().isJsonObject()) {
            var found = findArray(entry.getValue().getAsJsonObject(), depth + 1, keys);
            if (found != null) return found;
        }
        return null;
    }
    @Override public List<OnlineTrack> search(String query, int page, int limit) throws Exception {
        try {
            var data = rpc("music.search.SearchCgiService", "DoSearchForQQMusicDesktop",
                    Map.of("num_per_page", limit, "page_num", page, "search_type", 0, "query", query, "grp", 1),
                    Map.of("ct", "6", "cv", "80600", "tmeAppID", "qqmusic"));
            var found = parseSearch(data, limit);
            if (!found.isEmpty()) return found;
        } catch (IOException ignored) { /* The desktop channel may require a login for searches. */ }
        var json = http.json("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?" +
                MusicHttp.form(Map.of("w", query, "format", "json", "p", page, "n", limit)),
                null, null, "https://y.qq.com/", cookies());
        if (number(json, "code") != 0) throw new IOException("QQ search rejected request");
        return parseSearch(json, limit);
    }
    static List<OnlineTrack> parseSearch(JsonObject data, int limit) {
        var out = new ArrayList<OnlineTrack>();
        var root = data.has("body") ? object(data, "body") : object(data, "data");
        for (var entry : array(object(root, "song"), "list")) {
            if (!entry.isJsonObject() || out.size() >= limit) continue;
            var song = entry.getAsJsonObject();
            String mid = text(song, "mid");
            if (mid.isBlank()) mid = text(song, "songmid");
            if (!mid.matches("[A-Za-z0-9]{1,40}")) continue;
            String media = text(object(song, "file"), "media_mid");
            if (media.isBlank()) media = text(song, "strMediaMid");
            out.add(new OnlineTrack("qq", mid, media.isBlank() ? mid : media,
                    first(song, "title", "name", "songname"), artists(array(song, "singer")),
                    song.has("album") ? text(object(song, "album"), "name") : text(song, "albumname"), number(song, "interval") * 1000, albumCover(song)));
        }
        return List.copyOf(out);
    }
    private static String albumCover(JsonObject song) {
        String mid = song.has("album") ? text(object(song, "album"), "mid") : text(song, "albummid");
        return mid.matches("[A-Za-z0-9]{1,40}") ? "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + mid + ".jpg" : "";
    }

    @Override public Stream resolve(OnlineTrack track) throws Exception {
        var detail = http.json("https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?" +
                MusicHttp.form(Map.of("songmid", track.id(), "platform", "yqq", "format", "json")),
                null, null, "https://y.qq.com/", cookies());
        var songs = array(detail, "data");
        var song = songs.isEmpty() || !songs.get(0).isJsonObject() ? new JsonObject() : songs.get(0).getAsJsonObject();
        String media = text(object(song, "file"), "media_mid");
        if (media.isBlank()) media = track.mediaId();
        var parameters = Map.of("guid", guid, "songmid", List.of(track.id()),
                "filename", List.of("M500" + media + ".mp3"), "songtype", List.of(number(song, "type")),
                "uin", account.id(), "loginflag", 1, "platform", "20");
        var request = Map.of("req_0", Map.of("module", "vkey.GetVkeyServer", "method", "CgiGetVkey", "param", parameters));
        var json = http.json("https://u.y.qq.com/cgi-bin/musicu.fcg?" + MusicHttp.form(Map.of("format", "json",
                "platform", "yqq.json", "needNewCode", "0", "data", new Gson().toJson(request))),
                null, null, "https://y.qq.com/", cookies());
        if (number(object(json, "req_0"), "code") == 104009) throw new LoginRequired();
        return parseStream(object(object(json, "req_0"), "data"), track.durationMs());
    }
    static Stream parseStream(JsonObject data, long duration) {
        var entries = array(data, "midurlinfo");
        if (entries.isEmpty() || !entries.get(0).isJsonObject()) return null;
        String path = text(entries.get(0).getAsJsonObject(), "purl");
        if (path.isBlank()) return null;
        var sips = array(data, "sip");
        String base = sips.isEmpty() ? "https://dl.stream.qqmusic.qq.com/" : sips.get(0).getAsString();
        return new Stream(MusicHttp.https(base).resolve(path), 0, duration, false);
    }
    @Override public LyricTimeline lyrics(OnlineTrack track) throws Exception {
        var json = http.json("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?" +
                MusicHttp.form(Map.of("songmid", track.id(), "format", "json", "nobase64", "1", "g_tk", "5381")),
                null, null, "https://y.qq.com/");
        if (number(json, "retcode") != 0) throw new IOException("QQ lyrics rejected request");
        return LrcParser.parse(unescape(text(json, "lyric")), unescape(text(json, "trans")), track.durationMs());
    }
    private static String unescape(String value) {
        return value.replace("&#10;", "\n").replace("&#13;", "\r").replace("&apos;", "'")
                .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
    private JsonObject rpc(String module, String method, Map<String, ?> parameters, Map<String, ?> comm) throws Exception {
        return rpc(module, method, parameters, comm, account);
    }
    private JsonObject rpc(String module, String method, Map<String, ?> parameters, Map<String, ?> comm, Account current) throws Exception {
        var gson = new Gson();
        var authenticated = new LinkedHashMap<String, Object>(comm);
        authenticated.put("uin", current.id());
        if (!current.key().isEmpty()) { authenticated.put("qq", current.id()); authenticated.put("authst", current.key()); }
        var body = Map.of("comm", authenticated, "req", Map.of("module", module, "method", method, "param", parameters));
        var json = http.json("https://u.y.qq.com/cgi-bin/musicu.fcg", gson.toJson(body), "application/json", "https://y.qq.com/", cookies(current));
        var req = object(json, "req");
        if (!json.has("req") || !req.has("code") || number(req, "code") != 0) {
            if (number(req, "code") == 1000 || number(req, "code") == 104009 || number(req, "code") == 401) throw new LoginRequired();
            throw new IOException("QQ rejected request");
        }
        if (!req.has("data") || !req.get("data").isJsonObject()) throw new IOException("QQ response missing data");
        return object(req, "data");
    }
}
