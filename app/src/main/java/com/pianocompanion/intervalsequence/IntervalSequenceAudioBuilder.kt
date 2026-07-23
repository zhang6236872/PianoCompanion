package com.pianocompanion.intervalsequence

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 音程序列记忆训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [IntervalSequenceQuestion] 中的旋律线渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **渲染原理：**
 * 1. 从题目的 MIDI 序列计算每个音符的频率；
 * 2. 每个音符渲染为复合音色（基频 + 3 阶谐波 + 指数衰减包络 + tanh 软限幅）；
 * 3. 音符按顺序排列，各音符间留 [gapMs] 间隔；
 * 4. 整条旋律重复播放两次，便于巩固记忆。
 *
 * @param sampleRate 采样率
 */
class IntervalSequenceAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件（用于测试验证）。
     *
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param frequencyHz 基频（Hz）
     * @param midi MIDI 值
     */
    data class NoteEvent(
        val onsetMs: Double,
        val durationMs: Double,
        val frequencyHz: Double,
        val midi: Int
    )

    /** 为题目渲染音频。 */
    fun render(question: IntervalSequenceQuestion): FloatArray {
        val events = buildNoteEvents(question)
        val musicMs = estimateMusicMs(question)
        return renderNotes(events, musicMs)
    }

    /**
     * 构建全部音符事件（含重复播放）。
     */
    fun buildNoteEvents(question: IntervalSequenceQuestion): List<NoteEvent> {
        val noteDurationMs = question.difficulty.noteDurationMs
        val gapMs = question.difficulty.gapMs
        val midiNotes = question.midiNotes

        val events = mutableListOf<NoteEvent>()
        val noteSpacing = noteDurationMs + gapMs

        for (rep in 0 until REPEAT_COUNT) {
            val repBaseMs = rep * (midiNotes.size * noteSpacing + GAP_BETWEEN_REPETITIONS_MS)
            midiNotes.forEachIndexed { index, midi ->
                val onset = repBaseMs + index * noteSpacing
                val freq = midiToFrequency(midi)
                events.add(NoteEvent(onset, noteDurationMs, freq, midi))
            }
        }
        return events
    }

    /** 计算音符事件数量。 */
    fun noteCount(question: IntervalSequenceQuestion): Int {
        return buildNoteEvents(question).size
    }

    /** 计算音频总时长（毫秒，含前后静音与音符尾部衰减，须与 [renderNotes] 输出一致）。 */
    fun estimateDurationMs(question: IntervalSequenceQuestion): Long {
        return (LEAD_SILENCE_MS + estimateMusicMs(question) + NOTE_TAIL_DECAY_MS + TAIL_SILENCE_MS).toLong()
    }

    /** 估算纯音乐时长（毫秒，不含前后静音，含重复）。 */
    private fun estimateMusicMs(question: IntervalSequenceQuestion): Double {
        val noteDurationMs = question.difficulty.noteDurationMs
        val gapMs = question.difficulty.gapMs
        val noteCount = question.midiNotes.size
        val noteSpacing = noteDurationMs + gapMs
        val oneRepMs = noteCount * noteSpacing
        return REPEAT_COUNT * oneRepMs + (REPEAT_COUNT - 1) * GAP_BETWEEN_REPETITIONS_MS
    }

    // ── 渲染 ──────────────────────────────────────────

    fun renderNotes(events: List<NoteEvent>, musicMs: Double): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * (musicMs + NOTE_TAIL_DECAY_MS) / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (event in events) {
            val noteSamples = (sampleRate * (event.durationMs + NOTE_TAIL_DECAY_MS) / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val wave = generateTone(noteSamples, event.frequencyHz, NOTE_AMPLITUDE)
            for (j in wave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += wave[j]
                }
            }
        }

        // tanh 软限幅
        for (i in output.indices) {
            var sample = tanh(output[i].toDouble() * NORMALIZATION_FACTOR).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /**
     * 生成单音（基频 + 3 阶谐波 + 快速攻击 + 指数衰减）。
     */
    private fun generateTone(numSamples: Int, frequencyHz: Double, amplitude: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val attackSamples = (sampleRate * NOTE_ATTACK_MS / 1000.0).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val attackEnv = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decayEnv = exp(-i / (numSamples * DECAY_TAU_RATIO))

            val fundamental = sin(2.0 * PI * frequencyHz * t)
            val h2 = sin(2.0 * PI * frequencyHz * 2.0 * t) * HARMONIC_2_GAIN
            val h3 = sin(2.0 * PI * frequencyHz * 3.0 * t) * HARMONIC_3_GAIN
            val h4 = sin(2.0 * PI * frequencyHz * 4.0 * t) * HARMONIC_4_GAIN

            val sample = (fundamental + h2 + h3 + h4) * attackEnv * decayEnv * amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        const val LEAD_SILENCE_MS = 300.0
        const val TAIL_SILENCE_MS = 400.0
        const val NOTE_TAIL_DECAY_MS = 200.0

        const val NOTE_ATTACK_MS = 5.0
        const val DECAY_TAU_RATIO = 0.25

        const val NOTE_AMPLITUDE = 0.28f

        const val HARMONIC_2_GAIN = 0.35
        const val HARMONIC_3_GAIN = 0.20
        const val HARMONIC_4_GAIN = 0.10

        /** 旋律重复播放次数。 */
        const val REPEAT_COUNT = 2

        /** 两次重复之间的间隔（毫秒）。 */
        const val GAP_BETWEEN_REPETITIONS_MS = 300.0

        const val NORMALIZATION_FACTOR = 1.5

        fun midiToFrequency(midi: Int): Double {
            return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        }
    }
}
