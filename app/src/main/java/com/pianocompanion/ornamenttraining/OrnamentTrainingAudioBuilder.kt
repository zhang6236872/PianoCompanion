package com.pianocompanion.ornamenttraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 装饰音辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [OrnamentQuestion] 的装饰音音符序列渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 渲染流程：
 * 1. 装饰音的每个音符事件按顺序依次发声（legato 衔接，模拟装饰音的连贯性）
 * 2. 每个音符的实际 MIDI = 主音 + 半音偏移
 * 3. 带重音的音符（accent > 0）力度提升，模拟倚音的重音
 * 4. 音符序列按时轴依次排列（前导静音 + 序列 + 尾部静音）
 * 5. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class OrnamentTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（装饰音音符序列）。
     */
    fun render(question: OrnamentQuestion): FloatArray {
        return renderSequence(question.mainMidi, question.noteEvents)
    }

    /**
     * 将装饰音音符序列渲染为连续 PCM 采样。
     *
     * 音符按顺序依次发声（装饰音是连贯的单线条装饰，不需要和弦叠加）。
     *
     * @param mainMidi 主音 MIDI 音符号
     * @param noteEvents 音符事件序列（相对主音的半音偏移）
     * @param baseVelocity 基础力度（1-127）
     */
    fun renderSequence(
        mainMidi: Int,
        noteEvents: List<OrnamentNote>,
        baseVelocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (noteEvents.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 为每个音符渲染 PCM 采样
        val noteBuffers = noteEvents.map { note ->
            val midi = (mainMidi + note.semitoneOffset).coerceIn(MIN_MIDI, MAX_MIDI)
            // 重音提升力度（accent 0.0~1.0 → 力度 +0~+30）
            val velocity = (baseVelocity + (note.accent * ACCENT_VELOCITY_BOOST).toInt())
                .coerceAtMost(MAX_VELOCITY)
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = note.durationMs,
                velocity = velocity,
                articulation = Articulation.TENUTO
            )
        }

        // 顺序排列：前导静音 + 各音符依次 + 尾部静音
        val notesTotal = noteBuffers.sumOf { it.size }
        val totalLength = leadSilenceSamples + notesTotal + tailSilenceSamples

        val output = FloatArray(totalLength)
        var offset = leadSilenceSamples
        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
            offset += buffer.size
        }

        // 软限幅防止削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: OrnamentQuestion): Long {
        return LEAD_SILENCE_MS + question.sequenceDurationMs + TAIL_SILENCE_MS
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

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400L

        /** 基础力度。 */
        const val DEFAULT_VELOCITY = 72

        /** 最大力度。 */
        const val MAX_VELOCITY = 110

        /** 重音力度提升幅度。 */
        const val ACCENT_VELOCITY_BOOST = 30.0f

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108
    }
}
