// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource.LoginRequired;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlinePlaylistDetail;
import com.dishanhai.sh_better_ui.kernel.api.music.platform.QQMusicPlaylists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.internal.music.SourceJson.*;

public final class ClientQQMusicPlaylists implements QQMusicPlaylists {
    private static final String DETAIL = "music.srfDissInfo.DissInfo";
    private static final String BASE = "music.musicasset.PlaylistBaseWrite";
    private static final int PAGE_SIZE = 100;
    private static final Gson JSON = new Gson();
    private static final ReentrantLock WRITES = new ReentrantLock();

    private final Supplier<String> credentials;
    private final Executor executor;
    private final String snapshot;
    private final String uin;
    private final boolean signedIn;
    private final QQFavorites.Rpc transport;

    public ClientQQMusicPlaylists(Supplier<String> credentials, Executor executor) {
        this(credentials, executor, null);
    }

    ClientQQMusicPlaylists(Supplier<String> credentials, Executor executor, QQFavorites.Rpc transport) {
        this.credentials = credentials;
        this.executor = executor;
        snapshot = MusicHttp.cookie(credentials.get());
        uin = cookieField(snapshot, "qqmusic_uin");
        String key = cookieField(snapshot, "qm_keyst");
        signedIn = uin.matches("[1-9][0-9]{0,23}") && !key.isEmpty();
        this.transport = transport != null ? transport : (module, method, params, write) ->
                QQWebProtocol.request(uin, key, module, method, params, write);
    }

    @Override public boolean available() {
        return signedIn && snapshot.equals(MusicHttp.cookie(credentials.get()));
    }

    @Override public CompletableFuture<List<Playlist>> list() {
        return submit(() -> List.copyOf(ownedPlaylists()), false);
    }

    @Override public CompletableFuture<Playlist> create(String name) {
        String title = name == null ? "" : name.strip();
        if (title.isEmpty() || title.length() > NAME_LIMIT) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid QQ playlist name"));
        }
        return submit(() -> {
            var response = rpc(BASE, "AddPlaylist", Map.of("dirName", title, "dirDesc", "",
                    "dirPicUrl", "", "taglist", "", "dirShow", 2), true);
            Playlist created = playlist(object(response, "result"));
            if (created == null) throw new IOException("QQ playlist creation was not confirmed");
            return created;
        }, true);
    }

    @Override public CompletableFuture<AddResult> add(Playlist playlist, String songMid) {
        if (playlist == null || songMid == null || !songMid.matches("[A-Za-z0-9]{1,40}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid QQ playlist or track"));
        }
        return submit(() -> {
            long matches = ownedPlaylists().stream().filter(target -> target.id() == playlist.id()
                    && target.directoryId() == playlist.directoryId()).count();
            if (matches != 1) throw new IOException("QQ playlist is not writable by this account");
            if (containsTrack(playlist, songMid)) return AddResult.ALREADY_PRESENT;
            var detail = object(rpc("music.pf_song_detail_svr", "get_song_detail_yqq",
                    Map.of("song_mid", songMid), false), "track_info");
            long id = QQWebProtocol.integer(detail, "id");
            long type = QQWebProtocol.integer(detail, "type");
            if (!songMid.equals(text(detail, "mid")) || id <= 0 || type < 0 || type > Integer.MAX_VALUE) {
                throw new IOException("QQ track identity could not be verified");
            }
            // The regular playlist endpoint uses directory song types rather than catalog types.
            int writeType = switch ((int) type) {
                case 0, 1 -> 0;
                case 5, 111, 112, 113 -> (int) type;
                default -> 1;
            };
            rpc("music.musicasset.PlaylistDetailWrite", "AddSonglist", Map.of(
                    "dirId", playlist.directoryId(), "tid", playlist.id(), "bFmtUtf8", true,
                    "v_songInfo", List.of(Map.of("songId", id, "songType", writeType))), true);
            if (!containsTrack(playlist, songMid)) throw new IOException("QQ playlist addition was not confirmed");
            return AddResult.ADDED;
        }, true);
    }

    private List<Playlist> ownedPlaylists() throws IOException {
        var data = rpc("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin", Map.of("uin", uin), false);
        var playlists = new ArrayList<Playlist>();
        for (var value : QQMusicSource.requireArray(data, "v_playlist", "disslist", "list")) {
            if (!value.isJsonObject()) continue;
            var entry = value.getAsJsonObject();
            String owner = text(entry, "uin");
            if (!owner.isEmpty() && !uin.equals(owner)) continue;
            Playlist playlist = playlist(entry);
            if (playlist != null) playlists.add(playlist);
        }
        return playlists;
    }

    private static Playlist playlist(JsonObject entry) {
        long id = number(entry, "tid");
        long directory = number(entry, "dirId");
        if (directory == 0) directory = number(entry, "dirid");
        String name = text(entry, "dirName");
        if (name.isBlank()) name = text(entry, "diss_name");
        if (id <= 0 || directory <= 0 || directory > Integer.MAX_VALUE
                || directory >= 201 && directory <= 206 || name.isBlank()) return null;
        return new Playlist(id, (int) directory, name, (int) Math.max(0, Math.min(Integer.MAX_VALUE, number(entry, "songNum"))));
    }

    private boolean containsTrack(Playlist playlist, String mid) throws IOException {
        int total = -1;
        var pages = new HashSet<List<String>>();
        for (int start = 0; total < 0 || start < total; start += PAGE_SIZE) {
            var page = rpc(DETAIL, "CgiGetDiss", Map.of("disstid", playlist.id(), "dirid", 0,
                    "song_begin", start, "song_num", PAGE_SIZE, "tag", 1), false);
            var info = object(page, "dirinfo");
            if (number(info, "id") != playlist.id() || number(info, "dirid") != playlist.directoryId()
                    || number(info, "owndir") != 1) {
                throw new IOException("QQ playlist identity or ownership changed");
            }
            String count = text(page, "total_song_num");
            if (!count.matches("[0-9]{1,5}")) throw new IOException("QQ playlist count is unavailable");
            int reported = Integer.parseInt(count);
            if (reported > OnlinePlaylistDetail.MAX_TRACKS || total >= 0 && reported != total) {
                throw new IOException("QQ playlist size changed or is too large");
            }
            total = reported;
            var songs = QQMusicSource.parseSongList(page, PAGE_SIZE);
            int expected = Math.min(PAGE_SIZE, total - start);
            if (array(page, "songlist").size() != expected || songs.size() != expected) {
                throw new IOException("QQ playlist readback is incomplete");
            }
            if (songs.stream().anyMatch(song -> mid.equals(song.id()))) return true;
            if (expected == PAGE_SIZE && !pages.add(songs.stream().map(song -> song.id()).toList())) {
                throw new IOException("QQ repeated a playlist page");
            }
        }
        return false;
    }

    private JsonObject rpc(String module, String method, Map<String, ?> params, boolean write) throws IOException {
        if (!available()) throw new LoginRequired();
        var response = transport.call(module, method, JSON.toJsonTree(params).getAsJsonObject(), write);
        if (write && QQWebProtocol.integer(response, "retCode") != 0) throw new MusicWriteRejected("QQ playlist write rejected");
        return response;
    }

    @FunctionalInterface
    private interface Request<T> {
        T run() throws IOException;
    }

    private <T> CompletableFuture<T> submit(Request<T> request, boolean mutating) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (mutating) WRITES.lockInterruptibly();
                    try {
                        T result = request.run();
                        if (!available()) throw new LoginRequired();
                        return result;
                    } finally {
                        if (mutating) WRITES.unlock();
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(failure);
                } catch (IOException failure) {
                    throw new CompletionException(failure);
                }
            }, executor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static String cookieField(String cookie, String key) {
        String prefix = key + "=";
        for (String part : cookie.split(";")) {
            String field = part.strip();
            if (field.startsWith(prefix)) return field.substring(prefix.length());
        }
        return "";
    }
}
