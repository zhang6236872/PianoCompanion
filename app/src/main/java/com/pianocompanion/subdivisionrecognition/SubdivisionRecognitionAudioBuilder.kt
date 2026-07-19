package com.pianocompanion.subdivisionrecognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 节奏细分听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [SubdivisionQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 节奏细分听辨：**
 *
 * 渲染一段乐句：每拍等分成 N 个音符（N = [SubdivisionType.notesPerBeat]），N 个音符在拍内
 * **等间距**排列。用户据此判断一拍被分成了几等份（2 / 3 / 4）。
 *
 * 关键设计：
 * - **每拍起始音略加「拍点重音」**（更高音高 + 更大音量），帮助用户感知「拍」的分组，
 *   并在拍内部数细分密度。拍点重音不改变答案（答案始终是细分密度），仅辅助感知。
 * - **断奏 / 连奏（staccato / legato）** 控制难度：
 *   - 断奏：音符短促、音头清晰、音符间有静默间隙 → 易于数清音头（初级 / 中级）。
 *   - 连奏：音符衰减缓慢、相互重叠、音头模糊 → 须靠整体「律动感」而非数音头（高级）。
 * - **单一音色、固定音高组合**：拍点音与细分音各用一个固定音高，使「细分密度」成为
 *   唯一显著的时间特征，而非依赖音高变化。
 *
 * 渲染流程：
 * 1. [computeOnsetTimes] 计算每个音符的绝对时间戳（拍内等分）
 * 2. [computeBeatStartFlags] 标记每个音符是否为拍起始（用于重音 + 测试验证）
 * 3. [subdivIntervalMs] 计算相邻细分音符的时间间隔
 * 4. 在各时间戳叠加对应音符波形（拍点音 / 细分音）+ 软限幅
 *
 * @param sampleRate 采样率
 */
class SubdivisionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /** 为题目渲染音频。 */
    fun render(question: SubdivisionQuestion): FloatArray {
        val onsets = computeOnsetTimes(question)
        val beatStartFlags = computeBeatStartFlags(question)
        if (onsets.isEmpty()) return FloatArray(0)

        val interval = subdivIntervalMs(question)
        val noteLengthMs = noteLengthMs(question.staccato, interval)
        val noteSamples = (sampleRate * noteLengthMs / 1000.0).toInt().coerceAtLeast(1)
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + noteSamples + tailSamples

        val output = FloatArray(totalLength)

        // 预生成拍点音 / 细分音两种波形
        val beatNote = generateNote(noteSamples, BEAT_FREQUENCY, BEAT_AMPLITUDE, question.staccato, interval)
        val subdivNote = generateNote(noteSamples, SUBDIV_FREQUENCY, SUBDIV_AMPLITUDE, question.staccato, interval)

        for (i in onsets.indices) {
            val offset = (onsets[i] * sampleRate / 1000.0).toInt()
            val wave = if (beatStartFlags[i]) beatNote else subdivNote
            for (j in wave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += wave[j]
                }
            }
        }

        // 软限幅防止叠加削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 计算每个音符的绝对时间戳（毫秒）。
     *
     * 时序：前导静音 → 每拍内 N 个等间距音符（N = notesPerBeat），拍连续播放，小节连续播放。
     */
    fun computeOnsetTimes(question: SubdivisionQuestion): List<Double> {
        val interval = subdivIntervalMs(question)
        val onsets = mutableListOf<Double>()
        for (rep in 0 until question.measureRepeat) {
            val measureStart = LEAD_SILENCE_MS + rep * question.beatsPerMeasure * question.beatMs
            for (beat in 0 until question.beatsPerMeasure) {
                val beatStart = measureStart + beat * question.beatMs
                for (s in 0 until question.subdivision.notesPerBeat) {
                    onsets.add(beatStart + s * interval)
                }
            }
        }
        return onsets
    }

    /**
     * 计算每个音符是否为拍起始（true = 拍点音）。
     *
     * 每拍第 1 个音符（在拍内的索引 0）为拍点音。
     */
    fun computeBeatStartFlags(question: SubdivisionQuestion): List<Boolean> {
        val perBeat = question.subdivision.notesPerBeat
        val total = perBeat * question.beatsPerMeasure * question.measureRepeat
        return List(total) { idx -> (idx % perBeat) == 0 }
    }

    /** 相邻细分音符的时间间隔（毫秒）= 一拍时长 / 每拍音符数。 */
    fun subdivIntervalMs(question: SubdivisionQuestion): Double =
        question.beatMs / question.subdivision.notesPerBeat

    /** 单个音符的渲染时长（毫秒），断奏短促、连奏重叠。 */
    fun noteLengthMs(staccato: Boolean, intervalMs: Double): Double {
        return if (staccato) {
            minOf(intervalMs * STACCATO_LENGTH_RATIO, MAX_NOTE_MS)
        } else {
            minOf(intervalMs * LEGATO_LENGTH_RATIO, MAX_NOTE_MS)
        }
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: SubdivisionQuestion): Long {
        val onsets = computeOnsetTimes(question)
        if (onsets.isEmpty()) return 0L
        val interval = subdivIntervalMs(question)
        val noteLen = noteLengthMs(question.staccato, interval)
        return onsets.last().toLong() + noteLen.toLong() + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 计算指定音符索引在输出缓冲区中的能量（RMS）。
     *
     * 用于单元测试验证：拍点音能量应高于细分音（拍点重音存在）。
     */
    fun noteRmsEnergy(buffer: FloatArray, question: SubdivisionQuestion, noteIndex: Int): Double {
        val onsets = computeOnsetTimes(question)
        if (noteIndex !in onsets.indices) return 0.0
        val interval = subdivIntervalMs(question)
        val windowMs = noteLengthMs(question.staccato, interval)
        val onsetSample = (onsets[noteIndex] * sampleRate / 1000.0).toInt()
        val windowSamples = (sampleRate * windowMs / 1000.0).toInt()
        var sumSq = 0.0
        var count = 0
        for (i in onsetSample until minOf(onsetSample + windowSamples, buffer.size)) {
            sumSq += buffer[i].toDouble() * buffer[i]
            count++
        }
        return if (count > 0) sqrt(sumSq / count) else 0.0
    }

    /**
     * 生成单个音符波形（加法合成：基频 + 2 谐波，指数衰减包络）。
     *
     * 断奏：快速衰减（音头清晰、短促）。
     * 连奏：慢速衰减（音符持续到下一个音、相互重叠、音头模糊）。
     */
    private fun generateNote(
        numSamples: Int,
        frequency: Double,
        amplitude: Float,
        staccato: Boolean,
        intervalMs: Double
    ): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        // 衰减时间常数（毫秒）：断奏短、连奏长
        val decayMs = if (staccato) {
            intervalMs * STACCATO_DECAY_RATIO
        } else {
            intervalMs * LEGATO_DECAY_RATIO
        }
        val decayMsSafe = decayMs.coerceAtLeast(MIN_DECAY_MS)
        val decaySamples = sampleRate * decayMsSafe / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            // 基频 + 2 谐波（钢琴/马林巴风格的加法合成）
            val fundamental = sin(2.0 * PI * frequency * t)
            val h2 = sin(2.0 * PI * frequency * 2.0 * t) * HARMONIC_2_GAIN
            val h3 = sin(2.0 * PI * frequency * 3.0 * t) * HARMONIC_3_GAIN
            val sample = (fundamental + h2 + h3) * envelope * amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    /** 软限幅函数（保留拍点音与细分音的绝对响度差，不做峰值归一化）。 */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + abs(x) / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 450.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 拍点音频率（Hz，更高音高标记拍位）。 */
        const val BEAT_FREQUENCY = 880.0

        /** 细分音频率（Hz）。 */
        const val SUBDIV_FREQUENCY = 660.0

        /** 拍点音振幅。 */
        const val BEAT_AMPLITUDE = 0.55f

        /** 细分音振幅。 */
        const val SUBDIV_AMPLITUDE = 0.30f

        /** 2 次谐波增益。 */
        const val HARMONIC_2_GAIN = 0.30

        /** 3 次谐波增益。 */
        const val HARMONIC_3_GAIN = 0.15

        /** 断奏时音符长度占细分间隔的比例（短促，留静默间隙）。 */
        const val STACCATO_LENGTH_RATIO = 0.6

        /** 连奏时音符长度占细分间隔的比例（重叠）。 */
        const val LEGATO_LENGTH_RATIO = 2.0

        /** 断奏衰减时间常数占细分间隔的比例（快速衰减）。 */
        const val STACCATO_DECAY_RATIO = 0.3

        /** 连奏衰减时间常数占细分间隔的比例（缓慢衰减）。 */
        const val LEGATO_DECAY_RATIO = 1.4

        /** 最小衰减时间常数（毫秒），防止极快速度下衰减过快失真。 */
        const val MIN_DECAY_MS = 8.0

        /** 单个音符最大渲染时长（毫秒），限制内存。 */
        const val MAX_NOTE_MS = 240.0

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
