package com.pianocompanion.pitchtraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 绝对音高训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [PitchQuestion] 的单个 MIDI 音符渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 绝对音高训练与音程训练不同——这里只播放**一个**音符，让用户凭音色和频率识别音名。
 * 为了给用户足够时间辨识，单音持续时间较长（1.5 秒）。
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class PitchTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（单音）。
     */
    fun render(question: PitchQuestion): FloatArray {
        return renderNote(question.midiNote)
    }

    /**
     * 将单个 MIDI 音符渲染为 PCM 采样（含前导/尾部静音）。
     *
     * @param midiNote MIDI 音符号
     * @param velocity 力度（1-127）
     */
    fun renderNote(
        midiNote: Int,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        val buffer = synth.synthesize(
            frequency = MusicUtils.midiToFrequency(midiNote),
            durationMs = NOTE_DURATION_MS,
            velocity = velocity,
            articulation = Articulation.NONE
        )

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val totalLength = leadSilenceSamples + buffer.size + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 放置单音采样
        for (j in buffer.indices) {
            output[leadSilenceSamples + j] = softClip(buffer[j])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: PitchQuestion): Long {
        return LEAD_SILENCE_MS + NOTE_DURATION_MS + TAIL_SILENCE_MS
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

        /** 单音持续时长（毫秒，较长以便用户辨识绝对音高）。 */
        const val NOTE_DURATION_MS = 1500L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 500L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
