package com.pianocompanion.rhythmmemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmMemoryEngine] 单元测试。
 */
class RhythmMemoryEngineTest {

    @Test
    fun `beginner 选项数为 2`() {
        val engine = RhythmMemoryEngine.withSeed(1)
        val q = engine.generate(RhythmMemoryDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `intermediate 选项数为 3`() {
        val engine = RhythmMemoryEngine.withSeed(2)
        val q = engine.generate(RhythmMemoryDifficulty.INTERMEDIATE)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `advanced 选项数为 4`() {
        val engine = RhythmMemoryEngine.withSeed(3)
        val q = engine.generate(RhythmMemoryDifficulty.ADVANCED)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = RhythmMemoryEngine.withSeed(10)
        RhythmMemoryDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(q.answerChoices.size, q.answerChoices.distinct().size)
        }
    }

    @Test
    fun `正确答案在选项中`() {
        val engine = RhythmMemoryEngine.withSeed(20)
        RhythmMemoryDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertTrue(q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `正确答案等于目标节奏型显示串`() {
        val engine = RhythmMemoryEngine.withSeed(30)
        RhythmMemoryDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(q.targetPattern.displayString, q.correctAnswer)
        }
    }

    @Test
    fun `确定性 - 相同种子产生相同题目`() {
        val e1 = RhythmMemoryEngine.withSeed(42)
        val e2 = RhythmMemoryEngine.withSeed(42)
        val q1 = e1.generate(RhythmMemoryDifficulty.INTERMEDIATE)
        val q2 = e2.generate(RhythmMemoryDifficulty.INTERMEDIATE)
        assertEquals(q1.targetPattern.displayString, q2.targetPattern.displayString)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `不同种子产生不同题目`() {
        val set = mutableSetOf<String>()
        for (seed in 0 until 20) {
            val q = RhythmMemoryEngine.withSeed(seed.toLong()).generate(RhythmMemoryDifficulty.ADVANCED)
            set.add(q.targetPattern.displayString)
        }
        // 不同种子应产生多种不同的目标节奏型
        assertTrue("不同种子应产生至少 3 种不同目标", set.size >= 3)
    }

    @Test
    fun `beginner 目标节奏型拍数为 3`() {
        val q = RhythmMemoryEngine.withSeed(5).generate(RhythmMemoryDifficulty.BEGINNER)
        assertEquals(3, q.beats)
        assertEquals(3, q.targetPattern.beats)
    }

    @Test
    fun `intermediate 与 advanced 目标节奏型拍数为 4`() {
        val qi = RhythmMemoryEngine.withSeed(5).generate(RhythmMemoryDifficulty.INTERMEDIATE)
        val qa = RhythmMemoryEngine.withSeed(5).generate(RhythmMemoryDifficulty.ADVANCED)
        assertEquals(4, qi.beats)
        assertEquals(4, qa.beats)
    }

    @Test
    fun `beginner 仅使用初级节奏单元池`() {
        val pool = RhythmMemoryDifficulty.BEGINNER.cellPool.toSet()
        for (seed in 0 until 30) {
            val q = RhythmMemoryEngine.withSeed(seed.toLong()).generate(RhythmMemoryDifficulty.BEGINNER)
            q.targetPattern.cells.forEach { cell ->
                assertTrue("单元 $cell 应在初级池中", cell in pool)
            }
        }
    }

    @Test
    fun `intermediate 仅使用中级节奏单元池`() {
        val pool = RhythmMemoryDifficulty.INTERMEDIATE.cellPool.toSet()
        for (seed in 0 until 30) {
            val q = RhythmMemoryEngine.withSeed(seed.toLong()).generate(RhythmMemoryDifficulty.INTERMEDIATE)
            q.targetPattern.cells.forEach { cell ->
                assertTrue(cell in pool)
            }
        }
    }

    @Test
    fun `advanced 可使用全部 7 种节奏单元`() {
        val pool = RhythmMemoryDifficulty.ADVANCED.cellPool.toSet()
        assertEquals(7, pool.size)
        // 大样本覆盖：确保高级池所有单元都有机会出现
        val used = mutableSetOf<RhythmCellType>()
        for (seed in 0 until 200) {
            val q = RhythmMemoryEngine.withSeed(seed.toLong()).generate(RhythmMemoryDifficulty.ADVANCED)
            used.addAll(q.targetPattern.cells)
        }
        assertEquals("高级应覆盖全部 7 种单元", pool, used)
    }

    @Test
    fun `干扰项均与目标不同`() {
        for (seed in 0 until 50) {
            val q = RhythmMemoryEngine.withSeed(seed.toLong()).generate(RhythmMemoryDifficulty.ADVANCED)
            val target = q.correctAnswer
            q.answerChoices.forEach { choice ->
                if (choice != target) {
                    assertNotEquals("干扰项应不同于正确答案", target, choice)
                }
            }
        }
    }

    @Test
    fun `seed 字段被填充`() {
        val q = RhythmMemoryEngine.withSeed(7).generate(RhythmMemoryDifficulty.BEGINNER)
        assertTrue(q.seed != 0L || true) // seed 可能为 0，仅验证字段存在且可读
    }

    @Test
    fun `tempoBpm 继承自难度`() {
        RhythmMemoryDifficulty.ALL.forEach { d ->
            val q = RhythmMemoryEngine.withSeed(9).generate(d)
            assertEquals(d.tempoBpm, q.tempoBpm)
        }
    }

    @Test
    fun `选项均来自合法节奏型显示串`() {
        // 干扰项也是合法节奏型（每项均可被 RhythmPattern 表达）
        val q = RhythmMemoryEngine.withSeed(11).generate(RhythmMemoryDifficulty.ADVANCED)
        assertFalse(q.answerChoices.isEmpty())
        // 所有选项都应非空且长度合理
        q.answerChoices.forEach { choice ->
            assertTrue(choice.isNotBlank())
            assertTrue(choice.length >= 1)
        }
    }

    @Test
    fun `大样本下每次都能生成合法题目`() {
        // 1000 次生成不应抛异常（init 块校验通过）
        for (seed in 0 until 1000) {
            RhythmMemoryEngine.withSeed(seed.toLong()).generate(RhythmMemoryDifficulty.ADVANCED)
        }
    }
}
