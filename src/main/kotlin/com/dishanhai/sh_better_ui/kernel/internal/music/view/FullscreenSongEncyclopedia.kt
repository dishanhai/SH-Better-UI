// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.kernel.api.music.SongEncyclopedia
import com.dishanhai.sh_better_ui.kernel.internal.PlayingTrackPages
import com.dishanhai.sh_better_ui.kernel.internal.music.SongAiReview
import com.muoniumplayer.core.management.FontManager
import com.muoniumplayer.core.ncm.music.CloudMusic
import com.muoniumplayer.core.ncm.music.dto.Music
import com.muoniumplayer.core.utils.other.multithreading.MultiThreadingUtil
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/** Render-thread document for one song, with cancellable catalogue and review requests. */
internal class FullscreenSongEncyclopedia {
    private var songKey: String? = null
    private lateinit var metadata: PlayingTrackPages.SongMetadata
    private var pending: FutureTask<SongEncyclopedia>? = null
    private var notes: SongEncyclopedia? = null
    private var failed = false
    private var layoutWidth = -1
    private var fontRevision = -1L
    private var lyricsRevision = -1L
    private var reviewSetup: FutureTask<SongAiReview>? = null
    private var reviewChannel: SongAiReview? = null
    private var reviewRequest: CompletableFuture<String>? = null
    private var reviewQueued = false
    private var automaticAttempted = false
    private var reviewText: String? = null
    private var reviewExpanded = false
    private var reviewStatus: Component? = null
    private var language = ""
    var lines: List<String> = emptyList()
        private set
    var skeletonWidths: Map<Int, Double> = emptyMap()
        private set

    fun clear() {
        pending?.cancel(true)
        pending = null
        reviewSetup?.cancel(true)
        reviewSetup = null
        reviewChannel = null
        reviewRequest?.cancel(true)
        reviewRequest = null
        reviewQueued = false
        automaticAttempted = false
        reviewText = null
        reviewExpanded = false
        reviewStatus = null
        songKey = null
        notes = null
        failed = false
        layoutWidth = -1
        lyricsRevision = -1L
        lines = emptyList()
        skeletonWidths = emptyMap()
    }

    fun update(song: Music, title: String, width: Int, active: Boolean) {
        if (songKey != song.stableKey) {
            clear()
            songKey = song.stableKey
            metadata = PlayingTrackPages.songMetadata(song)
            loadReviewChannel()
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
        val selectedLanguage = Minecraft.getInstance().languageManager.selected
        if (language != selectedLanguage) {
            language = selectedLanguage
            layoutWidth = -1
        }
        advanceReview(song, title, active)
        val revision = CloudMusic.lyricsRevision
        val font = FontManager.pf14
        if (layoutWidth == width && fontRevision == font.metricsRevision() && lyricsRevision == revision) return
        lyricsRevision = revision
        layoutWidth = width
        fontRevision = font.metricsRevision()
        val placeholders = mutableMapOf<Int, Double>()
        lines = buildList {
            fun paragraph(text: String) {
                text.lineSequence().forEach { addAll(font.fitWidth(it, width.coerceAtLeast(1).toDouble())) }
            }
            fun skeleton() {
                for (fraction in SKELETON_WIDTHS) {
                    placeholders[size] = fraction
                    add("")
                }
            }
            reviewStatus?.let { paragraph(it.string) }
            if (reviewQueued || reviewRequest != null) skeleton()
            if (reviewExpanded) {
                reviewText?.let {
                    paragraph(label("ai_review").string)
                    paragraph(it)
                }
            }
            fun field(key: String, value: String?) {
                if (!value.isNullOrBlank()) paragraph(label(key, value.take(8192)).string)
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
                    .distinct().take(12).forEach { paragraph(it.take(512)) }
            }
            field("genre", notes?.genre())
            field("language", notes?.language())
            field("label", notes?.label())
            field("source", song.source.displayName)
            field("intro", notes?.introduction())
            field("album_notes", notes?.albumNotes())
            if (pending != null) {
                paragraph(label("loading").string)
                skeleton()
            }
            if (failed) paragraph(label("failed").string)
        }
        skeletonWidths = placeholders
    }

    fun reviewButton(): Component = label(when {
        reviewQueued || reviewRequest != null -> "review_cancel"
        reviewText != null -> if (reviewExpanded) "review_hide" else "review_show"
        reviewStatus != null -> "review_retry"
        else -> "ai_review"
    })

    fun reviewHint(): Component =
        if (reviewText == null && !reviewQueued && reviewRequest == null) label("review_help") else Component.empty()

    fun activateReview(): Boolean {
        automaticAttempted = true
        layoutWidth = -1
        if (reviewQueued || reviewRequest != null) {
            reviewQueued = false
            reviewRequest?.cancel(true)
            reviewRequest = null
            setReviewStatus(chatLabel("cancelled"))
            return false
        }
        if (reviewText != null) {
            reviewExpanded = !reviewExpanded
            return reviewExpanded
        }
        if (reviewChannel == null && reviewSetup == null) loadReviewChannel()
        queueReview()
        return true
    }

    private fun loadReviewChannel() {
        reviewSetup = FutureTask { SongAiReview.load() }.also { MultiThreadingUtil.runAsync(it) }
    }

    private fun queueReview() {
        reviewQueued = true
        setReviewStatus(label("review_waiting"))
    }

    private fun advanceReview(song: Music, title: String, active: Boolean) {
        reviewSetup?.takeIf { it.isDone }?.let {
            reviewSetup = null
            try { reviewChannel = it.get() } catch (_: ExecutionException) { /* Report on explicit request. */ }
        }
        val channel = reviewChannel
        if (active && !automaticAttempted && channel?.automatic() == true) {
            automaticAttempted = true
            queueReview()
        }
        if (active && reviewQueued && reviewSetup == null && pending == null) {
            reviewQueued = false
            when {
                channel == null -> setReviewStatus(chatLabel("storage_error"))
                !channel.configured() -> setReviewStatus(label("review_needs_config"))
                else -> reviewRequest = channel.request(title, song.artistsName, metadata.album(), notes, language)
            }
        }
        reviewRequest?.takeIf { it.isDone }?.let {
            reviewRequest = null
            try {
                reviewText = it.join()
                reviewExpanded = true
                setReviewStatus(null)
                OnlineMusicViewPaint.narrate(label("review_ready"))
            } catch (error: CompletionException) {
                setReviewStatus(chatLabel(SongAiReview.failureKey(error)))
            }
        }
    }

    private fun setReviewStatus(status: Component?) {
        reviewStatus = status
        layoutWidth = -1
        if (status != null) OnlineMusicViewPaint.narrate(status)
    }

    companion object {
        private val SKELETON_WIDTHS = doubleArrayOf(.88, .72, .46)
        private val CREDIT = Regex("^(作[词詞曲]|[词詞曲]|[编編]曲|制作人|製作人|Producer|Composer|Lyricist|Arranger)\\s*[:：]", RegexOption.IGNORE_CASE)
        fun label(key: String, vararg args: Any): Component =
            Component.translatable("sh_better_ui.song_encyclopedia." + key, *args)
        private fun chatLabel(key: String): Component = Component.translatable("sh_better_ui.music_chat." + key)
    }
}
