package com.pianocompanion.dynamicstraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 力度辨识出题引擎单元测试。
 */
class DynamicsTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = DynamicsTrainingEngine.withSeed(42)
        val e2 = DynamicsTrainingEngine.withSeed(42)
        val q1 = e1.generate(DynamicsTrainingDifficulty.BEGINNER)
        val q2 = e2.generate(DynamicsTrainingDifficulty.BEGINNER)
        assertEquals(q1.dynamic, q2.dynamic)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = DynamicsTrainingEngine.withSeed(1)
        val engine2 = DynamicsTrainingEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(DynamicsTrainingDifficulty.ADVANCED)
            val q2 = engine2.generate(DynamicsTrainingDifficulty.ADVANCED)
            if (q1.dynamic != q2.dynamic) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = DynamicsTrainingEngine.withSeed(1)
        val q = engine.generate(DynamicsTrainingDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = DynamicsTrainingEngine.withSeed(1)
        val q = engine.generate(DynamicsTrainingDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为6`() {
        val engine = DynamicsTrainingEngine.withSeed(1)
        val q = engine.generate(DynamicsTrainingDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = DynamicsTrainingEngine.withSeed(7)
        for (difficulty in DynamicsTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = DynamicsTrainingEngine.withSeed(3)
        for (difficulty in DynamicsTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertTrue(q.correctAnswer in q.answerChoices)
            }
        }
    }

    // ── 难度力度池 ──────────────────────────────────────

    @Test
    fun `初级力度池只含极弱中强极强`() {
        assertEquals(
            listOf(DynamicLevel.PIANISSIMO, DynamicLevel.MEZZO_FORTE, DynamicLevel.FORTISSIMO),
            DynamicLevel.BEGINNER_DYNAMICS
        )
    }

    @Test
    fun `中级力度池含极弱弱中强强`() {
        assertEquals(
            listOf(DynamicLevel.PIANISSIMO, DynamicLevel.PIANO, DynamicLevel.MEZZO_FORTE, DynamicLevel.FORTE),
            DynamicLevel.INTERMEDIATE_DYNAMICS
        )
    }

    @Test
    fun `高级力度池含全部6种`() {
        assertEquals(6, DynamicLevel.ALL.size)
        assertEquals(DynamicLevel.ALL, DynamicLevel.forDifficulty(DynamicsTrainingDifficulty.ADVANCED))
    }

    @Test
    fun `初级题目答案必在初级力度池中`() {
        val engine = DynamicsTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(DynamicsTrainingDifficulty.BEGINNER)
            assertTrue(q.dynamic in DynamicLevel.BEGINNER_DYNAMICS)
        }
    }

    @Test
    fun `中级题目答案必在中级力度池中`() {
        val engine = DynamicsTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(DynamicsTrainingDifficulty.INTERMEDIATE)
            assertTrue(q.dynamic in DynamicLevel.INTERMEDIATE_DYNAMICS)
        }
    }

    @Test
    fun `高级题目答案必在全部力度池中`() {
        val engine = DynamicsTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(DynamicsTrainingDifficulty.ADVANCED)
            assertTrue(q.dynamic in DynamicLevel.ALL)
        }
    }

    @Test
    fun `所有选项均来自该难度力度池`() {
        val engine = DynamicsTrainingEngine.withSeed(11)
        for (difficulty in DynamicsTrainingDifficulty.ALL) {
            val pool = DynamicLevel.forDifficulty(difficulty)
            val poolLabels = pool.map { it.fullLabel }.toSet()
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue("选项 $choice 不在难度池中", choice in poolLabels)
                }
            }
        }
    }

    // ── 高级难度可覆盖所有6种力度 ──────────────────────

    @Test
    fun `高级难度充分覆盖全部力度类型`() {
        val engine = DynamicsTrainingEngine.withSeed(100)
        val seen = mutableSetOf<DynamicLevel>()
        for (i in 1..200) {
            val q = engine.generate(DynamicsTrainingDifficulty.ADVANCED)
            seen.add(q.dynamic)
        }
        assertEquals(6, seen.size)
    }

    @Test
    fun `初级难度可覆盖全部3种力度类型`() {
        val engine = DynamicsTrainingEngine.withSeed(100)
        val seen = mutableSetOf<DynamicLevel>()
        for (i in 1..100) {
            val q = engine.generate(DynamicsTrainingDifficulty.BEGINNER)
            seen.add(q.dynamic)
        }
        assertEquals(3, seen.size)
    }

    // ── noteCount ──────────────────────────────────────

    @Test
    fun `默认noteCount为4`() {
        val engine = DynamicsTrainingEngine()
        val q = engine.generate(DynamicsTrainingDifficulty.BEGINNER)
        assertEquals(4, q.noteCount)
    }

    @Test
    fun `可自定义noteCount`() {
        val engine = DynamicsTrainingEngine()
        val q = engine.generate(DynamicsTrainingDifficulty.ADVANCED, noteCount = 6)
        assertEquals(6, q.noteCount)
    }

    // ── onset 时间 ──────────────────────────────────────

    @Test
    fun `computeOnsetTimes首音在LEAD_SILENCE`() {
        val engine = DynamicsTrainingEngine()
        val onsets = engine.computeOnsetTimes(noteCount = 4)
        assertEquals(DynamicsTrainingEngine.LEAD_SILENCE_MS, onsets[0], 0.01)
    }

    @Test
    fun `computeOnsetTimes数量等于noteCount`() {
        val engine = DynamicsTrainingEngine()
        for (count in listOf(1, 2, 4, 8)) {
            val onsets = engine.computeOnsetTimes(noteCount = count)
            assertEquals(count, onsets.size)
        }
    }

    @Test
    fun `computeOnsetTimes间距等于NOTE_DURATION`() {
        val engine = DynamicsTrainingEngine()
        val onsets = engine.computeOnsetTimes(noteCount = 4)
        val expectedInterval = DynamicsTrainingEngine.NOTE_DURATION_MS
        for (i in 1 until onsets.size) {
            assertEquals(expectedInterval, onsets[i] - onsets[i - 1], 0.01)
        }
    }

    @Test
    fun `computeOnsetTimes单调递增`() {
        val engine = DynamicsTrainingEngine()
        val onsets = engine.computeOnsetTimes(noteCount = 6)
        for (i in 1 until onsets.size) {
            assertTrue(onsets[i] > onsets[i - 1])
        }
    }

    // ── amplitude 验证 ────────────────────────────────

    @Test
    fun `amplitude范围在0到1之间`() {
        for (dynamic in DynamicLevel.ALL) {
            assertTrue("${dynamic.italianName} amplitude=${dynamic.amplitude}", dynamic.amplitude in 0.0f..1.0f)
        }
    }

    @Test
    fun `振幅从弱到强单调递增`() {
        val amplitudes = DynamicLevel.ALL.map { it.amplitude }
        for (i in 1 until amplitudes.size) {
            assertTrue("振幅应单调递增: ${amplitudes[i-1]} < ${amplitudes[i]}", amplitudes[i] > amplitudes[i - 1])
        }
    }

    @Test
    fun `极弱振幅最小极强振幅最大`() {
        assertTrue(DynamicLevel.PIANISSIMO.amplitude < DynamicLevel.FORTISSIMO.amplitude)
    }

    // ── answerChoices 格式 ─────────────────────────────

    @Test
    fun `正确答案格式与fullLabel一致`() {
        val engine = DynamicsTrainingEngine.withSeed(8)
        for (difficulty in DynamicsTrainingDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.dynamic.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `选项打乱后正确答案仍可匹配`() {
        val engine = DynamicsTrainingEngine.withSeed(22)
        for (i in 1..30) {
            val q = engine.generate(DynamicsTrainingDifficulty.ADVANCED)
            val matchCount = q.answerChoices.count { it == q.correctAnswer }
            assertEquals(1, matchCount)
        }
    }
}
