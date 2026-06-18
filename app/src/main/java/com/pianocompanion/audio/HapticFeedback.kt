package com.pianocompanion.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback manager for practice sessions.
 * Provides different vibration patterns for correct/wrong/missing notes.
 * Respects user's haptic feedback setting.
 */
class HapticFeedback(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled: Boolean = true

    /** Short gentle tick for correct notes */
    fun correctNote() {
        if (!enabled) return
        vibrate(durationMs = 15, amplitude = 80)
    }

    /** Double buzz for wrong pitch */
    fun wrongPitch() {
        if (!enabled) return
        vibratePattern(longArrayOf(0, 40, 60, 40), intArrayOf(0, 180, 0, 180))
    }

    /** Single strong pulse for extra notes */
    fun extraNote() {
        if (!enabled) return
        vibrate(durationMs = 30, amplitude = 200)
    }

    /** Long soft pulse for missing notes */
    fun missingNote() {
        if (!enabled) return
        vibrate(durationMs = 50, amplitude = 120)
    }

    /** Quick success vibration at end of practice */
    fun practiceComplete() {
        if (!enabled) return
        vibratePattern(
            timings = longArrayOf(0, 30, 50, 30, 50, 60),
            amplitudes = intArrayOf(0, 100, 0, 100, 0, 200)
        )
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val safeAmplitudes = amplitudes.map { it.coerceIn(0, 255) }.toIntArray()
            v.vibrate(VibrationEffect.createWaveform(timings, safeAmplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }
}
