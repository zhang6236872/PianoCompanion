package com.pianocompanion.contrapuntalmotiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 声部运动辨识训练出题引擎单元测试。
 *
 * 验证确定性出题、难度覆盖、选项完整性。
 */
class ContrapuntalMotionEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子生成相同题目`() {
        val engine1 = ContrapuntalMotionEngine.withSeed(42L)
        val engine2 = ContrapuntalMotionEngine.withSeed(42L)

        val q1 = engine1.generate(ContrapuntalMotionDifficulty.BEGINNER)
        val q2 = engine2.generate(ContrapuntalMotionDifficulty.BEGINNER)

        assertEquals(q1.motion, q2.motion)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能生成不同题目`() {
        val engine1 = ContrapuntalMotionEngine.withSeed(1L)
        val engine2 = ContrapuntalMotionEngine.withSeed(999L)

        var different = false
        for (i in 0 until 20) {
            val q1 = engine1.generate(ContrapuntalMotionDifficulty.ADVANCED)
            val q2 = engine2.generate(ContrapuntalMotionDifficulty.ADVANCED)
            if (q1.motion != q2.motion) {
                different = true
                break
            }
        }
        assertTrue("不同种子应可能生成不同题目", different)
    }

    // ── 难度覆盖 ──────────────────────────────────────────

    @Test
    fun `初级只包含2种运动`() {
        val engine = ContrapuntalMotionEngine.withSeed(100L)
        val seen = mutableSetOf<ContrapuntalMotionType>()

        repeat(100) {
            val q = engine.generate(ContrapuntalMotionDifficulty.BEGINNER)
            seen.add(q.motion)
            assertTrue(
                "初级不应出现 ${q.motion}",
                q.motion in ContrapuntalMotionType.BEGINNER_TYPES
            )
        }

        // 经过 100 次应能覆盖全部 2 种
        assertEquals(2, seen.size)
        assertTrue(ContrapuntalMotionType.PARALLEL in seen)
        assertTrue(ContrapuntalMotionType.OBLIQUE in seen)
    }

    @Test
    fun `中级包含3种运动`() {
        val engine = ContrapuntalMotionEngine.withSeed(200L)
        val seen = mutableSetOf<ContrapuntalMotionType>()

        repeat(100) {
            val q = engine.generate(ContrapuntalMotionDifficulty.INTERMEDIATE)
            seen.add(q.motion)
            assertTrue(
                "中级不应出现 ${q.motion}",
                q.motion in ContrapuntalMotionType.INTERMEDIATE_TYPES
            )
        }

        assertEquals(3, seen.size)
        assertTrue(ContrapuntalMotionType.CONTRARY in seen)
    }

    @Test
    fun `高级包含全部4种运动`() {
        val engine = ContrapuntalMotionEngine.withSeed(300L)
        val seen = mutableSetOf<ContrapuntalMotionType>()

        repeat(150) {
            val q = engine.generate(ContrapuntalMotionDifficulty.ADVANCED)
            seen.add(q.motion)
        }

        assertEquals(4, seen.size)
        assertTrue(ContrapuntalMotionType.SIMILAR in seen)
    }

    // ── 选项数量 ──────────────────────────────────────────

    @Test
    fun `初级选项数量为2`() {
        val engine = ContrapuntalMotionEngine.withSeed(1L)
        val q = engine.generate(ContrapuntalMotionDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `中级选项数量为3`() {
        val engine = ContrapuntalMotionEngine.withSeed(1L)
        val q = engine.generate(ContrapuntalMotionDifficulty.INTERMEDIATE)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `高级选项数量为4`() {
        val engine = ContrapuntalMotionEngine.withSeed(1L)
        val q = engine.generate(ContrapuntalMotionDifficulty.ADVANCED)
        assertEquals(4, q.answerChoices.size)
    }

    // ── 答案正确性 ──────────────────────────────────────────

    @Test
    fun `正确答案在选项中`() {
        val engine = ContrapuntalMotionEngine.withSeed(42L)
        ContrapuntalMotionDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `选项无重复`() {
        val engine = ContrapuntalMotionEngine.withSeed(42L)
        ContrapuntalMotionDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `正确答案与运动类型匹配`() {
        val engine = ContrapuntalMotionEngine.withSeed(42L)
        ContrapuntalMotionDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(q.motion.fullLabel, q.correctAnswer)
        }
    }

    // ── 模型完整性 ──────────────────────────────────────────

    @Test
    fun `所有运动类型有非空属性`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            assertTrue("英文名为空: $motion", motion.englishName.isNotBlank())
            assertTrue("中文名为空: $motion", motion.displayName.isNotBlank())
            assertTrue("描述为空: $motion", motion.description.isNotBlank())
            assertTrue("提示为空: $motion", motion.hint.isNotBlank())
            assertTrue("emoji 为空: $motion", motion.emoji.isNotBlank())
        }
    }

    @Test
    fun `所有难度有非空属性`() {
        ContrapuntalMotionDifficulty.ALL.forEach { d ->
            assertTrue("显示名为空: $d", d.displayName.isNotBlank())
            assertTrue("描述为空: $d", d.description.isNotBlank())
            assertTrue("选项数应 >0: $d", d.choiceCount > 0)
        }
    }

    @Test
    fun `fullLabel 格式正确`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            assertTrue("$motion fullLabel 应包含中文名", motion.fullLabel.contains(motion.displayName))
            assertTrue("$motion fullLabel 应包含英文名", motion.fullLabel.contains(motion.englishName))
        }
    }

    // ── 难度递进 ──────────────────────────────────────────

    @Test
    fun `难度递进选项数递增`() {
        val beginner = ContrapuntalMotionDifficulty.BEGINNER.choiceCount
        val intermediate = ContrapuntalMotionDifficulty.INTERMEDIATE.choiceCount
        val advanced = ContrapuntalMotionDifficulty.ADVANCED.choiceCount
        assertTrue("初级选项数应 < 中级", beginner < intermediate)
        assertTrue("中级选项数应 < 高级", intermediate < advanced)
    }

    @Test
    fun `forDifficulty 返回正确集合`() {
        assertEquals(2, ContrapuntalMotionType.forDifficulty(ContrapuntalMotionDifficulty.BEGINNER).size)
        assertEquals(3, ContrapuntalMotionType.forDifficulty(ContrapuntalMotionDifficulty.INTERMEDIATE).size)
        assertEquals(4, ContrapuntalMotionType.forDifficulty(ContrapuntalMotionDifficulty.ADVANCED).size)
    }

    @Test
    fun `高级包含全部运动类型`() {
        val types = ContrapuntalMotionType.forDifficulty(ContrapuntalMotionDifficulty.ADVANCED)
        assertEquals(ContrapuntalMotionType.ALL, types)
    }
}
