package com.pianocompanion.texturerecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureCategoryEngineTest {

    @Test
    fun `BEGINNER 生成题目包含正确数量的选项`() {
        val engine = TextureCategoryEngine.withSeed(42L)
        val q = engine.generate(MusicTextureDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
        assertEquals(3, q.answerChoices.distinct().size)
    }

    @Test
    fun `INTERMEDIATE 生成题目包含 4 个选项`() {
        val engine = TextureCategoryEngine.withSeed(7L)
        val q = engine.generate(MusicTextureDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
        assertEquals(4, q.answerChoices.distinct().size)
    }

    @Test
    fun `ADVANCED 生成题目包含 4 个选项`() {
        val engine = TextureCategoryEngine.withSeed(99L)
        val q = engine.generate(MusicTextureDifficulty.ADVANCED)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `正确答案在选项中`() {
        val engine = TextureCategoryEngine.withSeed(1L)
        repeat(20) {
            val q = engine.generate(MusicTextureDifficulty.INTERMEDIATE)
            assertTrue(q.correctAnswer in q.answerChoices)
            assertEquals(q.targetTexture.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `BEGINNER 目标织体只来自 BEGINNER 集合（不含支声复调）`() {
        val engine = TextureCategoryEngine.withSeed(123L)
        repeat(30) {
            val q = engine.generate(MusicTextureDifficulty.BEGINNER)
            assertTrue(q.targetTexture in MusicTextureType.BEGINNER_TYPES)
            assertNotEquals(MusicTextureType.HETEROPHONIC, q.targetTexture)
        }
    }

    @Test
    fun `相同种子产生相同题目（确定性）`() {
        val e1 = TextureCategoryEngine.withSeed(2026L)
        val e2 = TextureCategoryEngine.withSeed(2026L)
        val q1 = e1.generate(MusicTextureDifficulty.ADVANCED)
        val q2 = e2.generate(MusicTextureDifficulty.ADVANCED)
        assertEquals(q1.targetTexture, q2.targetTexture)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `选项全部来自该难度的织体集合`() {
        val engine = TextureCategoryEngine.withSeed(555L)
        val q = engine.generate(MusicTextureDifficulty.INTERMEDIATE)
        val validLabels = MusicTextureType.INTERMEDIATE_TYPES.map { it.fullLabel }.toSet()
        q.answerChoices.forEach { choice ->
            assertTrue("选项 $choice 不在有效织体集合中", choice in validLabels)
        }
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val q1 = TextureCategoryEngine.withSeed(1L).generate(MusicTextureDifficulty.ADVANCED)
        val q2 = TextureCategoryEngine.withSeed(2L).generate(MusicTextureDifficulty.ADVANCED)
        // 至少有一个字段不同（目标织体或选项顺序）
        val different = q1.targetTexture != q2.targetTexture || q1.answerChoices != q2.answerChoices
        assertTrue(different)
    }

    @Test
    fun `BEGINNER 选项数等于难度 choiceCount`() {
        val engine = TextureCategoryEngine.withSeed(3L)
        val q = engine.generate(MusicTextureDifficulty.BEGINNER)
        assertEquals(MusicTextureDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
    }

    @Test
    fun `题目继承难度的 tempo 和 complexity`() {
        val engine = TextureCategoryEngine.withSeed(8L)
        val q = engine.generate(MusicTextureDifficulty.ADVANCED)
        assertEquals(MusicTextureDifficulty.ADVANCED.tempoBpm, q.tempoBpm)
        assertEquals(MusicTextureDifficulty.ADVANCED.complexity, q.complexity)
    }
}
