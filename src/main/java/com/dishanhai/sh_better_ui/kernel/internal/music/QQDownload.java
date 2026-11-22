// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads.Access;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads.Option;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads.Options;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads.Quality;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dishanhai.sh_better_ui.kernel.internal.music.SourceJson.*;

/** QQ download metadata and file authorization. Never uses the playback or preview fallback chain. */
final class QQDownload {
    private static final String DEFAULT_CDN = "https://dl.stream.qqmusic.qq.com/";
    static final long MAX_FILE_SIZE = 128L * 1024 * 1024;

    record Detail(Options options, String media, long type) {}
    record FileGrant(Access access, URI uri, long bytes) {}

    static String prefix(Quality quality) {
        return switch (quality) {
            case STANDARD -> "M500";
            case HIGH -> "M800";
            case LOSSLESS -> "F000";
        };
    }

    static Detail detail(JsonObject song, OnlineTrack track, boolean signedIn) throws IOException {
        if (!track.id().equals(text(song, "mid")) && !track.id().equals(text(song, "songmid")))
            throw new IOException("QQ download metadata belongs to another track");
        var file = object(song, "file");
        String media = text(file, "media_mid");

        var options = new ArrayList<Option>();
        for (Quality quality : Quality.values()) {
            String sizeKey = switch (quality) {
                case STANDARD -> "size_128mp3";
                case HIGH -> "size_320mp3";
                case LOSSLESS -> "size_flac";
            };
            long size = field(file, sizeKey);
            if (size > 0 && size <= MAX_FILE_SIZE) options.add(new Option(quality, size));
        }
        // Catalog payment flags describe the song. The file grant checks the signed-in account's access.
        Access access = Access.UNAVAILABLE;
        if (!options.isEmpty() && media.matches("[A-Za-z0-9]{1,40}"))
            access = signedIn ? Access.AVAILABLE : Access.SIGN_IN;
        return new Detail(new Options(access, options), media, number(song, "type"));
    }

    static FileGrant resolve(QQMusicSource source, OnlineTrack track, Quality quality) throws Exception {
        var detail = source.downloadDetail(track);
        Access access = detail.options().access();
        if (access != Access.AVAILABLE) return new FileGrant(access, null, 0);
        var option = detail.options().qualities().stream().filter(item -> item.quality() == quality).findFirst();
        if (option.isEmpty()) return new FileGrant(Access.UNAVAILABLE, null, 0);
        String filename = prefix(quality) + detail.media() + "." + quality.extension();
        var data = source.downloadGrant(Map.of("songmid", List.of(track.id()), "filename", List.of(filename),
                "songtype", List.of(detail.type())));
        return grant(data, track.id(), filename, option.get().bytes());
    }

    static FileGrant grant(JsonObject data, String mid, String filename, long bytes) throws IOException {
        var entries = array(data, "midurlinfo");
        if (entries.size() != 1 || !entries.get(0).isJsonObject()) throw new IOException("Missing QQ file result");
        var item = entries.get(0).getAsJsonObject();
        if (!mid.equals(text(item, "songmid")) || !filename.equals(text(item, "filename")))
            throw new IOException("QQ returned a different file");
        long result = field(item, "result");
        if (result == 104009) return new FileGrant(Access.SIGN_IN, null, 0);
        if (result == 104003 || number(item, "pneedbuy") > 0)
            return new FileGrant(Access.UNAVAILABLE, null, 0);
        if (result != 0) throw new IOException("QQ file authorization failed");
        String path = text(item, "purl");
        if (path.isBlank() || !text(item, "ekey").isBlank())
            return new FileGrant(Access.UNAVAILABLE, null, 0);
        URI relative = URI.create(path);
        if (relative.isAbsolute() || relative.getAuthority() != null || relative.getFragment() != null
                || !(filename.equals(relative.getPath()) || ("/" + filename).equals(relative.getPath())))
            throw new IOException("QQ returned an unexpected media path");
        var cdns = array(data, "sip");
        for (var cdn : cdns) {
            if (!cdn.isJsonPrimitive() || !cdn.getAsJsonPrimitive().isString()) continue;
            URI uri = MusicHttp.https(cdn.getAsString()).resolve(relative);
            if (cdn(uri)) return new FileGrant(Access.AVAILABLE, uri, bytes);
        }
        return new FileGrant(Access.AVAILABLE, URI.create(DEFAULT_CDN).resolve(relative), bytes);
    }

    static boolean cdn(URI uri) {
        String host = uri.getHost();
        if (host == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getPort() != -1 && uri.getPort() != 443) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.endsWith(".qqmusic.qq.com") || host.endsWith(".y.qq.com") || host.endsWith(".music.tc.qq.com");
    }

    private static long field(JsonObject data, String key) {
        try { return Long.parseLong(text(data, key)); }
        catch (NumberFormatException failure) { return -1; }
    }

    private QQDownload() {}
}
