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
    @FunctionalInterface interface Transport {
        JsonObject json(String url, String body, String type, String referer, String cookie) throws IOException;
        default JsonObject json(String url, String body, String type, String referer) throws IOException {
            return json(url, body, type, referer, "");
        }
    }
    private final Transport http;
    private final QQFavorites favorites;
    private static final QQFavorites SHARED_FAVORITES = new QQFavorites();
    QQMusicSource() { this(SHARED_FAVORITES); }
    QQMusicSource(QQFavorites favorites) { this(favorites, new MusicHttp()::json); }
    QQMusicSource(QQFavorites favorites, Transport http) {
        this.favorites = Objects.requireNonNull(favorites); this.http = Objects.requireNonNull(http);
    }
    private record Account(String id, String key) {}
    private volatile Account account = new Account("0", "");
    @Override public OnlineMusicSource requestSnapshot() {
        var snapshot = new QQMusicSource(favorites, http); snapshot.account = account; return snapshot;
    }
    @Override public void credentials(String value) {
        String valid = MusicHttp.cookie(value);
        var fields = new HashMap<String, String>();
        for (var part : valid.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0) fields.put(part.substring(0, equals).strip(), part.substring(equals + 1).strip());
        }
        // The browser may contain both a QQ login identity and a distinct music identity.
        // Prefer the music-scoped alias; an empty optional alias must not hide a usable one.
        String id = cookieAlias(fields, "qqmusic_uin", "uin");
        if (id.isEmpty()) id = "0";
        String key = cookieAlias(fields, "qm_keyst", "qqmusic_key");
        if (!id.matches("[0-9]{1,24}") || (!valid.isEmpty() && (id.equals("0") || key.isBlank())))
            throw new IllegalArgumentException("Missing QQ Music account cookies");
        account = new Account(id, key);
    }
    private static String cookieAlias(Map<String, String> fields, String primary, String fallback) {
        String value = fields.getOrDefault(primary, "");
        return value.isBlank() ? fields.getOrDefault(fallback, "") : value;
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
        checkStatus(json, "profile");
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
        checkStatus(subscribed, "subscribed playlists");
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
    @Override public boolean supportsPlaylistDetail() { return true; }
    @Override public OnlinePlaylistDetail playlistDetail(String playlistId) throws Exception {
        if (!playlistId.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Invalid playlist");
        var current = credentialsSnapshot();
        var tracks = new ArrayList<OnlineTrack>();
        var fullPageIds = new HashSet<List<String>>();
        int total = -1;
        String name = "";
        do {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Playlist request cancelled");
            var page = rpc("music.srfDissInfo.DissInfo", "CgiGetDiss", Map.of("disstid", Long.parseLong(playlistId),
                    "onlysong", 0, "song_begin", tracks.size(), "song_num", 100), Map.of("ct", "24", "cv", "0"), current);
            var info = object(page, "dirinfo");
            String count = text(page, "total_song_num");
            String echoedId = text(info, "id"), alternateId = text(info, "tid");
            if (!playlistId.equals(echoedId.isEmpty() ? alternateId : echoedId)
                    || !alternateId.isEmpty() && !playlistId.equals(alternateId) || !count.matches("[0-9]{1,4}")) {
                throw new IOException("QQ playlist order could not be verified");
            }
            int reported = Integer.parseInt(count);
            if (reported > OnlinePlaylistDetail.MAX_TRACKS || total >= 0 && total != reported) {
                throw new IOException("QQ playlist size changed or is too large");
            }
            total = reported;
            if (tracks.isEmpty()) name = first(info, "title", "dissname", "name");
            if (!page.has("songlist") || !page.get("songlist").isJsonArray()) throw new IOException("QQ song list missing");
            int expected = Math.min(100, total - tracks.size());
            var songs = parseSongList(page, 100);
            if (array(page, "songlist").size() != expected || songs.size() != expected) {
                throw new IOException("QQ playlist song details are incomplete");
            }
            if (expected == 100 && !fullPageIds.add(songs.stream().map(OnlineTrack::id).toList())) {
                throw new IOException("QQ repeated an entire playlist page");
            }
            tracks.addAll(songs);
        } while (tracks.size() < total);
        return new OnlinePlaylistDetail(new OnlinePlaylist(id(), playlistId, name, total), tracks);
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
    Map<String, Boolean> favoriteStatuses(List<String> mids) throws IOException {
        var current = credentialsSnapshot();
        return favorites.statuses(mids, current.id(), current.key());
    }

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
    @Override public List<OnlineMusicComments.Order> commentOrders() { return MusicCommentFeeds.SORTED; }
    @Override public OnlineMusicComments comments(OnlineTrack track, OnlineMusicComments.Order order, int page, int limit) {
        return MusicCommentFeeds.qq(track, order, page, limit, (url, referer) -> http.json(url, null, null, referer));
    }
    @Override public OnlineMusicComments.ReplyPage commentReplies(OnlineTrack track, OnlineMusicComments.Entry parent,
                                                                  String cursor, int limit) {
        return MusicCommentFeeds.qqReplies(track, parent, cursor, limit, (url, referer) -> http.json(url, null, null, referer));
    }
    @Override public boolean supportsArtistTracks() { return true; }
    @Override public boolean supportsArtistOverview() { return true; }
    @Override public boolean supportsAlbumDetail() { return true; }
    private static final Map<String, ?> CATALOG_CHANNEL = Map.of("ct", 24, "cv", 10000);

    @Override public OnlineArtistTracks artistTracks(OnlineArtist artist, int page, int limit) throws Exception {
        if (!validMid(artist.id())) throw new IllegalArgumentException("Invalid QQ singer MID");
        int begin = (page - 1) * limit;
        var data = rpc("musichall.song_list_server", "GetSingerSongList",
                Map.of("singerMid", artist.id(), "begin", begin, "num", limit, "order", 1), CATALOG_CHANNEL);
        if (!artist.id().equals(text(data, "singerMid"))) throw new IOException("QQ singer identity changed");
        var songs = requireArray(data, "songList");
        long total = number(data, "totalNum");
        return new OnlineArtistTracks(parseSongs(songs, limit), !songs.isEmpty() && begin + songs.size() < total, total);
    }

    @Override public OnlineArtistOverview artistOverview(OnlineArtist artist, int page, int limit) throws Exception {
        if (!validMid(artist.id())) throw new IllegalArgumentException("Invalid QQ singer MID");
        int begin = (page - 1) * limit;
        var data = rpc("music.musichallAlbum.AlbumListServer", "GetAlbumList",
                Map.of("singerMid", artist.id(), "begin", begin, "number", limit, "order", 0), CATALOG_CHANNEL);
        if (!artist.id().equals(text(data, "singerMid"))) throw new IOException("QQ singer identity changed");
        var entries = requireArray(data, "albumList");
        var albums = new ArrayList<OnlineAlbum>();
        for (var entry : entries) {
            if (entry.isJsonObject()) {
                var release = qqAlbum(entry.getAsJsonObject(), "", "");
                if (release != null) albums.add(release);
            }
        }
        String description = "";
        long followers = 0;
        if (page == 1) {
            try {
                var profile = rpc("music.web_singer_info_svr", "get_singer_detail_info",
                        Map.of("singermid", artist.id(), "sort", 5, "sin", 0, "num", 1), CATALOG_CHANNEL);
                var info = object(profile, "singer_info");
                if (artist.id().equals(text(info, "mid"))) {
                    description = text(profile, "singer_brief");
                    followers = number(info, "fans");
                }
            } catch (IOException ignored) { /* The release list remains available. */ }
        }
        long total = number(data, "total");
        return new OnlineArtistOverview(singerCover(artist.id()), followers, description, albums, total,
                !entries.isEmpty() && begin + entries.size() < total);
    }

    @Override public OnlineAlbumDetail albumDetail(OnlineAlbum album) throws Exception {
        if (!validMid(album.id())) throw new IllegalArgumentException("Invalid QQ album MID");
        var detail = rpc("music.musichallAlbum.AlbumInfoServer", "GetAlbumDetail",
                Map.of("albumMId", album.id()), CATALOG_CHANNEL);
        var info = object(detail, "basicInfo");
        var release = qqAlbum(info, "", "");
        if (release == null || !album.id().equals(release.id())) throw new IOException("QQ album identity changed");
        var credits = qqArtists(array(object(detail, "singer"), "singerList"));
        var tracks = new ArrayList<OnlineTrack>();
        long total;
        do {
            var data = rpc("music.musichallAlbum.AlbumSongList", "GetAlbumSongList",
                    Map.of("albumMid", album.id(), "begin", tracks.size(), "num", 100, "order", 2), CATALOG_CHANNEL);
            if (!album.id().equals(text(data, "albumMid"))) throw new IOException("QQ album identity changed");
            var entries = requireArray(data, "songList");
            total = number(data, "totalNum");
            var page = parseSongs(entries, 100);
            if (total > 1000 || page.size() != entries.size() || (page.isEmpty() && tracks.size() < total))
                throw new IOException("QQ album track list is incomplete");
            tracks.addAll(page);
        } while (tracks.size() < total);
        return new OnlineAlbumDetail(release, credits, text(info, "desc"), tracks);
    }

    private static String singerCover(String mid) {
        return validMid(mid) ? "https://y.gtimg.cn/music/photo_new/T001R300x300M000" + mid + ".jpg" : "";
    }

    private static boolean validMid(String mid) { return mid.matches("[A-Za-z0-9]{1,40}") && !mid.equals("0"); }
    private static OnlineAlbum qqAlbum(JsonObject fields, String fallbackId, String fallbackName) {
        String mid = first(fields, "albumMid", "mid"), name = first(fields, "albumName", "name", "title");
        if (mid.isBlank()) mid = fallbackId;
        if (name.isBlank()) name = fallbackName;
        return validMid(mid) && !name.isBlank() ? new OnlineAlbum("qq", mid, name,
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + mid + ".jpg",
                first(fields, "publishDate", "time_public")) : null;
    }
    private static List<OnlineArtist> qqArtists(JsonArray singers) {
        return artistIdentities(singers, "qq", artist -> {
            String mid = first(artist, "mid", "singermid");
            return validMid(mid) ? mid : "";
        });
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
        var root = data.has("body") ? object(data, "body") : object(data, "data");
        return parseSongs(array(object(root, "song"), "list"), limit);
    }
    private static List<OnlineTrack> parseSongs(JsonArray entries, int limit) {
        var out = new ArrayList<OnlineTrack>();
        for (var entry : entries) {
            if (!entry.isJsonObject() || out.size() >= limit) continue;
            var song = entry.getAsJsonObject();
            if (song.has("songInfo")) song = object(song, "songInfo");
            String mid = text(song, "mid");
            if (mid.isBlank()) mid = text(song, "songmid");
            if (!mid.matches("[A-Za-z0-9]{1,40}")) continue;
            String media = text(object(song, "file"), "media_mid");
            if (media.isBlank()) media = text(song, "strMediaMid");
            var singers = array(song, "singer");
            var album = qqAlbum(object(song, "album"), text(song, "albummid"), text(song, "albumname"));
            out.add(new OnlineTrack("qq", mid, media.isBlank() ? mid : media,
                    first(song, "title", "name", "songname"), artists(singers),
                    song.has("album") ? text(object(song, "album"), "name") : text(song, "albumname"),
                    number(song, "interval") * 1000, albumCover(song),
                    qqArtists(singers), album, first(song, "id", "songid"), number(object(song, "pay"), "pay_play") == 1));
        }
        return List.copyOf(out);
    }
    private static String albumCover(JsonObject song) {
        String mid = song.has("album") ? text(object(song, "album"), "mid") : text(song, "albummid");
        return mid.matches("[A-Za-z0-9]{1,40}") ? "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + mid + ".jpg" : "";
    }

    QQDownload.Detail downloadDetail(OnlineTrack track) throws Exception {
        if (!id().equals(track.source()) || !track.id().matches("[A-Za-z0-9]{1,40}"))
            throw new IllegalArgumentException("Invalid QQ download track");
        var detail = singleSong(track);
        checkStatus(detail, "download detail");
        var songs = array(detail, "data");
        if (songs.size() != 1 || !songs.get(0).isJsonObject()) throw new IOException("Missing QQ song detail");
        return QQDownload.detail(songs.get(0).getAsJsonObject(), track, !account.key().isEmpty());
    }

    JsonObject downloadGrant(Map<String, ?> file) throws Exception {
        var fields = new LinkedHashMap<String, Object>(file);
        fields.put("guid", guid);
        fields.put("uin", credentialsSnapshot().id());
        fields.put("ctx", 0);
        return rpc("music.vkey.GetVkey", "UrlGetVkey", fields, Map.of("ct", "24", "cv", "0"));
    }

    private JsonObject singleSong(OnlineTrack track) throws IOException {
        return http.json("https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?" +
                MusicHttp.form(Map.of("songmid", track.id(), "platform", "yqq", "format", "json")),
                null, null, "https://y.qq.com/", cookies());
    }

    SongEncyclopedia encyclopedia(String mid) throws Exception {
        if (mid == null || !validMid(mid)) throw new IllegalArgumentException("Invalid QQ track MID");
        var data = rpc("music.pf_song_detail_svr", "get_song_detail_yqq", Map.of("song_mid", mid),
                Map.of("ct", 24, "cv", 0));
        var song = object(data, "track_info");
        if (!mid.equals(text(song, "mid"))) throw new IOException("QQ track identity changed");
        var info = object(data, "info");
        String date = infoValues(info, "pub_time");
        return new SongEncyclopedia(date.isEmpty() ? text(song, "time_public") : date, infoValues(info, "genre"),
                infoValues(info, "lan"), infoValues(info, "company"), infoValues(info, "intro"), "", "");
    }

    private static String infoValues(JsonObject info, String field) {
        var values = new LinkedHashSet<String>();
        for (var item : array(object(info, field), "content")) {
            if (!item.isJsonObject()) continue;
            String value = text(item.getAsJsonObject(), "value").strip();
            if (!value.isEmpty()) values.add(value);
            if (values.size() == 16) break;
        }
        return String.join(" / ", values);
    }

    OnlineTrack catalogTrack(String mid) throws IOException {
        if (!validMid(mid)) throw new IllegalArgumentException("Invalid QQ track MID");
        var data = singleSong(new OnlineTrack(id(), mid, mid, "", "", "", 0));
        var tracks = parseSongs(requireArray(data, "data"), 1);
        if (tracks.size() != 1 || !mid.equals(tracks.get(0).id())) throw new IOException("QQ track identity changed");
        return tracks.get(0);
    }

    @Override public Stream resolve(OnlineTrack track) throws Exception {
        var detail = singleSong(track);
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
        checkStatus(json, "RPC envelope");
        var req = object(json, "req");
        checkStatus(req, "RPC result");
        if (!req.has("data") || !req.get("data").isJsonObject()) throw new IOException("QQ response missing data");
        return object(req, "data");
    }
    /** Reject malformed success codes instead of treating a missing/non-numeric field as zero. */
    private static void checkStatus(JsonObject response, String operation) throws IOException, LoginRequired {
        String value = text(response, "code");
        final long code;
        try { code = Long.parseLong(value); }
        catch (NumberFormatException malformed) { throw new IOException("QQ " + operation + " response missing valid status"); }
        if (code == 1000 || code == 104009 || code == 401) throw new LoginRequired();
        if (code != 0) throw new IOException("QQ " + operation + " rejected: " + code);
    }
}
