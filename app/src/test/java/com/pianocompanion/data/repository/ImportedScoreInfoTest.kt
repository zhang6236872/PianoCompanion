package com.pianocompanion.data.repository

import com.pianocompanion.analytics.DifficultyEstimator
import com.pianocompanion.analytics.DifficultyLevel
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImportedScoreInfo.from] 工厂方法单元测试（纯 Kotlin，无 Android 依赖）。
 *
 * 验证 v2.61.0 的核心改动：导入乐谱在解析时由 [DifficultyEstimator] 计算
 * 难度总分与等级，使导入乐谱卡片与内置乐谱卡片展示一致的难度信息。
 *
 * 覆盖：基础字段映射、难度一致性、标题回退、来源标签、边界（空/单音）、
 * 相对难度（复杂乐谱 > 简单儿歌）、双手独立因子。
 */
class ImportedScoreInfoTest {

    // ══════════════════════════════════════════════════════════════════
    //  测试辅助构建器
    // ══════════════════════════════════════════════════════════════════

    /** 构建单声部旋律音符列表。 */
    private fun melody(
        pitches: List<Int>,
        intervalMs: Long = 500L,
        durationMs: Long = 450L,
        staff: Staff = Staff.TREBLE
    ): List<ScoreNote> = pitches.mapIndexed { idx, midi ->
        ScoreNote(
            midiNumber = midi,
            noteName = "n$midi",
            startTime = idx * intervalMs,
            duration = durationMs,
            measureIndex = idx / 4,
            staff = staff
        )
    }

    /** 构建测试用 Score。 */
    private fun score(
        notes: List<ScoreNote>,
        title: String = "测试曲",
        composer: String = "测试作曲家",
        tempo: Int = 120
    ): Score = Score(
        id = "test",
        title = title,
        composer = composer,
        notes = notes,
        tempo = tempo,
        timeSignature = "4/4"
    )

    // ══════════════════════════════════════════════════════════════════
    //  基础字段映射
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `basic fields are mapped correctly`() {
        val notes = melody(listOf(60, 62, 64, 65))
        val s = score(notes = notes, title = "月光奏鸣曲", composer = "贝多芬")
        val info = ImportedScoreInfo.from(s, "moonlight.xml", "MusicXML")

        assertEquals("moonlight.xml", info.fileName)
        assertEquals("月光奏鸣曲", info.title)
        assertEquals("贝多芬", info.composer)
        assertEquals(4, info.noteCount)
        assertEquals("MusicXML", info.source)
        assertFalse(info.parseFailed)
    }

    @Test
    fun `midi source label is set correctly`() {
        val info = ImportedScoreInfo.from(
            score(notes = melody(listOf(60, 62))),
            "song.mid",
            "MIDI"
        )
        assertEquals("MIDI", info.source)
    }

    @Test
    fun `parseFailed is always false for factory`() {
        // 工厂方法仅用于成功解析的乐谱，parseFailed 恒为 false
        val info = ImportedScoreInfo.from(
            score(notes = melody(listOf(60))),
            "a.xml",
            "MusicXML"
        )
        assertFalse("factory should never produce parseFailed=true", info.parseFailed)
    }

    // ══════════════════════════════════════════════════════════════════
    //  难度一致性（工厂结果 == DifficultyEstimator 直接结果）
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `difficulty score matches DifficultyEstimator directly`() {
        val notes = melody(listOf(60, 62, 64, 65, 67, 69, 71, 72), intervalMs = 350)
        val s = score(notes = notes, tempo = 140)
        val info = ImportedScoreInfo.from(s, "scale.xml", "MusicXML")
        val direct = DifficultyEstimator.estimate(s)

        assertEquals(direct.totalScore, info.difficultyScore)
        assertEquals(direct.level, info.difficultyLevel)
    }

    @Test
    fun `difficulty level is consistent with score`() {
        // level 应该由 fromScore(score) 映射得到，与难度分一致
        val notes = melody(listOf(60, 62, 64, 65))
        val info = ImportedScoreInfo.from(score(notes = notes), "easy.xml", "MusicXML")
        assertEquals(
            DifficultyLevel.fromScore(info.difficultyScore),
            info.difficultyLevel
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  标题回退
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `blank title falls back to fileName without extension`() {
        val info = ImportedScoreInfo.from(
            score(notes = melody(listOf(60)), title = ""),
            "无名乐谱.xml",
            "MusicXML"
        )
        assertEquals("无名乐谱", info.title)
    }

    @Test
    fun `blank title falls back for midi file`() {
        val info = ImportedScoreInfo.from(
            score(notes = melody(listOf(60)), title = "   "),
            "track.mid",
            "MIDI"
        )
        // "   ".isBlank() == true → 回退
        assertEquals("track", info.title)
    }

    @Test
    fun `non-blank title is preserved even if fileName differs`() {
        val info = ImportedScoreInfo.from(
            score(notes = melody(listOf(60)), title = "Für Elise"),
            "file_001.xml",
            "MusicXML"
        )
        assertEquals("Für Elise", info.title)
    }

    // ══════════════════════════════════════════════════════════════════
    //  边界情况
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `empty score does not crash and yields beginner`() {
        val s = score(notes = emptyList())
        val info = ImportedScoreInfo.from(s, "empty.xml", "MusicXML")
        assertEquals(0, info.noteCount)
        assertEquals(0, info.difficultyScore)
        assertEquals(DifficultyLevel.BEGINNER, info.difficultyLevel)
    }

    @Test
    fun `single note does not crash`() {
        val info = ImportedScoreInfo.from(
            score(notes = melody(listOf(60))),
            "one.xml",
            "MusicXML"
        )
        assertEquals(1, info.noteCount)
        // 单音符不应崩溃，难度合理（入门级）
        assertTrue("single note score=${info.difficultyScore}", info.difficultyScore in 0..100)
    }

    @Test
    fun `difficulty score is always within valid range`() {
        val cases = listOf(
            melody(listOf(60)),
            melody((60..84).toList(), intervalMs = 100),
            listOf(ScoreNote(60, "c", 0, 500, staff = Staff.TREBLE),
                   ScoreNote(48, "c", 0, 500, staff = Staff.BASS)) // 和弦
        )
        for (notes in cases) {
            val info = ImportedScoreInfo.from(score(notes = notes), "x.xml", "MusicXML")
            assertTrue(
                "score ${info.difficultyScore} out of range for $notes",
                info.difficultyScore in 0..100
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  相对难度（导入乐谱典型场景）
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `complex polyphonic score is harder than simple melody`() {
        // 简单儿歌（导入 MusicXML 典型场景）
        val easyInfo = ImportedScoreInfo.from(
            score(
                notes = melody(listOf(60, 60, 67, 67, 69, 69, 67), intervalMs = 400),
                tempo = 120
            ),
            "twinkle.xml",
            "MusicXML"
        )

        // 复杂多声部练习曲（导入 MIDI 典型场景：和弦 + 快速 + 大跨度）
        val hardNotes = mutableListOf<ScoreNote>()
        for (i in 0 until 24) {
            val t = i * 90L
            // 三音和弦，跨高低音谱表
            listOf(48 + (i * 3) % 36, 52 + (i * 3) % 36, 55 + (i * 3) % 36).forEach { midi ->
                hardNotes.add(
                    ScoreNote(
                        midiNumber = midi, noteName = "n",
                        startTime = t, duration = 80,
                        staff = if (i % 2 == 0) Staff.TREBLE else Staff.BASS
                    )
                )
            }
        }
        val hardInfo = ImportedScoreInfo.from(
            score(notes = hardNotes, tempo = 180),
            "etude.mid",
            "MIDI"
        )

        assertTrue(
            "hard (${hardInfo.difficultyScore}) should be >> easy (${easyInfo.difficultyScore})",
            hardInfo.difficultyScore > easyInfo.difficultyScore + 20
        )
    }

    @Test
    fun `two-staff score contributes hands independence factor`() {
        // 双谱表（高音+低音）应触发双手独立因子，使难度高于同等单谱表
        val twoStaffNotes = listOf(
            ScoreNote(60, "c", 0, 500, staff = Staff.TREBLE),
            ScoreNote(64, "e", 500, 500, staff = Staff.TREBLE),
            ScoreNote(48, "c", 0, 500, staff = Staff.BASS),
            ScoreNote(52, "e", 500, 500, staff = Staff.BASS)
        )
        val oneStaffNotes = listOf(
            ScoreNote(60, "c", 0, 500, staff = Staff.TREBLE),
            ScoreNote(64, "e", 500, 500, staff = Staff.TREBLE)
        )
        val twoStaffInfo = ImportedScoreInfo.from(
            score(notes = twoStaffNotes), "piano.xml", "MusicXML"
        )
        val oneStaffInfo = ImportedScoreInfo.from(
            score(notes = oneStaffNotes), "melody.xml", "MusicXML"
        )
        // 双手独立使总分 >= 单手（因子权重虽小但非负）
        assertTrue(
            "two-staff (${twoStaffInfo.difficultyScore}) >= one-staff (${oneStaffInfo.difficultyScore})",
            twoStaffInfo.difficultyScore >= oneStaffInfo.difficultyScore
        )
    }

    @Test
    fun `simple children song is beginner or easy`() {
        val info = ImportedScoreInfo.from(
            score(
                notes = melody(listOf(60, 60, 67, 67, 69, 69, 67), intervalMs = 400),
                tempo = 120
            ),
            "twinkle.xml",
            "MusicXML"
        )
        assertTrue(
            "simple song level ${info.difficultyLevel} should be BEGINNER or EASY",
            info.difficultyLevel == DifficultyLevel.BEGINNER ||
                info.difficultyLevel == DifficultyLevel.EASY
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  确定性
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `factory is deterministic for same input`() {
        val s = score(notes = melody(listOf(60, 62, 64, 65, 67), intervalMs = 350), tempo = 130)
        val i1 = ImportedScoreInfo.from(s, "a.xml", "MusicXML")
        val i2 = ImportedScoreInfo.from(s, "a.xml", "MusicXML")
        assertEquals(i1.difficultyScore, i2.difficultyScore)
        assertEquals(i1.difficultyLevel, i2.difficultyLevel)
    }
}
