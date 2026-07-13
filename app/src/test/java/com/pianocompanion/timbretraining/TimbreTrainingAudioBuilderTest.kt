package com.pianocompanion.timbretraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音色辨识训练音频构建器单元测试。
 */
class TimbreTrainingAudioBuilderTest {

    private val builder = TimbreTrainingAudioBuilder()

    // ── 基本 PCM 缓冲区验证 ────────────────────────────

    @Test
    fun `渲染所有乐器返回非空缓冲区`() {
        for (instrument in TimbreInstrument.ALL) {
            val pcm = builder.renderInstrument(instrument)
            assertTrue("${instrument.englishName} 缓冲区为空", pcm.isNotEmpty())
        }
    }

    @Test
    fun `渲染缓冲区长度正确`() {
        val durationMs = 1500L
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * durationMs / 1000.0).toInt()
        val expectedLength = leadSamples + noteSamples + tailSamples

        for (instrument in TimbreInstrument.ALL) {
            val pcm = builder.renderInstrument(instrument, durationMs)
            assertEquals("${instrument.englishName} 缓冲区长度不符", expectedLength, pcm.size)
        }
    }

    @Test
    fun `所有采样在-1到1范围内`() {
        for (instrument in TimbreInstrument.ALL) {
            val pcm = builder.renderInstrument(instrument)
            for ((i, sample) in pcm.withIndex()) {
                assertTrue(
                    "${instrument.englishName} 采样[$i]=$sample 超出 [-1,1]",
                    sample >= -1.0f && sample <= 1.0f
                )
            }
        }
    }

    @Test
    fun `渲染的缓冲区含非零音频内容`() {
        for (instrument in TimbreInstrument.ALL) {
            val pcm = builder.renderInstrument(instrument)
            val nonZeroCount = pcm.count { it != 0.0f }
            assertTrue("${instrument.englishName} 缓冲区全零", nonZeroCount > 0)
        }
    }

    // ── 前导静音验证 ────────────────────────────────────

    @Test
    fun `前导静音区域全零`() {
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val pcm = builder.renderInstrument(TimbreInstrument.PIANO)
        for (i in 0 until leadSamples) {
            assertEquals("前导静音区域[$i] 应为 0", 0.0f, pcm[i], 0.0001f)
        }
    }

    @Test
    fun `尾部静音区域全零`() {
        val tailSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val pcm = builder.renderInstrument(TimbreInstrument.PIANO)
        for (i in (pcm.size - tailSamples) until pcm.size) {
            assertEquals("尾部静音区域[$i] 应为 0", 0.0f, pcm[i], 0.0001f)
        }
    }

    // ── 音色差异性验证（核心） ─────────────────────────

    @Test
    fun `不同乐器的PCM不同`() {
        val piano = builder.renderInstrument(TimbreInstrument.PIANO)
        val flute = builder.renderInstrument(TimbreInstrument.FLUTE)
        val trumpet = builder.renderInstrument(TimbreInstrument.TRUMPET)

        // 至少钢琴和长笛应该显著不同
        var diff = 0
        for (i in piano.indices) {
            if (kotlin.math.abs(piano[i] - flute[i]) > 0.01f) diff++
        }
        assertTrue("钢琴和长笛的PCM过于相似", diff > 100)
    }

    @Test
    fun `所有6种乐器两两不同`() {
        val pcms = TimbreInstrument.ALL.associateWith { builder.renderInstrument(it) }

        for (i in TimbreInstrument.ALL.indices) {
            for (j in (i + 1) until TimbreInstrument.ALL.size) {
                val a = TimbreInstrument.ALL[i]
                val b = TimbreInstrument.ALL[j]
                val pcmA = pcms[a]!!
                val pcmB = pcms[b]!!
                var diff = 0
                for (k in pcmA.indices) {
                    if (kotlin.math.abs(pcmA[k] - pcmB[k]) > 0.005f) diff++
                }
                assertTrue("${a.englishName} 和 ${b.englishName} 的PCM完全相同", diff > 50)
            }
        }
    }

    @Test
    fun `钢琴具有衰减特征（峰值在前半段）`() {
        val pcm = builder.renderInstrument(TimbreInstrument.PIANO, durationMs = 1500)
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * 1500L / 1000.0).toInt()

        val firstQuarterMax = (0 until noteSamples / 4).maxOfOrNull { kotlin.math.abs(pcm[leadSamples + it]) } ?: 0f
        val lastQuarterMax = (noteSamples * 3 / 4 until noteSamples).maxOfOrNull { kotlin.math.abs(pcm[leadSamples + it]) } ?: 0f

        assertTrue(
            "钢琴前1/4最大振幅($firstQuarterMax)应大于后1/4($lastQuarterMax)",
            firstQuarterMax > lastQuarterMax
        )
    }

    @Test
    fun `长笛具有持续特征（后半段振幅不显著衰减）`() {
        val pcm = builder.renderInstrument(TimbreInstrument.FLUTE, durationMs = 1500)
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * 1500L / 1000.0).toInt()

        val firstQuarterMax = (0 until noteSamples / 4).maxOfOrNull { kotlin.math.abs(pcm[leadSamples + it]) } ?: 0f
        val midMax = (noteSamples * 3 / 8 until noteSamples * 5 / 8).maxOfOrNull { kotlin.math.abs(pcm[leadSamples + it]) } ?: 0f

        // 长笛持续型，中部振幅应与前部相近（不显著衰减）
        assertTrue(
            "长笛中部最大振幅($midMax)应与前1/4($firstQuarterMax)相近",
            midMax > firstQuarterMax * 0.5
        )
    }

    @Test
    fun `小号比长笛更明亮（谐波含量更多导致波形变化更剧烈）`() {
        // 小号有更多谐波，波形更接近锯齿/方波
        // 长笛接近纯正弦，波形平滑
        //
        // 测量指标: 归一化总变差 = totalVariation / RMS
        // 由于加法合成对谐波幅度之和归一化，谐波丰富的乐器基频被压缩
        // （小号基频归一化后仅 0.377，长笛 0.870），绝对总变差反而更小。
        // 使用 TV/RMS 可消除幅度差异，纯粹反映波形复杂度（谐波含量）。
        val trumpetPcm = builder.renderInstrument(TimbreInstrument.TRUMPET, durationMs = 500)
        val flutePcm = builder.renderInstrument(TimbreInstrument.FLUTE, durationMs = 500)
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * 500L / 1000.0).toInt()

        // 在音符中间段测量（避开起音/释放瞬态）
        val midStart = leadSamples + noteSamples / 3
        val midEnd = leadSamples + noteSamples * 2 / 3

        // 归一化亮度 = 总变差 / RMS，消除幅度差异，纯粹反映波形形状
        val trumpetBrightness = totalVariation(trumpetPcm, midStart, midEnd) /
            rms(trumpetPcm, midStart, midEnd)
        val fluteBrightness = totalVariation(flutePcm, midStart, midEnd) /
            rms(flutePcm, midStart, midEnd)

        assertTrue(
            "小号归一化亮度($trumpetBrightness)应大于长笛($fluteBrightness)（更丰富的谐波→波形更复杂）",
            trumpetBrightness > fluteBrightness
        )
    }

    @Test
    fun `钢琴比吉他衰减更快`() {
        val pianoPcm = builder.renderInstrument(TimbreInstrument.PIANO, durationMs = 2000)
        val guitarPcm = builder.renderInstrument(TimbreInstrument.GUITAR, durationMs = 2000)
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * 2000L / 1000.0).toInt()

        // 计算后半段的 RMS
        val pianoLateRms = rms(pianoPcm, leadSamples + noteSamples / 2, leadSamples + noteSamples)
        val guitarLateRms = rms(guitarPcm, leadSamples + noteSamples / 2, leadSamples + noteSamples)

        // 钢琴衰减系数(3.5)比吉他(1.8)大，后半段应更安静
        assertTrue(
            "钢琴后半段RMS($pianoLateRms)应小于吉他($guitarLateRms)",
            pianoLateRms < guitarLateRms
        )
    }

    // ── render 方法 ────────────────────────────────────

    @Test
    fun `render使用question参数`() {
        val question = TimbreTrainingQuestion(
            instrument = TimbreInstrument.VIOLIN,
            difficulty = TimbreTrainingDifficulty.BEGINNER,
            noteDurationMs = 800,
            answerChoices = listOf("Violin  小提琴"),
            correctAnswer = "Violin  小提琴"
        )
        val pcm = builder.render(question)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `不同durationMs产生不同长度缓冲区`() {
        val short = builder.renderInstrument(TimbreInstrument.PIANO, durationMs = 500)
        val long = builder.renderInstrument(TimbreInstrument.PIANO, durationMs = 2000)
        assertTrue(long.size > short.size)
    }

    // ── estimateDurationMs ─────────────────────────────

    @Test
    fun `estimateDurationMs包含前后静音`() {
        val question = TimbreTrainingQuestion(
            instrument = TimbreInstrument.PIANO,
            difficulty = TimbreTrainingDifficulty.BEGINNER,
            noteDurationMs = 1000,
            answerChoices = listOf("Piano  钢琴"),
            correctAnswer = "Piano  钢琴"
        )
        val estimated = builder.estimateDurationMs(question)
        val expected = (TimbreTrainingAudioBuilder.LEAD_SILENCE_MS + 1000 + TimbreTrainingAudioBuilder.TAIL_SILENCE_MS).toLong()
        assertEquals(expected, estimated)
    }

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同乐器多次渲染结果一致`() {
        val pcm1 = builder.renderInstrument(TimbreInstrument.CLARINET)
        val pcm2 = builder.renderInstrument(TimbreInstrument.CLARINET)
        assertArrayEquals(pcm1, pcm2, 0.0001f)
    }

    @Test
    fun `所有乐器渲染成功无异常`() {
        for (instrument in TimbreInstrument.ALL) {
            for (duration in listOf(300L, 800L, 1500L, 3000L)) {
                val pcm = builder.renderInstrument(instrument, duration)
                assertTrue("${instrument.englishName} @ ${duration}ms 为空", pcm.isNotEmpty())
            }
        }
    }

    // ── 自定义采样率 ────────────────────────────────────

    @Test
    fun `自定义采样率产生不同长度缓冲区`() {
        val b22050 = TimbreTrainingAudioBuilder(sampleRate = 22050)
        val b44100 = TimbreTrainingAudioBuilder(sampleRate = 44100)
        val pcm22050 = b22050.renderInstrument(TimbreInstrument.PIANO, durationMs = 1000)
        val pcm44100 = b44100.renderInstrument(TimbreInstrument.PIANO, durationMs = 1000)
        assertTrue(pcm44100.size > pcm22050.size)
    }

    @Test
    fun `22050采样率也产生有效音频`() {
        val b = TimbreTrainingAudioBuilder(sampleRate = 22050)
        val pcm = b.renderInstrument(TimbreInstrument.TRUMPET, durationMs = 500)
        assertTrue(pcm.isNotEmpty())
        val nonZero = pcm.count { it != 0.0f }
        assertTrue("22050Hz 下音频内容为空", nonZero > 0)
    }

    // ── 起音验证 ────────────────────────────────────────

    @Test
    fun `钢琴快速起音（前几ms内有非零值）`() {
        val pcm = builder.renderInstrument(TimbreInstrument.PIANO, durationMs = 1500)
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 起音区域 0.5% ≈ 7.5ms @ 1500ms
        val attackEnd = leadSamples + (1 * 1500L * 44100 / 1000 / 200).toInt()
        var hasNonZero = false
        for (i in leadSamples until minOf(attackEnd, pcm.size)) {
            if (kotlin.math.abs(pcm[i]) > 0.01f) {
                hasNonZero = true
                break
            }
        }
        assertTrue("钢琴起音区域内应有非零信号", hasNonZero)
    }

    @Test
    fun `长笛柔和起音（起音阶段振幅低于峰值）`() {
        val pcm = builder.renderInstrument(TimbreInstrument.FLUTE, durationMs = 1500)
        val leadSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * TimbreTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (TimbreTrainingAudioBuilder.DEFAULT_SAMPLE_RATE * 1500L / 1000.0).toInt()

        // 起音区域 5% ≈ 75ms
        val attackSamples = (noteSamples * 0.05).toInt()
        val earlyMax = (0 until attackSamples / 4).maxOfOrNull { kotlin.math.abs(pcm[leadSamples + it]) } ?: 0f
        val midMax = (noteSamples / 3 until noteSamples / 2).maxOfOrNull { kotlin.math.abs(pcm[leadSamples + it]) } ?: 0f

        assertTrue(
            "长笛极早期振幅($earlyMax)应低于中期($midMax)（柔和起音）",
            earlyMax < midMax
        )
    }

    // ── 辅助函数 ────────────────────────────────────────

    private fun totalVariation(pcm: FloatArray, start: Int, end: Int): Double {
        if (start >= end - 1) return 0.0
        var sum = 0.0
        for (i in start until minOf(end - 1, pcm.size - 1)) {
            sum += kotlin.math.abs(pcm[i + 1] - pcm[i])
        }
        val count = minOf(end - 1, pcm.size - 1) - start
        return if (count > 0) sum / count else 0.0
    }

    private fun rms(pcm: FloatArray, start: Int, end: Int): Double {
        if (start >= end) return 0.0
        var sum = 0.0
        var count = 0
        for (i in start until minOf(end, pcm.size)) {
            sum += pcm[i].toDouble() * pcm[i]
            count++
        }
        return if (count > 0) kotlin.math.sqrt(sum / count) else 0.0
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("Index $i", expected[i], actual[i], delta)
        }
    }
}
