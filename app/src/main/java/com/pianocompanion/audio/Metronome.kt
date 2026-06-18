package com.pianocompanion.audio

import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/**
 * Simple metronome using AudioTrack for low-latency click sounds.
 * Generates a short sine wave beep at each beat.
 */
class Metronome(
    private val sampleRate: Int = 44100,
    private val beatSoundMs: Int = 50,
    private val beatFreq: Double = 1000.0,  // Hz, click frequency
    private val accentFreq: Double = 1500.0 // Hz, downbeat frequency
) {
    private var bpm: Int = 120
    private var beatsPerMeasure: Int = 4
    private var currentBeat: Int = 0
    private var isPlaying: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    var onBeat: ((Int) -> Unit)? = null  // beat index callback for UI

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            playClick(currentBeat == 0)
            onBeat?.invoke(currentBeat)
            currentBeat = (currentBeat + 1) % beatsPerMeasure
            // Schedule next beat
            val intervalMs = (60_000L / bpm)
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        currentBeat = 0
        handler.post(clickRunnable)
    }

    fun stop() {
        isPlaying = false
        handler.removeCallbacks(clickRunnable)
    }

    fun setBpm(newBpm: Int) {
        bpm = newBpm.coerceIn(40, 240)
    }

    fun getBpm(): Int = bpm

    fun setBeatsPerMeasure(beats: Int) {
        beatsPerMeasure = beats.coerceIn(1, 12)
    }

    fun isPlaying(): Boolean = isPlaying

    private fun playClick(isAccent: Boolean) {
        val freq = if (isAccent) accentFreq else beatFreq
        val numSamples = sampleRate * beatSoundMs / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Envelope: quick attack, exponential decay
            val envelope = Math.exp(-t * 40.0)
            val sample = (sin(2.0 * PI * freq * t) * envelope * Short.MAX_VALUE * 0.3).toInt()
            buffer[i] = sample.toShort()
        }

        try {
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            track.write(buffer, 0, buffer.size)
            track.play()
            // Release after playback
            Handler(Looper.getMainLooper()).postDelayed({
                track.stop()
                track.release()
            }, beatSoundMs.toLong() + 50)
        } catch (e: Exception) {
            // Ignore audio errors
        }
    }
}
