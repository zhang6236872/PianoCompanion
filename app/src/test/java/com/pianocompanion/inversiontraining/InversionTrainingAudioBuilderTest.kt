package com.pianocompanion.inversiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练音频构建器单元测试。
 *
 * 验证渲染范围、不削波、不同和弦差异、时长预估等。
 */
class InversionTrainingAudioBuilderTest {

    private val builder = InversionTrainingAudioBuilder()

    // ── 基础渲染 ────────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.INTERMEDIATE)
        val audio = builder.render(q)
        assertTrue("渲染缓冲区不应为空", audio.isNotEmpty())
    }

    @Test
    fun `render sample rate is 44100`() {
        assertEquals(44100, InversionTrainingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `render produces correct sample rate`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.BEGINNER)
        val audio = builder.render(q)
        // 时长约 2.5s (200+1800+500ms) → 采样数约 110250
        assertTrue("采样数应在合理范围: ${audio.size}", audio.size > 80000 && audio.size < 150000)
    }

    // ── 不削波 ─────────────────────────────────────────────

    @Test
    fun `render output within minus one to one`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(10) {
                val q = InversionTrainingEngine.withSeed((it * 11).toLong()).generate(d)
                val audio = builder.render(q)
                audio.forEach { sample ->
                    assertTrue("$d 采样 $sample 应在 [-1, 1]", sample in -1.0f..1.0f)
                }
            }
        }
    }

    // ── 不同和弦产生不同音频 ───────────────────────────────

    @Test
    fun `different root notes produce different audio`() {
        val q1 = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.BEGINNER)
        val q2 = InversionTrainingEngine.withSeed(2L).generate(InversionDifficulty.BEGINNER)
        val a1 = builder.render(q1)
        val a2 = builder.render(q2)
        // 不同根音应产生不同音频（采样值不同）
        var foundDiff = false
        val minLen = minOf(a1.size, a2.size)
        for (i in 0 until minLen) {
            if (Math.abs(a1[i] - a2[i]) > 0.001f) {
                foundDiff = true
                break
            }
        }
        assertTrue("不同根音应产生不同音频", foundDiff || a1.size != a2.size)
    }

    @Test
    fun `different inversions produce different audio for same quality and root`() {
        // 手动构建相同根音不同转位的和弦
        val rootMidi = 60
        val root = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.MAJOR, InversionType.ROOT_POSITION, rootMidi)
        val first = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.MAJOR, InversionType.FIRST_INVERSION, rootMidi)
        val second = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.MAJOR, InversionType.SECOND_INVERSION, rootMidi)

        val audioRoot = builder.renderChord(root)
        val audioFirst = builder.renderChord(first)
        val audioSecond = builder.renderChord(second)

        assertTrue("原位与第一转位音频不同", buffersDiffer(audioRoot, audioFirst))
        assertTrue("原位与第二转位音频不同", buffersDiffer(audioRoot, audioSecond))
        assertTrue("第一转位与第二转位音频不同", buffersDiffer(audioFirst, audioSecond))
    }

    @Test
    fun `different qualities produce different audio for same inversion and root`() {
        val rootMidi = 60
        val major = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.MAJOR, InversionType.ROOT_POSITION, rootMidi)
        val minor = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.MINOR, InversionType.ROOT_POSITION, rootMidi)
        val aug = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.AUGMENTED, InversionType.ROOT_POSITION, rootMidi)
        val dim = InversionTrainingEngine.buildChordMidiNotes(ChordQuality.DIMINISHED, InversionType.ROOT_POSITION, rootMidi)

        val aMajor = builder.renderChord(major)
        val aMinor = builder.renderChord(minor)
        val aAug = builder.renderChord(aug)
        val aDim = builder.renderChord(dim)

        assertTrue("大调与小三和弦音频不同", buffersDiffer(aMajor, aMinor))
        assertTrue("大调与增三和弦音频不同", buffersDiffer(aMajor, aAug))
        assertTrue("小调与减三和弦音频不同", buffersDiffer(aMinor, aDim))
    }

    // ── 前导/尾部静音 ──────────────────────────────────────

    @Test
    fun `render has leading silence`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.BEGINNER)
        val audio = builder.render(q)
        val leadSamples = (InversionTrainingAudioBuilder.SAMPLE_RATE * InversionTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前 leadSamples 个采样应全部接近 0（静音）
        for (i in 0 until leadSamples) {
            assertTrue("前导静音区采样 $i = ${audio[i]} 应接近 0", Math.abs(audio[i]) < 0.01f)
        }
    }

    @Test
    fun `render has trailing silence`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.BEGINNER)
        val audio = builder.render(q)
        val tailSamples = (InversionTrainingAudioBuilder.SAMPLE_RATE * InversionTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        // 最后 tailSamples 个采样应全部接近 0
        val startIdx = audio.size - tailSamples
        for (i in startIdx until audio.size) {
            assertTrue("尾部静音区采样 $i = ${audio[i]} 应接近 0", Math.abs(audio[i]) < 0.01f)
        }
    }

    // ── estimateDurationMs ─────────────────────────────────

    @Test
    fun `estimateDurationMs matches formula`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.INTERMEDIATE)
        val expected = InversionTrainingAudioBuilder.LEAD_SILENCE_MS +
            InversionTrainingAudioBuilder.CHORD_DURATION_MS +
            InversionTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs is same for all difficulties`() {
        // 柱式和弦时长固定，与难度无关
        val begin = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.BEGINNER)
        val inter = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.INTERMEDIATE)
        val adv = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.ADVANCED)
        assertEquals(builder.estimateDurationMs(begin), builder.estimateDurationMs(inter))
        assertEquals(builder.estimateDurationMs(inter), builder.estimateDurationMs(adv))
    }

    // ── 空输入 ─────────────────────────────────────────────

    @Test
    fun `empty input returns empty buffer`() {
        val empty = builder.renderChord(emptyList())
        assertEquals(0, empty.size)
    }

    // ── 常量合理性 ─────────────────────────────────────────

    @Test
    fun `constants are reasonable`() {
        assertTrue(InversionTrainingAudioBuilder.CHORD_DURATION_MS in 500..5000)
        assertTrue(InversionTrainingAudioBuilder.LEAD_SILENCE_MS in 50..1000)
        assertTrue(InversionTrainingAudioBuilder.TAIL_SILENCE_MS in 100..2000)
        assertTrue(InversionTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127)
        assertTrue(InversionTrainingAudioBuilder.SOFTCLIP_K > 0f)
    }

    @Test
    fun `chord duration is longer than scale note`() {
        // 柱式和弦需要足够长让用户辨识
        assertTrue(InversionTrainingAudioBuilder.CHORD_DURATION_MS >= 1000)
    }

    // ── 全难度渲染 ─────────────────────────────────────────

    @Test
    fun `all difficulties render without error`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(5) {
                val q = InversionTrainingEngine.withSeed((it * 29).toLong()).generate(d)
                val audio = builder.render(q)
                assertTrue("$d 渲染非空", audio.isNotEmpty())
            }
        }
    }

    @Test
    fun `extreme piano range chord renders`() {
        // A0 附近的和弦（最低音域）
        val lowChord = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MAJOR, InversionType.ROOT_POSITION, 21
        )
        val audio = builder.renderChord(lowChord)
        assertTrue("低音域和弦渲染非空", audio.isNotEmpty())

        // C8 附近（高音域）
        val highChord = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MAJOR, InversionType.SECOND_INVERSION, 96
        )
        val audio2 = builder.renderChord(highChord)
        assertTrue("高音域和弦渲染非空", audio2.isNotEmpty())
    }

    // ── 辅助方法 ───────────────────────────────────────────

    private fun buffersDiffer(a: FloatArray, b: FloatArray): Boolean {
        if (a.size != b.size) return true
        var diffCount = 0
        for (i in a.indices) {
            if (Math.abs(a[i] - b[i]) > 0.001f) diffCount++
        }
        return diffCount > a.size / 10 // 超过 10% 的采样不同
    }
}
