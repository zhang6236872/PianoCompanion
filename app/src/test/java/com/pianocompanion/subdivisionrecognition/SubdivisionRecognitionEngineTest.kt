package com.pianocompanion.subdivisionrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SubdivisionEngine] 单元测试。
 *
 * 验证确定性出题、难度缩放（候选细分集合/速度/吐音）、选项正确性、答案包含正确细分等。
 */
class SubdivisionRecognitionEngineTest {

    @Test
    fun `generate produces valid question with all fields`() {
        val engine = SubdivisionEngine.withSeed(42L)
        val q = engine.generate(SubdivisionDifficulty.BEGINNER)

        assertNotNull(q)
        assertEquals(SubdivisionDifficulty.BEGINNER, q.difficulty)
        assertTrue(q.subdivision in SubdivisionDifficulty.BEGINNER.subdivisionOptions)
        assertEquals(SubdivisionDifficulty.BEGINNER.beatMs, q.beatMs, 0.0001)
        assertEquals(SubdivisionDifficulty.BEGINNER.beatsPerMeasure, q.beatsPerMeasure)
        assertEquals(SubdivisionDifficulty.BEGINNER.measureRepeat, q.measureRepeat)
        assertEquals(SubdivisionDifficulty.BEGINNER.staccato, q.staccato)
    }

    @Test
    fun `deterministic - same seed produces same question`() {
        val e1 = SubdivisionEngine.withSeed(123L)
        val e2 = SubdivisionEngine.withSeed(123L)

        val q1 = e1.generate(SubdivisionDifficulty.INTERMEDIATE)
        val q2 = e2.generate(SubdivisionDifficulty.INTERMEDIATE)

        assertEquals(q1.subdivision, q2.subdivision)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val e1 = SubdivisionEngine.withSeed(1L)
        val e2 = SubdivisionEngine.withSeed(9999L)
        // 在中级难度（2/3/4 三选一）下，不同种子很可能产生不同题目
        val q1 = e1.generate(SubdivisionDifficulty.INTERMEDIATE)
        val q2 = e2.generate(SubdivisionDifficulty.INTERMEDIATE)
        // 多次取样，至少有一对不同
        var anyDifferent = q1.subdivision != q2.subdivision
        repeat(10) {
            val a = e1.generate(SubdivisionDifficulty.INTERMEDIATE).subdivision
            val b = e2.generate(SubdivisionDifficulty.INTERMEDIATE).subdivision
            if (a != b) anyDifferent = true
        }
        assertTrue("不同种子应能产生不同题目", anyDifferent)
    }

    @Test
    fun `beginner only uses duple or triple`() {
        val engine = SubdivisionEngine.withSeed(7L)
        repeat(30) {
            val q = engine.generate(SubdivisionDifficulty.BEGINNER)
            assertTrue(q.subdivision in listOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE))
        }
    }

    @Test
    fun `beginner has exactly 2 choices`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val q = engine.generate(SubdivisionDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `intermediate and advanced use 3 choices`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val qi = engine.generate(SubdivisionDifficulty.INTERMEDIATE)
        val qa = engine.generate(SubdivisionDifficulty.ADVANCED)
        assertEquals(3, qi.answerChoices.size)
        assertEquals(3, qa.answerChoices.size)
    }

    @Test
    fun `intermediate and advanced include all of duple triple quadruple`() {
        val engine = SubdivisionEngine.withSeed(7L)
        for (d in listOf(SubdivisionDifficulty.INTERMEDIATE, SubdivisionDifficulty.ADVANCED)) {
            val q = engine.generate(d)
            assertEquals(
                listOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE, SubdivisionType.QUADRUPLE)
                    .map { it.displayName },
                q.answerChoices
            )
        }
    }

    @Test
    fun `all subdivision types reachable over many intermediate questions`() {
        val engine = SubdivisionEngine.withSeed(100L)
        val seen = mutableSetOf<SubdivisionType>()
        repeat(100) {
            seen.add(engine.generate(SubdivisionDifficulty.INTERMEDIATE).subdivision)
        }
        assertEquals(
            setOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE, SubdivisionType.QUADRUPLE),
            seen
        )
    }

    @Test
    fun `beginner reaches both duple and triple`() {
        val engine = SubdivisionEngine.withSeed(100L)
        val seen = mutableSetOf<SubdivisionType>()
        repeat(80) {
            seen.add(engine.generate(SubdivisionDifficulty.BEGINNER).subdivision)
        }
        assertEquals(setOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE), seen)
    }

    @Test
    fun `choices are ordered by subdivision density ascending`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val q = engine.generate(SubdivisionDifficulty.ADVANCED)
        // 升序：二分(2) → 三连音(3) → 四分(4)
        assertEquals(
            listOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE, SubdivisionType.QUADRUPLE)
                .map { it.displayName },
            q.answerChoices
        )
    }

    @Test
    fun `correct answer matches subdivision display name`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val q = engine.generate(SubdivisionDifficulty.ADVANCED)
        assertEquals(q.subdivision.displayName, q.correctAnswer)
    }

    @Test
    fun `correct answer is contained in choices`() {
        val engine = SubdivisionEngine.withSeed(7L)
        for (difficulty in SubdivisionDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `subdivision options are consistent with difficulty`() {
        val engine = SubdivisionEngine.withSeed(7L)
        repeat(50) {
            for (d in SubdivisionDifficulty.ALL) {
                val q = engine.generate(d)
                assertTrue(q.subdivision in d.subdivisionOptions)
            }
        }
    }

    @Test
    fun `staccato flag matches difficulty`() {
        val engine = SubdivisionEngine.withSeed(7L)
        assertEquals(true, engine.generate(SubdivisionDifficulty.BEGINNER).staccato)
        assertEquals(true, engine.generate(SubdivisionDifficulty.INTERMEDIATE).staccato)
        assertEquals(false, engine.generate(SubdivisionDifficulty.ADVANCED).staccato)
    }

    @Test
    fun `tempo beatMs decreases with difficulty`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val beginner = engine.generate(SubdivisionDifficulty.BEGINNER)
        val intermediate = engine.generate(SubdivisionDifficulty.INTERMEDIATE)
        val advanced = engine.generate(SubdivisionDifficulty.ADVANCED)
        // 初级最慢（beatMs 最大），高级最快（beatMs 最小）
        assertTrue(beginner.beatMs > intermediate.beatMs)
        assertTrue(intermediate.beatMs > advanced.beatMs)
    }

    @Test
    fun `total notes equals subdivision times beats times repeat`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val q = engine.generate(SubdivisionDifficulty.INTERMEDIATE)
        assertEquals(
            q.subdivision.notesPerBeat * q.beatsPerMeasure * q.measureRepeat,
            q.totalNotes
        )
    }

    @Test
    fun `subdivIntervalMs equals beatMs divided by notesPerBeat`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val q = engine.generate(SubdivisionDifficulty.INTERMEDIATE)
        assertEquals(
            q.beatMs / q.subdivision.notesPerBeat,
            q.subdivIntervalMs,
            0.0001
        )
    }

    @Test
    fun `all difficulties are generatable`() {
        val engine = SubdivisionEngine()
        for (d in SubdivisionDifficulty.ALL) {
            val q = engine.generate(d)
            assertEquals(d, q.difficulty)
        }
    }

    @Test
    fun `question fullDescription contains key info`() {
        val engine = SubdivisionEngine.withSeed(7L)
        val q = engine.generate(SubdivisionDifficulty.ADVANCED)
        val desc = q.fullDescription
        assertTrue(q.subdivision.displayName in desc)
        assertTrue("${q.subdivision.notesPerBeat}" in desc)
    }

    @Test
    fun `assertNotEquals works for distinct seeds`() {
        // 确保 notEquals 断言可用（编译校验）
        val a = SubdivisionEngine.withSeed(1L).generate(SubdivisionDifficulty.ADVANCED)
        val b = SubdivisionEngine.withSeed(2L).generate(SubdivisionDifficulty.ADVANCED)
        // 不强制不同，只验证对象可比较
        assertNotEquals(null, a)
        assertNotEquals(null, b)
    }
}
