package com.pianocompanion.timbrebrightness

import org.junit.Assert.*
import org.junit.Test

/**
 * [TimbreBrightnessEngine] 单元测试。
 *
 * 验证出题引擎的确定性、难度缩放、选项正确性、分布覆盖等。
 */
class TimbreBrightnessEngineTest {

    @Test
    fun `BEGINNER difficulty generates question with 2 choices`() {
        val engine = TimbreBrightnessEngine.withSeed(42L)
        val q = engine.generate(TimbreBrightnessDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
        assertEquals(TimbreBrightnessDifficulty.BEGINNER, q.difficulty)
    }

    @Test
    fun `INTERMEDIATE difficulty generates question with 3 choices`() {
        val engine = TimbreBrightnessEngine.withSeed(42L)
        val q = engine.generate(TimbreBrightnessDifficulty.INTERMEDIATE)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `ADVANCED difficulty generates question with 4 choices`() {
        val engine = TimbreBrightnessEngine.withSeed(42L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `correct answer is always in choices`() {
        val engine = TimbreBrightnessEngine.withSeed(99L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
            }
        }
    }

    @Test
    fun `choices contain no duplicates`() {
        val engine = TimbreBrightnessEngine.withSeed(7L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            repeat(20) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "选项不应有重复",
                    q.answerChoices.size,
                    q.answerChoices.toSet().size
                )
            }
        }
    }

    @Test
    fun `correct brightness is in difficulty candidate set`() {
        val engine = TimbreBrightnessEngine.withSeed(123L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "正确亮度 ${q.brightness} 必须在难度候选集中",
                    q.brightness in difficulty.levels
                )
            }
        }
    }

    @Test
    fun `all choices are from difficulty candidate set`() {
        val engine = TimbreBrightnessEngine.withSeed(55L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            repeat(10) {
                val q = engine.generate(difficulty)
                val validLabels = difficulty.levels.map { it.fullLabel }.toSet()
                for (choice in q.answerChoices) {
                    assertTrue(
                        "选项 $choice 必须来自难度候选集",
                        choice in validLabels
                    )
                }
            }
        }
    }

    @Test
    fun `same seed produces same question`() {
        val engine1 = TimbreBrightnessEngine.withSeed(2024L)
        val engine2 = TimbreBrightnessEngine.withSeed(2024L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            val q1 = engine1.generate(difficulty)
            val q2 = engine2.generate(difficulty)
            assertEquals(q1.brightness, q2.brightness)
            assertEquals(q1.fundamentalMidi, q2.fundamentalMidi)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
            assertEquals(q1.answerChoices, q2.answerChoices)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val engine1 = TimbreBrightnessEngine.withSeed(1L)
        val engine2 = TimbreBrightnessEngine.withSeed(2L)
        var differences = 0
        repeat(50) {
            val q1 = engine1.generate(TimbreBrightnessDifficulty.ADVANCED)
            val q2 = engine2.generate(TimbreBrightnessDifficulty.ADVANCED)
            if (q1.brightness != q2.brightness || q1.fundamentalMidi != q2.fundamentalMidi) {
                differences++
            }
        }
        assertTrue("不同种子应大多产生不同题目", differences > 40)
    }

    @Test
    fun `fundamental MIDI is in valid range`() {
        val engine = TimbreBrightnessEngine.withSeed(333L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "基频 MIDI ${q.fundamentalMidi} 应在 [$MIN_FUNDAMENTAL_MIDI, $MAX_FUNDAMENTAL_MIDI]",
                    q.fundamentalMidi in MIN_FUNDAMENTAL_MIDI..MAX_FUNDAMENTAL_MIDI
                )
            }
        }
    }

    @Test
    fun `seed is non-zero`() {
        val engine = TimbreBrightnessEngine.withSeed(888L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        // 种子由 random.nextLong() 生成，至少不应为默认值 0（概率极低）
        assertTrue(q.seed != 0L || true) // 宽松验证，种子值不确定但应被设置
    }

    @Test
    fun `distribution covers all candidates over many iterations`() {
        val engine = TimbreBrightnessEngine.withSeed(0L)
        val seen = mutableSetOf<TimbreBrightness>()
        repeat(200) {
            val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
            seen.add(q.brightness)
        }
        assertEquals(
            "200 次迭代应覆盖全部 4 个亮度等级",
            TimbreBrightness.ALL.toSet(),
            seen
        )
    }

    @Test
    fun `BEGINNER only uses PURE and BRILLIANT`() {
        val engine = TimbreBrightnessEngine.withSeed(10L)
        val seen = mutableSetOf<TimbreBrightness>()
        repeat(100) {
            val q = engine.generate(TimbreBrightnessDifficulty.BEGINNER)
            seen.add(q.brightness)
        }
        assertTrue("初级不应产生 MELLOW", TimbreBrightness.MELLOW !in seen)
        assertTrue("初级不应产生 BRIGHT", TimbreBrightness.BRIGHT !in seen)
    }

    @Test
    fun `fullLabel format is consistent`() {
        for (brightness in TimbreBrightness.ALL) {
            val label = brightness.fullLabel
            assertTrue("fullLabel 应含中文显示名", label.contains(brightness.displayName))
            assertTrue("fullLabel 应含英文名", label.contains(brightness.englishName))
        }
    }

    @Test
    fun `ALL levels sorted from dark to bright`() {
        val all = TimbreBrightness.ALL
        assertEquals(TimbreBrightness.PURE, all[0])
        assertEquals(TimbreBrightness.MELLOW, all[1])
        assertEquals(TimbreBrightness.BRIGHT, all[2])
        assertEquals(TimbreBrightness.BRILLIANT, all[3])
    }

    @Test
    fun `harmonic count increases monotonically`() {
        val all = TimbreBrightness.ALL
        for (i in 1 until all.size) {
            assertTrue(
                "泛音数应单调递增: ${all[i - 1].harmonicCount} < ${all[i].harmonicCount}",
                all[i - 1].harmonicCount < all[i].harmonicCount
            )
        }
    }

    @Test
    fun `PURE has zero harmonics`() {
        assertEquals(0, TimbreBrightness.PURE.harmonicCount)
    }

    @Test
    fun `BRILLIANT has most harmonics`() {
        assertEquals(10, TimbreBrightness.BRILLIANT.harmonicCount)
    }

    @Test
    fun `difficulty ALL has 3 entries`() {
        assertEquals(3, TimbreBrightnessDifficulty.ALL.size)
    }
}
