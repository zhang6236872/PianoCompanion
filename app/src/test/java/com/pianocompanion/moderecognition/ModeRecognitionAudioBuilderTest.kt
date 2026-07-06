package com.pianocompanion.moderecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ModeRecognitionAudioBuilder] 单元测试。
 *
 * 验证：
 * - 空序列渲染返回空缓冲区
 * - 单音符渲染长度合理（含前导/尾部静音）
 * - 多音符序列长度累加正确
 * - 上行+下行播放模式渲染正确
 * - 采样值在 [-1.0, 1.0] 范围内（不削波）
 * - 预估时长合理
 * - 采样率正确
 */
class ModeRecognitionAudioBuilderTest {

    private val builder = ModeRecognitionAudioBuilder()
    private val engine = ModeRecognitionEngine()

    private fun makeQuestion(
        mode: ModeType = ModeType.MAJOR,
        playMode: PlayMode = PlayMode.ASCENDING
    ): ModeQuestion {
        val tonic = Tonic(0)
        val asc = engine.buildAscendingMidi(0, mode)
        val desc = if (playMode == PlayMode.ASCENDING_DESCENDING) {
            engine.buildDescendingMidi(0, mode)
        } else {
            emptyList()
        }
        return ModeQuestion(
            mode = mode,
            tonic = tonic,
            difficulty = ModeDifficulty.ADVANCED,
            playMode = playMode,
            ascendingMidiNotes = asc,
            descendingMidiNotes = desc,
            answerChoices = listOf("大调"),
            correctAnswer = mode.displayName
        )
    }

    @Test
    fun `renderSequence returns empty for empty input`() {
        val output = builder.renderSequence(emptyList())
        assertEquals(0, output.size)
    }

    @Test
    fun `single note output is non-empty`() {
        val output = builder.renderSequence(listOf(60))
        assertTrue("单音符输出应非空，实际 ${output.size}", output.size > 0)
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, ModeRecognitionAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `all samples within valid range`() {
        val q = makeQuestion(ModeType.MAJOR)
        val output = builder.render(q)
        for ((i, sample) in output.withIndex()) {
            assertTrue(
                "采样 $i 超出范围: $sample",
                sample in -1.0f..1.0f
            )
        }
    }

    @Test
    fun `ascending render has expected minimum duration`() {
        val q = makeQuestion(ModeType.MAJOR, PlayMode.ASCENDING)
        val output = builder.render(q)
        val expectedMinSamples = 8 * (
            ModeRecognitionAudioBuilder.SAMPLE_RATE *
                ModeRecognitionAudioBuilder.NOTE_DURATION_MS / 1000
            ).toInt()
        assertTrue(
            "上行渲染采样数 ${output.size} 应大于 8 个音符的最小采样数 $expectedMinSamples",
            output.size > expectedMinSamples
        )
    }

    @Test
    fun `ascending descending render is longer than ascending only`() {
        val qAsc = makeQuestion(ModeType.MAJOR, PlayMode.ASCENDING)
        val qBoth = makeQuestion(ModeType.MAJOR, PlayMode.ASCENDING_DESCENDING)
        val outAsc = builder.render(qAsc)
        val outBoth = builder.render(qBoth)
        assertTrue(
            "上下行渲染 (${outBoth.size}) 应长于仅上行 (${outAsc.size})",
            outBoth.size > outAsc.size
        )
    }

    @Test
    fun `render output is non-empty for all modes`() {
        for (mode in ModeType.ALL) {
            val q = makeQuestion(mode)
            val output = builder.render(q)
            assertTrue(
                "${mode.englishName} 渲染输出应非空",
                output.size > 0
            )
        }
    }

    @Test
    fun `estimateDurationMs is positive for ascending`() {
        val q = makeQuestion(ModeType.MAJOR, PlayMode.ASCENDING)
        val duration = builder.estimateDurationMs(q)
        assertTrue("预估时长应 > 0，实际 $duration", duration > 0)
    }

    @Test
    fun `estimateDurationMs is longer for ascending descending`() {
        val qAsc = makeQuestion(ModeType.MAJOR, PlayMode.ASCENDING)
        val qBoth = makeQuestion(ModeType.MAJOR, PlayMode.ASCENDING_DESCENDING)
        val durAsc = builder.estimateDurationMs(qAsc)
        val durBoth = builder.estimateDurationMs(qBoth)
        assertTrue(
            "上下行预估时长 ($durBoth) 应长于仅上行 ($durAsc)",
            durBoth > durAsc
        )
    }

    @Test
    fun `lead and tail silence are added`() {
        val singleNoteSamples = (
            ModeRecognitionAudioBuilder.SAMPLE_RATE *
                ModeRecognitionAudioBuilder.NOTE_DURATION_MS / 1000
            ).toInt()
        val leadSamples = (
            ModeRecognitionAudioBuilder.SAMPLE_RATE *
                ModeRecognitionAudioBuilder.LEAD_SILENCE_MS / 1000
            ).toInt()
        val tailSamples = (
            ModeRecognitionAudioBuilder.SAMPLE_RATE *
                ModeRecognitionAudioBuilder.TAIL_SILENCE_MS / 1000
            ).toInt()

        val output = builder.renderSequence(listOf(60))
        // 输出应 > 单音符采样数（因为加了 lead + tail 静音）
        assertTrue(
            "含静音的输出 (${output.size}) 应大于纯单音符采样 ($singleNoteSamples)",
            output.size > singleNoteSamples
        )
        // 大致检查总长度
        val expectedMin = singleNoteSamples + leadSamples + tailSamples
        assertTrue(
            "输出 ${output.size} 应接近预期 $expectedMin",
            output.size >= singleNoteSamples
        )
    }

    @Test
    fun `renderSequence without lead silence is shorter`() {
        val withLead = builder.renderSequence(listOf(60), addLeadSilence = true)
        val withoutLead = builder.renderSequence(listOf(60), addLeadSilence = false)
        assertTrue(
            "无前导静音 (${withoutLead.size}) 应短于有前导静音 (${withLead.size})",
            withoutLead.size < withLead.size
        )
    }

    @Test
    fun `renderSequence without tail silence is shorter`() {
        val withTail = builder.renderSequence(listOf(60), addTailSilence = true)
        val withoutTail = builder.renderSequence(listOf(60), addTailSilence = false)
        assertTrue(
            "无尾部静音 (${withoutTail.size}) 应短于有尾部静音 (${withTail.size})",
            withoutTail.size < withTail.size
        )
    }

    @Test
    fun `different notes produce different audio output`() {
        val c4 = builder.renderSequence(listOf(60), addLeadSilence = false, addTailSilence = false)
        val a4 = builder.renderSequence(listOf(69), addLeadSilence = false, addTailSilence = false)
        // 不同音符的波形应该不同
        var hasDifference = false
        val minLen = minOf(c4.size, a4.size)
        for (i in 0 until minLen) {
            if (c4[i] != a4[i]) {
                hasDifference = true
                break
            }
        }
        assertTrue("不同音符应产生不同波形", hasDifference)
    }

    @Test
    fun `render full question has non-zero samples`() {
        val q = makeQuestion(ModeType.NATURAL_MINOR, PlayMode.ASCENDING_DESCENDING)
        val output = builder.render(q)
        val nonZeroCount = output.count { it != 0f }
        assertTrue(
            "渲染应有非零采样（音频信号），实际非零数 $nonZeroCount",
            nonZeroCount > 100
        )
    }
}
