package com.pianocompanion.progression

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.chord.ChordEngine
import kotlin.math.absoluteValue

/**
 * 和弦进行音频渲染器（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 将 [ProgressionInstance] 渲染为连续播放的 PCM 浮点采样。
 * 每个和弦按节拍时长发声，依次排列在时间轴上，构成完整的进行试听音频。
 *
 * 渲染流程：
 * 1. 每个和弦用 PianoToneSynthesizer 合成（柱式和弦，所有音同时发声）
 * 2. 按节拍间隔依次排列在时间轴上
 * 3. 软限幅防止叠加时削波
 * 4. 前导静音 + 尾部衰减
 *
 * @param synthesizer 音色合成器
 * @param sampleRate 采样率
 * @param tempoBpm 速度（拍/分钟），决定每个和弦的时长
 */
class ProgressionAudioBuilder(
    private val synthesizer: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE,
    private val tempoBpm: Int = 90
) {
    companion object {
        const val LEAD_SILENCE_MS = 300L
        const val TAIL_SILENCE_MS = 600L
        private const val SOFT_CLIP_K = 0.7f

        /**
         * 根据拍数和速度计算和弦持续时长（毫秒）。
         */
        fun chordDurationMs(beatsPerChord: Int, tempoBpm: Int): Long {
            val msPerBeat = 60_000L / tempoBpm
            return beatsPerChord * msPerBeat
        }
    }

    /**
     * 渲染完整进行。
     *
     * @param instance 和弦进行实例
     * @param velocity 力度（1-127）
     * @return PCM 浮点采样数组，值在 [-1.0, 1.0] 范围内
     */
    fun render(instance: ProgressionInstance, velocity: Int = 70): FloatArray {
        if (instance.chords.isEmpty()) return FloatArray(0)

        val beatsPerChord = instance.template.beatsPerChord
        val chordDurationMs = chordDurationMs(beatsPerChord, tempoBpm)

        // 为每个和弦合成采样
        val chordBuffers = instance.chords.map { chord ->
            val frequencies = ChordEngine.frequencies(chord.voicing)
            // 每个音符单独合成，然后混合到该和弦的缓冲区
            val noteBuffers = frequencies.map { freq ->
                synthesizer.synthesize(freq, chordDurationMs, velocity, Articulation.NONE)
            }
            mixNotes(noteBuffers)
        }

        // 计算时间轴排列
        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val chordSampleLengths = chordBuffers.map { it.size }
        val chordStartOffsets = mutableListOf<Int>()
        var currentOffset = leadSilenceSamples
        for (len in chordSampleLengths) {
            chordStartOffsets.add(currentOffset)
            currentOffset += len
        }

        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val totalLength = currentOffset + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 将每个和弦的缓冲区写入输出时间轴
        for ((i, buffer) in chordBuffers.withIndex()) {
            val offset = chordStartOffsets[i]
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
        }

        // 软限幅
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 渲染单个和弦的音频（用于逐和弦播放）。
     *
     * @param chord 进行中的和弦
     * @param beats 持续拍数
     * @param velocity 力度
     * @return PCM 浮点采样数组
     */
    fun renderSingleChord(
        chord: ProgressionChord,
        beats: Int = 4,
        velocity: Int = 70
    ): FloatArray {
        val durationMs = chordDurationMs(beats, tempoBpm)
        val frequencies = ChordEngine.frequencies(chord.voicing)
        val noteBuffers = frequencies.map { freq ->
            synthesizer.synthesize(freq, durationMs, velocity, Articulation.NONE)
        }
        val mixed = mixNotes(noteBuffers)

        // 加上前导和尾部静音
        val leadSilence = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilence = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val output = FloatArray(leadSilence + mixed.size + tailSilence)

        for (j in mixed.indices) {
            output[leadSilence + j] = softClip(mixed[j])
        }

        return output
    }

    /**
     * 将多个音符采样混合到一个缓冲区（简单叠加）。
     */
    private fun mixNotes(noteBuffers: List<FloatArray>): FloatArray {
        if (noteBuffers.isEmpty()) return FloatArray(0)
        val maxLen = noteBuffers.maxOf { it.size }
        val output = FloatArray(maxLen)
        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                output[j] += buffer[j]
            }
        }
        // 软限幅混合结果
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }
        return output
    }

    /**
     * 软限幅函数（tanh 近似）。
     */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + x.absoluteValue / SOFT_CLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    /**
     * 预估完整进行的渲染总时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(instance: ProgressionInstance): Long {
        val beatsPerChord = instance.template.beatsPerChord
        val chordMs = chordDurationMs(beatsPerChord, tempoBpm)
        val totalChordMs = chordMs * instance.chords.size
        return LEAD_SILENCE_MS + totalChordMs + TAIL_SILENCE_MS
    }
}
