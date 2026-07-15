package com.pianocompanion.contrapuntalmotiontraining

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 声部运动辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ContrapuntalMotionQuestion] 的运动类型渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 声部运动听辨：**
 * - 播放两条同时进行的旋律线（上声部 + 下声部）
 * - 每拍上声部和下声部各响一个音，同步进行
 * - 通过两条旋律线运动方向的组合方式来体现不同的声部运动类型
 * - 所有音符使用钢琴风格加法合成（基频 + 谐波）+ 指数衰减包络
 *
 * 各运动类型的音频特征（4 个音符，每音 500ms）：
 * - **平行进行**：上声部 C4→E4→G4→C5 上行 + 下声部 C3→E3→G3→C4 上行（严格平行八度）
 * - **同向进行**：上声部 C4→E4→G4→C5 大步上行 + 下声部 G3→A3→B3→C4 小步上行（间距扩大）
 * - **反向进行**：上声部 C4→E4→G4→C5 上行 + 下声部 C5→A4→F4→D4 下行（一升一降）
 * - **斜向进行**：上声部 C4→E4→G4→C5 上行 + 下声部 G3→G3→G3→G3 保持不变（踏板音）
 *
 * @param sampleRate 采样率
 */
class ContrapuntalMotionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件（在时间轴上的位置和属性）。
     *
     * @param midi MIDI 编号
     * @param onsetMs 起始时间（毫秒，相对于音乐开始）
     * @param durationMs 持续时间（毫秒）
     * @param gain 相对增益（0.0-1.0）
     * @param voice 声部标记（用于测试）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val gain: Float = 1.0f,
        val voice: Int = 0
    )

    /**
     * 为题目渲染音频。
     */
    fun render(question: ContrapuntalMotionQuestion): FloatArray {
        val events = buildEvents(question.motion)
        return renderEvents(events)
    }

    /**
     * 构建指定运动类型的音符事件序列。
     */
    fun buildEvents(motion: ContrapuntalMotionType): List<NoteEvent> {
        return when (motion) {
            ContrapuntalMotionType.PARALLEL -> parallelEvents()
            ContrapuntalMotionType.SIMILAR -> similarEvents()
            ContrapuntalMotionType.CONTRARY -> contraryEvents()
            ContrapuntalMotionType.OBLIQUE -> obliqueEvents()
        }
    }

    /**
     * 提取上声部音符序列（用于测试验证运动类型）。
     */
    fun extractUpperVoice(motion: ContrapuntalMotionType): List<Int> {
        return buildEvents(motion).filter { it.voice == VOICE_UPPER }.map { it.midi }
    }

    /**
     * 提取下声部音符序列（用于测试验证运动类型）。
     */
    fun extractLowerVoice(motion: ContrapuntalMotionType): List<Int> {
        return buildEvents(motion).filter { it.voice == VOICE_LOWER }.map { it.midi }
    }

    // ── 各运动类型音符编排 ──────────────────────────────────

    /**
     * 平行进行：上声部 C4→E4→G4→C5 + 下声部 C3→E3→G3→C4
     * 两个声部严格平行（均为 +4/+3/+5 半音偏移），始终保持八度关系。
     */
    private fun parallelEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val upper = listOf(60, 64, 67, 72) // C4, E4, G4, C5
        val lower = listOf(48, 52, 55, 60) // C3, E3, G3, C4 (exactly one octave below)
        for (i in upper.indices) {
            val onset = i * NOTE_MS
            events.add(NoteEvent(upper[i], onset, NOTE_MS, gain = UPPER_GAIN, voice = VOICE_UPPER))
            events.add(NoteEvent(lower[i], onset, NOTE_MS, gain = LOWER_GAIN, voice = VOICE_LOWER))
        }
        return events
    }

    /**
     * 同向进行：上声部 C4→E4→G4→C5（大步上行）+ 下声部 G3→A3→B3→C4（小步上行）
     * 两个声部同向上行，但上声部步幅更大，音程距离逐渐扩大。
     */
    private fun similarEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val upper = listOf(60, 64, 67, 72) // C4→E4→G4→C5 (+4,+3,+5)
        val lower = listOf(55, 57, 59, 60) // G3→A3→B3→C4 (+2,+2,+1)
        for (i in upper.indices) {
            val onset = i * NOTE_MS
            events.add(NoteEvent(upper[i], onset, NOTE_MS, gain = UPPER_GAIN, voice = VOICE_UPPER))
            events.add(NoteEvent(lower[i], onset, NOTE_MS, gain = LOWER_GAIN, voice = VOICE_LOWER))
        }
        return events
    }

    /**
     * 反向进行：上声部 C4→E4→G4→C5（上行）+ 下声部 C5→A4→F4→D4（下行）
     * 两个声部朝相反方向运动——一个攀升，一个下落。
     */
    private fun contraryEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val upper = listOf(60, 64, 67, 72) // C4→E4→G4→C5 (ascending)
        val lower = listOf(72, 69, 65, 62) // C5→A4→F4→D4 (descending)
        for (i in upper.indices) {
            val onset = i * NOTE_MS
            events.add(NoteEvent(upper[i], onset, NOTE_MS, gain = UPPER_GAIN, voice = VOICE_UPPER))
            events.add(NoteEvent(lower[i], onset, NOTE_MS, gain = LOWER_GAIN, voice = VOICE_LOWER))
        }
        return events
    }

    /**
     * 斜向进行：上声部 C4→E4→G4→C5（上行运动）+ 下声部 G3→G3→G3→G3（保持不变）
     * 下声部像踏板音一样保持不动，上声部运动。
     */
    private fun obliqueEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val upper = listOf(60, 64, 67, 72) // C4→E4→G4→C5 (ascending, moving)
        val lower = listOf(55, 55, 55, 55) // G3→G3→G3→G3 (held — pedal tone)
        for (i in upper.indices) {
            val onset = i * NOTE_MS
            events.add(NoteEvent(upper[i], onset, NOTE_MS, gain = UPPER_GAIN, voice = VOICE_UPPER))
            events.add(NoteEvent(lower[i], onset, NOTE_MS, gain = LOWER_GAIN, voice = VOICE_LOWER))
        }
        return events
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样（多声部混合）。
     *
     * 每个事件独立合成后按 onset 叠加到输出缓冲区，最后归一化。
     */
    fun renderEvents(events: List<NoteEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐事件合成并叠加
        for (event in events) {
            val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val wave = generateNote(midiToFreq(event.midi), noteSamples, event.gain)
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
            // 软限幅保护
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
    fun estimateDurationMs(motion: ContrapuntalMotionType): Long {
        val events = buildEvents(motion)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     *
     * 基频 + 5 个谐波（幅度递减），模拟钢琴音色。
     * 高于奈奎斯特频率的谐波自动跳过以避免混叠。
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
        const val LEAD_SILENCE_MS = 400.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_MS = 500.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 400.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.8f

        /** 上声部增益（旋律，略高）。 */
        const val UPPER_GAIN = 0.9f

        /** 下声部增益（支撑，略低）。 */
        const val LOWER_GAIN = 0.6f

        /** 声部标记常量。 */
        const val VOICE_UPPER = 0
        const val VOICE_LOWER = 1

        /** 每段旋律的音符数。 */
        const val NOTES_PER_CLIP = 4

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
