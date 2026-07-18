package com.pianocompanion.voicecounttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceCountEngine] 单元测试。
 */
class VoiceCountEngineTest {

    @Test
    fun `BEGINNER 生成的声部数在 1-3 范围内`() {
        val engine = VoiceCountEngine.withSeed(1L)
        repeat(50) {
            val q = engine.generate(VoiceCountDifficulty.BEGINNER)
            assertTrue("声部数 ${q.voiceCount} 应在 1..3", q.voiceCount in 1..3)
        }
    }

    @Test
    fun `INTERMEDIATE 生成的声部数在 1-4 范围内`() {
        val engine = VoiceCountEngine.withSeed(2L)
        repeat(50) {
            val q = engine.generate(VoiceCountDifficulty.INTERMEDIATE)
            assertTrue("声部数 ${q.voiceCount} 应在 1..4", q.voiceCount in 1..4)
        }
    }

    @Test
    fun `ADVANCED 生成的声部数在 1-6 范围内`() {
        val engine = VoiceCountEngine.withSeed(3L)
        repeat(50) {
            val q = engine.generate(VoiceCountDifficulty.ADVANCED)
            assertTrue("声部数 ${q.voiceCount} 应在 1..6", q.voiceCount in 1..6)
        }
    }

    @Test
    fun `选项数量等于难度最大声部数`() {
        val engine = VoiceCountEngine.withSeed(10L)
        assertEquals(3, engine.generate(VoiceCountDifficulty.BEGINNER).answerChoices.size)
        assertEquals(4, engine.generate(VoiceCountDifficulty.INTERMEDIATE).answerChoices.size)
        assertEquals(6, engine.generate(VoiceCountDifficulty.ADVANCED).answerChoices.size)
    }

    @Test
    fun `选项内容为 1 到 N 个音`() {
        val engine = VoiceCountEngine.withSeed(10L)
        val q = engine.generate(VoiceCountDifficulty.INTERMEDIATE)
        assertEquals("1 个音（单音）", q.answerChoices[0])
        assertEquals("2 个音（音程）", q.answerChoices[1])
        assertEquals("3 个音（三和弦）", q.answerChoices[2])
        assertEquals("4 个音（七和弦）", q.answerChoices[3])
    }

    @Test
    fun `正确答案在选项中且与 voiceCount 对应`() {
        val engine = VoiceCountEngine.withSeed(7L)
        for (d in VoiceCountDifficulty.ALL) {
            val q = engine.generate(d)
            assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
            assertEquals(VoiceCountQuestion.countLabelText(q.voiceCount), q.correctAnswer)
        }
    }

    @Test
    fun `voicing 长度等于 voiceCount 且无重复音`() {
        val engine = VoiceCountEngine.withSeed(11L)
        for (d in VoiceCountDifficulty.ALL) {
            repeat(20) {
                val q = engine.generate(d)
                assertEquals(q.voiceCount, q.voicing.size)
                assertEquals(q.voicing.size, q.voicing.toSet().size)
            }
        }
    }

    @Test
    fun `voicing 所有音在合法音域内`() {
        val engine = VoiceCountEngine.withSeed(99L)
        for (d in VoiceCountDifficulty.ALL) {
            repeat(30) {
                val q = engine.generate(d)
                q.voicing.forEach { midi ->
                    assertTrue("MIDI $midi 应在音域内", midi in VoiceCountQuestion.MIN_MIDI..VoiceCountQuestion.MAX_MIDI)
                }
            }
        }
    }

    @Test
    fun `voicing 严格递增`() {
        val engine = VoiceCountEngine.withSeed(123L)
        for (d in VoiceCountDifficulty.ALL) {
            repeat(20) {
                val q = engine.generate(d)
                for (i in 1 until q.voicing.size) {
                    assertTrue("voicing 应严格递增: ${q.voicing}", q.voicing[i] > q.voicing[i - 1])
                }
            }
        }
    }

    @Test
    fun `WIDE 间距相邻音至少纯四度（5 半音）`() {
        val engine = VoiceCountEngine.withSeed(5L)
        repeat(30) {
            val q = engine.generate(VoiceCountDifficulty.BEGINNER)
            for (i in 1 until q.voicing.size) {
                val interval = q.voicing[i] - q.voicing[i - 1]
                assertTrue("WIDE 间距应 ≥ 5，实际 $interval", interval >= 5)
            }
        }
    }

    @Test
    fun `CLOSE 间距相邻音至少小二度（1 半音）至多小三度（3 半音）`() {
        val engine = VoiceCountEngine.withSeed(8L)
        repeat(40) {
            val q = engine.generate(VoiceCountDifficulty.ADVANCED)
            for (i in 1 until q.voicing.size) {
                val interval = q.voicing[i] - q.voicing[i - 1]
                assertTrue("CLOSE 间距应在 1..3，实际 $interval", interval in 1..3)
            }
        }
    }

    @Test
    fun `MEDIUM 间距相邻音在大二度到大三度（2-4 半音）`() {
        val engine = VoiceCountEngine.withSeed(13L)
        repeat(40) {
            val q = engine.generate(VoiceCountDifficulty.INTERMEDIATE)
            for (i in 1 until q.voicing.size) {
                val interval = q.voicing[i] - q.voicing[i - 1]
                assertTrue("MEDIUM 间距应在 2..4，实际 $interval", interval in 2..4)
            }
        }
    }

    @Test
    fun `相同种子产生相同题目（确定性）`() {
        val e1 = VoiceCountEngine.withSeed(42L)
        val e2 = VoiceCountEngine.withSeed(42L)
        val q1 = e1.generate(VoiceCountDifficulty.ADVANCED)
        val q2 = e2.generate(VoiceCountDifficulty.ADVANCED)
        assertEquals(q1.voiceCount, q2.voiceCount)
        assertEquals(q1.voicing, q2.voicing)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `难度绑定正确`() {
        val engine = VoiceCountEngine.withSeed(1L)
        for (d in VoiceCountDifficulty.ALL) {
            val q = engine.generate(d)
            assertEquals(d, q.difficulty)
            assertEquals(d.spacing, q.spacing)
        }
    }

    @Test
    fun `rootMidi 等于 voicing 首音`() {
        val engine = VoiceCountEngine.withSeed(77L)
        repeat(20) {
            val q = engine.generate(VoiceCountDifficulty.ADVANCED)
            assertEquals(q.voicing.first(), q.rootMidi)
        }
    }

    @Test
    fun `覆盖性 - BEGINNER 能产生 1 2 3 三种声部数`() {
        val engine = VoiceCountEngine.withSeed(2024L)
        val counts = mutableSetOf<Int>()
        repeat(200) {
            counts.add(engine.generate(VoiceCountDifficulty.BEGINNER).voiceCount)
        }
        assertTrue("应覆盖 1,2,3: $counts", setOf(1, 2, 3).all { it in counts })
    }

    @Test
    fun `覆盖性 - ADVANCED 能产生 1 到 6 全部声部数`() {
        val engine = VoiceCountEngine.withSeed(2025L)
        val counts = mutableSetOf<Int>()
        repeat(400) {
            counts.add(engine.generate(VoiceCountDifficulty.ADVANCED).voiceCount)
        }
        assertTrue("应覆盖 1..6: $counts", (1..6).all { it in counts })
    }

    @Test
    fun `buildVoicing 单音返回一个在音域内的音`() {
        val v = VoiceCountEngine.buildVoicing(1, NoteSpacing.WIDE, kotlin.random.Random(1L), baseRoot = 60)
        assertEquals(1, v.size)
        assertTrue(v[0] in VoiceCountQuestion.MIN_MIDI..VoiceCountQuestion.MAX_MIDI)
    }

    @Test
    fun `buildVoicing 高根音不会超出音域上限`() {
        // 6 个 WIDE 音从 C7(96) 起会远超上限，应平移回合法音域
        val v = VoiceCountEngine.buildVoicing(6, NoteSpacing.WIDE, kotlin.random.Random(1L), baseRoot = 96)
        assertEquals(6, v.size)
        assertTrue(v.all { it <= VoiceCountQuestion.MAX_MIDI })
        assertTrue(v.all { it >= VoiceCountQuestion.MIN_MIDI })
        assertEquals(v.size, v.toSet().size)
    }

    @Test
    fun `countLabelText 对 1-6 返回带术语的文本`() {
        assertEquals("1 个音（单音）", VoiceCountQuestion.countLabelText(1))
        assertEquals("2 个音（音程）", VoiceCountQuestion.countLabelText(2))
        assertEquals("3 个音（三和弦）", VoiceCountQuestion.countLabelText(3))
        assertEquals("4 个音（七和弦）", VoiceCountQuestion.countLabelText(4))
        assertEquals("5 个音（九和弦）", VoiceCountQuestion.countLabelText(5))
        assertEquals("6 个音（密集音簇）", VoiceCountQuestion.countLabelText(6))
    }

    @Test
    fun `durationMs 为正`() {
        val engine = VoiceCountEngine.withSeed(1L)
        val q = engine.generate(VoiceCountDifficulty.INTERMEDIATE)
        assertTrue(q.durationMs > 0)
    }
}
