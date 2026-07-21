package com.pianocompanion.motiftransformation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 动机发展辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [MotifTransformationQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 动机变换听辨：**
 *
 * 渲染**两段旋律**：
 * 1. **原始动机**：按 [MotifTransformationQuestion.originalNotes] 的音高与时值依次鸣响。
 * 2. **变换后动机**：间隔一段静音后，按 [MotifTransformationQuestion.transformedNotes] 鸣响。
 *
 * 两段旋律的唯一区别就是变换类型所决定的变化：
 * - 重复：两段完全相同
 * - 模进：第二段整体移高/移低
 * - 倒影：第二段音程方向反转
 * - 逆行：第二段音符倒序
 * - 节奏扩张：第二段每个音时值加倍（变慢）
 * - 节奏紧缩：第二段每个音时值减半（变快）
 *
 * 用户通过对比两段旋律来辨识变换类型。
 *
 * **音色合成**：加法合成（基频 + 4 个递减谐波）+ 指数衰减包络（钢琴风格）+ tanh 软限幅。
 * 每个音符只渲染其时值的 [NOTE_DURATION_RATIO] 比例（留出间隙让起音清晰）。
 *
 * @param sampleRate 采样率
 */
class MotifTransformationAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符鸣响事件。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param section 所属段落（0 = 原始动机，1 = 变换后动机）
     */
    data class ToneEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val section: Int
    )

    /** 为题目渲染音频。 */
    fun render(question: MotifTransformationQuestion): FloatArray {
        val events = buildToneEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的音符鸣响事件序列。
     *
     * 结构：前导静音 → 原始动机音符 → 段间间隔 → 变换后动机音符。
     */
    fun buildToneEvents(question: MotifTransformationQuestion): List<ToneEvent> {
        val events = mutableListOf<ToneEvent>()

        // 原始动机
        var onsetMs = 0.0
        for (note in question.originalNotes) {
            events.add(ToneEvent(note.midi, onsetMs, note.durationMs, SECTION_ORIGINAL))
            onsetMs += note.durationMs
        }

        // 段间间隔
        onsetMs += GAP_BETWEEN_MOTIFS_MS

        // 变换后动机
        for (note in question.transformedNotes) {
            events.add(ToneEvent(note.midi, onsetMs, note.durationMs, SECTION_TRANSFORMED))
            onsetMs += note.durationMs
        }

        return events
    }

    /**
     * 计算所有音符的起始时间（毫秒），公开以便单元测试验证。
     */
    fun computeOnsets(question: MotifTransformationQuestion): DoubleArray =
        buildToneEvents(question).map { it.onsetMs }.toDoubleArray()

    /**
     * 获取原始动机段落的事件列表（section == 0）。
     */
    fun originalSectionEvents(question: MotifTransformationQuestion): List<ToneEvent> =
        buildToneEvents(question).filter { it.section == SECTION_ORIGINAL }

    /**
     * 获取变换后动机段落的事件列表（section == 1）。
     */
    fun transformedSectionEvents(question: MotifTransformationQuestion): List<ToneEvent> =
        buildToneEvents(question).filter { it.section == SECTION_TRANSFORMED }

    /** MIDI 编号转频率（A4=440Hz, MIDI=69）。 */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: MotifTransformationQuestion): Long {
        val events = buildToneEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    // ── 渲染 ──────────────────────────────────────────

    /** 将音符鸣响事件列表渲染为连续 PCM 采样。 */
    fun renderEvents(events: List<ToneEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐音符合成并叠加
        for (event in events) {
            val renderedDurationMs = event.durationMs * NOTE_DURATION_RATIO
            val toneSamples = (sampleRate * renderedDurationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val toneWave = generateTone(event.midi, toneSamples)
            for (j in toneWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += toneWave[j]
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

    /**
     * 生成单个音符波形（基频 + 4 个递减谐波 + 指数衰减包络）。
     */
    private fun generateTone(midi: Int, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val freq = midiToFreq(midi)
        val decayConstant = sampleRate * NOTE_DECAY_RATIO
        val nyquist = sampleRate / 2.0

        // 谐波幅度：1/n 衰减（n=1 基频，n=2~5 泛音）
        val harmonicAmps = doubleArrayOf(1.0, 1.0 / 2, 1.0 / 3, 1.0 / 4, 1.0 / 5)

        var maxAmplitude = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decayConstant)
            var sample = 0.0
            for (k in harmonicAmps.indices) {
                val harmonicFreq = freq * (k + 1)
                if (harmonicFreq >= nyquist) break
                sample += sin(2.0 * PI * harmonicFreq * t) * harmonicAmps[k]
            }
            sample *= envelope * NOTE_GAIN
            wave[i] = sample.toFloat()
            if (abs(sample) > maxAmplitude) {
                maxAmplitude = abs(sample)
            }
        }

        // 峰值归一化
        if (maxAmplitude > 1e-9) {
            val normFactor = NOTE_TARGET_AMP / maxAmplitude
            for (i in 0 until numSamples) {
                wave[i] = (wave[i] * normFactor.toFloat())
            }
        }

        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 原始动机与变换后动机之间的间隔（毫秒）。 */
        const val GAP_BETWEEN_MOTIFS_MS = 500.0

        /** 每个音符实际渲染时长占其时值的比例（留出间隙让起音清晰）。 */
        const val NOTE_DURATION_RATIO = 0.85

        /** 音符衰减时间常数与采样数之比。 */
        const val NOTE_DECAY_RATIO = 0.15

        /** 单个音符增益。 */
        const val NOTE_GAIN = 0.5

        /** 归一化目标峰值幅度。 */
        const val NOTE_TARGET_AMP = 0.7

        /** 段落标识：原始动机。 */
        const val SECTION_ORIGINAL = 0

        /** 段落标识：变换后动机。 */
        const val SECTION_TRANSFORMED = 1
    }
}
