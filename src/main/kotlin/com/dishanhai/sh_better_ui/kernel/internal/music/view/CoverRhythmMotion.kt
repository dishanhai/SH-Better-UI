// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Continuous artwork flow driven by playback, audio intensity and visible lyric movement. */
internal class CoverRhythmMotion {
    data class Frame(val pulse: Double, val energy: Double, val phase: Double, val bass: Double, val deformation: Double)

    private var session = Long.MIN_VALUE
    private var position = 0L
    private var seekStamp = 0L
    private var audioHold = 0.0
    private var pulse = 0.0
    private var energy = 0.0
    private var bass = 0.0
    private var bassFloor = 0.0
    private var bassPeak = .18
    private var ambientSpeed = 0.0
    private var lyricSwell = 0.0
    private var phase = 0.0

    fun reset() {
        session = Long.MIN_VALUE
        seekStamp = 0L
        audioHold = 0.0
        clearAudio()
        ambientSpeed = 0.0
        lyricSwell = 0.0
        phase = 0.0
    }

    @JvmOverloads
    fun sample(
        seconds: Double, sessionId: Long, positionMs: Long, playing: Boolean,
        staticMode: Boolean, onset: Float, loudness: Float, lowFrequency: Float = 0f,
        seekNanos: Long = 0L, lyricScrollPixels: Double = 0.0,
    ): Frame {
        if (staticMode) {
            session = Long.MIN_VALUE
            clearAudio()
            lyricSwell = 0.0
            return frame()
        }
        val dt = if (seconds.isFinite()) seconds.coerceIn(0.0, .1) else 0.0
        val sessionChanged = session != sessionId
        val jumped = seekNanos != seekStamp && seekNanos != 0L ||
            !sessionChanged && abs(positionMs.toDouble() - position) > 360 + dt * 3000
        session = sessionId
        position = positionMs
        seekStamp = seekNanos
        val ambientTarget = if (playing) .23 else .055
        if (sessionChanged || jumped) {
            clearAudio()
            lyricSwell = 0.0
            audioHold = if (jumped) .18 else 0.0
            if (sessionChanged) ambientSpeed = ambientTarget
            return frame()
        }
        val previousSpeed = speed()
        ambientSpeed = approach(ambientSpeed, ambientTarget, dt, .22, .16)
        audioHold = (audioHold - dt).coerceAtLeast(0.0)
        val audioReady = playing && audioHold == 0.0
        pulse = approach(pulse, if (audioReady) level(onset) else 0.0, dt, .034, if (playing) .26 else .10)
        energy = approach(energy, if (audioReady) level(loudness) else 0.0, dt, .18, if (playing) .46 else .13)
        val low = if (audioReady) level(lowFrequency) else 0.0
        bassFloor = approach(bassFloor, low, dt, 3.2, .6)
        bassPeak = approach(bassPeak, low, dt, .045, 4.0).coerceAtLeast(bassFloor + .13)
        val accent = ((low - bassFloor) / (bassPeak - bassFloor)).coerceIn(0.0, 1.0).pow(1.4)
        bass = approach(bass, accent, dt, .055, if (playing) .30 else .12)
        val scroll = if (dt > 0 && lyricScrollPixels.isFinite()) {
            (abs(lyricScrollPixels) / (dt * 440.0)).coerceIn(0.0, 1.0)
        } else 0.0
        lyricSwell = approach(lyricSwell, scroll * if (playing) .075 else .035, dt, .06, .20)
        phase += (previousSpeed + speed()) * .5 * dt
        return frame()
    }

    // Relative onsets and bass can peak even in quiet passages. Overall loudness gates their visual response.
    private fun intensity(): Double {
        val level = ((energy - .22) / .58).coerceIn(0.0, 1.0)
        return level * level * (3.0 - 2.0 * level)
    }

    private fun speed(): Double = ambientSpeed + intensity() * (.65 * pulse + .21 * energy + .16 * bass)

    private fun frame(): Frame = Frame(pulse, energy, phase, bass,
        intensity() * (.43 * pulse + .13 * energy + .14 * bass) + lyricSwell)

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
