package com.pianocompanion.progressiontraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 和弦进行听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ProgressionQuestion] 的和弦进行渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 渲染流程：
 * 1. 进行中的每个和弦（3 个音）用 PianoToneSynthesizer 分别合成（柱式和弦 = 同时播放）
 * 2. 多个和弦按时间轴依次排列（每个和弦播放 CHORD_DURATION_MS 后间隔 CHORD_GAP_MS）
 * 3. 各和弦内部的多音混合在同一时间段叠加
 * 4. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class ProgressionTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（和弦进行）。
     */
    fun render(question: ProgressionQuestion): FloatArray {
        return renderProgression(question.chordProgression)
    }

    /**
     * 将和弦进行渲染为 PCM 采样。
     *
     * 每个和弦是柱式三和弦（3 音同时发声），多个和弦依次排列。
     *
     * @param chordProgression 各和弦的 MIDI 音符号列表
     * @param velocity 力度（1-127）
     */
    fun renderProgression(
        chordProgression: List<List<Int>>,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (chordProgression.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val chordDurationSamples = (sampleRate * CHORD_DURATION_MS / 1000.0).toInt()
        val chordGapSamples = (sampleRate * CHORD_GAP_MS / 1000.0).toInt()

        // 计算总长度
        val totalChordArea = chordProgression.size * chordDurationSamples +
            (chordProgression.size - 1) * chordGapSamples
        val totalLength = leadSilenceSamples + totalChordArea + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 逐和弦渲染并放置到时间轴上
        for ((chordIdx, midiNotes) in chordProgression.withIndex()) {
            val chordOffset = leadSilenceSamples +
                chordIdx * (chordDurationSamples + chordGapSamples)

            // 合成该和弦的每个音符
            val noteBuffers = midiNotes.map { midi ->
                synth.synthesize(
                    frequency = MusicUtils.midiToFrequency(midi),
                    durationMs = CHORD_DURATION_MS,
                    velocity = velocity,
                    articulation = Articulation.NONE
                )
            }

            // 柱式和弦：3 个音从 chordOffset 同时开始叠加
            for (buffer in noteBuffers) {
                for (j in buffer.indices) {
                    val outIdx = chordOffset + j
                    if (outIdx in output.indices) {
                        output[outIdx] += buffer[j]
                    }
                }
            }
        }

        // 软限幅（多音叠加可能削波）
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: ProgressionQuestion): Long {
        val chordCount = question.chordProgression.size
        return LEAD_SILENCE_MS +
            chordCount * CHORD_DURATION_MS +
            (chordCount - 1) * CHORD_GAP_MS +
            TAIL_SILENCE_MS
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

        /** 单个和弦持续时间（毫秒）。需要足够长让用户辨识和弦色彩。 */
        const val CHORD_DURATION_MS = 700L

        /** 和弦间隔（毫秒）。和弦间留出短暂的间隙让用户区分每个和弦。 */
        const val CHORD_GAP_MS = 150L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 72

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
