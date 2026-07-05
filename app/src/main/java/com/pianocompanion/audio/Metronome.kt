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
 *
 * 支持 [Subdivision] 细分模式：可在每个主拍之间插入更细的子拍点点击，
 * 用于练习八分音符、三连音、十六分音符等节奏。
 */
class Metronome(
    private val sampleRate: Int = 44100,
    private val beatSoundMs: Int = 50,
    private val beatFreq: Double = 1000.0,  // Hz, weak-beat click frequency
    private val accentFreq: Double = 1500.0, // Hz, downbeat click frequency
    private val subFreq: Double = 800.0      // Hz, subdivision click frequency
) {
    private var bpm: Int = 120
    private var beatsPerMeasure: Int = 4
    private var subdivision: Subdivision = Subdivision.QUARTER
    /** 当前子拍点在整个小节序列中的索引。 */
    private var clickIndex: Int = 0
    private var isPlaying: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    var onBeat: ((Int) -> Unit)? = null  // main beat index callback for UI

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            val pattern = ClickPatternGenerator.pattern(beatsPerMeasure, subdivision)
            if (pattern.isEmpty()) {
                handler.postDelayed(this, ClickPatternGenerator.measureDurationMs(bpm, beatsPerMeasure))
                return
            }
            val clickType = pattern[clickIndex % pattern.size]
            playClick(clickType)
            // 仅在主拍（含强拍）触发 UI 回调
            if (clickType != ClickType.SUB) {
                onBeat?.invoke(ClickPatternGenerator.beatIndexOf(clickIndex, subdivision))
            }
            clickIndex = (clickIndex + 1) % pattern.size
            // Schedule next sub-click
            val intervalMs = ClickPatternGenerator.subClickIntervalMs(bpm, subdivision)
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        clickIndex = 0
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

    fun getBeatsPerMeasure(): Int = beatsPerMeasure

    /**
     * 设置细分模式。播放中切换时立即在下一个子拍点生效。
     */
    fun setSubdivision(sub: Subdivision) {
        subdivision = sub
    }

    fun getSubdivision(): Subdivision = subdivision

    fun isPlaying(): Boolean = isPlaying

    private fun playClick(clickType: ClickType) {
        val (freq, amplitude, durationMs) = when (clickType) {
            ClickType.ACCENT -> Triple(accentFreq, 0.30, beatSoundMs)
            ClickType.BEAT -> Triple(beatFreq, 0.25, beatSoundMs)
            ClickType.SUB -> {
                // 子拍点：更短促、更低音量，避免在快速细分时产生长拖尾叠加
                val dur = beatSoundMs.coerceAtMost(
                    (ClickPatternGenerator.subClickIntervalMs(bpm, subdivision).toInt() - 5)
                        .coerceAtLeast(10)
                )
                Triple(subFreq, 0.15, dur)
            }
        }
        val numSamples = sampleRate * durationMs / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Envelope: quick attack, exponential decay
            val envelope = Math.exp(-t * 40.0)
            val sample = (sin(2.0 * PI * freq * t) * envelope * Short.MAX_VALUE * amplitude).toInt()
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
            }, durationMs.toLong() + 50)
        } catch (e: Exception) {
            // Ignore audio errors
        }
    }
}
