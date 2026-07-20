package com.pianocompanion.harmonycolor

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 和声色彩听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [HarmonyColorQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 和声色彩听辨：**
 *
 * 渲染一个**柱式和弦（block chord）**：和弦的全部音符（根音 + 三音 + 五音）**同时起音**。
 * 不同和声色彩（大/小/减/增三和弦）的区别完全来自**音程结构**（三度与五度的半音数），
 * 而非音高、力度、节奏或音区——这些在所有题目中保持一致。因此「和声色彩」成为音频中
 * 唯一显著的特征。
 *
 * 为帮助听辨，和弦会**播放两遍**（中间留一段静音间隔），给耳朵两次感知机会。
 *
 * 每个音使用钢琴风格加法合成（基频 + 4 谐波）+ 指数衰减包络；多音叠加后用 tanh 软限幅。
 *
 * @param sampleRate 采样率
 */
class HarmonyColorAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个和弦实例（一次鸣响）。
     *
     * @param voicing 和弦的 MIDI 音高序列
     * @param onsetMs 本次鸣响的起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     */
    data class ChordEvent(
        val voicing: List<Int>,
        val onsetMs: Double,
        val durationMs: Double
    )

    /** 为题目渲染音频。 */
    fun render(question: HarmonyColorQuestion): FloatArray {
        val events = buildChordEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的和弦鸣响事件序列。
     *
     * 和弦播放两遍：第一次 onset=0，第二次 onset=CHORD_DURATION_MS + GAP_MS。
     */
    fun buildChordEvents(question: HarmonyColorQuestion): List<ChordEvent> {
        return listOf(
            ChordEvent(question.voicing, FIRST_ONSET_MS, CHORD_DURATION_MS),
            ChordEvent(question.voicing, SECOND_ONSET_MS, CHORD_DURATION_MS)
        )
    }

    /**
     * 计算两次和弦鸣响的起始时间（毫秒），公开以便单元测试验证。
     */
    fun computeOnsets(question: HarmonyColorQuestion): DoubleArray =
        doubleArrayOf(FIRST_ONSET_MS, SECOND_ONSET_MS)

    /** 计算和弦中指定 MIDI 音高的频率（用于测试验证音程结构）。 */
    fun noteFrequency(midi: Int): Double = midiToFreq(midi)

    // ── 渲染 ──────────────────────────────────────────

    /** 将和弦鸣响事件列表渲染为连续 PCM 采样（多音叠加 + tanh 软限幅）。 */
    fun renderEvents(events: List<ChordEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐和弦鸣响合成并叠加
        for (event in events) {
            val chordSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val chordWave = generateChord(event.voicing, chordSamples)
            for (j in chordWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += chordWave[j]
                }
            }
        }

        // tanh 软限幅防止削波
        for (i in output.indices) {
            var sample = tanh(output[i].toDouble()).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /** MIDI 编号转频率（A4=440Hz, MIDI=69）。 */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: HarmonyColorQuestion): Long {
        val events = buildChordEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个柱式和弦波形（所有音符同时起音 + 钢琴风格加法合成 + 指数衰减包络）。
     */
    private fun generateChord(voicing: List<Int>, numSamples: Int): FloatArray {
        if (numSamples <= 0 || voicing.isEmpty()) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CHORD_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            // 每个和弦音都贡献基频 + 谐波
            for (midi in voicing) {
                val frequency = midiToFreq(midi)
                for (h in HARMONICS.indices) {
                    val freq = frequency * (h + 1)
                    if (freq >= nyquist) break
                    sample += sin(2.0 * PI * freq * t) * HARMONICS[h]
                }
            }
            // 按和弦音数归一化，避免和弦音越多响度越大（消除响度作为线索）
            sample /= voicing.size.toDouble()
            wave[i] = (sample * envelope * CHORD_GAIN).toFloat()
        }

        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 单次和弦持续时间（毫秒）。 */
        const val CHORD_DURATION_MS = 900.0

        /** 和弦衰减时间常数（毫秒）—— 较长，让色彩充分展开。 */
        const val CHORD_DECAY_MS = 500.0

        /** 第一次鸣响起始时间（毫秒）。 */
        const val FIRST_ONSET_MS = 0.0

        /** 两次鸣响之间的间隔（毫秒）。 */
        const val GAP_MS = 250.0

        /** 第二次鸣响起始时间（毫秒）。 */
        val SECOND_ONSET_MS: Double = CHORD_DURATION_MS + GAP_MS

        /** 和弦总增益。 */
        const val CHORD_GAIN = 0.6f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
