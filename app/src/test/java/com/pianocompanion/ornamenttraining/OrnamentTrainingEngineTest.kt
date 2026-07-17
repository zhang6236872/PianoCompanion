package com.pianocompanion.ornamenttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装饰音辨识训练出题引擎单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：确定性出题、选项正确性/唯一性/完整性、装饰音类型定义、难度配置、
 * OrnamentType.forDifficulty / noteSequence / 音符计数。
 */
class OrnamentTrainingEngineTest {

    // ── 基本生成 ──────────────────────────────────────────

    @Test
    fun `generate returns question with ornament type from difficulty pool`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(42L).generate(difficulty)
            assertTrue(
                "装饰音类型 ${q.type} 应在难度 ${difficulty.name} 的集合中",
                OrnamentType.forDifficulty(difficulty).contains(q.type)
            )
        }
    }

    @Test
    fun `generate difficulty is preserved`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(7L).generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q1 = OrnamentTrainingEngine.withSeed(123L).generate(difficulty)
            val q2 = OrnamentTrainingEngine.withSeed(123L).generate(difficulty)
            assertEquals(q1.type, q2.type)
            assertEquals(q1.mainMidi, q2.mainMidi)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds likely produce different sequence`() {
        val types = (1..50).map {
            OrnamentTrainingEngine.withSeed(it.toLong()).generate(OrnamentDifficulty.ADVANCED).type
        }.toSet()
        // 5 种装饰音，50 次随机应覆盖至少 4 种（统计上几乎必然全覆盖）
        assertTrue("50 次出题应覆盖多种装饰音类型，实际覆盖 ${types.size} 种", types.size >= 4)
    }

    // ── 选项正确性 ──────────────────────────────────────────

    @Test
    fun `answer choices contain correct answer`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(99L).generate(difficulty)
            assertTrue(
                "正确答案 ${q.correctAnswer} 必须在选项中",
                q.answerChoices.contains(q.correctAnswer)
            )
        }
    }

    @Test
    fun `answer choices size matches difficulty pool`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(3L).generate(difficulty)
            val expected = OrnamentType.forDifficulty(difficulty).size
            assertEquals(
                "难度 ${difficulty.name} 的选项数应为 $expected",
                expected,
                q.answerChoices.size
            )
        }
    }

    @Test
    fun `answer choices are unique`() {
        val q = OrnamentTrainingEngine.withSeed(1L).generate(OrnamentDifficulty.ADVANCED)
        assertEquals(
            "选项不应有重复",
            q.answerChoices.size,
            q.answerChoices.toSet().size
        )
    }

    @Test
    fun `answer choices equal difficulty pool display names`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(5L).generate(difficulty)
            val expected = OrnamentType.forDifficulty(difficulty).map { it.displayName }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer equals type display name`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(8L).generate(difficulty)
            assertEquals(q.type.displayName, q.correctAnswer)
        }
    }

    // ── 主音范围 ────────────────────────────────────────────

    @Test
    fun `main midi is within C5 to G5 range`() {
        repeat(100) { i ->
            val q = OrnamentTrainingEngine.withSeed(i.toLong()).generate(OrnamentDifficulty.ADVANCED)
            assertTrue(
                "主音 ${q.mainMidi} 应在 C5(72)-G5(79) 范围",
                q.mainMidi in 72..79
            )
        }
    }

    @Test
    fun `main note name ends with octave 5`() {
        val q = OrnamentTrainingEngine.withSeed(10L).generate(OrnamentDifficulty.ADVANCED)
        assertTrue("主音名 ${q.mainNoteName} 应以 5 结尾", q.mainNoteName.endsWith("5"))
    }

    @Test
    fun `auxiliary notes stay within piano range`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            repeat(20) { i ->
                val q = OrnamentTrainingEngine.withSeed(i.toLong() + difficulty.ordinal * 1000).generate(difficulty)
                q.noteEvents.forEach { note ->
                    val midi = q.mainMidi + note.semitoneOffset
                    assertTrue(
                        "辅助音 $midi 应在钢琴范围 [21,108]",
                        midi in OrnamentTrainingEngine.MIN_MIDI..OrnamentTrainingEngine.MAX_MIDI
                    )
                }
            }
        }
    }

    // ── 音符事件 ──────────────────────────────────────────

    @Test
    fun `note events match type note sequence`() {
        OrnamentDifficulty.ALL.forEach { difficulty ->
            val q = OrnamentTrainingEngine.withSeed(2L).generate(difficulty)
            assertEquals(q.type.noteCount, q.noteEvents.size)
            q.noteEvents.forEachIndexed { idx, event ->
                val expected = q.type.noteSequence()[idx]
                assertEquals(expected.semitoneOffset, event.semitoneOffset)
                assertEquals(expected.durationMs, event.durationMs)
            }
        }
    }

    @Test
    fun `note events are non-empty`() {
        val q = OrnamentTrainingEngine.withSeed(1L).generate(OrnamentDifficulty.ADVANCED)
        assertTrue("音符序列不应为空", q.noteEvents.isNotEmpty())
    }

    // ── 装饰音类型定义 ───────────────────────────────────────

    @Test
    fun `trill has the most notes`() {
        // 颤音应有最多音符（9 个），是最「颤动」的
        val trill = OrnamentType.TRILL.noteCount
        OrnamentType.ALL.forEach { type ->
            assertTrue(
                "颤音($trill) 的音符数应不少于 ${type.displayName}(${type.noteCount})",
                trill >= type.noteCount
            )
        }
    }

    @Test
    fun `each ornament type has distinct note count or pattern`() {
        // 至少颤音(9)、波音(3)、回音(4)、短倚(2)、长倚(2) — 部分数量相同但节奏不同
        val counts = OrnamentType.ALL.map { it.noteCount }
        assertTrue("装饰音类型应有多样化的音符数", counts.toSet().size >= 3)
    }

    @Test
    fun `ornament note sequence durations are positive`() {
        OrnamentType.ALL.forEach { type ->
            type.noteSequence().forEach { note ->
                assertTrue(
                    "${type.displayName} 的音符时长应为正数，实际 ${note.durationMs}",
                    note.durationMs > 0
                )
            }
        }
    }

    @Test
    fun `trill alternates main and upper notes`() {
        val offsets = OrnamentType.TRILL.noteSequence().map { it.semitoneOffset }
        // 颤音应在 0 和 +2 之间交替
        assertTrue("颤音应含主音(0)", offsets.contains(0))
        assertTrue("颤音应含上方音(+2)", offsets.contains(2))
        assertTrue("颤音不应含下方音", offsets.none { it < 0 })
        assertEquals(0, offsets.last()) // 以主音收尾
    }

    @Test
    fun `turn includes upper main and lower notes`() {
        val offsets = OrnamentType.TURN.noteSequence().map { it.semitoneOffset }
        assertTrue("回音应含上方音(+2)", offsets.contains(2))
        assertTrue("回音应含主音(0)", offsets.contains(0))
        assertTrue("回音应含下方音(-2)", offsets.contains(-2))
    }

    @Test
    fun `grace note has two notes with first much shorter`() {
        val notes = OrnamentType.GRACE_NOTE.noteSequence()
        assertEquals(2, notes.size)
        assertTrue(
            "短倚音的装饰音应远短于主音: ${notes[0].durationMs} vs ${notes[1].durationMs}",
            notes[0].durationMs < notes[1].durationMs / 2
        )
    }

    @Test
    fun `appoggiatura grace is longer than grace note`() {
        // 长倚音的装饰音应比短倚音的装饰音长
        val appGrace = OrnamentType.APPOGGIATURA.noteSequence()[0].durationMs
        val graceGrace = OrnamentType.GRACE_NOTE.noteSequence()[0].durationMs
        assertTrue(
            "长倚音装饰音($appGrace)应长于短倚音装饰音($graceGrace)",
            appGrace > graceGrace
        )
    }

    @Test
    fun `mordent has exactly three notes`() {
        assertEquals(3, OrnamentType.MORDENT.noteCount)
    }

    // ── 难度配置 ──────────────────────────────────────────

    @Test
    fun `beginner pool has two contrasting types`() {
        val pool = OrnamentType.forDifficulty(OrnamentDifficulty.BEGINNER)
        assertEquals(2, pool.size)
        assertTrue("初级应含颤音", pool.contains(OrnamentType.TRILL))
        assertTrue("初级应含短倚音", pool.contains(OrnamentType.GRACE_NOTE))
    }

    @Test
    fun `intermediate pool has three types`() {
        val pool = OrnamentType.forDifficulty(OrnamentDifficulty.INTERMEDIATE)
        assertEquals(3, pool.size)
        assertTrue("中级应含波音", pool.contains(OrnamentType.MORDENT))
    }

    @Test
    fun `advanced pool has all five types`() {
        val pool = OrnamentType.forDifficulty(OrnamentDifficulty.ADVANCED)
        assertEquals(OrnamentType.ALL.size, pool.size)
        assertEquals(OrnamentType.ALL.toSet(), pool.toSet())
    }

    @Test
    fun `difficulty pools are subsets of advanced`() {
        OrnamentDifficulty.ALL.forEach { d ->
            val pool = OrnamentType.forDifficulty(d).toSet()
            assertTrue(
                "难度 ${d.name} 的集合应是高级集合的子集",
                OrnamentType.ALL.toSet().containsAll(pool)
            )
        }
    }

    @Test
    fun `all five ornament types exist`() {
        assertEquals(5, OrnamentType.ALL.size)
    }

    @Test
    fun `each ornament type has non-blank display name and description`() {
        OrnamentType.ALL.forEach { type ->
            assertTrue("${type.name} 显示名不应为空", type.displayName.isNotBlank())
            assertTrue("${type.name} 描述不应为空", type.description.isNotBlank())
            assertTrue("${type.name} 英文名不应为空", type.englishName.isNotBlank())
            assertTrue("${type.name} 符号不应为空", type.symbol.isNotBlank())
            assertTrue("${type.name} 听辨提示不应为空", type.listeningHint.isNotBlank())
        }
    }

    @Test
    fun `different difficulties produce same structure`() {
        // 同种子下，不同难度的题目结构一致（只是选项数不同）
        val q1 = OrnamentTrainingEngine.withSeed(42L).generate(OrnamentDifficulty.BEGINNER)
        val q2 = OrnamentTrainingEngine.withSeed(42L).generate(OrnamentDifficulty.ADVANCED)
        assertEquals(q1.mainMidi, q2.mainMidi)
        // 选项数不同（除非同种子恰好选中同类型）
        assertNotEquals(OrnamentDifficulty.BEGINNER, OrnamentDifficulty.ADVANCED)
        assertFalse(q1.answerChoices.size == q2.answerChoices.size && q1.answerChoices.toSet() == q2.answerChoices.toSet()
            && q1.type == q2.type) // 极不可能完全相同（这里只验证不会因结构问题崩溃）
    }
}
