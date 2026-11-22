// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music

import java.time.LocalDate

/** Public, read-only discussion on a provider's own track. */
class OnlineMusicComments(entries: List<Entry>, val total: Long?, val hasMore: Boolean) {
    /** The count, when available, is for the selected order. */
    val entries: List<Entry> = java.util.List.copyOf(entries)

    enum class Order { DEFAULT, HOT, LATEST }

    /** Null metadata was omitted by the provider. */
    data class Entry @JvmOverloads constructor(
        val id: String,
        val author: String,
        val content: String,
        val date: LocalDate?,
        val likes: Long?,
        val replies: Long?,
        val avatar: String? = null,
        /** Opaque source key for retrieving this entry's replies. */
        val replyKey: String? = null,
        val replyingTo: String? = null,
    )

    class ReplyPage(entries: List<Entry>, val nextCursor: String?) {
        val entries: List<Entry> = java.util.List.copyOf(entries)
    }
}
