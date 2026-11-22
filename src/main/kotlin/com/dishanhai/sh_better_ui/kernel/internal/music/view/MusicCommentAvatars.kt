// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineArtwork
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor

/** Visible avatars are loaded asynchronously; decoded images belong to the rendering view. */
internal class MusicCommentAvatars<T : AutoCloseable>(private val decode: (ByteArray) -> T) : AutoCloseable {
    private data class Slot<T>(var pending: Future<ByteArray>?, var image: T? = null)
    private val items = LinkedHashMap<String, Slot<T>>(32, .75f, true)
    private var worker: ScheduledThreadPoolExecutor? = null

    fun get(url: String?): T? {
        if (url == null) return null
        val slot = items[url] ?: run {
            if (items.size >= 64) {
                val oldest = items.entries.iterator()
                dispose(oldest.next().value)
                oldest.remove()
            }
            val pool = worker ?: ScheduledThreadPoolExecutor(3) { task ->
                Thread(task, "shb-comment-avatar").apply { isDaemon = true }
            }.apply { removeOnCancelPolicy = true }.also { worker = it }
            Slot<T>(pool.submit<ByteArray> { OnlineArtwork.downloadAvatar(url) }).also { items[url] = it }
        }
        val future = slot.pending
        if (future != null && future.isDone) {
            slot.pending = null
            slot.image = runCatching { decode(future.get()) }.getOrNull()
        }
        return slot.image
    }

    private fun dispose(slot: Slot<T>) {
        slot.pending?.cancel(true)
        slot.image?.close()
    }

    override fun close() {
        items.values.forEach(::dispose)
        items.clear()
        worker?.shutdownNow()
        worker = null
    }
}
