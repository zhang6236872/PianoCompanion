package com.pianocompanion.nonchordtonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦外音辨识训练出题引擎单元测试。
 *
 * 覆盖：难度选项数量、确定性、类型与模板自洽性（classifyContour）、
 * 选项完整性、选项打乱等。
 */
class NonChordToneEngineTest {

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = NonChordToneEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = NonChordToneEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.INTERMEDIATE)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 4 choices`() {
        val engine = NonChordToneEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            assertEquals(4, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are passing vs appoggiatura`() {
        val engine = NonChordToneEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(NonChordToneDifficulty.BEGINNER)
            assertTrue(q.answerChoices.contains(NonChordToneType.PASSING_TONE.fullLabel))
            assertTrue(q.answerChoices.contains(NonChordToneType.APPOGGIATURA.fullLabel))
        }
    }

    @Test
    fun `intermediate choices include the three intermediate types`() {
        val engine = NonChordToneEngine.withSeed(3)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.INTERMEDIATE)
            NonChordToneType.INTERMEDIATE_TYPES.forEach { t ->
                assertTrue("Missing ${t.fullLabel}", q.answerChoices.contains(t.fullLabel))
            }
        }
    }

    @Test
    fun `advanced choices include all four types`() {
        val engine = NonChordToneEngine.withSeed(3)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            assertEquals(NonChordToneType.ALL.size, q.answerChoices.size)
            NonChordToneType.ALL.forEach { t ->
                assertTrue("Missing ${t.fullLabel}", q.answerChoices.contains(t.fullLabel))
            }
        }
    }

    @Test
    fun `beginner only produces passing or appoggiatura types`() {
        val engine = NonChordToneEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(NonChordToneDifficulty.BEGINNER)
            assertTrue(
                "Beginner should only produce passing/appoggiatura, got ${q.type}",
                q.type in NonChordToneType.BEGINNER_TYPES
            )
        }
    }

    @Test
    fun `intermediate only produces the three intermediate types`() {
        val engine = NonChordToneEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(NonChordToneDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate produced out-of-set type ${q.type}",
                q.type in NonChordToneType.INTERMEDIATE_TYPES
            )
        }
    }

    @Test
    fun `advanced can produce all four types across many seeds`() {
        val seen = mutableSetOf<NonChordToneType>()
        for (seed in 0L..500) {
            val engine = NonChordToneEngine.withSeed(seed)
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            seen.add(q.type)
        }
        assertEquals(4, seen.size)
    }

    @Test
    fun `deterministic - same seed produces same question`() {
        val e1 = NonChordToneEngine.withSeed(777)
        val e2 = NonChordToneEngine.withSeed(777)
        val q1 = e1.generate(NonChordToneDifficulty.ADVANCED)
        val q2 = e2.generate(NonChordToneDifficulty.ADVANCED)
        assertEquals(q1.type, q2.type)
        assertEquals(q1.template, q2.template)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `correct answer is always in answer choices`() {
        val engine = NonChordToneEngine.withSeed(55)
        NonChordToneDifficulty.ALL.forEach { difficulty ->
            repeat(15) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Correct answer '${q.correctAnswer}' not in choices ${q.answerChoices}",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `correct answer equals the type's full label`() {
        val engine = NonChordToneEngine.withSeed(55)
        repeat(30) {
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            assertEquals(q.type.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `template belongs to the selected type`() {
        val engine = NonChordToneEngine.withSeed(99)
        repeat(40) {
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            assertTrue(
                "Template ${q.template} not in type ${q.type}'s templates",
                q.type.templates.any { it == q.template }
            )
        }
    }

    @Test
    fun `every template classifies back to its own type`() {
        // 关键自洽性：每个类型的模板轮廓必须能被 classifyContour 反推为该类型
        NonChordToneType.ALL.forEach { type ->
            type.templates.forEach { template ->
                val inferred = classifyContour(template)
                assertNotNull("Template $template of $type classified as null", inferred)
                assertEquals(
                    "Template $template classified as $inferred, expected $type",
                    type,
                    inferred
                )
            }
        }
    }

    @Test
    fun `every template has exactly 3 notes`() {
        NonChordToneType.ALL.forEach { type ->
            type.templates.forEach { template ->
                assertEquals("${type} template $template size", 3, template.size)
            }
        }
    }

    @Test
    fun `every template middle note is a non-chord tone`() {
        // 和弦音 = {0,4,7} mod 12（C 大三和弦）；中间音（template[1]）必须为和弦外音
        val chordTones = setOf(0, 4, 7)
        NonChordToneType.ALL.forEach { type ->
            type.templates.forEach { template ->
                val middle = ((template[1] % 12) + 12) % 12
                assertTrue(
                    "${type} template $template middle note ${template[1]} is a chord tone",
                    middle !in chordTones
                )
            }
        }
    }

    @Test
    fun `every template endpoints are chord tones`() {
        val chordTones = setOf(0, 4, 7)
        NonChordToneType.ALL.forEach { type ->
            type.templates.forEach { template ->
                val first = ((template[0] % 12) + 12) % 12
                val last = ((template[2] % 12) + 12) % 12
                assertTrue("${type} template $template first note not chord tone", first in chordTones)
                assertTrue("${type} template $template last note not chord tone", last in chordTones)
            }
        }
    }

    @Test
    fun `root midi is C4`() {
        val engine = NonChordToneEngine.withSeed(42)
        val q = engine.generate(NonChordToneDifficulty.INTERMEDIATE)
        assertEquals(60, q.rootMidi)
    }

    @Test
    fun `melody midi is template offset by root`() {
        val engine = NonChordToneEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            assertEquals(q.template.map { it + 60 }, q.melodyMidi)
        }
    }

    @Test
    fun `answer choices are unique within a question`() {
        val engine = NonChordToneEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(NonChordToneDifficulty.ADVANCED)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `classifyContour returns null for invalid contour`() {
        // 跳进接近 + 跳进解决（双跳）不属于任何标准类型
        assertNull(classifyContour(listOf(0, 7, 12)))
        // 同音（无运动）
        assertNull(classifyContour(listOf(4, 4, 4)))
        // 长度不是 3
        assertNull(classifyContour(listOf(0, 2, 4, 5)))
        assertNull(classifyContour(listOf(0, 2)))
    }

    @Test
    fun `isStep and isLeap are correct`() {
        assertTrue(isStep(1))
        assertTrue(isStep(2))
        assertTrue(isStep(-1))
        assertTrue(isStep(-2))
        assertTrue(isLeap(3))
        assertTrue(isLeap(5))
        assertTrue(isLeap(-3))
        assertTrue(!isStep(0))   // 同音不是级进
        assertTrue(!isLeap(2))
        assertTrue(!isStep(3))
    }

    @Test
    fun `passing and neighbor templates both use step approach and resolution`() {
        // 经过/辅助：接近和解决都是级进
        NonChordToneType.PASSING_TONE.templates.forEach { t ->
            assertTrue(isStep(t[1] - t[0]))
            assertTrue(isStep(t[2] - t[1]))
        }
        NonChordToneType.NEIGHBOR_TONE.templates.forEach { t ->
            assertTrue(isStep(t[1] - t[0]))
            assertTrue(isStep(t[2] - t[1]))
        }
    }

    @Test
    fun `appoggiatura uses leap approach and step resolution`() {
        NonChordToneType.APPOGGIATURA.templates.forEach { t ->
            assertTrue("approach should be leap", isLeap(t[1] - t[0]))
            assertTrue("resolution should be step", isStep(t[2] - t[1]))
        }
    }

    @Test
    fun `escape tone uses step approach and leap resolution`() {
        NonChordToneType.ESCAPE_TONE.templates.forEach { t ->
            assertTrue("approach should be step", isStep(t[1] - t[0]))
            assertTrue("resolution should be leap", isLeap(t[2] - t[1]))
        }
    }

    @Test
    fun `passing and neighbor are distinguished by direction`() {
        // 经过：同向；辅助：反向
        NonChordToneType.PASSING_TONE.templates.forEach { t ->
            val app = t[1] - t[0]
            val res = t[2] - t[1]
            assertEquals("passing should be same direction", app > 0, res > 0)
        }
        NonChordToneType.NEIGHBOR_TONE.templates.forEach { t ->
            val app = t[1] - t[0]
            val res = t[2] - t[1]
            assertEquals("neighbor should be opposite direction", app > 0, res < 0)
        }
    }
}
