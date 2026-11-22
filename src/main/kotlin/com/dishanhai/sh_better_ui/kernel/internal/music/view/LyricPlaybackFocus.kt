// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.exp

/** Playback-relative focus, independent of scroll position, wrapping and viewport size. */
object LyricPlaybackFocus {
    @JvmStatic
    fun blur(upcomingOrder: Int, untilStartMs: Double, inspection: Double): Double {
        if (upcomingOrder <= 0 || !untilStartMs.isFinite() || untilStartMs <= 0) return 0.0
        val depth = .30 + .70 * smooth((upcomingOrder - 1) / 4.0)
        return depth * smooth(untilStartMs / 1800.0) * (1 - inspection.coerceIn(0.0, 1.0))
    }

    private fun smooth(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Smooths discrete focus changes and only retains the lines that were drawn last frame. */
    class Transition {
        private var previous = IdentityHashMap<Any, Double>()
        private var current = IdentityHashMap<Any, Double>()
        private var lastFrame = Long.MIN_VALUE
        private var elapsedMs = 0.0

        fun beginFrame(nowMs: Long) {
            elapsedMs = if (lastFrame == Long.MIN_VALUE) 0.0 else (nowMs - lastFrame).coerceAtLeast(0).toDouble()
            lastFrame = nowMs
            val spare = previous
            previous = current
            current = spare
            current.clear()
        }

        fun sample(line: Any, target: Double): Double {
            val start = previous[line] ?: target
            val timeConstant = if (target < start) 55.0 else 105.0
            val next = target + (start - target) * exp(-elapsedMs / timeConstant)
            val value = if (abs(next - target) < .003) target else next
            current[line] = value
            return value
        }

        fun reset() {
            previous.clear()
            current.clear()
            lastFrame = Long.MIN_VALUE
        }
    }
}
