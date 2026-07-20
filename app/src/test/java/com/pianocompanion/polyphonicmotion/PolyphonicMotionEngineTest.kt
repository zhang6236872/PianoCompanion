package com.pianocompanion.polyphonicmotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复调运动辨识训练出题引擎单元测试。
 */
class PolyphonicMotionEngineTest {

    // ── 选项正确性 ──────────────────────────────────

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = MotionEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(MotionDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = MotionEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(MotionDifficulty.INTERMEDIATE)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 3 choices`() {
        val engine = MotionEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(MotionDifficulty.ADVANCED)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are parallel vs contrary`() {
        val engine = MotionEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(MotionDifficulty.BEGINNER)
            assertTrue(
                "Expected Parallel/Contrary labels, got: ${q.answerChoices}",
                q.answerChoices.contains(MotionType.PARALLEL.fullLabel) &&
                    q.answerChoices.contains(MotionType.CONTRARY.fullLabel)
            )
        }
    }

    @Test
    fun `intermediate choices are parallel contrary oblique`() {
        val engine = MotionEngine.withSeed(100)
        repeat(30) {
            val q = engine.generate(MotionDifficulty.INTERMEDIATE)
            val expected = MotionDifficulty.INTERMEDIATE.motions.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced choices are all 3 motions`() {
        val engine = MotionEngine.withSeed(5)
        repeat(30) {
            val q = engine.generate(MotionDifficulty.ADVANCED)
            val expected = MotionType.ALL.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = MotionEngine.withSeed(3)
        MotionDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Correct answer not in choices: ${q.correctAnswer}",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `choices contain no duplicates`() {
        val engine = MotionEngine.withSeed(13)
        MotionDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "Duplicate choices found: ${q.answerChoices}",
                    q.answerChoices.size,
                    q.answerChoices.distinct().size
                )
            }
        }
    }

    @Test
    fun `choices are never empty`() {
        val engine = MotionEngine.withSeed(33)
        MotionDifficulty.ALL.forEach { difficulty ->
            repeat(5) {
                val q = engine.generate(difficulty)
                assertTrue("Choices should not be empty", q.answerChoices.isNotEmpty())
            }
        }
    }

    @Test
    fun `answer count matches difficulty choice count`() {
        val engine = MotionEngine.withSeed(33)
        MotionDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.choiceCount, q.answerChoices.size)
        }
    }

    // ── 确定性 / 种子 ──────────────────────────────────

    @Test
    fun `deterministic generation with same seed`() {
        val engine1 = MotionEngine.withSeed(777)
        val engine2 = MotionEngine.withSeed(777)
        repeat(10) {
            val q1 = engine1.generate(MotionDifficulty.ADVANCED)
            val q2 = engine2.generate(MotionDifficulty.ADVANCED)
            assertEquals(q1.motionType, q2.motionType)
            assertEquals(q1.upperVoice, q2.upperVoice)
            assertEquals(q1.lowerVoice, q2.lowerVoice)
            assertEquals(q1.answerChoices, q2.answerChoices)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val questions = (0L..100).map { seed ->
            val e = MotionEngine.withSeed(seed)
            e.generate(MotionDifficulty.ADVANCED)
        }
        val distinctMotions = questions.map { it.motionType }.distinct()
        assertTrue(
            "Expected variety of motions across seeds, got ${distinctMotions.size}",
            distinctMotions.size >= 2
        )
    }

    @Test
    fun `different engines with different seeds produce different sequences`() {
        val engine1 = MotionEngine.withSeed(1)
        val engine2 = MotionEngine.withSeed(2)
        var anyDifferent = false
        repeat(20) {
            val q1 = engine1.generate(MotionDifficulty.ADVANCED)
            val q2 = engine2.generate(MotionDifficulty.ADVANCED)
            if (q1.motionType != q2.motionType || q1.upperVoice != q2.upperVoice) {
                anyDifferent = true
            }
        }
        assertTrue("Different seeds should produce different sequences", anyDifferent)
    }

    @Test
    fun `seed is captured in question`() {
        val engine = MotionEngine.withSeed(123)
        val q = engine.generate(MotionDifficulty.ADVANCED)
        assertNotNull(q.seed)
    }

    // ── 难度缩放 / 候选集约束 ──────────────────────────────────

    @Test
    fun `beginner correct answer is parallel or contrary only`() {
        val engine = MotionEngine.withSeed(2)
        repeat(50) {
            val q = engine.generate(MotionDifficulty.BEGINNER)
            assertTrue(
                "Beginner answer must be Parallel or Contrary, got ${q.motionType}",
                q.motionType == MotionType.PARALLEL || q.motionType == MotionType.CONTRARY
            )
        }
    }

    @Test
    fun `intermediate correct answer is in intermediate set`() {
        val engine = MotionEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(MotionDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate answer not in candidate set, got ${q.motionType}",
                q.motionType in MotionDifficulty.INTERMEDIATE.motions
            )
        }
    }

    @Test
    fun `advanced correct answer is in advanced set`() {
        val engine = MotionEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(MotionDifficulty.ADVANCED)
            assertTrue(
                "Advanced answer not in candidate set, got ${q.motionType}",
                q.motionType in MotionDifficulty.ADVANCED.motions
            )
        }
    }

    @Test
    fun `beginner produces both parallel and contrary across seeds`() {
        val answers = mutableSetOf<MotionType>()
        for (seed in 0L..500) {
            val engine = MotionEngine.withSeed(seed)
            val q = engine.generate(MotionDifficulty.BEGINNER)
            answers.add(q.motionType)
        }
        assertTrue("Should produce both motions", answers.size >= 2)
        assertTrue("Should include Parallel", MotionType.PARALLEL in answers)
        assertTrue("Should include Contrary", MotionType.CONTRARY in answers)
    }

    @Test
    fun `intermediate produces all 3 motions across seeds`() {
        val answers = mutableSetOf<MotionType>()
        for (seed in 0L..3000) {
            val engine = MotionEngine.withSeed(seed)
            val q = engine.generate(MotionDifficulty.INTERMEDIATE)
            answers.add(q.motionType)
        }
        assertEquals(
            "Should produce all 3 motion types",
            MotionType.ALL.toSet(),
            answers
        )
    }

    @Test
    fun `question difficulty matches requested`() {
        val engine = MotionEngine.withSeed(33)
        MotionDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    // ── 声部结构正确性（核心：verifyMotion 不变量） ──────────────────────────────────

    @Test
    fun `generated voices always satisfy motion invariant`() {
        val engine = MotionEngine.withSeed(20)
        MotionDifficulty.ALL.forEach { difficulty ->
            repeat(50) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Generated voices must satisfy ${q.motionType} invariant. " +
                        "upper=${q.upperVoice}, lower=${q.lowerVoice}",
                    MotionEngine.verifyMotion(q.upperVoice, q.lowerVoice, q.motionType)
                )
            }
        }
    }

    @Test
    fun `upper and lower voices have equal length`() {
        val engine = MotionEngine.withSeed(20)
        repeat(50) {
            val q = engine.generate(MotionDifficulty.ADVANCED)
            assertEquals(
                "Voices must be equal length",
                q.upperVoice.size,
                q.lowerVoice.size
            )
        }
    }

    @Test
    fun `voices have correct note count for difficulty`() {
        val engine = MotionEngine.withSeed(20)
        MotionDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                assertEquals(difficulty.noteCount, q.upperVoice.size)
            }
        }
    }

    @Test
    fun `upper voice always above lower voice`() {
        val engine = MotionEngine.withSeed(8)
        repeat(100) {
            val q = engine.generate(MotionDifficulty.ADVANCED)
            q.upperVoice.indices.forEach { i ->
                assertTrue(
                    "Upper ${q.upperVoice[i]} must be above lower ${q.lowerVoice[i]} at note $i",
                    q.upperVoice[i] > q.lowerVoice[i]
                )
            }
        }
    }

    @Test
    fun `all midi values within valid range`() {
        val engine = MotionEngine.withSeed(8)
        repeat(100) {
            val q = engine.generate(MotionDifficulty.ADVANCED)
            assertTrue(q.upperVoice.all { it in MIN_MIDI..MAX_MIDI })
            assertTrue(q.lowerVoice.all { it in MIN_MIDI..MAX_MIDI })
        }
    }

    @Test
    fun `choices are shuffled and not always in declaration order`() {
        val naturalOrder = MotionType.ALL.map { it.fullLabel }
        var foundShuffled = false
        for (seed in 0L..200) {
            val engine = MotionEngine.withSeed(seed)
            val q = engine.generate(MotionDifficulty.ADVANCED)
            if (q.answerChoices != naturalOrder) {
                foundShuffled = true
                break
            }
        }
        assertTrue("Choices should sometimes be shuffled from natural order", foundShuffled)
    }

    // ── verifyMotion 直接测试 ──────────────────────────────────

    @Test
    fun `verifyMotion accepts valid parallel`() {
        // 高声部 72,74,76,78 上行；低声部 48,50,52,54 上行 → 同向
        assertTrue(MotionEngine.verifyMotion(listOf(72, 74, 76, 78), listOf(48, 50, 52, 54), MotionType.PARALLEL))
    }

    @Test
    fun `verifyMotion rejects parallel with opposite directions`() {
        // 高声部上行、低声部下行 → 不是同向
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 74, 76), listOf(52, 50, 48), MotionType.PARALLEL))
    }

    @Test
    fun `verifyMotion accepts valid contrary`() {
        // 高声部上行、低声部下行 → 反向
        assertTrue(MotionEngine.verifyMotion(listOf(72, 74, 76, 78), listOf(54, 52, 50, 48), MotionType.CONTRARY))
    }

    @Test
    fun `verifyMotion rejects contrary with same directions`() {
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 74, 76), listOf(48, 50, 52), MotionType.CONTRARY))
    }

    @Test
    fun `verifyMotion accepts valid oblique with upper holding`() {
        // 高声部保持 72；低声部移动 48,50,52 → 斜向
        assertTrue(MotionEngine.verifyMotion(listOf(72, 72, 72), listOf(48, 50, 52), MotionType.OBLIQUE))
    }

    @Test
    fun `verifyMotion accepts valid oblique with lower holding`() {
        // 低声部保持 48；高声部移动 72,74,76 → 斜向
        assertTrue(MotionEngine.verifyMotion(listOf(72, 74, 76), listOf(48, 48, 48), MotionType.OBLIQUE))
    }

    @Test
    fun `verifyMotion rejects oblique where both move`() {
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 74, 76), listOf(48, 50, 52), MotionType.OBLIQUE))
    }

    @Test
    fun `verifyMotion rejects oblique with inconsistent holding voice`() {
        // 第一步高声部保持、第二步低声部保持 → 保持声部切换，不符合斜向定义
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 72, 74), listOf(48, 50, 50), MotionType.OBLIQUE))
    }

    @Test
    fun `verifyMotion rejects crossing voices`() {
        // 高声部低于低声部 → 声部交叉
        assertTrue(!MotionEngine.verifyMotion(listOf(48, 50, 52), listOf(72, 74, 76), MotionType.PARALLEL))
    }

    @Test
    fun `verifyMotion rejects touching voices`() {
        // 高声部等于低声部 → 不严格高于
        assertTrue(!MotionEngine.verifyMotion(listOf(60, 62, 64), listOf(60, 60, 60), MotionType.OBLIQUE))
    }

    @Test
    fun `verifyMotion rejects unequal lengths`() {
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 74), listOf(48, 50, 52), MotionType.PARALLEL))
    }

    @Test
    fun `verifyMotion rejects single note`() {
        assertTrue(!MotionEngine.verifyMotion(listOf(72), listOf(48), MotionType.PARALLEL))
    }

    @Test
    fun `verifyMotion rejects parallel with a hold step`() {
        // 第二步高声部不动 → 同向要求均非零
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 72, 74), listOf(48, 50, 52), MotionType.PARALLEL))
    }

    @Test
    fun `verifyMotion rejects contrary with a hold step`() {
        assertTrue(!MotionEngine.verifyMotion(listOf(72, 72, 74), listOf(54, 52, 50), MotionType.CONTRARY))
    }

    @Test
    fun `no two consecutive questions are guaranteed identical`() {
        val engine = MotionEngine.withSeed(999)
        val q1 = engine.generate(MotionDifficulty.INTERMEDIATE)
        val q2 = engine.generate(MotionDifficulty.INTERMEDIATE)
        assertNotEquals(0L, q1.seed)
        assertNotEquals(0L, q2.seed)
    }
}
