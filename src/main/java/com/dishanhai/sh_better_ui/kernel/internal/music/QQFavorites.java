// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource.LoginRequired;
import com.google.gson.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** One mutation at a time across credential snapshots; every phase remains interruptible. */
final class QQFavorites {
    @FunctionalInterface interface Rpc {
        JsonObject call(String module, String method, JsonObject parameters, boolean write) throws IOException;
    }
    private final ReentrantLock mutations = new ReentrantLock();
    private final Rpc override;
    QQFavorites() { this(null); }
    QQFavorites(Rpc override) { this.override = override; }

    static final class NotSent extends IOException {}

    void like(OnlineTrack track, boolean target, String uin, String key) throws Exception {
        mutations.lockInterruptibly();
        try {
            Rpc rpc = (module, method, param, write) -> {
                QQWebProtocol.checkCancelled();
                var data = override == null ? QQWebProtocol.request(uin, key, module, method, param, write)
                        : override.call(module, method, param, write);
                QQWebProtocol.checkCancelled();
                return data;
            };
            JsonObject write;
            try {
                if (status(rpc, track.id()) == target) return;
                var param = new JsonObject(); param.addProperty("song_mid", track.id());
                var detail = SourceJson.object(rpc.call("music.pf_song_detail_svr", "get_song_detail_yqq", param, false), "track_info");
                long id = QQWebProtocol.integer(detail, "id"), type = QQWebProtocol.integer(detail, "type");
                if (!track.id().equals(SourceJson.text(detail, "mid")) || id <= 0 || type < 0 || type > Integer.MAX_VALUE)
                    throw new IOException("QQ track identity could not be verified");
                var song = new JsonObject(); song.addProperty("songId", id); song.addProperty("songType", type);
                var songs = new JsonArray(); songs.add(song);
                write = new JsonObject(); write.addProperty("dirId", 201); write.add("v_songInfo", songs);
            } catch (LoginRequired error) { throw error; }
            catch (IOException error) {
                if (Thread.currentThread().isInterrupted()) throw error;
                throw new NotSent();
            }
            var accepted = rpc.call("music.musicasset.PlaylistDetailWrite", target ? "AddSonglist" : "DelSonglist", write, true);
            if (QQWebProtocol.integer(accepted, "retCode") != 0) throw new MusicWriteRejected("QQ favorite write rejected");
            try {
                if (status(rpc, track.id()) != target) throw new IOException("QQ favorite change was not confirmed");
            } catch (LoginRequired error) {
                // A rejected readback cannot establish what happened to the accepted write.
                throw new IOException("QQ favorite readback unavailable");
            }
        } finally { mutations.unlock(); }
    }
    private static boolean status(Rpc rpc, String mid) throws IOException {
        Boolean value = statuses(rpc, List.of(mid)).get(mid);
        if (value == null) throw new IOException("QQ favorite status missing");
        return value;
    }

    Map<String, Boolean> statuses(List<String> tracks, String uin, String key) throws IOException {
        Rpc rpc = override == null
                ? (module, method, param, write) -> QQWebProtocol.request(uin, key, module, method, param, write)
                : override;
        return statuses(rpc, tracks);
    }

    private static Map<String, Boolean> statuses(Rpc rpc, List<String> tracks) throws IOException {
        var mids = new JsonArray();
        tracks.forEach(mids::add);
        var parameters = new JsonObject(); parameters.add("v_songMid", mids);
        var data = rpc.call("music.musicasset.SongFavRead", "IsSongFanByMid", parameters, false);
        var fan = SourceJson.object(data, "m_fan");
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String mid : tracks) {
            var value = fan.get(mid);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                result.put(mid, value.getAsBoolean());
            }
        }
        return result;
    }
}
