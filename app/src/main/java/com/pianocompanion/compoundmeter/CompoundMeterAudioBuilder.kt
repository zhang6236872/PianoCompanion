package com.pianocompanion.compoundmeter

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 复合节拍听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [CompoundMeterQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 复合节拍听辨：**
 *
 * 不同拍子的区别在于**重音模式（accent pattern）**——即八分音符序列中哪些位置
 * 被强调（强拍/次强拍）。
 *
 * 渲染音频的结构：
 * 1. **前导静默**：短暂的准备时间。
 * 2. **第 1 小节**：按拍子的重音模式播放八分音符序列。
 * 3. **小节间隔**：短暂静默。
 * 4. **第 2 小节**：重复相同模式。
 *
 * **重音的音频表现：**
 * - **强拍（downbeat）**：高音（880 Hz / A5）+ 大音量（1.0）— 最突出的重音。
 * - **次强拍**：中音（660 Hz / E5）+ 中音量（0.55）— 拍点标识。
 * - **细分音（subdivision）**：低音（440 Hz / A4）+ 小音量（0.2）— 填充节拍内部。
 *
 * 这种设计让用户能通过**重音分组模式**辨识拍子：
 * - 6/8：3+3 分组（每 3 个音一个重音）→「强-弱-弱-中-弱-弱」
 * - 3/4：2+2+2 分组（每 2 个音一个重音）→「强-弱-中-弱-中-弱」
 *
 * @param sampleRate 采样率
 */
class CompoundMeterAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个节拍事件（用于测试验证）。
     *
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param accent 重音级别（0.0-1.0）
     * @param frequencyHz 频率（Hz）
     * @param isDownbeat 是否为小节最强拍
     * @param isBeat 是否为拍点（强拍或次强拍）
     * @param barNumber 小节号（0-indexed）
     * @param positionInBar 小节内位置（0-indexed）
     */
    data class BeatEvent(
        val onsetMs: Double,
        val durationMs: Double,
        val accent: Float,
        val frequencyHz: Double,
        val isDownbeat: Boolean,
        val isBeat: Boolean,
        val barNumber: Int,
        val positionInBar: Int
    )

    /** 为题目渲染音频。 */
    fun render(question: CompoundMeterQuestion): FloatArray {
        val events = buildBeatEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建题目的全部节拍事件。
     *
     * 结构：[小节1: 重音模式] → [间隔] → [小节2: 重音模式]
     */
    fun buildBeatEvents(question: CompoundMeterQuestion): List<BeatEvent> {
        val d = question.difficulty
        val meter = question.targetMeter
        val eighthDur = d.eighthNoteDurationMs.toDouble()
        val barGap = d.barGapMs.toDouble()
        val pattern = meter.accentPattern

        val events = mutableListOf<BeatEvent>()
        var currentTime = 0.0

        for (bar in 0 until d.barCount) {
            for (pos in pattern.indices) {
                val accent = pattern[pos]
                val isBeat = accent >= BEAT_THRESHOLD
                val isDownbeat = accent >= DOWNBEAT_THRESHOLD

                val (freq, _) = getFrequencyForAccent(accent)
                val noteDur = if (isBeat) eighthDur * BEAT_DURATION_RATIO else eighthDur * SUB_DURATION_RATIO

                events.add(
                    BeatEvent(
                        onsetMs = currentTime,
                        durationMs = noteDur,
                        accent = accent,
                        frequencyHz = freq,
                        isDownbeat = isDownbeat,
                        isBeat = isBeat,
                        barNumber = bar,
                        positionInBar = pos
                    )
                )
                currentTime += eighthDur
            }
            // 小节间隔（最后一小节后不加）
            if (bar < d.barCount - 1) {
                currentTime += barGap
            }
        }

        return events
    }

    /** 计算每小节的八分音符数量。 */
    fun eighthNotesPerBar(question: CompoundMeterQuestion): Int {
        return question.targetMeter.eighthNotesPerBar
    }

    /** 计算总节拍事件数。 */
    fun totalBeatEventCount(question: CompoundMeterQuestion): Int {
        return question.targetMeter.accentPattern.size * question.difficulty.barCount
    }

    /** 计算音频总时长（毫秒，不含前后静音）。 */
    fun musicDurationMs(question: CompoundMeterQuestion): Double {
        val events = buildBeatEvents(question)
        return if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
    }

    /** 预估渲染时长（毫秒，含前后静音）。 */
    fun estimateDurationMs(question: CompoundMeterQuestion): Long {
        val musicMs = musicDurationMs(question)
        return (LEAD_SILENCE_MS + musicMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 根据重音级别获取频率和振幅。
     *
     * - 强拍（accent >= 0.8）→ 880 Hz（A5）
     * - 拍点（accent >= 0.4）→ 660 Hz（E5）
     * - 细分音 → 440 Hz（A4）
     */
    private fun getFrequencyForAccent(accent: Float): Pair<Double, Float> {
        return when {
            accent >= DOWNBEAT_THRESHOLD -> DOWNBEAT_FREQ to accent
            accent >= BEAT_THRESHOLD -> BEAT_FREQ to accent
            else -> SUBDIVISION_FREQ to accent
        }
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将节拍事件列表渲染为连续 PCM 采样。
     *
     * 所有音符使用打击乐式音色（快速衰减包络），重音通过音高和音量区分。
     */
    fun renderEvents(events: List<BeatEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (event in events) {
            val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val noteWave = generateClickTone(event.frequencyHz, noteSamples, event.accent)
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
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
     * 生成打击乐式单击音色（快速攻击 + 指数衰减 + 基频+1谐波）。
     *
     * 模拟节拍器/木鱼的音色——短促、清晰、辨识度高。
     */
    private fun generateClickTone(freq: Double, numSamples: Int, accent: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CLICK_DECAY_MS / 1000.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            val fundamental = sin(2.0 * PI * freq * t)
            val harmonic = sin(2.0 * PI * freq * 2.0 * t) * 0.3
            val sample = (fundamental + harmonic) * envelope * accent * CLICK_GAIN
            wave[i] = sample.toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 250.0

        /** 强拍阈值（accent >= 此值视为强拍/downbeat）。 */
        const val DOWNBEAT_THRESHOLD = 0.8f

        /** 拍点阈值（accent >= 此值视为拍点）。 */
        const val BEAT_THRESHOLD = 0.4f

        /** 强拍频率（A5 = 880 Hz）。 */
        const val DOWNBEAT_FREQ = 880.0

        /** 拍点频率（E5 ≈ 659 Hz）。 */
        const val BEAT_FREQ = 660.0

        /** 细分音频率（A4 = 440 Hz）。 */
        const val SUBDIVISION_FREQ = 440.0

        /** 拍点音符时长比例（相对于八分音符间距）。 */
        const val BEAT_DURATION_RATIO = 0.45

        /** 细分音符时长比例。 */
        const val SUB_DURATION_RATIO = 0.35

        /** 单击衰减时间常数（毫秒）。 */
        const val CLICK_DECAY_MS = 35.0

        /** 单击增益。 */
        const val CLICK_GAIN = 0.85f
    }
}
