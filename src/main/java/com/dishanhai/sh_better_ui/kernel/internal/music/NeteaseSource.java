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
    private final MusicHttp http = new MusicHttp();
    private volatile String cookie = "";
    @Override public void credentials(String value) { cookie = MusicHttp.cookie(value); }
    @Override public String id() { return "netease"; }
    @Override public QrChallenge qrLogin() throws Exception { return new NeteaseQrLogin(); }
    @Override public List<OnlineTrack> search(String query, int page, int limit) throws Exception {
        var data = call("/weapi/cloudsearch/get/web", Map.of("s", query, "type", "1", "limit", "" + limit,
                "offset", "" + ((page - 1) * limit), "total", "true"));
        return parseSearch(data, limit);
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
                    song.has("dt") ? number(song, "dt") : number(song, "duration")));
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
        var publicParameters = new LinkedHashMap<>(parameters);
        String publicPath = path.replace("/weapi/", "/api/")
                .replace("/cloudsearch/get/web", "/search/get/web")
                .replace("/song/enhance/player/url/v1", "/song/enhance/player/url");
        if (path.contains("/player/url")) publicParameters.put("br", "128000");
        publicParameters.put("os", "pc");
        var publicResult = http.json("https://music.163.com" + publicPath + "?" + MusicHttp.form(publicParameters),
                null, null, "https://music.163.com/", "os=pc; appver=8.9.70; " + cookie);
        if (number(publicResult, "code") == 200) return publicResult;
        var encrypted = MuWeapi.weapi(new Gson().toJson(parameters));
        var json = http.json("https://music.163.com" + path,
                MusicHttp.form(Map.of("params", encrypted.getParams(), "encSecKey", encrypted.getEncSecKey())),
                "application/x-www-form-urlencoded", "https://music.163.com/", "os=pc; appver=8.9.70; " + cookie);
        if (number(json, "code") != 200) throw new IOException("NetEase rejected request");
        return json;
    }
}
