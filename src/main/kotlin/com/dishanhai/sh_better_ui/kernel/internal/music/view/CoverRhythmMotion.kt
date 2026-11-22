// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Integrates eased audio envelopes into a continuous phase for the artwork backdrop. */
internal class CoverRhythmMotion {
    data class Frame(val pulse: Double, val energy: Double, val phase: Double, val bass: Double) {
        val deformation: Double get() = .36 * pulse + .12 * bass + .12 * energy
    }

    private var session = Long.MIN_VALUE
    private var position = 0L
    private var pulse = 0.0
    private var energy = 0.0
    private var bass = 0.0
    private var bassFloor = 0.0
    private var bassPeak = .18
    private var phase = 0.0

    fun reset() {
        session = Long.MIN_VALUE
        clearAudio()
        phase = 0.0
    }

    @JvmOverloads
    fun sample(
        seconds: Double, sessionId: Long, positionMs: Long, playing: Boolean,
        staticMode: Boolean, onset: Float, loudness: Float, lowFrequency: Float = 0f,
    ): Frame {
        if (staticMode) {
            session = Long.MIN_VALUE
            clearAudio()
            return Frame(pulse, energy, phase, bass)
        }
        val dt = if (seconds.isFinite()) seconds.coerceIn(0.0, .1) else 0.0
        val discontinuity = session != sessionId ||
            abs(positionMs.toDouble() - position) > 360 + dt * 3000
        session = sessionId
        position = positionMs
        if (discontinuity) {
            clearAudio()
            return Frame(pulse, energy, phase, bass)
        }
        val previousSpeed = speed()
        pulse = approach(pulse, if (playing) level(onset) else 0.0, dt, .034, .26)
        energy = approach(energy, if (playing) level(loudness) else 0.0, dt, .18, .46)
        val low = if (playing) level(lowFrequency) else 0.0
        bassFloor = approach(bassFloor, low, dt, 3.2, .6)
        bassPeak = approach(bassPeak, low, dt, .045, 4.0).coerceAtLeast(bassFloor + .13)
        val accent = ((low - bassFloor) / (bassPeak - bassFloor)).coerceIn(0.0, 1.0).pow(1.4)
        bass = approach(bass, accent, dt, .055, .30)
        phase += (previousSpeed + speed()) * .5 * dt
        return Frame(pulse, energy, phase, bass)
    }

    // Bass is measured against a moving floor and peak, so a quiet bass accent can still be normalized to 1.
    private fun speed(): Double = .95 * pulse + .40 * energy + .20 * bass

    private fun clearAudio() {
        pulse = 0.0
        energy = 0.0
        bass = 0.0
        bassFloor = 0.0
        bassPeak = .18
    }

    private fun level(value: Float): Double =
        if (value.isFinite()) value.toDouble().coerceIn(0.0, 1.0) else 0.0

    private fun approach(value: Double, target: Double, seconds: Double, attack: Double, release: Double): Double {
        val tau = if (target > value) attack else release
        val next = target + (value - target) * exp(-seconds / tau)
        return if (next < .001) 0.0 else next
    }
}
