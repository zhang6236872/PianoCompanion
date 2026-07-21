package com.pianocompanion.motiftransformation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 动机发展辨识训练出题引擎单元测试。
 */
class MotifTransformationEngineTest {

    @Test
    fun `BEGINNER generates 2-option question`() {
        val engine = MotifTransformationEngine.withSeed(42L)
        val q = engine.generate(MotifTransformationDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
        assertEquals(2, q.difficulty.choiceCount)
    }

    @Test
    fun `INTERMEDIATE generates 4-option question`() {
        val engine = MotifTransformationEngine.withSeed(1L)
        val q = engine.generate(MotifTransformationDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
        assertEquals(4, q.difficulty.choiceCount)
    }

    @Test
    fun `ADVANCED generates 6-option question`() {
        val engine = MotifTransformationEngine.withSeed(99L)
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
        assertEquals(6, q.difficulty.choiceCount)
    }

    @Test
    fun `correct answer is always in choices`() {
        val engine = MotifTransformationEngine.withSeed(7L)
        MotifTransformationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `choices are unique`() {
        val engine = MotifTransformationEngine.withSeed(3L)
        MotifTransformationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(
                "选项必须唯一",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `choices match difficulty candidates`() {
        val engine = MotifTransformationEngine.withSeed(5L)
        MotifTransformationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            val candidateLabels = d.candidates.map { it.fullLabel }.toSet()
            q.answerChoices.forEach { choice ->
                assertTrue("选项 $choice 必须在难度 ${d.name} 候选中", choice in candidateLabels)
            }
        }
    }

    @Test
    fun `deterministic with same seed`() {
        val e1 = MotifTransformationEngine.withSeed(123L)
        val e2 = MotifTransformationEngine.withSeed(123L)
        val q1 = e1.generate(MotifTransformationDifficulty.ADVANCED)
        val q2 = e2.generate(MotifTransformationDifficulty.ADVANCED)
        assertEquals(q1.transformation, q2.transformation)
        assertEquals(q1.originalPitches, q2.originalPitches)
        assertEquals(q1.transformedPitches, q2.transformedPitches)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds produce different questions`() {
        var diffCount = 0
        for (s in 0L..20L) {
            val e1 = MotifTransformationEngine.withSeed(s)
            val e2 = MotifTransformationEngine.withSeed(s + 100)
            val q1 = e1.generate(MotifTransformationDifficulty.ADVANCED)
            val q2 = e2.generate(MotifTransformationDifficulty.ADVANCED)
            if (q1.originalPitches != q2.originalPitches) diffCount++
        }
        assertTrue("不同种子应产生不同的动机", diffCount > 10)
    }

    @Test
    fun `original motif has 4 notes`() {
        val engine = MotifTransformationEngine.withSeed(10L)
        MotifTransformationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(MotifTransformationEngine.MOTIF_LENGTH, q.originalNotes.size)
            assertEquals(MotifTransformationEngine.MOTIF_LENGTH, q.transformedNotes.size)
        }
    }

    @Test
    fun `original motif pitches are in valid range`() {
        val engine = MotifTransformationEngine.withSeed(20L)
        MotifTransformationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            q.originalPitches.forEach { p ->
                assertTrue("原始音高 $p 在范围 [55, 79]", p in MIN_MOTIF_MIDI..MAX_MOTIF_MIDI)
            }
        }
    }

    @Test
    fun `transformed motif pitches are in valid MIDI range`() {
        val engine = MotifTransformationEngine.withSeed(30L)
        MotifTransformationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            q.transformedPitches.forEach { p ->
                assertTrue("变换后音高 $p 在范围 [$MIN_MIDI, $MAX_MIDI]", p in MIN_MIDI..MAX_MIDI)
            }
        }
    }

    @Test
    fun `all transformation types are generated across many seeds`() {
        val seen = mutableSetOf<MotifTransformation>()
        repeat(500) {
            val engine = MotifTransformationEngine.withSeed(it.toLong())
            val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
            seen.add(q.transformation)
        }
        assertEquals("ADVANCED 应覆盖全部 6 种变换", MotifTransformation.ALL.toSet(), seen)
    }

    // ── 变换正确性测试 ──────────────────────────────────

    @Test
    fun `REPETITION produces identical pitches and durations`() {
        val notes = listOf(
            MotifNote(60, 300.0),
            MotifNote(62, 300.0),
            MotifNote(64, 300.0),
            MotifNote(65, 300.0)
        )
        val transformed = applyTransformation(notes, MotifTransformation.REPETITION, Random(0))
        assertEquals(notes.map { it.midi }, transformed.map { it.midi })
        assertEquals(notes.map { it.durationMs }, transformed.map { it.durationMs })
    }

    @Test
    fun `SEQUENCE transposes all pitches by same amount`() {
        val notes = listOf(
            MotifNote(60, 300.0),
            MotifNote(62, 300.0),
            MotifNote(64, 300.0),
            MotifNote(65, 300.0)
        )
        val transformed = applyTransformation(notes, MotifTransformation.SEQUENCE, Random(0))
        val originalPitches = notes.map { it.midi }
        val shifts = transformed.mapIndexed { i, n -> n.midi - originalPitches[i] }
        // 所有移位量相同（整体移位）
        assertEquals(1, shifts.toSet().size)
        // 移位量非零
        assertTrue("模进移位不应为零", shifts[0] != 0)
        // 时值不变
        assertEquals(notes.map { it.durationMs }, transformed.map { it.durationMs })
    }

    @Test
    fun `SEQUENCE shift keeps pitches in valid range`() {
        repeat(50) { seed ->
            val notes = listOf(
                MotifNote(60, 300.0),
                MotifNote(62, 300.0),
                MotifNote(64, 300.0),
                MotifNote(65, 300.0)
            )
            val transformed = applyTransformation(notes, MotifTransformation.SEQUENCE, Random(seed.toLong()))
            transformed.forEach { n ->
                assertTrue("模进后音高 ${n.midi} 在有效范围", n.midi in MIN_MIDI..MAX_MIDI)
            }
        }
    }

    @Test
    fun `INVERSION flips intervals around first note`() {
        val notes = listOf(
            MotifNote(60, 300.0),
            MotifNote(62, 300.0),  // +2
            MotifNote(64, 300.0),  // +4
            MotifNote(65, 300.0)   // +5
        )
        val transformed = applyTransformation(notes, MotifTransformation.INVERSION, Random(0))
        // 第一音不变
        assertEquals(60, transformed[0].midi)
        // 第二音：60 - (62 - 60) = 58
        assertEquals(58, transformed[1].midi)
        // 第三音：60 - (64 - 60) = 56
        assertEquals(56, transformed[2].midi)
        // 第四音：60 - (65 - 60) = 55
        assertEquals(55, transformed[3].midi)
        // 时值不变
        assertEquals(notes.map { it.durationMs }, transformed.map { it.durationMs })
    }

    @Test
    fun `INVERSION preserves first note`() {
        repeat(20) { seed ->
            val notes = listOf(
                MotifNote(64, 300.0),
                MotifNote(66, 300.0),
                MotifNote(65, 300.0),
                MotifNote(67, 300.0)
            )
            val transformed = applyTransformation(notes, MotifTransformation.INVERSION, Random(seed.toLong()))
            assertEquals("倒影保持第一音不变", notes[0].midi, transformed[0].midi)
        }
    }

    @Test
    fun `RETROGRADE reverses note order`() {
        val notes = listOf(
            MotifNote(60, 300.0),
            MotifNote(62, 300.0),
            MotifNote(64, 300.0),
            MotifNote(65, 300.0)
        )
        val transformed = applyTransformation(notes, MotifTransformation.RETROGRADE, Random(0))
        assertEquals(listOf(65, 64, 62, 60), transformed.map { it.midi })
        // 时值不变
        assertEquals(notes.map { it.durationMs }, transformed.map { it.durationMs })
    }

    @Test
    fun `AUGMENTATION doubles durations`() {
        val notes = listOf(
            MotifNote(60, 300.0),
            MotifNote(62, 300.0),
            MotifNote(64, 300.0),
            MotifNote(65, 300.0)
        )
        val transformed = applyTransformation(notes, MotifTransformation.AUGMENTATION, Random(0))
        assertEquals(listOf(600.0, 600.0, 600.0, 600.0), transformed.map { it.durationMs })
        // 音高不变
        assertEquals(notes.map { it.midi }, transformed.map { it.midi })
    }

    @Test
    fun `DIMINUTION halves durations`() {
        val notes = listOf(
            MotifNote(60, 300.0),
            MotifNote(62, 300.0),
            MotifNote(64, 300.0),
            MotifNote(65, 300.0)
        )
        val transformed = applyTransformation(notes, MotifTransformation.DIMINUTION, Random(0))
        assertEquals(listOf(150.0, 150.0, 150.0, 150.0), transformed.map { it.durationMs })
        // 音高不变
        assertEquals(notes.map { it.midi }, transformed.map { it.midi })
    }

    @Test
    fun `AUGMENTATION factor is 2x`() {
        assertEquals(2.0, AUGMENTATION_FACTOR, 0.001)
    }

    @Test
    fun `empty list transformation returns empty`() {
        val empty = emptyList<MotifNote>()
        MotifTransformation.ALL.forEach { t ->
            val transformed = applyTransformation(empty, t, Random(0))
            assertTrue("${t.name} 空列表应返回空", transformed.isEmpty())
        }
    }

    @Test
    fun `difficulty base note durations decrease with difficulty`() {
        assertTrue(
            "初级时值 > 中级时值",
            MotifTransformationDifficulty.BEGINNER.baseNoteMs >
                MotifTransformationDifficulty.INTERMEDIATE.baseNoteMs
        )
        assertTrue(
            "中级时值 > 高级时值",
            MotifTransformationDifficulty.INTERMEDIATE.baseNoteMs >
                MotifTransformationDifficulty.ADVANCED.baseNoteMs
        )
    }

    @Test
    fun `question stores seed for reproducibility`() {
        val engine = MotifTransformationEngine.withSeed(777L)
        val q = engine.generate(MotifTransformationDifficulty.INTERMEDIATE)
        assertNotNull(q.seed)
    }

    @Test
    fun `BEGINNER only generates repetition or sequence`() {
        repeat(200) {
            val engine = MotifTransformationEngine.withSeed(it.toLong())
            val q = engine.generate(MotifTransformationDifficulty.BEGINNER)
            assertTrue(
                "初级只出重复或模进",
                q.transformation in MotifTransformation.BEGINNER_CANDIDATES
            )
        }
    }

    @Test
    fun `BEGINNER distributes both transformation types`() {
        val seen = mutableSetOf<MotifTransformation>()
        repeat(100) {
            val engine = MotifTransformationEngine.withSeed(it.toLong())
            val q = engine.generate(MotifTransformationDifficulty.BEGINNER)
            seen.add(q.transformation)
        }
        assertEquals(2, seen.size)
    }

    @Test
    fun `INTERMEDIATE candidates contain 4 types`() {
        assertEquals(4, MotifTransformationDifficulty.INTERMEDIATE.candidates.size)
        assertTrue(MotifTransformation.INVERSION in MotifTransformationDifficulty.INTERMEDIATE.candidates)
        assertTrue(MotifTransformation.RETROGRADE in MotifTransformationDifficulty.INTERMEDIATE.candidates)
    }

    @Test
    fun `ADVANCED candidates contain all 6 types`() {
        assertEquals(6, MotifTransformationDifficulty.ADVANCED.candidates.size)
        assertEquals(MotifTransformation.ALL, MotifTransformationDifficulty.ADVANCED.candidates)
    }

    @Test
    fun `withSeed produces deterministic motif pitches`() {
        val e1 = MotifTransformationEngine.withSeed(555L)
        val e2 = MotifTransformationEngine.withSeed(555L)
        val q1 = e1.generate(MotifTransformationDifficulty.BEGINNER)
        val q2 = e2.generate(MotifTransformationDifficulty.BEGINNER)
        assertEquals(q1.originalPitches, q2.originalPitches)
    }
}
