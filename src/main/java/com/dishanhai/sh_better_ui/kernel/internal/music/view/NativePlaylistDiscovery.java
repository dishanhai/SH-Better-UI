// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Parse fixed provider links as IDs only. Network calls are made by the selected provider, never to the submitted URI. */
final class NativePlaylistDiscovery {
    private static final Pattern QQ_PATH = Pattern.compile("/n/ryqq(?:_v2)?/playlist/([1-9][0-9]{0,17})/?");
    private static final Pattern NETEASE_ID = Pattern.compile("[1-9][0-9]{0,23}");

    static String discoveryUrl(String source) {
        return switch (source) {
            case "qq" -> "https://y.qq.com/n/ryqq_v2/category";
            case "netease" -> "https://music.163.com/#/discover/playlist";
            default -> null;
        };
    }

    static String playlistId(String source, String input) {
        if (input == null || input.length() > 2048) return null;
        try {
            var uri = URI.create(input.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getHost() == null) return null;
            if ("qq".equals(source) && "y.qq.com".equalsIgnoreCase(uri.getHost()) && uri.getRawFragment() == null) {
                var match = QQ_PATH.matcher(uri.getRawPath());
                return match.matches() ? match.group(1) : null;
            }
            if (!"netease".equals(source) || !"music.163.com".equalsIgnoreCase(uri.getHost())) return null;
            String query;
            if ("/playlist".equals(uri.getRawPath()) && uri.getRawFragment() == null) query = uri.getRawQuery();
            else if ("/".equals(uri.getRawPath()) && uri.getRawQuery() == null && uri.getRawFragment() != null
                    && uri.getRawFragment().startsWith("/playlist?")) query = uri.getRawFragment().substring("/playlist?".length());
            else return null;
            if (query == null) return null;
            String id = null;
            for (var parameter : query.split("&", -1)) {
                if (!parameter.startsWith("id=")) continue;
                if (id != null) return null;
                id = parameter.substring(3);
                if (!NETEASE_ID.matcher(id).matches()) return null;
            }
            return id;
        } catch (IllegalArgumentException invalidUri) {
            return null;
        }
    }

    private final OnlineMusicPlayer player;
    private CompletableFuture<OnlinePlaylistDetail> pending;
    private String source, id;
    private LongSupplier epoch;
    private long startedAt;
    OnlinePlaylistDetail detail;
    String status = "discovery.hint";

    NativePlaylistDiscovery(OnlineMusicPlayer player) { this.player = player; }

    void open(String source, String input, LongSupplier epoch) {
        clear();
        this.id = playlistId(source, input);
        if (id == null) { status = "discovery.invalid"; return; }
        if (!player.supportsPlaylistDetail(source)) { status = "discovery.unsupported"; return; }
        if (!player.authenticated(source)) { status = LibraryAccessFeedback.SIGN_IN; return; }
        this.source = source;
        this.epoch = epoch;
        startedAt = epoch.getAsLong();
        status = "discovery.loading";
        try { pending = player.playlistDetail(source, id); }
        catch (RuntimeException failure) { status = "discovery.failed"; }
    }

    boolean changed() {
        if (source == null) return false;
        if (!player.authenticated(source) || epoch.getAsLong() != startedAt) {
            clear(); status = "discovery.session_changed"; return true;
        }
        if (pending == null || !pending.isDone()) return false;
        var request = pending;
        pending = null;
        try {
            var result = request.join();
            if (!source.equals(result.playlist().source()) || !id.equals(result.playlist().id())) {
                status = "discovery.failed";
            } else {
                detail = result;
                status = detail.tracks().isEmpty() ? "library.empty" : "library.results";
            }
        } catch (RuntimeException failure) {
            var error = LibraryAccessFeedback.failure(failure);
            status = LibraryAccessFeedback.AUTHORIZATION_REQUIRED.equals(error) ? error : "discovery.failed";
        }
        return true;
    }

    boolean loading() { return pending != null; }
    CompletableFuture<OnlinePlaylistDetail> pending() { return pending; }

    void play(int position) {
        if (source == null || !player.authenticated(source) || epoch.getAsLong() != startedAt || detail == null || position < 0 || position >= detail.tracks().size()) return;
        var queue = player.queue();
        if (queue == null || !queue.supportsModeReplacement()) throw new UnsupportedOperationException("Ordered playback unavailable");
        queue.replace(detail.tracks(), position, OnlineMusicQueue.Mode.SEQUENTIAL);
    }

    void clear() {
        if (pending != null) pending.cancel(true);
        pending = null; detail = null; source = null; id = null; epoch = null; status = "discovery.hint";
    }
}
