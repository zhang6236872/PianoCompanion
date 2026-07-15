package com.pianocompanion.modulationrecognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 转调辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ModulationQuestion] 的转调类型渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 转调听辨：**
 * - 播放一段 4 小节和弦进行（每小节 1 个和弦）
 * - 前 2 小节在原调（C大调），确立调性中心
 * - 后 2 小节根据转调类型发生变化：
 *   - TO_DOMINANT：转入 G大调（V-I 完满终止）
 *   - TO_SUBDOMINANT：转入 F大调（IV-I 完满终止）
 *   - TO_RELATIVE：转入 a小调（i-V-i 小调终止）
 *   - NO_MODULATION：继续在 C大调（I-IV-V-I 功能进行）
 * - 每个和弦由 3 个音组成（三和弦 root position），使用钢琴风格加法合成 + 指数衰减
 *
 * 和弦进行设计（以 C大调为原调）：
 * - TO_DOMINANT:  C(I)→G(V)→D(V/V)→G(V)→C(I-of-G)→G(V)→D(V/V)→G(I-in-G)
 *   简化版: C(I)→G(V/C)→C(I)→|G(V)→D(V/V)→G(I-in-G)
 * - 更简洁的设计：前2小节 C大调 I-IV，后2小节转到目标调的 V-I
 *
 * @param sampleRate 采样率
 */
class ModulationAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个和弦事件。
     *
     * @param midiNotes 组成该和弦的 MIDI 音符列表（从低到高）
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param keyLabel 该和弦所属的调性（用于测试验证）
     * @param romanNumeral 罗马数字标记
     */
    data class ChordEvent(
        val midiNotes: List<Int>,
        val onsetMs: Double,
        val durationMs: Double,
        val keyLabel: String,
        val romanNumeral: String
    )

    /**
     * 为题目渲染音频。
     */
    fun render(question: ModulationQuestion): FloatArray {
        val events = buildChordEvents(question.modulation)
        return renderEvents(events)
    }

    /**
     * 构建指定转调类型的和弦事件序列。
     *
     * 设计：4 个和弦，每个持续 CHORD_MS 毫秒
     * - 前半段：在原调建立调性
     * - 后半段：根据转调类型变化
     */
    fun buildChordEvents(modulation: ModulationType): List<ChordEvent> {
        val events = mutableListOf<ChordEvent>()

        when (modulation) {
            ModulationType.TO_DOMINANT -> {
                // 原调 C大调: I → IV，然后转属调 G大调: V → I
                events.add(ChordEvent(C_MAJOR_I, 0 * CHORD_MS, CHORD_MS, "C", "I"))
                events.add(ChordEvent(C_MAJOR_IV, 1 * CHORD_MS, CHORD_MS, "C", "IV"))
                // 转属调 G大调: D(V of G) → G(I of G)
                events.add(ChordEvent(G_MAJOR_V, 2 * CHORD_MS, CHORD_MS, "G", "V"))
                events.add(ChordEvent(G_MAJOR_I, 3 * CHORD_MS, CHORD_MS, "G", "I"))
            }
            ModulationType.TO_SUBDOMINANT -> {
                // 原调 C大调: I → V，然后转下属调 F大调: V → I
                events.add(ChordEvent(C_MAJOR_I, 0 * CHORD_MS, CHORD_MS, "C", "I"))
                events.add(ChordEvent(C_MAJOR_V, 1 * CHORD_MS, CHORD_MS, "C", "V"))
                // 转下属调 F大调: C(V of F) → F(I of F)
                events.add(ChordEvent(F_MAJOR_V, 2 * CHORD_MS, CHORD_MS, "F", "V"))
                events.add(ChordEvent(F_MAJOR_I, 3 * CHORD_MS, CHORD_MS, "F", "I"))
            }
            ModulationType.TO_RELATIVE -> {
                // 原调 C大调: I → V，然后转关系小调 a小调: V → i
                events.add(ChordEvent(C_MAJOR_I, 0 * CHORD_MS, CHORD_MS, "C", "I"))
                events.add(ChordEvent(C_MAJOR_V, 1 * CHORD_MS, CHORD_MS, "C", "V"))
                // 转关系小调 a小调: E(V of am) → Am(i of am)
                events.add(ChordEvent(A_MINOR_V, 2 * CHORD_MS, CHORD_MS, "Am", "V"))
                events.add(ChordEvent(A_MINOR_I, 3 * CHORD_MS, CHORD_MS, "Am", "i"))
            }
            ModulationType.NO_MODULATION -> {
                // 全程 C大调: I → IV → V → I (功能进行，巩固调性)
                events.add(ChordEvent(C_MAJOR_I, 0 * CHORD_MS, CHORD_MS, "C", "I"))
                events.add(ChordEvent(C_MAJOR_IV, 1 * CHORD_MS, CHORD_MS, "C", "IV"))
                events.add(ChordEvent(C_MAJOR_V, 2 * CHORD_MS, CHORD_MS, "C", "V"))
                events.add(ChordEvent(C_MAJOR_I, 3 * CHORD_MS, CHORD_MS, "C", "I"))
            }
        }

        return events
    }

    /**
     * 提取所有和弦的调性标签序列（用于测试验证转调发生）。
     */
    fun extractKeyLabels(modulation: ModulationType): List<String> {
        return buildChordEvents(modulation).map { it.keyLabel }
    }

    /**
     * 提取所有和弦的 MIDI 音符（用于测试验证音高内容）。
     */
    fun extractAllMidiNotes(modulation: ModulationType): List<List<Int>> {
        return buildChordEvents(modulation).map { it.midiNotes }
    }

    /**
     * 判断指定转调类型的音频中是否发生了调性变化。
     */
    fun hasKeyChange(modulation: ModulationType): Boolean {
        val keys = extractKeyLabels(modulation)
        if (keys.isEmpty()) return false
        val firstKey = keys.first()
        return keys.any { it != firstKey }
    }

    // ── 和弦定义（MIDI 音符，三和弦原位）──────────────────────

    companion object {
        // C大调和弦
        private val C_MAJOR_I = listOf(60, 64, 67)   // C-E-G (C大三和弦)
        private val C_MAJOR_IV = listOf(65, 69, 72)   // F-A-C (F大三和弦)
        private val C_MAJOR_V = listOf(67, 71, 74)    // G-B-D (G大三和弦)

        // G大调和弦（属调）
        private val G_MAJOR_I = listOf(67, 71, 74)    // G-B-D (G大三和弦)
        private val G_MAJOR_V = listOf(74, 78, 81)    // D-F#-A (D大三和弦，高八度以确保转调感)

        // F大调和弦（下属调）
        private val F_MAJOR_I = listOf(65, 69, 72)    // F-A-C (F大三和弦)
        private val F_MAJOR_V = listOf(72, 76, 79)    // C-E-G (C大三和弦，高八度)

        // a小调和弦（关系小调）
        private val A_MINOR_I = listOf(69, 72, 76)    // A-C-E (a小三和弦)
        private val A_MINOR_V = listOf(76, 80, 83)    // E-G#-B (E大三和弦，和声小调V，高八度)

        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个和弦持续时间（毫秒）。 */
        const val CHORD_MS = 800.0

        /** 音符衰减时间常数（毫秒）。 */
        const val CHORD_DECAY_MS = 600.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.8f

        /** 单音增益（和弦中每个音的增益）。 */
        const val NOTE_GAIN = 0.6f

        /** 和弦中的音符数。 */
        const val NOTES_PER_CHORD = 3

        /** 和弦总数。 */
        const val CHORD_COUNT = 4

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将和弦事件列表渲染为连续 PCM 采样（多音叠加）。
     */
    fun renderEvents(events: List<ChordEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐和弦逐音合成并叠加
        for (event in events) {
            for (midi in event.midiNotes) {
                val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
                val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
                val wave = generateNote(midiToFreq(midi), noteSamples, NOTE_GAIN)
                for (j in wave.indices) {
                    val outIdx = offset + j
                    if (outIdx in output.indices) {
                        output[outIdx] += wave[j]
                    }
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
    fun estimateDurationMs(modulation: ModulationType): Long {
        val events = buildChordEvents(modulation)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     */
    private fun generateNote(frequency: Double, numSamples: Int, gain: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CHORD_DECAY_MS / 1000.0
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
}
