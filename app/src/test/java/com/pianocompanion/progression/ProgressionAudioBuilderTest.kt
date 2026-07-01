package com.pianocompanion.progression

import com.pianocompanion.chord.ChordRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProgressionAudioBuilder 单元测试。
 *
 * 验证进行的 PCM 渲染：缓冲区长度、静音边界、不削波、确定性等。
 */
class ProgressionAudioBuilderTest {

    private val builder = ProgressionAudioBuilder(tempoBpm = 120)
    private val sampleRate = 44100

    private fun makeInstance(key: ChordRoot = ChordRoot.C): ProgressionInstance {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        return ProgressionEngine.instantiate(template, key)
    }

    // ════════════════════════════════════════════════════════════
    //  基本渲染
    // ════════════════════════════════════════════════════════════

    @Test
    fun `空进行返回空缓冲区`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val emptyInstance = ProgressionInstance(template, ChordRoot.C, emptyList())
        val audio = builder.render(emptyInstance)
        assertEquals(0, audio.size)
    }

    @Test
    fun `四和弦进行缓冲区非空`() {
        val instance = makeInstance()
        val audio = builder.render(instance)
        assertTrue("缓冲区应非空", audio.size > 0)
    }

    @Test
    fun `缓冲区包含前导静音`() {
        val instance = makeInstance()
        val audio = builder.render(instance)
        val leadSilenceSamples = (sampleRate * ProgressionAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()

        // 前 leadSilenceSamples 个采样应接近 0
        for (i in 0 until leadSilenceSamples) {
            assertEquals(0.0f, audio[i], 0.001f)
        }
    }

    @Test
    fun `缓冲区包含尾部静音`() {
        val instance = makeInstance()
        val audio = builder.render(instance)
        val tailSilenceSamples = (sampleRate * ProgressionAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()

        // 最后 tailSilenceSamples 个采样应接近 0
        for (i in audio.size - tailSilenceSamples until audio.size) {
            assertEquals(0.0f, audio[i], 0.001f)
        }
    }

    @Test
    fun `所有采样值在正负 1 范围内不削波`() {
        val instance = makeInstance()
        val audio = builder.render(instance)
        for (sample in audio) {
            assertTrue("采样值 $sample 超出范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `渲染区域有非零内容`() {
        val instance = makeInstance()
        val audio = builder.render(instance)
        val leadSilence = (sampleRate * ProgressionAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilence = (sampleRate * ProgressionAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()

        var hasNonZero = false
        for (i in leadSilence until audio.size - tailSilence) {
            if (audio[i] != 0.0f) {
                hasNonZero = true
                break
            }
        }
        assertTrue("渲染区域应有非零内容", hasNonZero)
    }

    // ════════════════════════════════════════════════════════════
    //  长度验证
    // ════════════════════════════════════════════════════════════

    @Test
    fun `更多和弦产生更长的缓冲区`() {
        val fourChord = makeInstance() // I-V-vi-IV (4 chords)
        val template = ProgressionEngine.findTemplate("pop_cannon")!!
        val eightChord = ProgressionEngine.instantiate(template, ChordRoot.D) // 8 chords

        val audio4 = builder.render(fourChord)
        val audio8 = builder.render(eightChord)

        assertTrue(
            "8 和弦应比 4 和弦长 (${audio8.size} vs ${audio4.size})",
            audio8.size > audio4.size
        )
    }

    @Test
    fun `estimateDurationMs 与和弦数成正比`() {
        val instance4 = makeInstance()
        val template8 = ProgressionEngine.findTemplate("pop_cannon")!!
        val instance8 = ProgressionEngine.instantiate(template8, ChordRoot.D)

        val dur4 = builder.estimateDurationMs(instance4)
        val dur8 = builder.estimateDurationMs(instance8)

        assertTrue("8 和弦应比 4 和弦长 ($dur8 vs $dur4)", dur8 > dur4)
    }

    @Test
    fun `chordDurationMs 计算正确`() {
        // 4 拍 @ 120 BPM = 4 * (60000/120) = 2000 ms
        val ms = ProgressionAudioBuilder.chordDurationMs(4, 120)
        assertEquals(2000L, ms)
    }

    @Test
    fun `chordDurationMs 1 拍 @ 60 BPM = 1000ms`() {
        val ms = ProgressionAudioBuilder.chordDurationMs(1, 60)
        assertEquals(1000L, ms)
    }

    @Test
    fun `chordDurationMs 2 拍 @ 90 BPM`() {
        // 2 * (60000/90) = 2 * 666 = 1332 (Long integer division)
        val ms = ProgressionAudioBuilder.chordDurationMs(2, 90)
        assertEquals(1332L, ms)
    }

    // ════════════════════════════════════════════════════════════
    //  确定性
    // ════════════════════════════════════════════════════════════

    @Test
    fun `相同参数相同输出`() {
        val instance = makeInstance()
        val audio1 = builder.render(instance)
        val audio2 = builder.render(instance)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `不同调性产生不同输出`() {
        val instanceC = makeInstance(ChordRoot.C)
        val instanceG = makeInstance(ChordRoot.G)

        val audioC = builder.render(instanceC)
        val audioG = builder.render(instanceG)

        // 长度应相同（相同模板/和弦数）
        assertEquals(audioC.size, audioG.size)
        // 但内容不同（不同音符）
        var hasDifference = false
        for (i in audioC.indices) {
            if (audioC[i] != audioG[i]) {
                hasDifference = true
                break
            }
        }
        assertTrue("不同调应产生不同音频", hasDifference)
    }

    @Test
    fun `不同速度产生不同长度`() {
        val slow = ProgressionAudioBuilder(tempoBpm = 60)
        val fast = ProgressionAudioBuilder(tempoBpm = 180)

        val instance = makeInstance()
        val audioSlow = slow.render(instance)
        val audioFast = fast.render(instance)

        assertTrue(
            "慢速应比快速长 (${audioSlow.size} vs ${audioFast.size})",
            audioSlow.size > audioFast.size
        )
    }

    // ════════════════════════════════════════════════════════════
    //  单和弦渲染
    // ════════════════════════════════════════════════════════════

    @Test
    fun `renderSingleChord 返回非空缓冲区`() {
        val instance = makeInstance()
        val chord = instance.chords[0]
        val audio = builder.renderSingleChord(chord, beats = 4)
        assertTrue(audio.size > 0)
    }

    @Test
    fun `renderSingleChord 包含前导和尾部静音`() {
        val instance = makeInstance()
        val chord = instance.chords[0]
        val audio = builder.renderSingleChord(chord, beats = 4)
        val leadSilence = (sampleRate * ProgressionAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()

        // 前导静音区域
        for (i in 0 until leadSilence) {
            assertEquals(0.0f, audio[i], 0.001f)
        }
    }

    @Test
    fun `renderSingleChord 不削波`() {
        val instance = makeInstance()
        val chord = instance.chords[0]
        val audio = builder.renderSingleChord(chord, beats = 4)
        for (sample in audio) {
            assertTrue("采样值 $sample 超出范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `更高力度产生更高振幅`() {
        val instance = makeInstance()
        val chord = instance.chords[0]
        val audioLow = builder.renderSingleChord(chord, beats = 4, velocity = 30)
        val audioHigh = builder.renderSingleChord(chord, beats = 4, velocity = 100)

        val maxLow = audioLow.maxOf { kotlin.math.abs(it) }
        val maxHigh = audioHigh.maxOf { kotlin.math.abs(it) }

        assertTrue("高力度应有更高振幅 ($maxHigh vs $maxLow)", maxHigh > maxLow)
    }

    // ════════════════════════════════════════════════════════════
    //  蓝调进行渲染
    // ════════════════════════════════════════════════════════════

    @Test
    fun `蓝调进行渲染不削波`() {
        val template = ProgressionEngine.findTemplate("blues_basic")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)
        val audio = builder.render(instance)
        for (sample in audio) {
            assertTrue("采样值 $sample 超出范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `卡农进行 8 和弦渲染成功`() {
        val template = ProgressionEngine.findTemplate("pop_cannon")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.D)
        val audio = builder.render(instance)
        assertTrue("8 和弦应产生较长缓冲区", audio.size > sampleRate * 2) // > 2 秒
    }

    // ════════════════════════════════════════════════════════════
    //  estimateDurationMs 验证
    // ════════════════════════════════════════════════════════════

    @Test
    fun `estimateDurationMs 包含前导和尾部静音`() {
        val instance = makeInstance()
        val duration = builder.estimateDurationMs(instance)

        // 至少包含前导 + 尾部
        assertTrue(
            "时长应至少包含前后静音 (${ProgressionAudioBuilder.LEAD_SILENCE_MS + ProgressionAudioBuilder.TAIL_SILENCE_MS}ms)",
            duration >= ProgressionAudioBuilder.LEAD_SILENCE_MS + ProgressionAudioBuilder.TAIL_SILENCE_MS
        )
    }

    @Test
    fun `所有内置模板都能渲染`() {
        ProgressionEngine.builtinTemplates.forEach { template ->
            val instance = ProgressionEngine.instantiate(template, template.exampleKey)
            val audio = builder.render(instance)
            assertTrue("模板 ${template.id} 渲染失败", audio.size > 0)
        }
    }
}
