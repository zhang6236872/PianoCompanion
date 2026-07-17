package com.pianocompanion.sequencetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模进辨识训练出题引擎单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：确定性出题、选项正确性/唯一性/完整性、模进结构正确性、难度配置、
 * 音域钳制、动机偏移合理性。
 */
class SequenceTrainingEngineTest {

    // ── 基本生成 ──────────────────────────────────────────

    @Test
    fun `generate returns question with sequence type from difficulty pool`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(42L).generate(difficulty)
            assertTrue(
                "构造类型 ${q.type} 应在难度 ${difficulty.name} 的集合中",
                SequenceType.forDifficulty(difficulty).contains(q.type)
            )
        }
    }

    @Test
    fun `generate difficulty is preserved`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(7L).generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q1 = SequenceTrainingEngine.withSeed(123L).generate(difficulty)
            val q2 = SequenceTrainingEngine.withSeed(123L).generate(difficulty)
            assertEquals(q1.type, q2.type)
            assertEquals(q1.startMidi, q2.startMidi)
            assertEquals(q1.noteMidiSequence, q2.noteMidiSequence)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds likely produce different types`() {
        val types = (1..80).map {
            SequenceTrainingEngine.withSeed(it.toLong()).generate(SequenceDifficulty.ADVANCED).type
        }.toSet()
        // 4 种构造类型，80 次随机应覆盖至少 3 种（统计上几乎必然全覆盖）
        assertTrue("80 次出题应覆盖多种构造类型，实际覆盖 ${types.size} 种", types.size >= 3)
    }

    // ── 选项正确性 ──────────────────────────────────────────

    @Test
    fun `answer choices contain correct answer`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(99L).generate(difficulty)
            assertTrue(
                "正确答案 ${q.correctAnswer} 必须在选项中",
                q.answerChoices.contains(q.correctAnswer)
            )
        }
    }

    @Test
    fun `answer choices size matches difficulty pool`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(3L).generate(difficulty)
            val expected = SequenceType.forDifficulty(difficulty).size
            assertEquals(
                "难度 ${difficulty.name} 的选项数应为 $expected",
                expected,
                q.answerChoices.size
            )
        }
    }

    @Test
    fun `answer choices are unique`() {
        val q = SequenceTrainingEngine.withSeed(1L).generate(SequenceDifficulty.ADVANCED)
        assertEquals(
            "选项不应有重复",
            q.answerChoices.size,
            q.answerChoices.toSet().size
        )
    }

    @Test
    fun `answer choices equal difficulty pool display names`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(5L).generate(difficulty)
            val expected = SequenceType.forDifficulty(difficulty).map { it.displayName }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer equals type display name`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(8L).generate(difficulty)
            assertEquals(q.type.displayName, q.correctAnswer)
        }
    }

    // ── 动机与旋律结构 ────────────────────────────────────────

    @Test
    fun `motif first offset is always zero`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            assertEquals(
                "动机第一个音偏移必须为 0，实际 ${q.motifOffsets.first()}",
                0,
                q.motifOffsets.first()
            )
        }
    }

    @Test
    fun `motif length is three notes`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            assertEquals(3, q.motifLength)
        }
    }

    @Test
    fun `statement count is three`() {
        val q = SequenceTrainingEngine.withSeed(10L).generate(SequenceDifficulty.ADVANCED)
        assertEquals(3, q.statementCount)
    }

    @Test
    fun `non-free melody length equals motif length times statement count`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type != SequenceType.FREE) {
                val expected = q.motifLength * q.statementCount
                assertEquals(
                    "${q.type.displayName} 旋律长度应为动机×重复=$expected，实际 ${q.noteCount}",
                    expected,
                    q.noteCount
                )
            } else {
                // FREE 也应等于 motif*statement（生成相同总数音符）
                assertEquals(
                    "自由进行旋律长度",
                    q.motifLength * q.statementCount,
                    q.noteCount
                )
            }
        }
    }

    // ── 模进步距语义 ──────────────────────────────────────────

    @Test
    fun `ascending type has positive step`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type == SequenceType.ASCENDING) {
                assertTrue(
                    "上行模进步距应为正，实际 ${q.stepSemitones}",
                    q.stepSemitones > 0
                )
            }
        }
    }

    @Test
    fun `descending type has negative step`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type == SequenceType.DESCENDING) {
                assertTrue(
                    "下行模进步距应为负，实际 ${q.stepSemitones}",
                    q.stepSemitones < 0
                )
            }
        }
    }

    @Test
    fun `repetition and free types have zero step`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type == SequenceType.REPETITION || q.type == SequenceType.FREE) {
                assertEquals(
                    "${q.type.displayName} 步距应为 0",
                    0,
                    q.stepSemitones
                )
            }
        }
    }

    @Test
    fun `ascending melody overall trend is upward`() {
        // 上行模进：每段动机的起始音应高于上一段
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type == SequenceType.ASCENDING) {
                val segmentStarts = (0 until q.statementCount).map { s ->
                    q.noteMidiSequence[s * q.motifLength]
                }
                for (s in 1 until segmentStarts.size) {
                    assertTrue(
                        "上行模进第 $s 段起始(${segmentStarts[s]})应高于前段(${segmentStarts[s - 1]})",
                        segmentStarts[s] > segmentStarts[s - 1]
                    )
                }
            }
        }
    }

    @Test
    fun `descending melody overall trend is downward`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type == SequenceType.DESCENDING) {
                val segmentStarts = (0 until q.statementCount).map { s ->
                    q.noteMidiSequence[s * q.motifLength]
                }
                for (s in 1 until segmentStarts.size) {
                    assertTrue(
                        "下行模进第 $s 段起始(${segmentStarts[s]})应低于前段(${segmentStarts[s - 1]})",
                        segmentStarts[s] < segmentStarts[s - 1]
                    )
                }
            }
        }
    }

    @Test
    fun `repetition melody has identical segments`() {
        repeat(30) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            if (q.type == SequenceType.REPETITION) {
                val segments = (0 until q.statementCount).map { s ->
                    q.noteMidiSequence.subList(s * q.motifLength, (s + 1) * q.motifLength).toList()
                }
                for (s in 1 until segments.size) {
                    assertEquals(
                        "重复模进各段应完全相同",
                        segments[0],
                        segments[s]
                    )
                }
            }
        }
    }

    // ── 音域钳制 ──────────────────────────────────────────

    @Test
    fun `all melody notes stay within piano range`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            repeat(20) { i ->
                val q = SequenceTrainingEngine.withSeed(i.toLong() + difficulty.ordinal * 1000).generate(difficulty)
                q.noteMidiSequence.forEach { midi ->
                    assertTrue(
                        "旋律音符 $midi 应在钢琴范围 [21,108]",
                        midi in SequenceTrainingEngine.MIN_MIDI..SequenceTrainingEngine.MAX_MIDI
                    )
                }
            }
        }
    }

    @Test
    fun `start midi is within piano range`() {
        repeat(100) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            assertTrue(
                "起始音 ${q.startMidi} 应在钢琴范围 [21,108]",
                q.startMidi in SequenceTrainingEngine.MIN_MIDI..SequenceTrainingEngine.MAX_MIDI
            )
        }
    }

    @Test
    fun `note duration is positive`() {
        repeat(20) { i ->
            val q = SequenceTrainingEngine.withSeed(i.toLong()).generate(SequenceDifficulty.ADVANCED)
            assertTrue("音符时长应为正，实际 ${q.noteDurationMs}", q.noteDurationMs > 0)
        }
    }

    @Test
    fun `start note name is non-blank`() {
        val q = SequenceTrainingEngine.withSeed(1L).generate(SequenceDifficulty.ADVANCED)
        assertTrue("起始音名不应为空", q.startNoteName.isNotBlank())
    }

    // ── 难度配置 ──────────────────────────────────────────

    @Test
    fun `beginner pool has two directional types`() {
        val pool = SequenceType.forDifficulty(SequenceDifficulty.BEGINNER)
        assertEquals(2, pool.size)
        assertTrue("初级应含上行模进", pool.contains(SequenceType.ASCENDING))
        assertTrue("初级应含下行模进", pool.contains(SequenceType.DESCENDING))
    }

    @Test
    fun `intermediate pool has three types`() {
        val pool = SequenceType.forDifficulty(SequenceDifficulty.INTERMEDIATE)
        assertEquals(3, pool.size)
        assertTrue("中级应含自由进行", pool.contains(SequenceType.FREE))
    }

    @Test
    fun `advanced pool has all four types`() {
        val pool = SequenceType.forDifficulty(SequenceDifficulty.ADVANCED)
        assertEquals(SequenceType.ALL.size, pool.size)
        assertEquals(SequenceType.ALL.toSet(), pool.toSet())
    }

    @Test
    fun `difficulty pools are subsets of advanced`() {
        SequenceDifficulty.ALL.forEach { d ->
            val pool = SequenceType.forDifficulty(d).toSet()
            assertTrue(
                "难度 ${d.name} 的集合应是高级集合的子集",
                SequenceType.ALL.toSet().containsAll(pool)
            )
        }
    }

    @Test
    fun `all four sequence types exist`() {
        assertEquals(4, SequenceType.ALL.size)
    }

    @Test
    fun `each sequence type has non-blank metadata`() {
        SequenceType.ALL.forEach { type ->
            assertTrue("${type.name} 显示名不应为空", type.displayName.isNotBlank())
            assertTrue("${type.name} 描述不应为空", type.description.isNotBlank())
            assertTrue("${type.name} 英文名不应为空", type.englishName.isNotBlank())
            assertTrue("${type.name} 听辨提示不应为空", type.listeningHint.isNotBlank())
        }
    }

    @Test
    fun `sequence duration is duration times note count`() {
        val q = SequenceTrainingEngine.withSeed(2L).generate(SequenceDifficulty.ADVANCED)
        assertEquals(
            q.noteDurationMs * q.noteCount,
            q.sequenceDurationMs
        )
    }

    @Test
    fun `different difficulties produce valid structures`() {
        // 同种子下，不同难度的题目都能正确生成（只是选项数和类型不同）
        SequenceDifficulty.ALL.forEach { d ->
            val q = SequenceTrainingEngine.withSeed(42L).generate(d)
            assertEquals(d, q.difficulty)
            assertTrue(q.noteCount > 0)
        }
        assertNotEquals(SequenceDifficulty.BEGINNER, SequenceDifficulty.ADVANCED)
    }
}
