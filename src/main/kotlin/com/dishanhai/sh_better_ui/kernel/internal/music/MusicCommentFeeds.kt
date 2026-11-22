// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Entry
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.ReplyPage
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Fixed, public provider routes and normalization of their comment payloads. */
internal object MusicCommentFeeds {
    private val numberId = Regex("[1-9][0-9]{0,23}")
    private val qqMid = Regex("[A-Za-z0-9]{1,40}")
    private val qqCommentId = Regex("[A-Za-z0-9!._-]{1,128}")
    private val china = ZoneId.of("Asia/Shanghai")
    @JvmField
    val SORTED: List<Order> = java.util.List.of(Order.HOT, Order.LATEST)

    @JvmStatic
    fun qq(track: OnlineTrack, order: Order, page: Int, limit: Int, request: QQRequest): OnlineMusicComments {
        checkPage(order, SORTED, page, limit)
        require(track.source() == "qq" && track.id().matches(qqMid))
        if (order == Order.HOT && page > 1) return OnlineMusicComments(emptyList(), null, false)
        val ref = "https://y.qq.com/"
        val id = if (numericId(track.id())) track.id() else qqSongId(track.id(), ref, request)
        if (order == Order.HOT) {
            try {
                val data = qqCommentRead("GetHotCommentList", id, limit,
                    mapOf("HotType" to 1, "SortType" to 1, "LastCommentSeqNo" to "0", "WithHot" to 1), request)
                val group = SourceJson.`object`(data, "CommentList")
                return OnlineMusicComments(rows(group, "Comments", count(group, "Total"), limit, ::qqModern),
                    count(data, "TotalCmNum"), false)
            } catch (failure: IOException) {
                if (Thread.currentThread().isInterrupted) throw failure
                // The legacy list can still be read if the global-comment service is unavailable.
            }
        }
        val query = MusicHttp.form(mapOf("biztype" to 1, "topid" to id, "cmd" to 8,
            "pagenum" to page - 1, "pagesize" to limit, "format" to "json"))
        val response = request.read("https://c.y.qq.com/base/fcgi-bin/fcg_global_comment_h5.fcg?$query", ref)
        if (count(response, "code") != 0L || SourceJson.text(response, "topid") != id) {
            throw IOException("QQ rejected comment request")
        }
        val hot = order == Order.HOT
        val group = SourceJson.`object`(response, if (hot) "hot_comment" else "comment")
        val reported = count(group, "commenttotal")
        if (hot && group.size() == 0 && response.get("comment")?.isJsonObject == true) {
            return OnlineMusicComments(emptyList(), null, false)
        }
        val entries = rows(group, "commentlist", reported, limit) { row ->
            val parent = value(row, "rootcommentid")
            val root = parent == null || parent == value(row, "commentid")
            entry(row, "commentid", SourceJson.text(row, "nick"), if (root) "rootcommentcontent" else "middlecommentcontent",
                date(row, "time", 2005), count(row, "praisenum"), count(row, "replynum"),
                avatar(row, "avatarurl"), value(row, "commentid")?.takeIf(qqCommentId::matches),
                value(row, "rootcommentnick")?.removePrefix("@")?.takeUnless { root })
        }
        val more = !hot && count(response, "morecomment") == 1L && entries.isNotEmpty()
        return OnlineMusicComments(entries, if (hot) null else reported, more)
    }

    @JvmStatic
    fun qqReplies(track: OnlineTrack, parent: Entry, cursor: String?, limit: Int, request: QQRequest): ReplyPage {
        require(track.source() == "qq" && track.id().matches(qqMid) && limit in 1..20)
        val root = requireNotNull(parent.replyKey)
        require(root == parent.id && root.matches(qqCommentId) && (cursor == null || numericId(cursor)))
        val id = if (numericId(track.id())) track.id() else qqSongId(track.id(), "https://y.qq.com/", request)
        val data = qqCommentRead("GetReplyCommentList", id, limit,
            mapOf("RootCmId" to root, "LastCommentSeqNo" to (cursor ?: "0")), request)
        val group = SourceJson.`object`(data, "CommentList")
        val entries = rows(group, "Comments", count(group, "Total"), limit) { qqModern(it)?.copy(replyKey = null) }
        val next = SourceJson.array(group, "Comments").objects().lastOrNull()?.let { value(it, "SeqNo") }
            ?.takeIf { count(group, "HasMore") == 1L && it != cursor && numericId(it) && entries.isNotEmpty() }
        return ReplyPage(entries, next)
    }

    private fun qqCommentRead(method: String, id: String, limit: Int, extra: Map<String, Any>, request: QQRequest): JsonObject {
        val fields = mapOf("BizType" to 1, "BizId" to id, "PageSize" to limit) + extra
        val data = com.google.gson.Gson().toJson(mapOf(
            "comm" to mapOf("ct" to 24, "cv" to 0, "uin" to "0", "format" to "json"),
            "req" to mapOf("module" to "music.globalComment.CommentReadServer", "method" to method, "param" to fields)))
        val response = request.read("https://u.y.qq.com/cgi-bin/musicu.fcg?${MusicHttp.form(mapOf("data" to data))}", "https://y.qq.com/")
        val result = SourceJson.`object`(response, "req")
        if (count(response, "code") != 0L || count(result, "code") != 0L) throw IOException("QQ rejected comment read")
        return SourceJson.`object`(result, "data")
    }

    private fun qqModern(row: JsonObject): Entry? {
        val to = SourceJson.array(row, "RepliedComments").objects().firstOrNull()?.let { value(it, "Nick") }
        return entry(row, "CmId", SourceJson.text(row, "Nick"), "Content", date(row, "PubTime", 2005),
            count(row, "PraiseNum"), count(row, "ReplyCnt"), avatar(row, "Avatar"),
            value(row, "CmId")?.takeIf(qqCommentId::matches), to)
    }

    fun interface QQRequest {
        @Throws(IOException::class)
        fun read(url: String, referer: String): JsonObject
    }

    private fun qqSongId(mid: String, ref: String, request: QQRequest): String {
        val query = MusicHttp.form(mapOf("songmid" to mid, "platform" to "yqq", "format" to "json"))
        val detail = request.read("https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg?$query", ref)
        val song = SourceJson.array(detail, "data").objects().firstOrNull { SourceJson.text(it, "mid") == mid }
        val id = song?.let { SourceJson.text(it, "id") }
        if (count(detail, "code") != 0L || id == null || !numericId(id)) {
            throw IOException("QQ comment track identity unavailable")
        }
        return id
    }

    @JvmStatic
    fun netease(track: OnlineTrack, order: Order, page: Int, limit: Int,
                request: NeteaseRequest): OnlineMusicComments {
        checkPage(order, SORTED, page, limit)
        require(track.source() == "netease" && numericId(track.id()))
        val hot = order == Order.HOT
        val route = if (hot) "hotcomments" else "comments"
        val response = try {
            request.read("/weapi/v1/resource/$route/R_SO_4_${track.id()}",
                mapOf("rid" to track.id(), "limit" to "$limit", "offset" to "${(page - 1) * limit}", "total" to "true"))
        } catch (failure: OnlineMusicSource.LoginRequired) {
            throw IOException("NetEase public comments unavailable", failure)
        }
        val total = count(response, "total")
        val entries = rows(response, if (hot) "hotComments" else "comments", total, limit, ::neteaseEntry)
        val more = response.get(if (hot) "hasMore" else "more")?.let { it.isJsonPrimitive && it.asBoolean } == true
        return OnlineMusicComments(entries, total, more && entries.isNotEmpty())
    }

    @JvmStatic
    fun neteaseReplies(track: OnlineTrack, parent: Entry, cursor: String?, limit: Int, request: NeteaseRequest): ReplyPage {
        require(track.source() == "netease" && numericId(track.id()) && limit in 1..20)
        val root = requireNotNull(parent.replyKey)
        require(root == parent.id && numericId(root) && (cursor == null || numericId(cursor)))
        val data = try {
            SourceJson.`object`(request.read("/weapi/resource/comment/floor/get",
                mapOf("threadId" to "R_SO_4_${track.id()}", "parentCommentId" to root,
                    "time" to (cursor ?: "-1"), "limit" to "$limit")), "data")
        } catch (failure: OnlineMusicSource.LoginRequired) {
            throw IOException("NetEase public replies unavailable", failure)
        }
        val entries = rows(data, "comments", count(data, "totalCount"), limit) { neteaseEntry(it)?.copy(replyKey = null) }
        val next = value(data, "time")?.takeIf {
            data.get("hasMore")?.isJsonPrimitive == true && data.get("hasMore").asBoolean &&
                it != cursor && numericId(it) && entries.isNotEmpty()
        }
        return ReplyPage(entries, next)
    }

    private fun neteaseEntry(row: JsonObject): Entry? {
        val user = SourceJson.`object`(row, "user")
        val to = SourceJson.array(row, "beReplied").objects().firstOrNull()
            ?.let { value(SourceJson.`object`(it, "user"), "nickname") }
        return entry(row, "commentId", SourceJson.text(user, "nickname"), "content", date(row, "time", 2013, millis = true),
            count(row, "likedCount"), count(row, "replyCount") ?: count(SourceJson.`object`(row, "showFloorComment"), "replyCount"),
            avatar(user, "avatarUrl"), value(row, "commentId")?.takeIf(::numericId), to)
    }

    fun interface NeteaseRequest {
        @Throws(Exception::class)
        fun read(path: String, parameters: Map<String, String>): JsonObject
    }

    fun kugou(response: JsonObject, page: Int, limit: Int): OnlineMusicComments {
        if (count(response, "status") != 1L || count(response, "err_code") != 0L) {
            throw IOException("Kugou rejected comment request")
        }
        val total = count(response, "count")
        val reportedPage = count(response, "current_page")
        if (reportedPage != null && reportedPage != page.toLong()) throw IOException("Kugou comment page changed")
        val child = value(response, "childrenid")?.takeIf(::numericId)
        val entries = rows(response, "list", total, limit) { row -> kugouEntry(row, child) }
        val maxPage = count(response, "maxPage")
        val more = maxPage?.let { page < it } ?: (total != null && page * limit < total)
        return OnlineMusicComments(entries, total, more && entries.isNotEmpty())
    }

    fun kugouReplies(response: JsonObject, parent: Entry, page: Int, limit: Int): ReplyPage {
        val error = count(response, "err_code")
        if (count(response, "status") != 1L || value(response, "childrenid") != parent.replyKey ||
            value(response, "tid") != parent.id || count(response, "current_page") != page.toLong() ||
            error != 0L && error != 60102L) throw IOException("Kugou rejected comment replies")
        val total = count(response, "comments_num")
        val entries = rows(response, "list", total, limit) { row -> kugouEntry(row, null) }
        val next = (page + 1).toString().takeIf { page < 1000 && total != null && page * limit < total && entries.isNotEmpty() }
        return ReplyPage(entries, next)
    }

    private fun kugouEntry(row: JsonObject, child: String?): Entry? {
        val day = runCatching { LocalDate.parse(SourceJson.text(row, "addtime").take(10)) }.getOrNull()
            ?.takeIf { validDate(it, 2004) }
        val like = SourceJson.`object`(row, "like")
        return entry(row, "id", SourceJson.text(row, "user_name"), "content", day,
            count(like, "likenum") ?: count(like, "count"), count(row, "reply_num"), avatar(row, "user_pic"),
            child, value(SourceJson.`object`(row, "puser"), "user_name"))
    }

    fun checkPage(order: Order, supported: List<Order>, page: Int, limit: Int) {
        require(order in supported && page in 1..1000 && limit in 1..20) { "Invalid comment request" }
    }

    fun numericId(value: String): Boolean = value.matches(numberId)

    private fun rows(parent: JsonObject, key: String, total: Long?, limit: Int,
                     parse: (JsonObject) -> Entry?): List<Entry> {
        val value = parent.get(key)
        if (value?.isJsonArray != true) {
            if (total == 0L && (value == null || value.isJsonNull)) return emptyList()
            throw IOException("Music comment list missing")
        }
        val raw = value.asJsonArray
        val entries = raw.objects().mapNotNull(parse).distinctBy(Entry::id).take(limit).toList()
        if (!raw.isEmpty && entries.isEmpty()) throw IOException("Music comment entries are invalid")
        return entries
    }

    private fun JsonArray.objects(): Sequence<JsonObject> = asSequence().filter { it.isJsonObject }.map { it.asJsonObject }

    private fun entry(row: JsonObject, idKey: String, author: String, contentKey: String,
                      date: LocalDate?, likes: Long?, replies: Long?, avatar: String?, replyKey: String?,
                      replyingTo: String?): Entry? {
        val id = SourceJson.text(row, idKey)
        val content = plain(SourceJson.text(row, contentKey), 2000)
        if (id.isBlank() || id.length > 256 || content.isBlank()) return null
        return Entry(id, name(author), content, date, likes, replies, avatar, replyKey,
            replyingTo?.let(::name)?.takeIf(String::isNotBlank))
    }

    private fun avatar(row: JsonObject, key: String): String? = value(row, key)?.let {
        runCatching { OnlineArtwork.validatedAvatar(it).toString() }.getOrNull()
    }

    private fun name(text: String): String = plain(text, 160)
        .filter { Character.getType(it) != Character.FORMAT.toInt() }.replace('\n', ' ').trim()

    private fun value(row: JsonObject, key: String): String? = SourceJson.text(row, key).takeIf(String::isNotBlank)

    private fun plain(text: String, limit: Int): String {
        val cleaned = text.replace("\r\n", "\n").replace('\r', '\n')
            .filter { it == '\n' || !it.isISOControl() }.replace("§", "").trim()
        if (cleaned.length <= limit) return cleaned
        val end = if (cleaned[limit - 1].isHighSurrogate()) limit - 1 else limit
        return cleaned.take(end) + "…"
    }

    private fun date(row: JsonObject, key: String, firstYear: Int, millis: Boolean = false): LocalDate? =
        count(row, key)?.takeIf { it > 0 }?.let {
            runCatching { (if (millis) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it))
                .atZone(china).toLocalDate() }.getOrNull()
        }?.takeIf { validDate(it, firstYear) }

    private fun validDate(date: LocalDate, firstYear: Int): Boolean =
        date.year >= firstYear && !date.isAfter(LocalDate.now(china).plusDays(1))

    private fun count(row: JsonObject, key: String): Long? =
        SourceJson.text(row, key).toLongOrNull()?.takeIf { it >= 0 }
}
