// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Entry
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.ReplyPage
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack
import java.util.concurrent.CompletableFuture
import net.minecraft.network.chat.Component

/** UI-thread state for one public discussion, with ownership of its pending request. */
internal class MusicCommentBrowser(private val player: OnlineMusicPlayer) {
    private companion object {
        const val REPLIES_PER_REVEAL = 3
    }

    data class State(val track: OnlineTrack, val order: Order, val page: Int,
                     val result: OnlineMusicComments? = null, val failed: Boolean = false,
                     val request: Boolean = false)
    data class Thread(val parent: Entry, val entries: List<Entry> = emptyList(),
                      val next: String? = null, val loading: Boolean = true, val failed: Boolean = false,
                      val visibleCount: Int = 0) {
        val hasBufferedReplies: Boolean get() = visibleCount < entries.size
    }
    data class Line(val entry: Entry?, val nested: Boolean = false, val status: String? = null,
                    val action: String? = null, val actionEntry: Entry? = null) {
        fun actionLabel(): Component {
            val key = requireNotNull(action)
            return if (key == "view_replies_count") MusicCommentsPanel.label(key, requireNotNull(entry?.replies))
            else MusicCommentsPanel.label(key)
        }
    }

    var state: State? = null
        private set
    var orders: List<Order> = emptyList()
        private set
    private var pending: CompletableFuture<OnlineMusicComments>? = null
    private var pendingReplies: CompletableFuture<ReplyPage>? = null
    var thread: Thread? = null
        private set
    val loading: Boolean get() = pending != null

    fun lines(): List<Line> = buildList {
        val expanded = thread
        for (entry in state?.result?.entries.orEmpty()) {
            val opened = expanded?.parent?.id == entry.id
            val action = when {
                opened -> "hide_replies"
                entry.replyKey == null || entry.replies == 0L -> null
                entry.replies == null -> "view_replies"
                else -> "view_replies_count"
            }
            add(Line(entry, action = action, actionEntry = entry.takeIf { action != null }))
            if (opened) {
                for (index in 0 until expanded.visibleCount) add(Line(expanded.entries[index], nested = true))
                when {
                    expanded.loading -> add(Line(null, nested = true, status = "loading"))
                    expanded.failed -> add(Line(null, nested = true, action = "retry_replies"))
                    expanded.hasBufferedReplies || expanded.next != null -> add(Line(null, nested = true, action = "more_replies"))
                    expanded.entries.isEmpty() -> add(Line(null, nested = true, status = "empty_replies"))
                }
            }
        }
    }

    fun activate(line: Line) {
        if (line.action == null) return
        val parent = line.actionEntry
        if (parent != null) toggleReplies(parent) else showMoreReplies()
    }

    fun supports(track: OnlineTrack?): Boolean = track != null && player.commentOrders(track.source()).isNotEmpty()

    fun open(track: OnlineTrack) {
        val available = player.commentOrders(track.source()).distinct()
        if (available.isEmpty()) return
        clear()
        orders = available
        state = State(track, available.first(), 1)
        load(1)
    }

    fun select(order: Order) {
        val current = state ?: return
        if (order == current.order || order !in orders) return
        state = current.copy(order = order)
        load(1)
    }

    fun load(page: Int) {
        val current = state ?: return
        if (page !in 1..1000) return
        cancel()
        state = State(current.track, current.order, page)
        pending = try {
            player.comments(current.track, current.order, page)
        } catch (_: RuntimeException) {
            state = state?.copy(failed = true)
            null
        }
    }

    fun poll() {
        val current = state ?: return
        if (current.request) load(current.page)
        pollReplies()
        val request = pending ?: return
        if (!request.isDone) return
        pending = null
        state = try {
            state?.copy(result = request.join())
        } catch (_: RuntimeException) {
            state?.copy(failed = true)
        }
    }

    fun toggleReplies(entry: Entry) {
        val collapse = thread?.parent?.id == entry.id
        clearReplies()
        if (!collapse && entry.replyKey != null && entry.replies != 0L) {
            thread = Thread(entry)
            showMoreReplies()
        }
    }

    private fun showMoreReplies() {
        val current = thread ?: return
        val song = state?.track ?: return
        if (pendingReplies != null) return
        if (current.hasBufferedReplies) {
            thread = current.copy(visibleCount = minOf(current.visibleCount + REPLIES_PER_REVEAL, current.entries.size))
            return
        }
        if (!current.failed && current.entries.isNotEmpty() && current.next == null) return
        thread = current.copy(loading = true, failed = false)
        pendingReplies = try {
            player.commentReplies(song, current.parent, current.next)
        } catch (_: RuntimeException) {
            thread = current.copy(loading = false, failed = true)
            null
        }
    }

    private fun pollReplies() {
        val request = pendingReplies?.takeIf { it.isDone } ?: return
        val current = thread ?: return
        pendingReplies = null
        thread = try {
            val page = request.join()
            val knownIds = current.entries.mapTo(HashSet(), Entry::id)
            val additions = page.entries.filter { knownIds.add(it.id) }.sortedWith(compareByDescending(Entry::likes))
            val combined = current.entries + additions
            current.copy(
                entries = combined,
                next = page.nextCursor?.takeIf { it != current.next && additions.isNotEmpty() && combined.size < 1000 },
                loading = false,
                visibleCount = minOf(current.visibleCount + REPLIES_PER_REVEAL, combined.size),
            )
        } catch (_: RuntimeException) {
            current.copy(loading = false, failed = true)
        }
    }

    private fun clearReplies() {
        pendingReplies?.cancel(true)
        pendingReplies = null
        thread = null
    }

    fun bookmark(): State? = state?.let { it.copy(request = it.request || loading) }

    fun restore(bookmark: State) {
        clear()
        val available = player.commentOrders(bookmark.track.source()).distinct()
        if (bookmark.order !in available) return
        orders = available
        state = bookmark
    }

    fun suspend() {
        state = bookmark()
        cancel()
    }

    fun clear() {
        cancel()
        state = null
        orders = emptyList()
    }

    private fun cancel() {
        clearReplies()
        pending?.cancel(true)
        pending = null
    }
}
