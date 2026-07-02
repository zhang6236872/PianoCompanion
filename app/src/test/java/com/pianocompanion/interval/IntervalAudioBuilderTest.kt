package com.pianocompanion.interval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IntervalAudioBuilder] 单元测试。
 *
 * 验证：
 * - 渲染缓冲区非空且长度正确（前导静音 + 两个音符 + 间隔 + 尾部静音）
 * - 前导/尾部静音区域采样值为 0
 * - 有声音区域采样值非零
 * - 采样值在 [-1.0, 1.0] 范围内（不削波）
 * - 确定性渲染（相同参数相同输出）
 * - 不同 MIDI 音符产生不同的音频输出
 * - estimateDurationMs 返回合理值
 */
class IntervalAudioBuilderTest {

    private val builder = IntervalAudioBuilder()

    private fun createQuestion(
        lowerMidi: Int = 60,
        higherMidi: Int = 64
    ): IntervalQuestion {
        return IntervalQuestion(
            clef = IntervalClef.TREBLE,
            difficulty = IntervalDifficulty.BEGINNER,
            lowerStaffStep = 0,
            higherStaffStep = 2,
            lowerMidi = lowerMidi,
            higherMidi = higherMidi,
            lowerLetterName = "C",
            higherLetterName = "E",
            interval = Interval(IntervalNumber.THIRD, IntervalQuality.MAJOR),
            requiresQuality = false,
            answerChoices = listOf("三度", "二度", "四度", "五度"),
            correctAnswer = "三度"
        )
    }

    @Test
    fun `render produces non-empty buffer`() {
        val audio = builder.render(createQuestion(60, 64))
        assertTrue("音频缓冲区不应为空", audio.isNotEmpty())
    }

    @Test
    fun `render buffer length includes silence padding`() {
        val audio = builder.render(createQuestion(60, 64))
        // 至少应该包含前导静音 + 尾部静音
        val minExpected = IntervalAudioBuilder.SILENCE_LEAD_SAMPLES +
            IntervalAudioBuilder.SILENCE_TAIL_SAMPLES
        assertTrue(
            "音频长度 ${audio.size} 应大于最小静音长度 $minExpected",
            audio.size > minExpected
        )
    }

    @Test
    fun `leading silence region is zero`() {
        val audio = builder.render(createQuestion(60, 64))
        val leadSamples = IntervalAudioBuilder.SILENCE_LEAD_SAMPLES
        for (i in 0 until leadSamples) {
            assertEquals(
                "前导静音区 sample[$i] = ${audio[i]} 不为零",
                0.0f, audio[i], 0.0001f
            )
        }
    }

    @Test
    fun `trailing silence region is zero`() {
        val audio = builder.render(createQuestion(60, 64))
        val tailSamples = IntervalAudioBuilder.SILENCE_TAIL_SAMPLES
        val start = audio.size - tailSamples
        for (i in start until audio.size) {
            assertEquals(
                "尾部静音区 sample[$i] = ${audio[i]} 不为零",
                0.0f, audio[i], 0.0001f
            )
        }
    }

    @Test
    fun `audio region has non-zero content`() {
        val audio = builder.render(createQuestion(60, 64))
        val leadSamples = IntervalAudioBuilder.SILENCE_LEAD_SAMPLES
        val tailSamples = IntervalAudioBuilder.SILENCE_TAIL_SAMPLES
        val end = audio.size - tailSamples
        var nonZeroCount = 0
        for (i in leadSamples until end) {
            if (audio[i] != 0.0f) nonZeroCount++
        }
        assertTrue(
            "有声音区域应有非零采样，实际只有 $nonZeroCount 个",
            nonZeroCount > 100
        )
    }

    @Test
    fun `all samples within valid range`() {
        val audio = builder.render(createQuestion(60, 64))
        for (i in audio.indices) {
            assertTrue(
                "sample[$i] = ${audio[i]} 超出 [-1, 1] 范围",
                audio[i] in -1.0f..1.0f
            )
        }
    }

    @Test
    fun `deterministic rendering same input same output`() {
        val audio1 = builder.render(createQuestion(60, 64))
        val audio2 = builder.render(createQuestion(60, 64))
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `different intervals produce different audio`() {
        val audio1 = builder.render(createQuestion(60, 64)) // C4-E4 大三度
        val audio2 = builder.render(createQuestion(60, 67)) // C4-G4 纯五度
        var diffCount = 0
        val minLen = minOf(audio1.size, audio2.size)
        for (i in 0 until minLen) {
            if (audio1[i] != audio2[i]) diffCount++
        }
        assertTrue(
            "不同音程应产生不同音频，但差异样本数仅 $diffCount",
            diffCount > 100
        )
    }

    @Test
    fun `renderInterval produces non-empty buffer`() {
        val audio = builder.renderInterval(60, 64)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `renderInterval leading silence is zero`() {
        val audio = builder.renderInterval(60, 64)
        for (i in 0 until IntervalAudioBuilder.SILENCE_LEAD_SAMPLES) {
            assertEquals(0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `renderInterval trailing silence is zero`() {
        val audio = builder.renderInterval(60, 64)
        val tail = IntervalAudioBuilder.SILENCE_TAIL_SAMPLES
        for (i in audio.size - tail until audio.size) {
            assertEquals(0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `estimateDurationMs returns reasonable value`() {
        val ms = builder.estimateDurationMs()
        // 应为 lead + note + gap + note + tail = 100 + 700 + 80 + 700 + 300 = 1880ms
        assertEquals(1880L, ms)
    }

    @Test
    fun `estimateDurationMs is consistent`() {
        val ms1 = builder.estimateDurationMs()
        val ms2 = builder.estimateDurationMs()
        assertEquals(ms1, ms2)
    }

    @Test
    fun `render does not clip for low notes`() {
        // 低音 A0 = MIDI 21, B0 = MIDI 23
        val audio = builder.renderInterval(21, 23)
        for (i in audio.indices) {
            assertTrue(audio[i] in -1.0f..1.0f)
        }
    }

    @Test
    fun `render does not clip for high notes`() {
        // 高音 C8 = MIDI 108, D8 = MIDI 110
        val audio = builder.renderInterval(108, 110)
        for (i in audio.indices) {
            assertTrue(audio[i] in -1.0f..1.0f)
        }
    }

    @Test
    fun `sample rate constant is 44100`() {
        assertEquals(44100, IntervalAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `bass clef question renders correctly`() {
        val question = IntervalQuestion(
            clef = IntervalClef.BASS,
            difficulty = IntervalDifficulty.INTERMEDIATE,
            lowerStaffStep = 0,
            higherStaffStep = 4,
            lowerMidi = 43, // G2
            higherMidi = 50, // D3
            lowerLetterName = "G",
            higherLetterName = "D",
            interval = Interval(IntervalNumber.FIFTH, IntervalQuality.PERFECT),
            requiresQuality = true,
            answerChoices = listOf("纯五度", "减五度", "纯四度", "增四度"),
            correctAnswer = "纯五度"
        )
        val audio = builder.render(question)
        assertTrue(audio.isNotEmpty())
        val leadSamples = IntervalAudioBuilder.SILENCE_LEAD_SAMPLES
        var nonZero = false
        for (i in leadSamples until audio.size - IntervalAudioBuilder.SILENCE_TAIL_SAMPLES) {
            if (audio[i] != 0.0f) { nonZero = true; break }
        }
        assertTrue("低音谱号题目应产生有声音频", nonZero)
    }
}
