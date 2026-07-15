package com.pianocompanion.polyrhythmtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 复合节奏辨识训练出题引擎单元测试。
 */
class PolyrhythmEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = PolyrhythmTrainingEngine.withSeed(42)
        val e2 = PolyrhythmTrainingEngine.withSeed(42)
        val q1 = e1.generate(PolyrhythmDifficulty.BEGINNER)
        val q2 = e2.generate(PolyrhythmDifficulty.BEGINNER)
        assertEquals(q1.polyrhythm, q2.polyrhythm)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val engine2 = PolyrhythmTrainingEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(PolyrhythmDifficulty.ADVANCED)
            val q2 = engine2.generate(PolyrhythmDifficulty.ADVANCED)
            if (q1.polyrhythm != q2.polyrhythm) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为2`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q = engine.generate(PolyrhythmDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为3`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q = engine.generate(PolyrhythmDifficulty.INTERMEDIATE)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为5`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q = engine.generate(PolyrhythmDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = PolyrhythmTrainingEngine.withSeed(100)
        repeat(50) {
            val q = engine.generate(PolyrhythmDifficulty.ADVANCED)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = PolyrhythmTrainingEngine.withSeed(7)
        repeat(20) {
            val q = engine.generate(PolyrhythmDifficulty.ADVANCED)
            assertTrue(q.correctAnswer in q.answerChoices)
        }
    }

    // ── 难度覆盖 ────────────────────────────────────────

    @Test
    fun `初级难度只使用2种复合节奏`() {
        val engine = PolyrhythmTrainingEngine.withSeed(42)
        val seen = mutableSetOf<PolyrhythmType>()
        repeat(100) {
            val q = engine.generate(PolyrhythmDifficulty.BEGINNER)
            seen.add(q.polyrhythm)
        }
        assertTrue(seen.all { it in PolyrhythmType.BEGINNER_POLYRHYTHMS })
    }

    @Test
    fun `中级难度只使用3种复合节奏`() {
        val engine = PolyrhythmTrainingEngine.withSeed(42)
        val seen = mutableSetOf<PolyrhythmType>()
        repeat(100) {
            val q = engine.generate(PolyrhythmDifficulty.INTERMEDIATE)
            seen.add(q.polyrhythm)
        }
        assertTrue(seen.all { it in PolyrhythmType.INTERMEDIATE_POLYRHYTHMS })
    }

    @Test
    fun `高级难度覆盖全部5种复合节奏`() {
        val engine = PolyrhythmTrainingEngine.withSeed(42)
        val seen = mutableSetOf<PolyrhythmType>()
        repeat(200) {
            val q = engine.generate(PolyrhythmDifficulty.ADVANCED)
            seen.add(q.polyrhythm)
        }
        assertEquals(PolyrhythmType.ALL.size, seen.size)
        assertTrue(seen.containsAll(PolyrhythmType.ALL))
    }

    // ── 题目正确性 ──────────────────────────────────────

    @Test
    fun `正确答案与题目复合节奏一致`() {
        val engine = PolyrhythmTrainingEngine.withSeed(13)
        repeat(30) {
            val q = engine.generate(PolyrhythmDifficulty.ADVANCED)
            assertEquals(q.polyrhythm.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `题目难度与传入难度一致`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q1 = engine.generate(PolyrhythmDifficulty.BEGINNER)
        assertEquals(PolyrhythmDifficulty.BEGINNER, q1.difficulty)

        val q2 = engine.generate(PolyrhythmDifficulty.INTERMEDIATE)
        assertEquals(PolyrhythmDifficulty.INTERMEDIATE, q2.difficulty)

        val q3 = engine.generate(PolyrhythmDifficulty.ADVANCED)
        assertEquals(PolyrhythmDifficulty.ADVANCED, q3.difficulty)
    }

    @Test
    fun `题目默认周期数为2`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q = engine.generate(PolyrhythmDifficulty.ADVANCED)
        assertEquals(2, q.cycleCount)
    }

    @Test
    fun `自定义周期数`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q = engine.generate(PolyrhythmDifficulty.ADVANCED, cycleCount = 3)
        assertEquals(3, q.cycleCount)
    }

    // ── Onset 计算 ──────────────────────────────────────

    @Test
    fun `高音声部onset数量等于highCount乘周期数`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val (highOnsets, _) = engine.computeOnsetTimes(PolyrhythmType.TWO_THREE, 2)
        assertEquals(2 * 2, highOnsets.size) // 2 per cycle × 2 cycles
    }

    @Test
    fun `低音声部onset数量等于lowCount乘周期数`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val (_, lowOnsets) = engine.computeOnsetTimes(PolyrhythmType.TWO_THREE, 2)
        assertEquals(3 * 2, lowOnsets.size) // 3 per cycle × 2 cycles
    }

    @Test
    fun `两声部在每周期起始时刻对齐`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val (highOnsets, lowOnsets) = engine.computeOnsetTimes(PolyrhythmType.THREE_FOUR, 2)
        // 每个周期起点，高音和低音的第一个 onset 应相同
        val cycle0Start = PolyrhythmTrainingEngine.LEAD_SILENCE_MS
        val cycle1Start = PolyrhythmTrainingEngine.LEAD_SILENCE_MS + PolyrhythmTrainingEngine.CYCLE_DURATION_MS
        assertTrue(highOnsets.contains(cycle0Start))
        assertTrue(lowOnsets.contains(cycle0Start))
        assertTrue(highOnsets.contains(cycle1Start))
        assertTrue(lowOnsets.contains(cycle1Start))
    }

    @Test
    fun `等距音符的时间间距正确`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val (highOnsets, _) = engine.computeOnsetTimes(PolyrhythmType.TWO_THREE, 1)
        // 2:3 的高音声部在1周期内奏2个音符，间距 = CYCLE / 2
        val expectedStep = PolyrhythmTrainingEngine.CYCLE_DURATION_MS / 2.0
        val firstOnset = highOnsets[0]
        val secondOnset = highOnsets[1]
        assertEquals(expectedStep, secondOnset - firstOnset, 0.01)
    }

    // ── 复合性属性 ──────────────────────────────────────

    @Test
    fun `复杂度乘积随比例增大而增大`() {
        // 2:3 complexity=6, 3:4 complexity=12, 4:5 complexity=20, 2:5 complexity=10, 3:5 complexity=15
        assertTrue(PolyrhythmType.TWO_THREE.complexity < PolyrhythmType.THREE_FOUR.complexity)
        assertTrue(PolyrhythmType.THREE_FOUR.complexity < PolyrhythmType.FOUR_FIVE.complexity)
    }

    @Test
    fun `所有复合节奏的高音和低音数量都至少为2`() {
        PolyrhythmType.ALL.forEach { pr ->
            assertTrue(pr.highCount >= 2)
            assertTrue(pr.lowCount >= 2)
        }
    }

    @Test
    fun `所有比例中高音数不等于低音数`() {
        PolyrhythmType.ALL.forEach { pr ->
            assertNotEquals(pr.highCount, pr.lowCount)
        }
    }
}
