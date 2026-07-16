package com.pianocompanion.consonancetraining

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 协和度辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ConsonanceQuestion] 的音程渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 协和度听辨：**
 * - 播放两个音（较低音 + 较高音），二者之间的半音距离决定协和度
 * - **旋律方式（Melodic）**：两个音先后发响（先低后高），适合初级感知「距离感」
 * - **和声方式（Harmonic）**：两个音同时发响，直接感知「融合 / 碰撞」的协和度
 * - 每个音使用钢琴风格加法合成（基频 + 4 谐波）+ 指数衰减包络
 *
 * 和声方式下，不协和音程（如三全音、小二度）会产生明显的「拍音」（beating）和
 * 粗糙感（roughness），这正是协和度听辨的物理基础。
 *
 * @param sampleRate 采样率
 */
class ConsonanceAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double
    )

    /** 题目中实际使用的两个 MIDI 音高（从低到高，用于测试验证）。 */
    data class IntervalPitches(val lower: Int, val higher: Int) {
        /** 半音距离。 */
        val semitoneDistance: Int get() = higher - lower
    }

    /**
     * 为题目渲染音频。
     */
    fun render(question: ConsonanceQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的音符事件序列。
     *
     * - 旋律方式：低音 → 间隔 GAP_MS → 高音，各持续 NOTE_MS
     * - 和声方式：低音和高音同时开始，持续 NOTE_MS
     */
    fun buildNoteEvents(question: ConsonanceQuestion): List<NoteEvent> {
        val lower = question.lowerMidi
        val higher = question.higherMidi
        return if (question.presentation == Presentation.MELODIC) {
            listOf(
                NoteEvent(lower, 0.0, NOTE_MS),
                NoteEvent(higher, NOTE_MS + GAP_MS, NOTE_MS)
            )
        } else {
            listOf(
                NoteEvent(lower, 0.0, NOTE_MS),
                NoteEvent(higher, 0.0, NOTE_MS)
            )
        }
    }

    /**
     * 提取题目中实际使用的两个音高（从低到高）。
     */
    fun extractPitches(question: ConsonanceQuestion): IntervalPitches {
        val pair = if (question.lowerMidi <= question.higherMidi) {
            question.lowerMidi to question.higherMidi
        } else {
            question.higherMidi to question.lowerMidi
        }
        return IntervalPitches(pair.first, pair.second)
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样（多音叠加）。
     */
    fun renderEvents(events: List<NoteEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐音符合成并叠加
        for (event in events) {
            val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val wave = generateNote(midiToFreq(event.midi), noteSamples, NOTE_GAIN)
            for (j in wave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += wave[j]
                }
            }
        }

        // 归一化并应用主振幅
        val maxAbs = output.maxOfOrNull { abs(it) } ?: 1.0f
        val norm = if (maxAbs > 0.0001f) MASTER_AMPLITUDE / maxAbs else MASTER_AMPLITUDE
        for (i in output.indices) {
            var sample = output[i] * norm
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /**
     * MIDI 编号转频率（A4=440Hz, MIDI=69）。
     */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: ConsonanceQuestion): Long {
        val events = buildNoteEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     */
    private fun generateNote(frequency: Double, numSamples: Int, gain: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * NOTE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            for (h in HARMONICS.indices) {
                val freq = frequency * (h + 1)
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * HARMONICS[h]
            }
            wave[i] = (sample * envelope * gain).toFloat()
        }

        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 300.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_MS = 700.0

        /** 旋律方式中两音之间的间隔（毫秒）。 */
        const val GAP_MS = 80.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 500.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.85f

        /** 单音增益。 */
        const val NOTE_GAIN = 0.7f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
