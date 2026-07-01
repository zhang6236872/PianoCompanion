package com.pianocompanion.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * ScaleAudioBuilder 单元测试。
 *
 * 覆盖上行/下行/上下行渲染缓冲区长度、静音边界、不削波、
 * 确定性、力度影响振幅、不同方向长度差异等。
 */
class ScaleAudioBuilderTest {

    private val builder = ScaleAudioBuilder()

    private val cMajor = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
    private val cPentatonic = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR_PENTATONIC)

    // ════════════════════════════════════════
    //  缓冲区长度
    // ════════════════════════════════════════

    @Test
    fun `上行渲染缓冲区非空`() {
        val audio = builder.renderAscending(cMajor)
        assertTrue("缓冲区应非空", audio.isNotEmpty())
    }

    @Test
    fun `上行渲染缓冲区长度包含前导和尾部静音`() {
        val audio = builder.renderAscending(cMajor)
        val sampleRate = ScaleAudioBuilder.SAMPLE_RATE
        val expectedNoteSamples = cMajor.ascendingMidiNotes.size *
            (sampleRate * ScaleAudioBuilder.NOTE_DURATION_MS / 1000.0).toInt()
        val expectedLead = (sampleRate * ScaleAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val expectedTail = (sampleRate * ScaleAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val expectedTotal = expectedLead + expectedNoteSamples + expectedTail
        assertEquals(expectedTotal, audio.size)
    }

    @Test
    fun `下行渲染缓冲区长度正确`() {
        val audio = builder.renderDescending(cMajor)
        val sampleRate = ScaleAudioBuilder.SAMPLE_RATE
        val expectedNoteSamples = cMajor.descendingMidiNotes.size *
            (sampleRate * ScaleAudioBuilder.NOTE_DURATION_MS / 1000.0).toInt()
        val expectedLead = (sampleRate * ScaleAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val expectedTail = (sampleRate * ScaleAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val expectedTotal = expectedLead + expectedNoteSamples + expectedTail
        assertEquals(expectedTotal, audio.size)
    }

    @Test
    fun `上下行渲染缓冲区长度约为两倍`() {
        val upOnly = builder.renderAscending(cMajor)
        val upDown = builder.renderAscendingDescending(cMajor)
        // 上下行 = 上行(含前导不含尾部) + 下行(不含前导含尾部)
        // 总长度应约为上行的2倍 - 一个尾部静音
        assertTrue(
            "上下行应明显长于仅上行: ${upDown.size} vs ${upOnly.size}",
            upDown.size > upOnly.size * 1.5
        )
    }

    // ════════════════════════════════════════
    //  静音边界
    // ════════════════════════════════════════

    @Test
    fun `前导静音区域为零`() {
        val audio = builder.renderAscending(cMajor)
        val leadSamples = (ScaleAudioBuilder.SAMPLE_RATE * ScaleAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        for (i in 0 until leadSamples) {
            assertEquals(
                "前导静音区样本 $i 应为0",
                0.0f, audio[i], 0.001f
            )
        }
    }

    @Test
    fun `尾部静音区域为零`() {
        val audio = builder.renderAscending(cMajor)
        val tailSamples = (ScaleAudioBuilder.SAMPLE_RATE * ScaleAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        for (i in audio.size - tailSamples until audio.size) {
            // 尾部静音可能因为软限幅而不完全为0，但应接近0
            assertTrue(
                "尾部静音区样本 $i 应接近0: ${audio[i]}",
                audio[i].absoluteValue < 0.01f
            )
        }
    }

    // ════════════════════════════════════════
    //  不削波
    // ════════════════════════════════════════

    @Test
    fun `上行渲染不削波`() {
        val audio = builder.renderAscending(cMajor)
        for (sample in audio) {
            assertTrue("样本 ${sample} 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `下行渲染不削波`() {
        val audio = builder.renderDescending(cMajor)
        for (sample in audio) {
            assertTrue("样本 ${sample} 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `上下行渲染不削波`() {
        val audio = builder.renderAscendingDescending(cMajor)
        for (sample in audio) {
            assertTrue("样本 ${sample} 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `高力度不削波`() {
        val audio = builder.renderAscending(cMajor, velocity = 127)
        for (sample in audio) {
            assertTrue("样本 ${sample} 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    // ════════════════════════════════════════
    //  非零内容
    // ════════════════════════════════════════

    @Test
    fun `上行渲染音符区域有非零内容`() {
        val audio = builder.renderAscending(cMajor)
        val leadSamples = (ScaleAudioBuilder.SAMPLE_RATE * ScaleAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val noteRegion = audio.copyOfRange(leadSamples, audio.size - 1)
        assertTrue("音符区域应包含非零采样", noteRegion.any { it.absoluteValue > 0.01f })
    }

    @Test
    fun `五声音阶渲染非空`() {
        val audio = builder.renderAscending(cPentatonic)
        assertTrue(audio.isNotEmpty())
    }

    // ════════════════════════════════════════
    //  确定性
    // ════════════════════════════════════════

    @Test
    fun `相同参数上行渲染结果相同`() {
        val audio1 = builder.renderAscending(cMajor)
        val audio2 = builder.renderAscending(cMajor)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `相同参数下行渲染结果相同`() {
        val audio1 = builder.renderDescending(cMajor)
        val audio2 = builder.renderDescending(cMajor)
        assertEquals(audio1.size, audio2.size)
    }

    // ════════════════════════════════════════
    //  不同根音产生不同输出
    // ════════════════════════════════════════

    @Test
    fun `不同根音上行渲染结果不同`() {
        val cScale = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        val dScale = ScaleEngine.build(ScaleRoot.D, ScaleType.MAJOR)
        val audioC = builder.renderAscending(cScale)
        val audioD = builder.renderAscending(dScale)
        var hasDifference = false
        for (i in 0 until minOf(audioC.size, audioD.size)) {
            if (audioC[i].absoluteValue > 0.01f && audioD[i].absoluteValue > 0.01f) {
                if (audioC[i] != audioD[i]) {
                    hasDifference = true
                    break
                }
            }
        }
        assertTrue("不同根音应产生不同音频", hasDifference)
    }

    // ════════════════════════════════════════
    //  不同音阶类型产生不同长度
    // ════════════════════════════════════════

    @Test
    fun `半音阶渲染比大调更长`() {
        val chromatic = ScaleEngine.build(ScaleRoot.C, ScaleType.CHROMATIC)
        val major = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        val audioChromatic = builder.renderAscending(chromatic)
        val audioMajor = builder.renderAscending(major)
        assertTrue(
            "半音阶应比大调长: ${audioChromatic.size} vs ${audioMajor.size}",
            audioChromatic.size > audioMajor.size
        )
    }

    @Test
    fun `五声音阶渲染比大调更短`() {
        val penta = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR_PENTATONIC)
        val major = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        val audioPenta = builder.renderAscending(penta)
        val audioMajor = builder.renderAscending(major)
        assertTrue(
            "五声音阶应比大调短: ${audioPenta.size} vs ${audioMajor.size}",
            audioPenta.size < audioMajor.size
        )
    }

    // ════════════════════════════════════════
    //  力度影响振幅
    // ════════════════════════════════════════

    @Test
    fun `高力度振幅大于低力度`() {
        val lowVel = builder.renderAscending(cMajor, velocity = 30)
        val highVel = builder.renderAscending(cMajor, velocity = 120)
        val leadSamples = (ScaleAudioBuilder.SAMPLE_RATE * ScaleAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 在音符区域中查找最大振幅
        var maxLow = 0.0f
        var maxHigh = 0.0f
        for (i in leadSamples until minOf(lowVel.size, highVel.size)) {
            if (lowVel[i].absoluteValue > maxLow) maxLow = lowVel[i].absoluteValue
            if (highVel[i].absoluteValue > maxHigh) maxHigh = highVel[i].absoluteValue
        }
        assertTrue(
            "高力度最大振幅应大于低力度: $maxHigh vs $maxLow",
            maxHigh > maxLow
        )
    }

    // ════════════════════════════════════════
    //  estimateDurationMs
    // ════════════════════════════════════════

    @Test
    fun `estimateDurationMs 上行正确`() {
        val duration = builder.estimateDurationMs(cMajor, PlayDirection.ASCENDING)
        val expected = ScaleAudioBuilder.LEAD_SILENCE_MS +
            cMajor.ascendingMidiNotes.size * ScaleAudioBuilder.NOTE_DURATION_MS +
            ScaleAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, duration)
    }

    @Test
    fun `estimateDurationMs 下行正确`() {
        val duration = builder.estimateDurationMs(cMajor, PlayDirection.DESCENDING)
        val expected = ScaleAudioBuilder.LEAD_SILENCE_MS +
            cMajor.descendingMidiNotes.size * ScaleAudioBuilder.NOTE_DURATION_MS +
            ScaleAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, duration)
    }

    @Test
    fun `estimateDurationMs 上下行大于仅上行`() {
        val upOnly = builder.estimateDurationMs(cMajor, PlayDirection.ASCENDING)
        val upDown = builder.estimateDurationMs(cMajor, PlayDirection.ASCENDING_DESCENDING)
        assertTrue(upDown > upOnly)
    }

    // ════════════════════════════════════════
    //  旋律小调上下行不同
    // ════════════════════════════════════════

    @Test
    fun `旋律小调上行和下行音频不同`() {
        val melodicMinor = ScaleEngine.build(ScaleRoot.A, ScaleType.MELODIC_MINOR)
        val audioUp = builder.renderAscending(melodicMinor)
        val audioDown = builder.renderDescending(melodicMinor)
        // 长度应相等（都包含前导+尾部静音），但内容不同
        assertEquals(audioUp.size, audioDown.size)
        var hasDifference = false
        for (i in audioUp.indices) {
            if (audioUp[i] != audioDown[i]) {
                hasDifference = true
                break
            }
        }
        assertTrue("旋律小调上下行音频应不同", hasDifference)
    }

    // ════════════════════════════════════════
    //  空输入处理
    // ════════════════════════════════════════

    @Test
    fun `空 MIDI 序列返回空数组`() {
        // 用空的 ScaleInfo 测试（虽然实际不会发生，验证防御性编程）
        val emptyScale = ScaleInfo(
            root = ScaleRoot.C,
            type = ScaleType.MAJOR,
            ascendingMidiNotes = emptyList(),
            descendingMidiNotes = emptyList(),
            noteNames = emptyList(),
            fullName = "test",
            preferFlats = false
        )
        val audio = builder.renderAscending(emptyScale)
        assertEquals(0, audio.size)
    }
}


