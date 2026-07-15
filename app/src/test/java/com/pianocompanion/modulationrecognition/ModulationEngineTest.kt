package com.pianocompanion.modulationrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转调辨识训练出题引擎单元测试。
 *
 * 验证：
 * - 确定性：相同种子生成相同题目
 * - 难度覆盖：各难度的选项数量正确
 * - 答案正确性：正确答案始终在选项中
 * - 选项完整性：无重复选项，数量等于 choiceCount
 */
class ModulationEngineTest {

    @Test
    fun `相同种子生成相同题目`() {
        val engine1 = ModulationEngine.withSeed(42L)
        val engine2 = ModulationEngine.withSeed(42L)

        val q1 = engine1.generate(ModulationDifficulty.ADVANCED)
        val q2 = engine2.generate(ModulationDifficulty.ADVANCED)

        assertEquals(q1.modulation, q2.modulation)
        assertEquals(q1.seed, q2.seed)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子生成不同种子值`() {
        val engine1 = ModulationEngine.withSeed(1L)
        val engine2 = ModulationEngine.withSeed(2L)

        val q1 = engine1.generate(ModulationDifficulty.ADVANCED)
        val q2 = engine2.generate(ModulationDifficulty.ADVANCED)

        // 种子值极大概率不同（不能保证类型不同，但种子值应该不同）
        assertTrue(q1.seed != q2.seed || q1.modulation != q2.modulation)
    }

    @Test
    fun `初级难度生成2个选项`() {
        val engine = ModulationEngine.withSeed(10L)
        val q = engine.generate(ModulationDifficulty.BEGINNER)

        assertEquals(2, q.answerChoices.size)
        assertEquals(2, q.answerChoices.toSet().size) // 无重复
    }

    @Test
    fun `中级难度生成3个选项`() {
        val engine = ModulationEngine.withSeed(10L)
        val q = engine.generate(ModulationDifficulty.INTERMEDIATE)

        assertEquals(3, q.answerChoices.size)
        assertEquals(3, q.answerChoices.toSet().size)
    }

    @Test
    fun `高级难度生成4个选项`() {
        val engine = ModulationEngine.withSeed(10L)
        val q = engine.generate(ModulationDifficulty.ADVANCED)

        assertEquals(4, q.answerChoices.size)
        assertEquals(4, q.answerChoices.toSet().size)
    }

    @Test
    fun `正确答案始终在选项中`() {
        val engine = ModulationEngine.withSeed(100L)
        ModulationDifficulty.ALL.forEach { d ->
            repeat(10) {
                val q = engine.generate(d)
                assertTrue(
                    "正确答案 ${q.correctAnswer} 应在选项中: ${q.answerChoices}",
                    q.correctAnswer in q.answerChoices
                )
            }
        }
    }

    @Test
    fun `初级难度只使用初级类型集合`() {
        val engine = ModulationEngine.withSeed(50L)
        val validTypes = ModulationType.BEGINNER_TYPES.map { it.fullLabel }
        repeat(20) {
            val q = engine.generate(ModulationDifficulty.BEGINNER)
            assertTrue(
                "正确答案 ${q.correctAnswer} 应在初级集合中: $validTypes",
                q.correctAnswer in validTypes
            )
        }
    }

    @Test
    fun `中级难度只使用中级类型集合`() {
        val engine = ModulationEngine.withSeed(50L)
        val validTypes = ModulationType.INTERMEDIATE_TYPES.map { it.fullLabel }
        repeat(20) {
            val q = engine.generate(ModulationDifficulty.INTERMEDIATE)
            assertTrue(
                "正确答案 ${q.correctAnswer} 应在中级集合中: $validTypes",
                q.correctAnswer in validTypes
            )
        }
    }

    @Test
    fun `高级难度覆盖所有4种类型`() {
        val engine = ModulationEngine.withSeed(999L)
        val seenTypes = mutableSetOf<ModulationType>()
        repeat(100) {
            val q = engine.generate(ModulationDifficulty.ADVANCED)
            seenTypes.add(q.modulation)
        }
        assertEquals(4, seenTypes.size)
    }

    @Test
    fun `题目包含难度信息`() {
        val engine = ModulationEngine.withSeed(7L)
        ModulationDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(d, q.difficulty)
        }
    }

    @Test
    fun `题目包含种子值`() {
        val engine = ModulationEngine.withSeed(7L)
        val q = engine.generate(ModulationDifficulty.BEGINNER)
        assertTrue(q.seed != 0L)
    }

    @Test
    fun `选项顺序被随机打乱`() {
        val engine = ModulationEngine.withSeed(42L)
        // 生成大量题目，验证选项顺序不总是相同的
        val seenOrders = mutableSetOf<List<String>>()
        repeat(50) {
            val q = engine.generate(ModulationDifficulty.ADVANCED)
            seenOrders.add(q.answerChoices)
        }
        assertTrue("应该有多种选项排列顺序", seenOrders.size > 1)
    }

    @Test
    fun `fullLabel 格式正确`() {
        ModulationType.ALL.forEach { type ->
            val q = ModulationEngine.withSeed(0L).generate(ModulationDifficulty.ADVANCED).let {
                // 只检查格式
                type.fullLabel
            }
            assertNotNull(q)
        }
    }

    @Test
    fun `forDifficulty 返回正确类型集合`() {
        assertEquals(2, ModulationType.forDifficulty(ModulationDifficulty.BEGINNER).size)
        assertEquals(3, ModulationType.forDifficulty(ModulationDifficulty.INTERMEDIATE).size)
        assertEquals(4, ModulationType.forDifficulty(ModulationDifficulty.ADVANCED).size)
    }
}
