package com.pianocompanion.cadencetraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 终止式听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [CadenceQuestion] 的和弦进行渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 渲染流程：
 * 1. 每个和弦的每个音符用 PianoToneSynthesizer 合成（柱式和弦 = 所有音同时发声）
 * 2. 每个和弦渲染为一段 PCM 采样（内部时间轴混合）
 * 3. 多个和弦按时间轴依次排列（和弦之间有间隔）
 * 4. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class CadenceTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（和弦进行）。
     */
    fun render(question: CadenceQuestion): FloatArray {
        return renderProgression(question.chordProgression)
    }

    /**
     * 将和弦进行渲染为连续 PCM 采样。
     *
     * 每个和弦以柱式方式同时发声，和弦之间有间隔。
     *
     * @param chordProgression 和弦进行（每个元素为该和弦的 MIDI 音符列表）
     * @param velocity 力度（1-127）
     */
    fun renderProgression(
        chordProgression: List<List<Int>>,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (chordProgression.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val chordGapSamples = (sampleRate * CHORD_GAP_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 为每个和弦渲染 PCM 采样
        val chordBuffers = chordProgression.map { midiNotes ->
            renderChord(midiNotes, velocity)
        }

        // 计算各和弦在输出缓冲区中的起始偏移
        val chordStartOffsets = chordBuffers.indices.map { chordIndex ->
            val chordDurationSamples = chordBuffers[chordIndex].size
            // 偏移 = 前导静音 + 之前所有和弦的长度 + 间隔
            var offset = leadSilenceSamples
            for (i in 0 until chordIndex) {
                offset += chordBuffers[i].size + chordGapSamples
            }
            offset
        }

        // 总输出长度
        val lastChordEnd = chordStartOffsets.last() + chordBuffers.last().size
        val totalLength = lastChordEnd + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 时间轴混合：将每个和弦的采样叠加到输出缓冲区
        for ((i, buffer) in chordBuffers.withIndex()) {
            val offset = chordStartOffsets[i]
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
        }

        // 软限幅防止叠加后削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 渲染单个和弦（柱式 = 所有音同时发声）。
     */
    private fun renderChord(midiNotes: List<Int>, velocity: Int): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        // 为每个音符合成采样
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = CHORD_DURATION_MS,
                velocity = velocity,
                articulation = Articulation.NONE
            )
        }

        // 柱式：所有音同时起始，长度 = 最长音符的长度
        val maxLength = noteBuffers.maxOf { it.size }
        val output = FloatArray(maxLength)

        // 叠加所有音符
        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                output[j] += buffer[j]
            }
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: CadenceQuestion): Long {
        val chordCount = question.chordProgression.size
        val gaps = (chordCount - 1) * CHORD_GAP_MS
        return LEAD_SILENCE_MS + chordCount * CHORD_DURATION_MS + gaps + TAIL_SILENCE_MS
    }

    /**
     * 软限幅函数（tanh 近似）。
     * x / (1 + |x| / K) — 平滑地压缩大值到 [-1, 1] 范围。
     */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + x.absoluteValue / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 单个和弦持续时间（毫秒）。终止式需要足够时长听辨色彩变化。 */
        const val CHORD_DURATION_MS = 1200L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 和弦之间的间隔（毫秒），让用户分辨两个和弦的过渡。 */
        const val CHORD_GAP_MS = 150L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 500L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
