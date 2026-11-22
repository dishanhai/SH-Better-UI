// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.kernel.api.music.SongEncyclopedia
import com.dishanhai.sh_better_ui.kernel.internal.PlayingTrackPages
import com.muoniumplayer.core.management.FontManager
import com.muoniumplayer.core.ncm.music.CloudMusic
import com.muoniumplayer.core.ncm.music.dto.Music
import com.muoniumplayer.core.utils.other.multithreading.MultiThreadingUtil
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.FutureTask
import java.util.concurrent.ExecutionException

/** Render-thread document for one song. Only the catalogue lookup runs on a worker. */
internal class FullscreenSongEncyclopedia {
    private var songKey: String? = null
    private lateinit var metadata: PlayingTrackPages.SongMetadata
    private var pending: FutureTask<SongEncyclopedia>? = null
    private var notes: SongEncyclopedia? = null
    private var failed = false
    private var layoutWidth = -1
    private var fontRevision = -1L
    private var lyricsRevision = -1L
    var lines: List<String> = emptyList()
        private set

    fun clear() {
        pending?.cancel(true)
        pending = null
        songKey = null
        notes = null
        failed = false
        layoutWidth = -1
        lyricsRevision = -1L
        lines = emptyList()
    }

    fun update(song: Music, width: Int) {
        if (songKey != song.stableKey) {
            clear()
            songKey = song.stableKey
            metadata = PlayingTrackPages.songMetadata(song)
            val id = metadata.catalogId()
            if (!id.isNullOrBlank()) {
                val source = if (song.isQQ) "qq" else "netease"
                pending = FutureTask { SongEncyclopedia.lookup(source, id) }.also { MultiThreadingUtil.runAsync(it) }
            }
        }
        pending?.takeIf { it.isDone }?.let {
            try { notes = it.get() } catch (_: ExecutionException) { failed = true }
            pending = null
            layoutWidth = -1
        }
        val revision = CloudMusic.lyricsRevision
        val font = FontManager.pf14
        if (layoutWidth == width && fontRevision == font.metricsRevision() && lyricsRevision == revision) return
        lyricsRevision = revision
        layoutWidth = width
        fontRevision = font.metricsRevision()
        val fields = buildList {
            fun field(key: String, value: String?) {
                if (!value.isNullOrBlank()) add(label(key, value.take(8192)).string)
            }
            field("artist", song.artistsName)
            field("album", metadata.album())
            val date = notes?.songDate()?.takeIf { it.isNotBlank() }
                ?: metadata.publishedAt().takeIf { it > 0 }?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString()
                }
            field("date", date)
            field("album_date", notes?.albumDate())
            if (CloudMusic.lyricsTrackKey == songKey) {
                CloudMusic.lyrics.asSequence().map { it.lyric.trim() }.filter { CREDIT.containsMatchIn(it) }
                    .distinct().take(12).forEach { add(it.take(512)) }
            }
            field("genre", notes?.genre())
            field("language", notes?.language())
            field("label", notes?.label())
            field("source", song.source.displayName)
            field("intro", notes?.introduction())
            field("album_notes", notes?.albumNotes())
            if (pending != null) add(label("loading").string)
            if (failed) add(label("failed").string)
        }
        lines = fields.flatMap { field ->
            field.lineSequence().flatMap { font.fitWidth(it, width.coerceAtLeast(1).toDouble()).asSequence() }.toList()
        }
    }

    companion object {
        private val CREDIT = Regex("^(作[词詞曲]|[词詞曲]|[编編]曲|制作人|製作人|Producer|Composer|Lyricist|Arranger)\\s*[:：]", RegexOption.IGNORE_CASE)
        fun label(key: String, vararg args: Any): Component =
            Component.translatable("sh_better_ui.song_encyclopedia." + key, *args)
    }
}
