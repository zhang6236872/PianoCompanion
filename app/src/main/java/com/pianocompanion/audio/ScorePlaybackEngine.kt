package com.pianocompanion.audio

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.util.MusicUtils

/**
 * 乐谱播放引擎：将 [Score] 转换为完整 PCM 音频缓冲区（FloatArray）。
 *
 * 遍历乐谱中每个音符，使用 [PianoToneSynthesizer] 合成音色，并按时间轴
 * 将各音符采样混合（叠加）到统一输出缓冲区中。支持：
 * - 和弦（同时发声的多音符自动混合）
 * - 力度（velocity → 振幅）
 * - 演奏法（staccato/accent 等影响时值和力度）
 * - 多声部（treble + bass 同时混合）
 * - 速度控制（全局缩放音符时间位置和时值）
 *
 * 纯 Kotlin，无 Android 依赖，可完全单测。
 *
 * @param sampleRate 采样率（Hz）
 * @param synthesizer 音色合成器实例（可注入以实现 mock 测试）
 */
class ScorePlaybackEngine(
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE,
    private val synthesizer: PianoToneSynthesizer = PianoToneSynthesizer(sampleRate)
) {

    /**
     * 计算乐谱的总时长（毫秒），包含最后一个音符的完整延续。
     */
    fun totalDurationMs(score: Score): Long {
        if (score.notes.isEmpty()) return 0L
        return score.notes.maxOf { it.startTime + it.duration }
    }

    /**
     * 将完整乐谱合成为 PCM 音频缓冲区。
     *
     * @param score 乐谱
     * @param tempoBpm 播放速度（BPM），null 时使用乐谱自带速度
     * @param leadSilenceMs 开头静音时间（毫秒），给用户准备时间
     @param trailSilenceMs 结尾静音时间（毫秒），让最后一个音符自然衰减
     * @return FloatArray PCM 采样数据
     */
    fun render(
        score: Score,
        tempoBpm: Int? = null,
        leadSilenceMs: Long = DEFAULT_LEAD_SILENCE_MS,
        trailSilenceMs: Long = DEFAULT_TRAIL_SILENCE_MS
    ): FloatArray {
        if (score.notes.isEmpty()) return FloatArray(0)

        val effectiveTempo = tempoBpm ?: score.tempo
        val tempoScale = score.tempo.toDouble() / effectiveTempo.toDouble()

        // 缩放后的音符时间轴
        val scaledNotes = score.notes.map { note ->
            ScaledNote(
                startTimeMs = (note.startTime * tempoScale).toLong() + leadSilenceMs,
                durationMs = (note.duration * tempoScale).toLong(),
                note = note
            )
        }

        val scoreDurationMs = scaledNotes.maxOf { it.startTimeMs + it.durationMs }
        val totalSamples = msToSamples(scoreDurationMs + trailSilenceMs)

        val output = FloatArray(totalSamples)

        for (scaled in scaledNotes) {
            // 跳过无效音符（力度为 0 或无音高）
            if (scaled.note.midiNumber <= 0 || scaled.note.velocity <= 0) continue

            val tone = synthesizer.synthesize(
                frequency = MusicUtils.midiToFrequency(scaled.note.midiNumber),
                durationMs = scaled.durationMs,
                velocity = scaled.note.velocity,
                articulation = scaled.note.articulation
            )

            val offset = msToSamples(scaled.startTimeMs)

            // 混合到输出缓冲区（叠加 + 软限幅防止削波）
            for (j in tone.indices) {
                val outIdx = offset + j
                if (outIdx >= totalSamples) break
                output[outIdx] = softClip(output[outIdx] + tone[j])
            }
        }

        return output
    }

    /**
     * 将毫秒转换为采样数。
     */
    fun msToSamples(ms: Long): Int = (sampleRate * ms / 1000.0).toInt()

    /**
     * 将采样数转换为毫秒。
     */
    fun samplesToMs(samples: Int): Long = (samples.toLong() * 1000 / sampleRate)

    /**
     * 软限幅函数（tanh 近似），防止多音符叠加时削波。
     * 输入可能 > 1.0，输出始终在 [-1.0, 1.0]。
     */
    private fun softClip(x: Float): Float {
        // tanh 近似: x / (1 + |x|/K)，K=0.8
        val k = 0.8f
        return (x / (1f + kotlin.math.abs(x) / k))
    }

    /**
     * 内部数据类：缩放后的音符时间信息。
     */
    private data class ScaledNote(
        val startTimeMs: Long,
        val durationMs: Long,
        val note: ScoreNote
    )

    companion object {
        const val DEFAULT_LEAD_SILENCE_MS = 200L
        const val DEFAULT_TRAIL_SILENCE_MS = 500L
    }
}
