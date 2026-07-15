package com.pianocompanion.modulationrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转调辨识训练音频构建器单元测试。
 *
 * 验证：
 * - PCM 缓冲区非空且有效
 * - 采样值在 [-1.0, 1.0] 范围内
 * - 不同转调类型产生不同的和弦序列
 * - 调性变化检测正确
 * - 音频持续时间合理
 */
class ModulationAudioBuilderTest {

    private val builder = ModulationAudioBuilder()

    // ── 缓冲区有效性 ──────────────────────────────────────────

    @Test
    fun `render 产生非空缓冲区`() {
        ModulationType.ALL.forEach { type ->
            val question = ModulationQuestion(
                modulation = type,
                difficulty = ModulationDifficulty.ADVANCED,
                seed = 42L,
                answerChoices = listOf(type.fullLabel),
                correctAnswer = type.fullLabel
            )
            val buffer = builder.render(question)

            assertTrue("转调类型 $type 的缓冲区不应为空", buffer.isNotEmpty())
        }
    }

    @Test
    fun `所有采样值在有效范围内`() {
        ModulationType.ALL.forEach { type ->
            val question = ModulationQuestion(
                modulation = type,
                difficulty = ModulationDifficulty.ADVANCED,
                seed = 42L,
                answerChoices = listOf(type.fullLabel),
                correctAnswer = type.fullLabel
            )
            val buffer = builder.render(question)

            buffer.forEach { sample ->
                assertTrue("采样值 $sample 应 >= -1.0", sample >= -1.0f)
                assertTrue("采样值 $sample 应 <= 1.0", sample <= 1.0f)
            }
        }
    }

    @Test
    fun `缓冲区包含非零内容`() {
        ModulationType.ALL.forEach { type ->
            val question = ModulationQuestion(
                modulation = type,
                difficulty = ModulationDifficulty.ADVANCED,
                seed = 42L,
                answerChoices = listOf(type.fullLabel),
                correctAnswer = type.fullLabel
            )
            val buffer = builder.render(question)

            val nonZeroCount = buffer.count { it != 0.0f }
            assertTrue("转调类型 $type 的缓冲区应有非零采样", nonZeroCount > 0)
        }
    }

    @Test
    fun `缓冲区有静音前导和尾部`() {
        val question = ModulationQuestion(
            modulation = ModulationType.TO_DOMINANT,
            difficulty = ModulationDifficulty.ADVANCED,
            seed = 42L,
            answerChoices = listOf(ModulationType.TO_DOMINANT.fullLabel),
            correctAnswer = ModulationType.TO_DOMINANT.fullLabel
        )
        val buffer = builder.render(question)

        // 前导静音应接近零
        val leadSamples = (ModulationAudioBuilder.DEFAULT_SAMPLE_RATE * ModulationAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        for (i in 0 until minOf(leadSamples, buffer.size)) {
            assertEquals("前导采样 $i 应为 0", 0.0f, buffer[i], 0.001f)
        }
    }

    // ── 和弦事件 ──────────────────────────────────────────

    @Test
    fun `每种类型生成4个和弦`() {
        ModulationType.ALL.forEach { type ->
            val events = builder.buildChordEvents(type)
            assertEquals("转调类型 $type 应有 ${ModulationAudioBuilder.CHORD_COUNT} 个和弦",
                ModulationAudioBuilder.CHORD_COUNT, events.size)
        }
    }

    @Test
    fun `每个和弦有3个音符`() {
        ModulationType.ALL.forEach { type ->
            val events = builder.buildChordEvents(type)
            events.forEach { event ->
                assertEquals("和弦应有 ${ModulationAudioBuilder.NOTES_PER_CHORD} 个音符",
                    ModulationAudioBuilder.NOTES_PER_CHORD, event.midiNotes.size)
            }
        }
    }

    @Test
    fun `和弦按时间顺序排列`() {
        ModulationType.ALL.forEach { type ->
            val events = builder.buildChordEvents(type)
            for (i in 1 until events.size) {
                assertTrue(
                    "和弦 ${i} 的起始时间应大于和弦 ${i - 1}",
                    events[i].onsetMs >= events[i - 1].onsetMs
                )
            }
        }
    }

    // ── 调性变化验证 ──────────────────────────────────────────

    @Test
    fun `转入属调有调性变化`() {
        assertTrue(builder.hasKeyChange(ModulationType.TO_DOMINANT))
    }

    @Test
    fun `转入下属调有调性变化`() {
        assertTrue(builder.hasKeyChange(ModulationType.TO_SUBDOMINANT))
    }

    @Test
    fun `转入关系调有调性变化`() {
        assertTrue(builder.hasKeyChange(ModulationType.TO_RELATIVE))
    }

    @Test
    fun `无转调没有调性变化`() {
        assertFalse(builder.hasKeyChange(ModulationType.NO_MODULATION))
    }

    @Test
    fun `转入属调从C到G`() {
        val keys = builder.extractKeyLabels(ModulationType.TO_DOMINANT)
        assertEquals("C", keys.first())
        assertEquals("G", keys.last())
    }

    @Test
    fun `转入下属调从C到F`() {
        val keys = builder.extractKeyLabels(ModulationType.TO_SUBDOMINANT)
        assertEquals("C", keys.first())
        assertEquals("F", keys.last())
    }

    @Test
    fun `转入关系调从C到Am`() {
        val keys = builder.extractKeyLabels(ModulationType.TO_RELATIVE)
        assertEquals("C", keys.first())
        assertEquals("Am", keys.last())
    }

    @Test
    fun `无转调始终在C`() {
        val keys = builder.extractKeyLabels(ModulationType.NO_MODULATION)
        keys.forEach { key ->
            assertEquals("无转调应始终在C调", "C", key)
        }
    }

    @Test
    fun `不同转调类型的和弦序列不同`() {
        val dominantNotes = builder.extractAllMidiNotes(ModulationType.TO_DOMINANT)
        val subdominantNotes = builder.extractAllMidiNotes(ModulationType.TO_SUBDOMINANT)
        val relativeNotes = builder.extractAllMidiNotes(ModulationType.TO_RELATIVE)
        val noModNotes = builder.extractAllMidiNotes(ModulationType.NO_MODULATION)

        // 后半段应该不同（不同转调类型）
        val dominantBack = dominantNotes.takeLast(2)
        val subdominantBack = subdominantNotes.takeLast(2)
        val relativeBack = relativeNotes.takeLast(2)

        assertFalse("属调和下属调的后半段应不同", dominantBack == subdominantBack)
        assertFalse("属调和关系调的后半段应不同", dominantBack == relativeBack)
        assertFalse("下属调和关系调的后半段应不同", subdominantBack == relativeBack)
    }

    // ── 持续时间 ──────────────────────────────────────────

    @Test
    fun `estimateDurationMs 返回合理时长`() {
        ModulationType.ALL.forEach { type ->
            val duration = builder.estimateDurationMs(type)
            // 4 和弦 * 800ms + 前导 400ms + 尾部 400ms = 4000ms
            val expectedMin = (ModulationAudioBuilder.CHORD_COUNT * ModulationAudioBuilder.CHORD_MS +
                ModulationAudioBuilder.LEAD_SILENCE_MS + ModulationAudioBuilder.TAIL_SILENCE_MS).toLong()
            assertTrue("时长 $duration 应 >= $expectedMin", duration >= expectedMin)
        }
    }

    // ── MIDI 转频率 ──────────────────────────────────────────

    @Test
    fun `midiToFreq A4 等于 440`() {
        val freq = builder.midiToFreq(69)
        assertEquals(440.0, freq, 0.01)
    }

    @Test
    fun `midiToFreq C4 接近 261_63`() {
        val freq = builder.midiToFreq(60)
        assertEquals(261.63, freq, 0.1)
    }

    @Test
    fun `midiToFreq 高八度频率翻倍`() {
        val freq1 = builder.midiToFreq(60) // C4
        val freq2 = builder.midiToFreq(72) // C5
        assertEquals(2.0, freq2 / freq1, 0.001)
    }
}
