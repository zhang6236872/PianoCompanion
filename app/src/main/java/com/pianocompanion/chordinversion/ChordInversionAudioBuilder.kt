package com.pianocompanion.chordinversion

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 和弦转位听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ChordInversionQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 和弦转位听辨：**
 *
 * 不同转位的区别在于**最低音（bass note）**是和弦的哪个成员。转位的听辨完全依赖
 * 对低音的感知——当根音在低音时，和弦稳定完整；当三音/五音/七音在低音时，
 * 和弦色彩和稳定度发生变化。
 *
 * 渲染音频的结构：
 * 1. **前导静默**：短暂的准备时间。
 * 2. **柱式和弦**：所有音符同时发响，持续约 1 秒。
 * 3. **尾部静音**：自然衰减 + 收尾。
 *
 * **音色设计：**
 * - 每个音符使用加法合成（基频 + 3 阶谐波 + 指数衰减包络 + tanh 软限幅），
 *   模拟钢琴般的自然音色
 * - 低音音符略加强调（1.25 倍振幅），帮助感知低音位置
 * - 所有音符同时起音，模拟柱式和弦（block chord）的演奏方式
 *
 * @param sampleRate 采样率
 */
class ChordInversionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个和弦音事件（用于测试验证）。
     *
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param midi MIDI 音高
     * @param frequencyHz 频率（Hz）
     * @param amplitude 振幅（0.0-1.0）
     * @param isBass 是否为低音音符（最低音）
     * @param memberName 对应的和弦成员名
     */
    data class ChordNoteEvent(
        val onsetMs: Double,
        val durationMs: Double,
        val midi: Int,
        val frequencyHz: Double,
        val amplitude: Float,
        val isBass: Boolean,
        val memberName: String
    )

    /** 为题目渲染音频。 */
    fun render(question: ChordInversionQuestion): FloatArray {
        val events = buildChordNoteEvents(question)
        return renderEvents(events, question.chordDurationMs.toDouble())
    }

    /**
     * 构建题目和弦的全部音符事件。
     *
     * 所有音符同时起音（onsetMs = 0），构成柱式和弦。
     * 低音音符（最低音）获得略高的振幅。
     */
    fun buildChordNoteEvents(question: ChordInversionQuestion): List<ChordNoteEvent> {
        val notes = question.midiNotes
        val duration = question.chordDurationMs.toDouble()
        val bassMidi = notes.minOrNull() ?: return emptyList()
        val memberNames = question.chordType.memberNames
        // 计算转位后每个音符对应的成员名
        val inv = question.targetInversion.order
        val rotatedMembers = memberNames.drop(inv) + memberNames.take(inv)

        return notes.mapIndexed { index, midi ->
            val isBass = midi == bassMidi
            ChordNoteEvent(
                onsetMs = 0.0,
                durationMs = duration,
                midi = midi,
                frequencyHz = midiToFrequency(midi),
                amplitude = if (isBass) BASS_AMPLITUDE else NORMAL_AMPLITUDE,
                isBass = isBass,
                memberName = rotatedMembers.getOrElse(index) { "?" }
            )
        }
    }

    /** 计算 MIDI 音符数量。 */
    fun noteCount(question: ChordInversionQuestion): Int = question.midiNotes.size

    /** 计算音频总时长（毫秒，含前后静音）。 */
    fun estimateDurationMs(question: ChordInversionQuestion): Long {
        return (LEAD_SILENCE_MS + question.chordDurationMs + TAIL_SILENCE_MS).toLong()
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将和弦音符事件列表渲染为连续 PCM 采样。
     *
     * 所有音符使用加法合成音色（基频 + 谐波 + 指数衰减包络），
     * 同时起音模拟柱式和弦，tanh 软限幅防止削波。
     */
    fun renderEvents(events: List<ChordNoteEvent>, chordDurationMs: Double): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * (chordDurationMs + NOTE_TAIL_DECAY_MS) / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (event in events) {
            val noteSamples = (sampleRate * (event.durationMs + NOTE_TAIL_DECAY_MS) / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val noteWave = generatePianoTone(event.frequencyHz, noteSamples, event.amplitude)
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
                }
            }
        }

        // tanh 软限幅防止削波
        for (i in output.indices) {
            var sample = tanh(output[i].toDouble() * NORMALIZATION_FACTOR).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /**
     * 生成钢琴式音色（加法合成 + 指数衰减包络 + 软起音）。
     *
     * - 基频 + 3 阶谐波（递减振幅），模拟自然泛音
     * - 快速起音（2ms）+ 指数衰减，模拟钢琴击弦
     */
    private fun generatePianoTone(freq: Double, numSamples: Int, amplitude: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val attackSamples = (sampleRate * ATTACK_MS / 1000.0).toInt().coerceAtLeast(1)
        val decaySamples = numSamples.toDouble()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // 起音包络（线性上升）+ 指数衰减
            val attackEnv = if (i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                1.0
            }
            val decayEnv = exp(-i / (decaySamples * DECAY_TAU_RATIO))

            // 加法合成：基频 + 3 阶谐波
            val fundamental = sin(2.0 * PI * freq * t)
            val h2 = sin(2.0 * PI * freq * 2.0 * t) * HARMONIC_2_GAIN
            val h3 = sin(2.0 * PI * freq * 3.0 * t) * HARMONIC_3_GAIN
            val h4 = sin(2.0 * PI * freq * 4.0 * t) * HARMONIC_4_GAIN

            val sample = (fundamental + h2 + h3 + h4) * attackEnv * decayEnv * amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 音符尾部自然衰减时间（毫秒），超出 chordDurationMs 后继续衰减。 */
        const val NOTE_TAIL_DECAY_MS = 400.0

        /** 起音时间（毫秒）。 */
        const val ATTACK_MS = 3.0

        /** 衰减时间常数比例（相对于总音符长度）。 */
        const val DECAY_TAU_RATIO = 0.35

        /** 基频振幅。 */
        const val FUNDAMENTAL_GAIN = 1.0

        /** 第 2 谐波振幅。 */
        const val HARMONIC_2_GAIN = 0.35

        /** 第 3 谐波振幅。 */
        const val HARMONIC_3_GAIN = 0.15

        /** 第 4 谐波振幅。 */
        const val HARMONIC_4_GAIN = 0.08

        /** 普通音符振幅。 */
        const val NORMAL_AMPLITUDE = 0.35f

        /** 低音音符振幅（略高，帮助感知低音）。 */
        const val BASS_AMPLITUDE = 0.45f

        /** 归一化因子（多音叠加后调整 tanh 输入范围）。 */
        const val NORMALIZATION_FACTOR = 1.2

        /**
         * 将 MIDI 音高转换为频率（Hz）。
         *
         * 使用 A4=440Hz 标准：freq = 440 * 2^((midi - 69) / 12)
         */
        fun midiToFrequency(midi: Int): Double {
            return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        }
    }
}
