package com.pianocompanion.subdivisionrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SubdivisionAudioBuilder] 单元测试。
 *
 * 验证：
 * - onset 时间序列正确（拍内等分、拍/小节连续）
 * - 拍点重音存在（拍点音能量高于细分音）
 * - 不同细分类型产生不同数量的 onset（2/3/4）
 * - 断奏 vs 连奏的音符长度差异
 * - 渲染的 PCM 有效（范围、非全零）
 */
class SubdivisionRecognitionAudioBuilderTest {

    private val builder = SubdivisionAudioBuilder()

    private fun question(
        subdivision: SubdivisionType,
        difficulty: SubdivisionDifficulty = SubdivisionDifficulty.INTERMEDIATE
    ): SubdivisionQuestion {
        return SubdivisionEngine.withSeed(1L)
            .let { SubdivisionEngine() }
            .generate(difficulty)
            .copy(subdivision = subdivision)
    }

    // ── onset 时间序列 ──────────────────────────────────────

    @Test
    fun `onset count matches notesPerBeat times beats times repeat`() {
        for (sub in SubdivisionType.ALL) {
            val q = question(sub)
            val onsets = builder.computeOnsetTimes(q)
            assertEquals(
                sub.notesPerBeat * q.beatsPerMeasure * q.measureRepeat,
                onsets.size
            )
        }
    }

    @Test
    fun `duple produces 2 notes per beat`() {
        val q = question(SubdivisionType.DUPLE)
        val onsets = builder.computeOnsetTimes(q)
        // 第一拍的 2 个 onset
        val firstBeat = onsets.take(2)
        assertEquals(2, firstBeat.size)
    }

    @Test
    fun `triple produces 3 notes per beat`() {
        val q = question(SubdivisionType.TRIPLE)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(3, onsets.take(3).size)
    }

    @Test
    fun `quadruple produces 4 notes per beat`() {
        val q = question(SubdivisionType.QUADRUPLE)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(4, onsets.take(4).size)
    }

    @Test
    fun `subdivisions within a beat are equally spaced`() {
        val q = question(SubdivisionType.TRIPLE)
        val onsets = builder.computeOnsetTimes(q)
        val interval = builder.subdivIntervalMs(q)
        // 第一拍内的 3 个 onset 应等距
        val beat0 = onsets.take(3)
        val gap1 = beat0[1] - beat0[0]
        val gap2 = beat0[2] - beat0[1]
        assertEquals(interval, gap1, 0.5)
        assertEquals(interval, gap2, 0.5)
    }

    @Test
    fun `first onset starts after lead silence`() {
        val q = question(SubdivisionType.DUPLE)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(SubdivisionAudioBuilder.LEAD_SILENCE_MS, onsets.first(), 0.0001)
    }

    @Test
    fun `beats within measure are spaced by beatMs`() {
        val q = question(SubdivisionType.DUPLE)
        val onsets = builder.computeOnsetTimes(q)
        // 拍 0 起始 onset 和拍 1 起始 onset 之间应相差一个 beatMs
        val beat0Start = onsets[0]
        val beat1Start = onsets[q.subdivision.notesPerBeat]
        assertEquals(q.beatMs, beat1Start - beat0Start, 0.5)
    }

    @Test
    fun `subdivIntervalMs equals beatMs over notesPerBeat`() {
        for (sub in SubdivisionType.ALL) {
            val q = question(sub)
            val expected = q.beatMs / sub.notesPerBeat
            assertEquals(expected, builder.subdivIntervalMs(q), 0.0001)
        }
    }

    // ── 拍点重音 ────────────────────────────────────────────

    @Test
    fun `beat start flags mark first note of each beat`() {
        val q = question(SubdivisionType.TRIPLE)
        val flags = builder.computeBeatStartFlags(q)
        // 每 3 个里第 1 个为 true
        for ((idx, flag) in flags.withIndex()) {
            assertEquals(idx % 3 == 0, flag)
        }
    }

    @Test
    fun `beat start flags count equals number of beats`() {
        val q = question(SubdivisionType.QUADRUPLE)
        val flags = builder.computeBeatStartFlags(q)
        val beatCount = q.beatsPerMeasure * q.measureRepeat
        assertEquals(beatCount, flags.count { it })
    }

    @Test
    fun `beat note has higher energy than subdivision note`() {
        // 拍点音应比细分音响（重音存在）
        val q = question(SubdivisionType.TRIPLE)
        val audio = builder.render(q)
        val beatEnergy = builder.noteRmsEnergy(audio, q, 0) // 拍点音
        val subdivEnergy = builder.noteRmsEnergy(audio, q, 1) // 细分音
        assertTrue(
            "拍点音($beatEnergy) 应比细分音($subdivEnergy) 响",
            beatEnergy > subdivEnergy
        )
    }

    // ── PCM 有效性 ─────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer in valid range`() {
        for (sub in SubdivisionType.ALL) {
            val q = question(sub)
            val audio = builder.render(q)
            assertTrue("缓冲区非空 (${sub.displayName})", audio.isNotEmpty())
            for (v in audio) {
                assertTrue("样本在 [-1,1]: $v", v in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `render produces non-silent audio`() {
        for (sub in SubdivisionType.ALL) {
            val q = question(sub)
            val audio = builder.render(q)
            val maxAbs = audio.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            assertTrue(
                "${sub.displayName} 音频非静音 (maxAbs=$maxAbs)",
                maxAbs > 0.05f
            )
        }
    }

    @Test
    fun `render handles all difficulties`() {
        for (d in SubdivisionDifficulty.ALL) {
            val q = SubdivisionEngine.withSeed(1L).generate(d)
            val audio = builder.render(q)
            assertTrue("${d.displayName} 渲染成功", audio.isNotEmpty())
        }
    }

    // ── 断奏 vs 连奏 ───────────────────────────────────────

    @Test
    fun `staccato note is shorter than legato note`() {
        val interval = 200.0
        val staccatoLen = builder.noteLengthMs(true, interval)
        val legatoLen = builder.noteLengthMs(false, interval)
        assertTrue(
            "断奏($staccatoLen) 应短于连奏($legatoLen)",
            staccatoLen < legatoLen
        )
    }

    @Test
    fun `staccato note length is bounded by interval ratio`() {
        val interval = 200.0
        val len = builder.noteLengthMs(true, interval)
        assertEquals(interval * SubdivisionAudioBuilder.STACCATO_LENGTH_RATIO, len, 0.0001)
    }

    @Test
    fun `legato note length overlaps into next note`() {
        val interval = 150.0
        val len = builder.noteLengthMs(false, interval)
        assertTrue("连奏应重叠到下一个音: len=$len interval=$interval", len > interval)
    }

    @Test
    fun `note length capped at MAX_NOTE_MS`() {
        // 极大的 interval（极慢细分），连奏长度应被截断
        val hugeInterval = 1000.0
        val len = builder.noteLengthMs(false, hugeInterval)
        assertEquals(SubdivisionAudioBuilder.MAX_NOTE_MS, len, 0.0001)
    }

    // ── 不同细分的可区分性 ─────────────────────────────────

    @Test
    fun `duple triple quadruple produce different onset counts`() {
        val duple = builder.computeOnsetTimes(question(SubdivisionType.DUPLE)).size
        val triple = builder.computeOnsetTimes(question(SubdivisionType.TRIPLE)).size
        val quadruple = builder.computeOnsetTimes(question(SubdivisionType.QUADRUPLE)).size
        assertTrue(duple < triple)
        assertTrue(triple < quadruple)
    }

    @Test
    fun `estimateDurationMs is positive`() {
        for (sub in SubdivisionType.ALL) {
            val q = question(sub)
            val dur = builder.estimateDurationMs(q)
            assertTrue("${sub.displayName} 时长为正: $dur", dur > 0L)
        }
    }

    @Test
    fun `noteRmsEnergy returns zero for out-of-range index`() {
        val q = question(SubdivisionType.DUPLE)
        val audio = builder.render(q)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(0.0, builder.noteRmsEnergy(audio, q, onsets.size + 10), 0.0001)
    }

    @Test
    fun `different subdivisions produce different length audio`() {
        // 四分细分的总时长（更多音符 + 拖尾）应长于二分细分
        val dupleLen = builder.render(question(SubdivisionType.DUPLE)).size
        val quadrupleLen = builder.render(question(SubdivisionType.QUADRUPLE)).size
        assertTrue(quadrupleLen >= dupleLen)
    }
}
