// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Entry
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.ReplyPage
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack
import com.google.gson.JsonObject
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

/** Guest song search and public comments keyed by Kugou's album audio ID (MixSongID). */
internal class KugouCommentSource : OnlineMusicSource {
    private val http = MusicHttp()

    override fun id(): String = "kugou"
    override fun supportsPlayback(): Boolean = false
    override fun commentOrders(): List<Order> = java.util.List.of(Order.DEFAULT)

    override fun search(query: String, page: Int, limit: Int): List<OnlineTrack> {
        require(page in 1..1000 && limit in 1..20)
        val parameters = MusicHttp.form(mapOf("keyword" to query, "page" to page, "pagesize" to limit))
        val response = http.json("https://mobiles.kugou.com/api/v3/search/song?$parameters", null, null, REFERER)
        val data = SourceJson.`object`(response, "data")
        if (SourceJson.text(response, "status") != "1" || data.get("info")?.isJsonArray != true) {
            throw IOException("Kugou rejected song search")
        }
        return SourceJson.array(data, "info").asSequence().filter { it.isJsonObject }.mapNotNull { row ->
            val song = row.asJsonObject
            val id = SourceJson.text(song, "album_audio_id")
            val title = SourceJson.text(song, "songname")
            if (!MusicCommentFeeds.numericId(id) || title.isBlank()) return@mapNotNull null
            OnlineTrack(id(), id, id, title, SourceJson.text(song, "singername"),
                SourceJson.text(song, "album_name"), SourceJson.number(song, "duration").coerceIn(0, 86_400) * 1000)
        }.distinctBy { it.id() }.take(limit).toList()
    }

    override fun comments(track: OnlineTrack, order: Order, page: Int, limit: Int): OnlineMusicComments {
        require(track.source() == id() && MusicCommentFeeds.numericId(track.id()))
        MusicCommentFeeds.checkPage(order, commentOrders(), page, limit)
        return MusicCommentFeeds.kugou(read("cmtlist", track.id(), page, limit, mapOf("extdata" to "0")), page, limit)
    }

    override fun commentReplies(track: OnlineTrack, parent: Entry, cursor: String?, limit: Int): ReplyPage {
        val page = if (cursor == null) 1 else requireNotNull(cursor.toIntOrNull())
        val child = requireNotNull(parent.replyKey)
        require(track.source() == id() && MusicCommentFeeds.numericId(track.id()) &&
            MusicCommentFeeds.numericId(child) && MusicCommentFeeds.numericId(parent.id) &&
            page in 1..1000 && limit in 1..20)
        val data = read("hot_replylist", track.id(), page, limit, mapOf("childrenid" to child, "tid" to parent.id))
        return MusicCommentFeeds.kugouReplies(data, parent, page, limit)
    }

    private fun read(route: String, song: String, page: Int, limit: Int, extra: Map<String, String>): JsonObject {
        val parameters = sortedMapOf(
            "appid" to "1005", "clientver" to "20489", "clienttime" to Instant.now().epochSecond.toString(),
            "mid" to "0", "dfid" to "-", "uuid" to "-", "userid" to "0", "ver" to "6",
            "mixsongid" to song, "p" to page.toString(), "pagesize" to limit.toString(),
            "show_classify" to "0", "show_hotword_list" to "0",
            "need_show_image" to "0", "code" to "fc4be23b4e972707f36b8a828a93ba8a",
        )
        parameters.putAll(extra)
        val input = parameters.entries.joinToString("", prefix = SIGNATURE_SALT, postfix = SIGNATURE_SALT) {
            "${it.key}=${it.value}"
        }
        parameters["signature"] = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)))
        return http.json("https://gateway.kugou.com/mcomment/v1/$route?${MusicHttp.form(parameters)}",
            null, null, REFERER)
    }

    override fun resolve(track: OnlineTrack): OnlineMusicSource.Stream? = null
    override fun lyrics(track: OnlineTrack): LyricTimeline = LyricTimeline.empty()

    private companion object {
        const val REFERER = "https://www.kugou.com/"
        const val SIGNATURE_SALT = "OIlwieks28dk2k092lksi2UIkp"
    }
}
