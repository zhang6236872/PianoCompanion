package com.pianocompanion.notation

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.tanh

/**
 * 识谱训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将单个 [NoteReadingQuestion] 的音符转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，用户答题后可以听到正确音符的音高。
 *
 * @param synth 音色合成器实例
 */
class NoteReadingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {

    /**
     * 为题目渲染单个音符的音频。
     *
     * @param question 识谱训练题目
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(question: NoteReadingQuestion): FloatArray {
        return renderNote(question.midiNote)
    }

    /**
     * 渲染单个 MIDI 音符的音频（前导静音 + 音符 + 尾部静音）。
     */
    fun renderNote(midi: Int): FloatArray {
        val noteBuf = synth.synthesize(
            frequency = MusicUtils.midiToFrequency(midi),
            durationMs = NOTE_DURATION_MS,
            velocity = DEFAULT_VELOCITY
        )

        val totalSamples = SILENCE_LEAD_SAMPLES + noteBuf.size + SILENCE_TAIL_SAMPLES
        val output = FloatArray(totalSamples)

        // 将音符采样写入输出缓冲区（偏移前导静音）
        for (i in noteBuf.indices) {
            output[SILENCE_LEAD_SAMPLES + i] = noteBuf[i]
        }

        return softLimit(output)
    }

    /**
     * 预估渲染时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(midi: Int = 60): Long {
        return SILENCE_LEAD_MS + NOTE_DURATION_MS + SILENCE_TAIL_MS
    }

    /**
     * 软限幅：使用 tanh 近似将输出限制在 [-1.0, 1.0]，
     * 同时保持低音量信号的线性度。
     */
    private fun softLimit(input: FloatArray): FloatArray {
        val k = SOFTCLIP_K
        return FloatArray(input.size) { i ->
            val x = input[i]
            (x / (1.0f + kotlin.math.abs(x) / k)).coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 800L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 72

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.5f

        /** 前导静音（毫秒）。 */
        const val SILENCE_LEAD_MS = 100L

        /** 尾部静音（毫秒）。 */
        const val SILENCE_TAIL_MS = 300L

        /** 前导静音采样数。 */
        val SILENCE_LEAD_SAMPLES = (SAMPLE_RATE * SILENCE_LEAD_MS / 1000.0).toInt()

        /** 尾部静音采样数。 */
        val SILENCE_TAIL_SAMPLES = (SAMPLE_RATE * SILENCE_TAIL_MS / 1000.0).toInt()
    }
}
