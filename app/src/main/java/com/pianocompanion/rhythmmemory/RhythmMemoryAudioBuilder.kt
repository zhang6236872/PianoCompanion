package com.pianocompanion.rhythmmemory

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 节奏型记忆训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [RhythmMemoryQuestion] 中的目标节奏型渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 节奏型的听觉编码：**
 *
 * 节奏型由「拍」组成，每拍包含一种细分模式（[RhythmCellType]）。渲染时：
 * 1. 以恒定 [RhythmMemoryQuestion.tempoBpm] 确定一拍时长 `beatMs`；
 * 2. 对每一拍，按其细分模式的相对时长，在拍内对应位置放置**击打事件**（hit）；
 *    例如「两个八分」= 拍首 + 拍中各一个击打；「附点八分+十六分」= 拍首 + 3/4 处；
 * 3. 每个击打渲染为短促的木琴/响板式音色（基频 + 1 谐波 + 快速指数衰减），形成清晰的
 *    节奏点序列；
 * 4. **重音标记**：每小节（每条节奏型）的第一拍第一击更响，帮助建立拍点参照；
 * 5. 整条节奏型**重复播放两次**（中间留短间隔），便于用户在短期记忆中巩固节奏。
 *
 * 音色采用单一固定音高（不含旋律信息），使注意力完全集中在**节奏时序**上。
 *
 * @param sampleRate 采样率
 */
class RhythmMemoryAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个击打事件（用于测试验证）。
     *
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 击打音色持续时长（毫秒）
     * @param intensity 强度（0.0-1.0，重音更响）
     * @param beatIndex 所属拍序号（0-based）
     */
    data class RhythmHit(
        val onsetMs: Double,
        val durationMs: Double,
        val intensity: Float,
        val beatIndex: Int
    )

    /** 为题目渲染音频。 */
    fun render(question: RhythmMemoryQuestion): FloatArray {
        val hits = buildHits(question)
        val musicMs = estimateMusicMs(question)
        return renderHits(hits, musicMs)
    }

    /**
     * 根据题目的目标节奏型构建全部击打事件。
     *
     * 每拍按细分模式放置击打；整条节奏型重复 [REPEAT_COUNT] 次。
     */
    fun buildHits(question: RhythmMemoryQuestion): List<RhythmHit> {
        val beatMs = 60_000.0 / question.tempoBpm
        val patternBeats = question.beats
        val hits = mutableListOf<RhythmHit>()

        for (rep in 0 until REPEAT_COUNT) {
            val repBaseMs = rep * (patternBeats * beatMs + GAP_BETWEEN_REPETITIONS_MS)
            question.targetPattern.cells.forEachIndexed { beatIndex, cell ->
                var pos = 0.0
                cell.subdivisions.forEach { dur ->
                    val onset = repBaseMs + beatIndex * beatMs + pos * beatMs
                    // 重音：第一次重复的第一拍第一击最强；每拍首击为次强；拍内其余为弱
                    val isPhraseStart = (beatIndex == 0 && pos == 0.0)
                    val isBeatStart = (pos == 0.0)
                    val intensity = when {
                        isPhraseStart -> ACCENT_INTENSITY
                        isBeatStart -> BEAT_INTENSITY
                        else -> SUBDIVISION_INTENSITY
                    }
                    hits.add(RhythmHit(onset, HIT_DURATION_MS, intensity, beatIndex))
                    pos += dur
                }
            }
        }
        return hits
    }

    /** 计算击打事件数量。 */
    fun hitCount(question: RhythmMemoryQuestion): Int = buildHits(question).size

    /** 计算音频总时长（毫秒，含前后静音）。 */
    fun estimateDurationMs(question: RhythmMemoryQuestion): Long {
        return (LEAD_SILENCE_MS + estimateMusicMs(question) + TAIL_SILENCE_MS).toLong()
    }

    /** 估算纯音乐时长（毫秒，不含前后静音，含 2 次重复与间隔）。 */
    private fun estimateMusicMs(question: RhythmMemoryQuestion): Double {
        val beatMs = 60_000.0 / question.tempoBpm
        val oneRepMs = question.beats * beatMs
        return REPEAT_COUNT * oneRepMs + (REPEAT_COUNT - 1) * GAP_BETWEEN_REPETITIONS_MS
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将击打事件列表渲染为连续 PCM 采样（木琴式音色 + 指数衰减 + tanh 软限幅）。
     */
    fun renderHits(hits: List<RhythmHit>, musicMs: Double): FloatArray {
        if (hits.isEmpty()) return FloatArray(0)

        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * (musicMs + HIT_TAIL_DECAY_MS) / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (hit in hits) {
            val hitSamples = (sampleRate * (hit.durationMs + HIT_TAIL_DECAY_MS) / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * hit.onsetMs / 1000.0).toInt()
            val wave = generateClickTone(hitSamples, hit.intensity)
            for (j in wave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += wave[j]
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
     * 生成木琴/响板式击打音色（基频 + 1 谐波 + 快速攻击 + 指数衰减）。
     */
    private fun generateClickTone(numSamples: Int, intensity: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val attackSamples = (sampleRate * CLICK_ATTACK_MS / 1000.0).toInt().coerceAtLeast(1)
        val freq = HIT_FREQUENCY_HZ

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val attackEnv = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decayEnv = exp(-i / (numSamples * DECAY_TAU_RATIO))

            val fundamental = sin(2.0 * PI * freq * t)
            val h2 = sin(2.0 * PI * freq * 2.0 * t) * HARMONIC_2_GAIN

            val sample = (fundamental + h2) * attackEnv * decayEnv * intensity
            wave[i] = sample.toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        const val LEAD_SILENCE_MS = 300.0
        const val TAIL_SILENCE_MS = 400.0
        const val HIT_TAIL_DECAY_MS = 160.0

        /** 单个击打音色持续时长（毫秒）。 */
        const val HIT_DURATION_MS = 80.0
        const val CLICK_ATTACK_MS = 1.5
        const val DECAY_TAU_RATIO = 0.30

        const val HARMONIC_2_GAIN = 0.30

        /** 击打基础频率（Hz，单一音高，不含旋律信息）。 */
        const val HIT_FREQUENCY_HZ = 880.0

        /** 节奏型重复播放次数。 */
        const val REPEAT_COUNT = 2

        /** 两次重复之间的间隔（毫秒）。 */
        const val GAP_BETWEEN_REPETITIONS_MS = 250.0

        /** 小节首拍重音强度。 */
        const val ACCENT_INTENSITY = 0.62f

        /** 每拍首击强度。 */
        const val BEAT_INTENSITY = 0.46f

        /** 拍内细分击打强度。 */
        const val SUBDIVISION_INTENSITY = 0.40f

        const val NORMALIZATION_FACTOR = 1.2

        fun midiToFrequency(midi: Int): Double {
            return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        }
    }
}
