// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.ListenTogetherService;
import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.NeteaseMusicService;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.utils.other.multithreading.MultiThreadingUtil;
import com.muoniumplayer.party.direct.MpDirect;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatWidgets.text;

/** Bridges validated requests to the same player operations used by its controls. */
final class MusicChatPlayback {
    record Context(long generation, MusicPlatform platform) {
        static Context capture() {
            return new Context(CloudMusic.generation.get(), NeteaseMusicService.getCurrentPlatform());
        }

        boolean trackChanged() { return generation != CloudMusic.generation.get(); }
        boolean sourceChanged() { return platform != NeteaseMusicService.getCurrentPlatform(); }
    }

    record Selection(List<Music> tracks, int index) {
        Music song() { return tracks.get(index); }
    }

    record Search(Selection match, List<MusicChatActions.Option> alternatives) {}
    record Resolution(List<Music> tracks, MusicChatActions.Option missing) {}

    static boolean available() {
        return !CloudMusic.isFullscreenQueueModeLocked() && !ListenTogetherService.isEngaged() && !MpDirect.isActive();
    }

    static CompletableFuture<Search> search(MusicChatActions.Command command) {
        return lookup(result -> {
            var tracks = CloudMusic.search(command.query());
            return new Search(select(tracks, command.target(), command.value()),
                    command.value().isEmpty() ? alternatives(tracks, command.target()) : List.of());
        });
    }

    static CompletableFuture<Resolution> resolve(List<MusicChatActions.Option> choices) {
        return lookup(result -> {
            var tracks = new ArrayList<Music>();
            for (var choice : choices) {
                if (result.isDone()) return null;
                var match = select(CloudMusic.search(choice.title() + " " + choice.artist()), choice.title(), choice.artist());
                if (match == null) return new Resolution(List.of(), choice);
                tracks.add(match.song());
            }
            return new Resolution(List.copyOf(tracks), null);
        });
    }

    private static <T> CompletableFuture<T> lookup(Function<CompletableFuture<T>, T> resolve) {
        var result = new CompletableFuture<T>();
        MultiThreadingUtil.runAsync(() -> {
            if (!result.isDone()) result.complete(resolve.apply(result));
        }).exceptionally(failure -> {
            result.completeExceptionally(failure);
            return null;
        });
        return result.orTimeout(25, TimeUnit.SECONDS);
    }

    private static Selection select(List<Music> results, String title, String artist) {
        title = normalized(title);
        artist = normalized(artist);
        for (int i = 0; i < results.size(); i++) {
            Music song = results.get(i);
            if (song == null || !title.equalsIgnoreCase(normalized(song.getName()))) continue;
            if (artist.isEmpty() || matchesArtist(song, artist)) return new Selection(results, i);
        }
        return null;
    }

    static List<MusicChatActions.Option> alternatives(List<Music> results, String target) {
        String term = normalized(target).toLowerCase(Locale.ROOT);
        var options = new LinkedHashMap<String, MusicChatActions.Option>();
        for (Music song : results) {
            if (song == null) continue;
            String title = normalized(song.getName());
            String artist = normalized(song.getArtistsName());
            String name = title.toLowerCase(Locale.ROOT);
            if (artist.isBlank() || !(name.equals(term) || term.codePointCount(0, term.length()) >= 2
                    && name.startsWith(term) && name.length() <= term.length() + 3)) continue;
            String id = name + "\n" + artist.toLowerCase(Locale.ROOT);
            options.putIfAbsent(id, new MusicChatActions.Option("", title, artist));
            if (options.size() == 6) break;
        }
        return List.copyOf(options.values());
    }

    private static boolean matchesArtist(Music song, String artist) {
        if (artist.equalsIgnoreCase(normalized(song.getArtistsName()))) return true;
        return song.getArtists() != null && song.getArtists().stream().anyMatch(value ->
                value != null && artist.equalsIgnoreCase(normalized(value.getName())));
    }

    private static String normalized(String name) {
        return name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFKC).strip();
    }

    static String play(MusicChatActions.Command command, Context original, Selection selection) {
        if (!available()) return text("action_unavailable");
        if (original.trackChanged() || original.sourceChanged()) return text("action_stale");
        if (selection == null) return text("action_no_song", command.target());
        Music song = selection.song();
        if (command.action() == MusicChatActions.Action.QUEUE_NEXT) {
            if (CloudMusic.getSnapshot().music == null || CloudMusic.playList.isEmpty() || CloudMusic.curIdx < 0
                    || !CloudMusic.playNext(song)) return text("action_unavailable");
            return text("action_queued", song.getName(), song.getArtistsName());
        }
        CloudMusic.playFromSearchSelection(song, selection.tracks(), selection.index());
        return text("action_play_requested", song.getName(), song.getArtistsName());
    }

    static String applySelection(MusicChatActions.Purpose purpose, Context original, Resolution resolved) {
        if (!available()) return text("action_unavailable");
        if (original.trackChanged() || original.sourceChanged()) return text("action_stale");
        if (resolved.missing() != null) return text("choice_song_missing", resolved.missing().title(), resolved.missing().artist());
        var tracks = resolved.tracks();
        if (purpose == MusicChatActions.Purpose.QUEUE && CloudMusic.getSnapshot().music != null) {
            return CloudMusic.playNextBatch(tracks) ? text("choice_queue_done", tracks.size()) : text("action_unavailable");
        }
        CloudMusic.play(tracks, 0);
        return tracks.size() > 1 ? text("choice_playlist_started", tracks.size())
                : text("action_play_requested", tracks.get(0).getName(), tracks.get(0).getArtistsName());
    }

    static String control(MusicChatActions.Command command, Context original) {
        if (!available()) return text("action_unavailable");
        if (original.trackChanged() && command.action() != MusicChatActions.Action.SET_VOLUME
                && command.action() != MusicChatActions.Action.PLAY_MODE) return text("action_stale");
        return switch (command.action()) {
            case PAUSE -> pauseOrResume(false);
            case RESUME -> pauseOrResume(true);
            case NEXT -> step(1);
            case PREVIOUS -> step(-1);
            case SEEK -> {
                var snapshot = CloudMusic.getSnapshot();
                long millis = command.number() * 1_000L;
                if (snapshot.durationMs <= 0 || millis >= snapshot.durationMs
                        || !CloudMusic.seekToMillis(millis, snapshot)) yield text("action_unavailable");
                yield text("action_seek", command.number());
            }
            case SET_VOLUME -> {
                CloudMusic.setVolume(command.number() / 100f, true);
                yield text("action_volume", command.number());
            }
            case PLAY_MODE -> {
                var mode = switch (command.value()) {
                    case "shuffle" -> CloudMusic.PlayMode.Random;
                    case "repeat_all" -> CloudMusic.PlayMode.LoopInList;
                    case "repeat_one" -> CloudMusic.PlayMode.LoopSingle;
                    default -> CloudMusic.PlayMode.Sequential;
                };
                yield CloudMusic.setLocalPlayMode(mode)
                        ? text("action_mode", text("mode_" + command.value())) : text("action_unavailable");
            }
            default -> throw new IllegalArgumentException("Unsupported transport command");
        };
    }

    private static String pauseOrResume(boolean resume) {
        var snapshot = CloudMusic.getSnapshot();
        if (snapshot.music == null) return text("action_unavailable");
        String result = text(resume ? "action_resumed" : "action_paused");
        if (snapshot.player != null && !snapshot.player.isFinished() && snapshot.playing == resume) return result;
        if (!resume && !snapshot.playing) return text("action_unavailable");
        var applied = CloudMusic.togglePlayPause(snapshot.player == null ? null : snapshot);
        return applied == (resume ? CloudMusic.PlayPauseResult.RESUMED : CloudMusic.PlayPauseResult.PAUSED)
                ? result : text("action_unavailable");
    }

    private static String step(int direction) {
        int size = CloudMusic.playList.size();
        int target = CloudMusic.curIdx + direction;
        var mode = CloudMusic.playMode;
        if (size == 0 || (target < 0 || target >= size) && !mode.repeatsAll() && !mode.repeatsOne()) {
            return text("action_unavailable");
        }
        if (direction > 0) CloudMusic.next();
        else CloudMusic.prev();
        return text(direction > 0 ? "action_next" : "action_previous");
    }
}
