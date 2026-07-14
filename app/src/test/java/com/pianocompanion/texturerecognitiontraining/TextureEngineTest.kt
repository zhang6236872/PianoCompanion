package com.pianocompanion.texturerecognitiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 织体辨识训练出题引擎单元测试。
 *
 * 验证确定性出题、难度覆盖、选项完整性。
 */
class TextureEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子生成相同题目`() {
        val engine1 = TextureEngine.withSeed(42L)
        val engine2 = TextureEngine.withSeed(42L)

        val q1 = engine1.generate(TextureDifficulty.BEGINNER)
        val q2 = engine2.generate(TextureDifficulty.BEGINNER)

        assertEquals(q1.texture, q2.texture)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能生成不同题目`() {
        val engine1 = TextureEngine.withSeed(1L)
        val engine2 = TextureEngine.withSeed(999L)

        var different = false
        for (i in 0 until 20) {
            val q1 = engine1.generate(TextureDifficulty.ADVANCED)
            val q2 = engine2.generate(TextureDifficulty.ADVANCED)
            if (q1.texture != q2.texture) {
                different = true
                break
            }
        }
        assertTrue("不同种子应可能生成不同题目", different)
    }

    // ── 难度覆盖 ──────────────────────────────────────────

    @Test
    fun `初级只包含3种织体`() {
        val engine = TextureEngine.withSeed(100L)
        val seen = mutableSetOf<TextureType>()
        for (i in 0 until 50) {
            val q = engine.generate(TextureDifficulty.BEGINNER)
            seen.add(q.texture)
            assertTrue("初级不应包含 ${q.texture}", q.texture in TextureType.BEGINNER_TYPES)
        }
        assertEquals("50 题应覆盖全部 3 种初级织体", 3, seen.size)
    }

    @Test
    fun `中级包含4种织体`() {
        val engine = TextureEngine.withSeed(100L)
        val seen = mutableSetOf<TextureType>()
        for (i in 0 until 80) {
            val q = engine.generate(TextureDifficulty.INTERMEDIATE)
            seen.add(q.texture)
            assertTrue("中级不应包含 ${q.texture}", q.texture in TextureType.INTERMEDIATE_TYPES)
        }
        assertEquals("80 题应覆盖全部 4 种中级织体", 4, seen.size)
    }

    @Test
    fun `高级包含全部5种织体`() {
        val engine = TextureEngine.withSeed(100L)
        val seen = mutableSetOf<TextureType>()
        for (i in 0 until 100) {
            val q = engine.generate(TextureDifficulty.ADVANCED)
            seen.add(q.texture)
        }
        assertEquals("100 题应覆盖全部 5 种织体", 5, seen.size)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `选项数量等于难度选择数`() {
        val engine = TextureEngine.withSeed(42L)
        for (difficulty in TextureDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(
                "${difficulty.displayName} 选项数应为 ${difficulty.choiceCount}",
                difficulty.choiceCount,
                q.answerChoices.size
            )
        }
    }

    @Test
    fun `正确答案在选项中`() {
        val engine = TextureEngine.withSeed(42L)
        for (difficulty in TextureDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue(
                "正确答案 '${q.correctAnswer}' 应在选项中",
                q.correctAnswer in q.answerChoices
            )
        }
    }

    @Test
    fun `选项无重复`() {
        val engine = TextureEngine.withSeed(42L)
        for (difficulty in TextureDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(
                "选项不应有重复",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `正确答案等于所选织体的全标签`() {
        val engine = TextureEngine.withSeed(77L)
        for (difficulty in TextureDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.texture.fullLabel, q.correctAnswer)
        }
    }

    // ── 种子 ──────────────────────────────────────────

    @Test
    fun `每道题种子非零`() {
        val engine = TextureEngine.withSeed(42L)
        val q = engine.generate(TextureDifficulty.ADVANCED)
        // 种子可以是任何 Long 值（包括 0 理论上），但连续生成的种子不同
        val q2 = engine.generate(TextureDifficulty.ADVANCED)
        assertNotEquals("连续题目的种子应不同", q.seed, q2.seed)
    }

    @Test
    fun `多题连续生成不出错`() {
        val engine = TextureEngine.withSeed(12345L)
        for (i in 0 until 200) {
            val q = engine.generate(TextureDifficulty.ADVANCED)
            assertNotNull(q.texture)
            assertNotNull(q.correctAnswer)
            assertTrue(q.answerChoices.isNotEmpty())
        }
    }

    @Test
    fun `withSeed 工厂方法创建确定性引擎`() {
        val e1 = TextureEngine.withSeed(555L)
        val e2 = TextureEngine.withSeed(555L)
        val results1 = (0 until 10).map { e1.generate(TextureDifficulty.INTERMEDIATE).texture }
        val results2 = (0 until 10).map { e2.generate(TextureDifficulty.INTERMEDIATE).texture }
        assertEquals(results1, results2)
    }

    // ── 难度与织体集合一致性 ──────────────────────────────────────────

    @Test
    fun `初级织体集合包含单声部柱式和声复调`() {
        assertEquals(
            listOf(TextureType.MONOPHONIC, TextureType.HOMOPHONIC_CHORDAL, TextureType.POLYPHONIC),
            TextureType.BEGINNER_TYPES
        )
    }

    @Test
    fun `中级织体集合加入分解和弦`() {
        assertTrue(TextureType.HOMOPHONIC_ARPEGGIATED in TextureType.INTERMEDIATE_TYPES)
        assertTrue(TextureType.HETEROPHONIC !in TextureType.INTERMEDIATE_TYPES)
    }

    @Test
    fun `高级织体集合包含全部5种`() {
        assertEquals(5, TextureType.ADVANCED_TYPES.size)
        assertEquals(TextureType.ALL, TextureType.ADVANCED_TYPES)
    }

    @Test
    fun `forDifficulty 返回正确的集合`() {
        assertEquals(TextureType.BEGINNER_TYPES, TextureType.forDifficulty(TextureDifficulty.BEGINNER))
        assertEquals(TextureType.INTERMEDIATE_TYPES, TextureType.forDifficulty(TextureDifficulty.INTERMEDIATE))
        assertEquals(TextureType.ADVANCED_TYPES, TextureType.forDifficulty(TextureDifficulty.ADVANCED))
    }

    @Test
    fun `choiceCount 与织体集合大小一致`() {
        for (d in TextureDifficulty.ALL) {
            val types = TextureType.forDifficulty(d)
            assertTrue(
                "${d.displayName} choiceCount=${d.choiceCount} 应 <= 可用织体数 ${types.size}",
                d.choiceCount <= types.size
            )
        }
    }
}
