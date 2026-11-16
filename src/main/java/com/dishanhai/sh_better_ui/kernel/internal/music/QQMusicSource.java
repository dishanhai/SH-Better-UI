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
    private record Account(String id, String key) {}
    private volatile Account account = new Account("0", "");
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
        var current = account;
        return current.key().isEmpty() ? "" : "uin=" + current.id() + "; qqmusic_uin=" + current.id()
                + "; qm_keyst=" + current.key() + "; qqmusic_key=" + current.key();
    }
    private final String guid = Long.toString(1_000_000_000L + new java.security.SecureRandom().nextInt(Integer.MAX_VALUE));
    @Override public String id() { return "qq"; }
    @Override public QrChallenge qrLogin() throws Exception { return new QQQrLogin(); }
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
                    song.has("title") ? text(song, "title") : text(song, "songname"), artists(array(song, "singer")),
                    text(object(song, "album"), "name"), number(song, "interval") * 1000));
        }
        return List.copyOf(out);
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
        var gson = new Gson();
        var authenticated = new LinkedHashMap<String, Object>(comm);
        var current = account;
        authenticated.put("uin", current.id());
        if (!current.key().isEmpty()) { authenticated.put("qq", current.id()); authenticated.put("authst", current.key()); }
        var body = Map.of("comm", authenticated, "req", Map.of("module", module, "method", method, "param", parameters));
        var json = http.json("https://u.y.qq.com/cgi-bin/musicu.fcg", gson.toJson(body), "application/json", "https://y.qq.com/", cookies());
        var req = object(json, "req");
        if (!json.has("req") || number(req, "code") != 0) throw new IOException("QQ rejected request");
        return object(req, "data");
    }
}
