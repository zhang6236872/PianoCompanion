package com.pianocompanion.circle

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils

/**
 * 五度圈音频渲染器（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 使用 [PianoToneSynthesizer] 将调内顺阶和弦序列或音阶渲染为 PCM 浮点采样，
 * 帮助用户「听到」一个调的色彩与和声功能。
 *
 * 支持两种渲染模式：
 * - [renderDiatonicChords]：依次播放 7 个顺阶三和弦（I ii iii IV V vi vii°），
 *   每个和弦的三个音同时发声（柱式和弦）。这是聆听调性色彩的经典方式。
 * - [renderScale]：依次播放音阶各音（上行）。
 *
 * 渲染流程：每个和弦/音符按顺序用 PianoToneSynthesizer 合成，
 * 按时间轴依次排列（和弦内叠加，和弦间不叠加），最后软限幅防削波，
 * 并添加前导静音与尾部静音。
 */
class CircleOfFifthsAudioBuilder(
    private val synthesizer: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val LEAD_SILENCE_MS = 200L
        const val TAIL_SILENCE_MS = 400L
        const val CHORD_DURATION_MS = 600L
        const val SCALE_NOTE_DURATION_MS = 400L
        private const val SOFT_CLIP_K = 0.85f
    }

    /**
     * 渲染调内顺阶三和弦序列（I → vii°，依次播放）。
     *
     * 每个和弦的三个 MIDI 音符同时发声（柱式叠加），和弦之间依次排列。
     *
     * @param key 调性
     * @param velocity 力度（1-127）
     */
    fun renderDiatonicChords(key: CircleKey, velocity: Int = 68): FloatArray {
        val chords = CircleOfFifthsEngine.diatonicChords(key)
        if (chords.isEmpty()) return FloatArray(0)

        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val chordSamples = (sampleRate * CHORD_DURATION_MS / 1000.0).toInt()

        // 为每个和弦合成柱式和弦缓冲区（三音叠加）
        val chordBuffers = chords.map { chord ->
            renderBlockedChord(chord.midiNotes, chordSamples, velocity)
        }

        val totalLength = leadSamples + chordBuffers.sumOf { it.size } + tailSamples
        val output = FloatArray(totalLength)

        var offset = leadSamples
        for (buffer in chordBuffers) {
            var j = 0
            while (j < buffer.size && offset + j < totalLength) {
                output[offset + j] = buffer[j]
                j++
            }
            offset += buffer.size
        }

        // 软限幅
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }
        return output
    }

    /**
     * 渲染单个柱式和弦：所有音符同时发声，采样叠加后软限幅。
     *
     * @param midiNotes 和弦 MIDI 音符列表
     * @param totalSamples 该和弦的总采样数（时值）
     * @param velocity 力度
     */
    private fun renderBlockedChord(
        midiNotes: List<Int>,
        totalSamples: Int,
        velocity: Int
    ): FloatArray {
        if (midiNotes.isEmpty() || totalSamples <= 0) return FloatArray(0)

        // 合成每个音符
        val noteBuffers = midiNotes.map { midi ->
            val freq = MusicUtils.midiToFrequency(midi)
            synthesizer.synthesize(freq, CHORD_DURATION_MS, velocity, Articulation.TENUTO)
        }

        val output = FloatArray(totalSamples)
        // 叠加所有音符
        for (buffer in noteBuffers) {
            var j = 0
            while (j < buffer.size && j < totalSamples) {
                output[j] += buffer[j]
                j++
            }
        }
        // 缩放防止叠加削波（三音叠加最大约 3 倍），再软限幅
        val scale = 1.0f / midiNotes.size.coerceAtLeast(1)
        for (i in output.indices) {
            output[i] = softClip(output[i] * scale * 1.6f)
        }
        return output
    }

    /**
     * 渲染上行音阶（依次播放各音）。
     *
     * @param key 调性
     * @param velocity 力度
     */
    fun renderScale(key: CircleKey, velocity: Int = 70): FloatArray {
        val notes = CircleOfFifthsEngine.scaleMidiNotes(key)
        if (notes.isEmpty()) return FloatArray(0)

        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val noteBuffers = notes.map { midi ->
            val freq = MusicUtils.midiToFrequency(midi)
            synthesizer.synthesize(freq, SCALE_NOTE_DURATION_MS, velocity, Articulation.TENUTO)
        }

        val totalLength = leadSamples + noteBuffers.sumOf { it.size } + tailSamples
        val output = FloatArray(totalLength)

        var offset = leadSamples
        for (buffer in noteBuffers) {
            var j = 0
            while (j < buffer.size && offset + j < totalLength) {
                output[offset + j] = buffer[j]
                j++
            }
            offset += buffer.size
        }

        for (i in output.indices) {
            output[i] = softClip(output[i])
        }
        return output
    }

    /**
     * 估算顺阶和弦序列渲染总时长（毫秒）。
     */
    fun estimateDiatonicChordsDurationMs(key: CircleKey): Long {
        val count = CircleOfFifthsEngine.diatonicChords(key).size
        return LEAD_SILENCE_MS + count * CHORD_DURATION_MS + TAIL_SILENCE_MS
    }

    /**
     * 估算上行音阶渲染总时长（毫秒）。
     */
    fun estimateScaleDurationMs(key: CircleKey): Long {
        val count = CircleOfFifthsEngine.scaleMidiNotes(key).size
        return LEAD_SILENCE_MS + count * SCALE_NOTE_DURATION_MS + TAIL_SILENCE_MS
    }

    /** 软限幅函数（tanh 近似）。 */
    private fun softClip(x: Float): Float {
        val absX = if (x < 0) -x else x
        return (x / (1.0f + absX / SOFT_CLIP_K)).coerceIn(-1.0f, 1.0f)
    }
}

/**
 * 五度圈播放模式。
 */
enum class CirclePlayMode(val displayName: String) {
    DIATONIC_CHORDS("顺阶和弦"),
    SCALE("音阶")
}
