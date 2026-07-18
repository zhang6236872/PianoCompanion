package com.pianocompanion.voicecounttraining

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 声部数量听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [VoiceCountQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 声部数量听辨：**
 *
 * 渲染一段 **block chord**：所有音同时起、同时落（同一 onset、同一 duration）。用户据此判断
 * 同时鸣响的音有多少个。唯一区分依据是**和声密度**——同时鸣响的音越多，频谱越丰富、越"厚"。
 *
 * 为使数量可被听辨（而非融合成单一音色），采用以下设计：
 * 1. **加法合成钢琴音色**：每个音 = 基频 + 4 个递减谐波，模拟钢琴音色，使每个音有独立的基频。
 * 2. **轻微失谐**：每个音按其声部索引做 ±[DETUNE_MAX_CENTS] cents 的确定性失谐（避免谐波完全对齐
 *    导致融合；轻微失谐制造可感知的"多源"质感与轻微拍音，利于数清声部）。
 * 3. **间距由难度控制**：宽间距（初级）每个音基频相距大、清晰可辨；密集间距（高级）形成音簇、
 *    相互融合，最难数清。
 *
 * 渲染流程：
 * 1. 为 voicing 中每个 MIDI 音生成一段加法合成音色（指数衰减包络）。
 * 2. 所有音色对齐叠加到同一时间窗（block chord）。
 * 3. [render] 做软限幅（[softClip]）防削波，输出 [-1,1]；[renderRaw] 返回叠加后未限幅缓冲区，
 *    便于单元测试验证"声部越多 → 原始能量越高"。
 *
 * @param sampleRate 采样率
 */
class VoiceCountAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /** 为题目渲染音频（软限幅后，[-1,1]，可直接播放）。 */
    fun render(question: VoiceCountQuestion): FloatArray {
        val raw = renderRaw(question)
        val out = FloatArray(raw.size)
        for (i in raw.indices) {
            out[i] = softClip(raw[i])
        }
        return out
    }

    /**
     * 为题目渲染音频（叠加后未限幅）。
     *
     * 声部越多 → 叠加的能量越高（见 [sustainEnergy]）。供单元测试验证能量单调性。
     */
    fun renderRaw(question: VoiceCountQuestion): FloatArray {
        val chordSamples = (sampleRate * question.durationMs / 1000.0).toInt()
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val totalLength = leadSamples + chordSamples + tailSamples

        val output = FloatArray(totalLength)

        // 每个 MIDI 音生成一段加法合成音色，对齐叠加到 leadSamples 起点
        question.voicing.forEachIndexed { idx, midi ->
            val detuneCents = detuneForVoice(idx)
            val tone = buildTone(midi, detuneCents, chordSamples)
            val offset = leadSamples
            for (j in tone.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += tone[j]
                }
            }
        }

        return output
    }

    /**
     * 生成单个音的加法合成音色（钢琴风格：基频 + 谐波，指数衰减包络）。
     *
     * @param midi MIDI 音高
     * @param detuneCents 失谐量（cents，可为负）
     * @param numSamples 样本数
     */
    fun buildTone(midi: Int, detuneCents: Double, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val freq = midiToFreq(midi) * centsToRatio(detuneCents)
        // 奈奎斯特保护：基频不超过采样率的一半
        val safeFreq = freq.coerceAtMost(sampleRate / 2.0 * 0.9)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * TONE_DECAY_MS / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            // 基频 + 4 个递减谐波
            for (h in 1..HARMONIC_COUNT) {
                val harmonicFreq = safeFreq * h
                if (harmonicFreq >= sampleRate / 2.0) break  // 谐波超过奈奎斯特则跳过
                val amp = AMPLITUDE_PER_VOICE / h   // 1/n 谐波衰减
                sample += sin(2.0 * PI * harmonicFreq * t) * amp
            }
            wave[i] = (sample * envelope).toFloat()
        }
        return wave
    }

    /** MIDI → 频率（A4=440Hz, MIDI 69）。 */
    fun midiToFreq(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    /** cents → 频率比。 */
    fun centsToRatio(cents: Double): Double = Math.pow(2.0, cents / 1200.0)

    /** 第 idx 个声部（0-based）的确定性失谐量（cents）。交替正负，幅值随 idx 递增但封顶。 */
    fun detuneForVoice(idx: Int): Double {
        val sign = if (idx % 2 == 0) 1.0 else -1.0
        val mag = (idx + 1).coerceAtMost(3) * (DETUNE_MAX_CENTS / 3.0)
        return sign * mag
    }

    /**
     * 计算缓冲区在持续段（sustain region）内的 RMS 能量。
     *
     * 用于单元测试：声部越多，原始（未限幅）叠加能量越高。
     * 持续段 = 跳过前导静音、取和弦前 [sustainFraction] 比例的样本（避开尾部衰减过低）。
     */
    fun sustainEnergy(buffer: FloatArray, question: VoiceCountQuestion, sustainFraction: Double = 0.5): Double {
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val chordSamples = (sampleRate * question.durationMs / 1000.0).toInt()
        val windowLen = (chordSamples * sustainFraction).toInt().coerceAtLeast(1)
        var sumSq = 0.0
        var count = 0
        for (i in leadSamples until minOf(leadSamples + windowLen, buffer.size)) {
            sumSq += buffer[i].toDouble() * buffer[i]
            count++
        }
        return if (count > 0) sqrt(sumSq / count) else 0.0
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: VoiceCountQuestion): Long {
        return LEAD_SILENCE_MS.toLong() + question.durationMs + TAIL_SILENCE_MS.toLong()
    }

    /** 软限幅函数（保留 block chord 的整体厚度，仅防削波）。 */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + abs(x) / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 350.0

        /** 单音衰减时间常数（毫秒）。 */
        const val TONE_DECAY_MS = 700.0

        /** 每个音的基频振幅（叠加后由软限幅控制）。 */
        const val AMPLITUDE_PER_VOICE = 0.18

        /** 谐波个数（含基频）。 */
        const val HARMONIC_COUNT = 5

        /** 最大失谐量（cents）。 */
        const val DETUNE_MAX_CENTS = 9.0

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f

        /** 用确定性种子渲染一段音频（便于测试中复现）。 */
        fun renderStatic(
            difficulty: VoiceCountDifficulty,
            seed: Long,
            sampleRate: Int = DEFAULT_SAMPLE_RATE
        ): Pair<VoiceCountQuestion, FloatArray> {
            val engine = VoiceCountEngine.withSeed(seed)
            val q = engine.generate(difficulty)
            val builder = VoiceCountAudioBuilder(sampleRate)
            return q to builder.render(q)
        }

        @Suppress("unused")
        private val UNUSED_FOR_JVM_STATIC_INIT = Unit
    }
}
