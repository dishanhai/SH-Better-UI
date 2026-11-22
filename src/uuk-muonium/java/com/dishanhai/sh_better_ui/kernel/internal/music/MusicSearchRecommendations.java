// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.muoniumplayer.core.ncm.api.CloudMusicApi;
import com.muoniumplayer.core.ncm.music.KuGouMusicService;
import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.qq.QQDiscoveryApi;
import com.muoniumplayer.core.utils.other.multithreading.MultiThreadingUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The search surface and discovery pages share the providers' trending keywords. */
public final class MusicSearchRecommendations {
    private static final long CACHE_MILLIS = 300_000;
    private static final int KEYWORD_LIMIT = 30;
    private static final EnumMap<MusicPlatform, Request> REQUESTS = new EnumMap<>(MusicPlatform.class);

    private static final class QQ {
        private static final QQMusicSource SOURCE = new QQMusicSource();
    }

    private MusicSearchRecommendations() {}

    private record Request(long started, CompletableFuture<List<String>> terms) {}
    public record HotSearch(String keyword, String description, long score) {}

    public static List<String> qqSuggestions(String query, int limit) throws Exception {
        return QQ.SOURCE.suggest(query, limit);
    }

    static synchronized CompletableFuture<List<String>> hot(MusicPlatform platform) {
        if (platform != MusicPlatform.QQ && platform != MusicPlatform.NETEASE && platform != MusicPlatform.KUGOU) {
            return CompletableFuture.completedFuture(List.of());
        }
        long now = System.currentTimeMillis();
        Request current = REQUESTS.get(platform);
        if (current != null && (!current.terms().isDone() || now - current.started() < CACHE_MILLIS)) {
            return current.terms();
        }
        var terms = new CompletableFuture<List<String>>();
        REQUESTS.put(platform, new Request(now, terms));
        MultiThreadingUtil.runAsync(() -> {
            try {
                List<String> result = switch (platform) {
                    case QQ -> QQDiscoveryApi.hotSearches(KEYWORD_LIMIT);
                    case NETEASE -> neteaseHotSearches().stream().map(HotSearch::keyword).toList();
                    case KUGOU -> KuGouMusicService.hotSearch();
                    default -> List.of();
                };
                terms.complete(result.stream().filter(term -> term != null && !term.isBlank())
                        .map(String::strip).distinct().limit(KEYWORD_LIMIT).toList());
            } catch (Exception error) {
                terms.complete(List.of());
            }
        });
        return terms;
    }

    public static List<HotSearch> neteaseHotSearches() {
        var root = CloudMusicApi.searchHotDetail().toJsonObject();
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) return List.of();
        var result = new ArrayList<HotSearch>();
        for (var element : root.getAsJsonArray("data")) {
            if (!element.isJsonObject()) continue;
            var item = element.getAsJsonObject();
            String keyword = text(item, "searchWord", "first", "keyword");
            if (keyword.isEmpty()) continue;
            long score = 0;
            try {
                var value = item.get("score");
                if (value != null && value.isJsonPrimitive()) score = value.getAsLong();
            } catch (NumberFormatException ignored) {
                // Some entries omit a numeric score.
            }
            result.add(new HotSearch(keyword, text(item, "content"), score));
        }
        return result;
    }

    private static String text(JsonObject item, String... keys) {
        for (String key : keys) {
            var value = item.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString().strip();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }
}
