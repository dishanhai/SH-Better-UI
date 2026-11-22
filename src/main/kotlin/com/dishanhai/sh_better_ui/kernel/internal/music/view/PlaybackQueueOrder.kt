// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

/** Keeps a queue's original order while its live order is shuffled in place. */
internal class PlaybackQueueOrder<T>(
    tracks: List<T>,
    private val key: (T) -> String,
    originalKeys: List<String>? = null,
) : ArrayList<T>(tracks) {
    @Volatile
    var originalOrder: List<String>? = originalKeys
        private set

    fun shuffleFrom(active: Int, pending: Int): Int {
        if (active !in indices || size < 2) return active
        if (originalOrder == null) originalOrder = map(key)
        val pendingEnd = (active + 1 + pending).coerceAtMost(size)
        val rest = (subList(pendingEnd, size) + subList(0, active)).shuffled()
        val ordered = subList(active, pendingEnd).toList() + rest
        ordered.forEachIndexed { index, track -> this[index] = track }
        return 0
    }

    fun restoreFrom(active: Int, pending: Int): Int {
        val original = originalOrder ?: return active
        if (active !in indices) return active
        originalOrder = null
        val pendingEnd = (active + 1 + pending).coerceAtMost(size)
        val explicit = subList(active + 1, pendingEnd).toList()
        val positions = original.withIndex().groupBy({ it.value }, { it.index })
            .mapValues { ArrayDeque(it.value) }
        val order = indices.filter { it <= active || it >= pendingEnd }.map { index ->
            IndexedValue(index, positions[key(this[index])]?.removeFirstOrNull() ?: Int.MAX_VALUE)
        }.sortedBy { it.value }
        val trackIndex = order.indexOfFirst { it.index == active }
        val ordered = order.map { this[it.index] }.toMutableList()
        ordered.addAll(trackIndex + 1, explicit)
        ordered.forEachIndexed { index, track -> this[index] = track }
        return trackIndex
    }
}
