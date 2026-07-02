package com.pianocompanion.cadence

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.chord.ChordEngine
import com.pianocompanion.chord.ChordVoicing
import com.pianocompanion.data.model.Articulation
import kotlin.math.absoluteValue

/**
 * 终止式音频渲染器（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 将 [CadenceInstance] 的和弦序列渲染为连续的 PCM 浮点采样。
 * 每个和弦以柱式和弦形式播放（所有音同时发声），和弦间有短暂间隔。
 *
 * 渲染流程：
 * 1. 对终止式中每个和弦，用 [PianoToneSynthesizer] 合成各音符的采样
 * 2. 将同一和弦内的音符采样叠加（柱式和弦混合）
 * 3. 将各和弦依次排列在时间轴上（和弦间有 [CHORD_GAP_MS] 间隔）
 * 4. 软限幅防止多音符叠加时削波
 * 5. 前导静音 + 尾部静音
 */
class CadenceAudioBuilder(
    private val synthesizer: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val LEAD_SILENCE_MS = 200L
        const val TAIL_SILENCE_MS = 500L

        /** 每个和弦的持续时长（毫秒）。 */
        const val CHORD_DURATION_MS = 1000L

        /** 和弦之间的间隔（毫秒），模拟手抬起再落下。 */
        const val CHORD_GAP_MS = 100L

        private const val SOFT_CLIP_K = 0.7f
    }

    /**
     * 渲染终止式音频。
     *
     * @param instance 终止式实例
     * @param velocity 力度（1-127），默认中等偏强
     * @return PCM 浮点采样数组，值在 [-1.0, 1.0] 范围内
     */
    fun render(instance: CadenceInstance, velocity: Int = 72): FloatArray {
        if (instance.steps.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = msToSamples(LEAD_SILENCE_MS)
        val tailSilenceSamples = msToSamples(TAIL_SILENCE_MS)
        val chordSamples = msToSamples(CHORD_DURATION_MS)
        val gapSamples = msToSamples(CHORD_GAP_MS)

        // 为每个和弦渲染柱式和弦采样
        val chordBuffers = instance.steps.map { step ->
            renderChord(step.voicing, velocity)
        }

        // 计算每个和弦在输出中的起始偏移
        val chordStartOffsets = chordBuffers.indices.map { i ->
            leadSilenceSamples + i * (chordSamples + gapSamples)
        }

        // 总长度 = 前导 + 所有和弦 + 最后间隔不需要 + 尾部
        val totalLength = chordStartOffsets.last() + chordSamples + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 将每个和弦的采样混合到输出时间轴
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
     * 渲染单个柱式和弦（所有音符同时发声）。
     */
    private fun renderChord(voicing: ChordVoicing, velocity: Int): FloatArray {
        if (voicing.midiNotes.isEmpty()) return FloatArray(0)

        val frequencies = ChordEngine.frequencies(voicing)
        val noteBuffers = frequencies.map { freq ->
            synthesizer.synthesize(freq, CHORD_DURATION_MS, velocity, Articulation.NONE)
        }

        val length = noteBuffers.maxOf { it.size }
        val output = FloatArray(length)

        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                output[j] += buffer[j]
            }
        }

        return output
    }

    /**
     * 估算渲染总时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(instance: CadenceInstance): Long {
        val chordCount = instance.steps.size
        return LEAD_SILENCE_MS +
            chordCount * CHORD_DURATION_MS +
            (chordCount - 1).coerceAtLeast(0) * CHORD_GAP_MS +
            TAIL_SILENCE_MS
    }

    private fun msToSamples(ms: Long): Int = (sampleRate * ms / 1000.0).toInt()

    private fun softClip(x: Float): Float {
        return (x / (1.0f + x.absoluteValue / SOFT_CLIP_K)).coerceIn(-1.0f, 1.0f)
    }
}
