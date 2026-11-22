// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

/** Projects the known playback order without guessing the next shuffle or radio batch. */
internal object UpcomingQueueOrder {
    data class Sections(val next: List<Int>, val radio: List<Int>)

    @JvmStatic
    fun indices(size: Int, active: Int, pending: Int, repeatOne: Boolean, repeatAll: Boolean, radio: Boolean): Sections {
        if (active !in 0 until size) return Sections(emptyList(), emptyList())
        val first = active + 1
        val pendingEnd = first + pending.coerceIn(0, size - first)
        return when {
            radio -> Sections((first until pendingEnd).toList(), (pendingEnd until size).toList())
            repeatOne -> Sections((first until pendingEnd).toList(), emptyList())
            repeatAll -> Sections((first until size).toList() + (0 until active), emptyList())
            else -> Sections((first until size).toList(), emptyList())
        }
    }
}
