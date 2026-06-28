package com.pianocompanion.analytics

import com.pianocompanion.data.model.ErrorPosition
import com.pianocompanion.data.model.MatchStatus
import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoteMasteryAnalyzer] 纯 JVM 单元测试 —— 覆盖音级错误分布、黑/白键归一化、
 * 音域分布、具体音符排行、音高混淆、摘要生成与各类边界情况。
 */
class NoteMasteryAnalyzerTest {

    // ════════════════════════════════════════════════════════════════
    //  测试数据构造辅助
    // ════════════════════════════════════════════════════════════════

    private var timeSeed = 1_000L
    private fun nextTime(): Long = timeSeed++

    /**
     * 构建一个错误位置。
     *
     * @param type 错误类型（默认 WRONG_PITCH）
     * @param expected 期望音符名（默认 "C4"）
     * @param detected 实际弹奏音符名（默认 "D4"）
     */
    private fun err(
        type: MatchStatus = MatchStatus.WRONG_PITCH,
        expected: String = "C4",
        detected: String = "D4"
    ) = ErrorPosition(
        measureIndex = 0,
        expectedNote = expected,
        detectedNote = detected,
        errorType = type,
        timestamp = nextTime()
    )

    /**
     * 构建一个会话，包含给定的错误列表。
     */
    private fun session(
        errors: List<ErrorPosition>,
        startTime: Long = nextTime()
    ): SessionRecord = SessionRecord(
        id = startTime,
        scoreTitle = "测试乐谱",
        startTime = startTime,
        durationMs = 60_000L,
        totalNotes = 100,
        correctNotes = 100 - errors.size,
        wrongNotes = errors.size,
        missedNotes = 0,
        extraNotes = 0,
        accuracy = if (errors.isNotEmpty()) 0.9f else 1.0f,
        errorPositions = errors
    )

    // ════════════════════════════════════════════════════════════════
    //  边界情况
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `空会话列表返回空报告`() {
        val report = NoteMasteryAnalyzer.analyze(emptyList())
        assertFalse(report.hasData)
        assertEquals(0, report.totalSessions)
        assertEquals(0, report.totalAnalyzedErrors)
        assertEquals(0, report.totalRawErrors)
        assertTrue(report.summary.contains("暂无练习数据"))
        assertTrue(report.pitchClassStats.isEmpty())
        assertTrue(report.weakestNotes.isEmpty())
        assertTrue(report.topConfusions.isEmpty())
    }

    @Test
    fun `有会话但无任何错误返回鼓励信息`() {
        val report = NoteMasteryAnalyzer.analyze(listOf(session(emptyList())))
        assertFalse(report.hasData)
        assertEquals(1, report.totalSessions)
        assertEquals(0, report.totalAnalyzedErrors)
        assertEquals(0, report.totalRawErrors)
        assertTrue(report.summary.contains("表现优异"))
    }

    @Test
    fun `错误全是占位符无法解析时报告跳过分析`() {
        // "—" 是 ScoreFollower 在期望音为 null 时使用的占位符
        val s = session(listOf(
            err(expected = "—", detected = "—"),
            err(expected = "(未弹)", detected = "(未弹)")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertFalse(report.hasData)
        assertEquals(2, report.totalRawErrors)
        assertEquals(0, report.totalAnalyzedErrors)
        assertTrue(report.summary.contains("无法解析具体音高"))
    }

    // ════════════════════════════════════════════════════════════════
    //  音级错误分布
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `单个错误正确映射到音级`() {
        // C4 = MIDI 60, pitch class 0
        val report = NoteMasteryAnalyzer.analyze(listOf(
            session(listOf(err(expected = "C4", detected = "D4")))
        ))
        assertTrue(report.hasData)
        assertEquals(1, report.totalAnalyzedErrors)
        // 12 个音级都有
        assertEquals(12, report.pitchClassStats.size)
        // 最薄弱音级是 C
        val weakest = report.weakestPitchClass
        assertNotNull(weakest)
        assertEquals(0, weakest!!.pitchClass)
        assertEquals("C", weakest.name)
        assertEquals(1, weakest.errorCount)
    }

    @Test
    fun `升号音级正确标记为 accidental`() {
        // F#4 = MIDI 66, pitch class 6 → 黑键
        val report = NoteMasteryAnalyzer.analyze(listOf(
            session(listOf(err(expected = "F#4", detected = "G4")))
        ))
        val weakest = report.weakestPitchClass!!
        assertEquals("F#", weakest.name)
        assertEquals(6, weakest.pitchClass)
        assertTrue(weakest.isAccidental)
    }

    @Test
    fun `多个错误按错误数降序排列音级`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),   // C: 1
            err(expected = "E4", detected = "F4"),   // E: 1
            err(expected = "G4", detected = "A4"),   // G: 1
            err(expected = "C5", detected = "D5")    // C (另一个八度): +1 → C 总计 2
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(4, report.totalAnalyzedErrors)
        // C 错误最多（2 次），排第一
        assertEquals("C", report.pitchClassStats[0].name)
        assertEquals(2, report.pitchClassStats[0].errorCount)
        // 第二名的错误数 ≤ 第一名
        assertTrue(report.pitchClassStats[1].errorCount <= report.pitchClassStats[0].errorCount)
    }

    @Test
    fun `errorRate 正确计算占比`() {
        // 4 个错误，C 占 2 个 → 50%
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "C5", detected = "D5"),
            err(expected = "E4", detected = "F4"),
            err(expected = "G4", detected = "A4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        val cStat = report.pitchClassStats.first { it.pitchClass == 0 }
        assertEquals(0.5f, cStat.errorRate, 0.001f)
    }

    @Test
    fun `dominantErrorType 返回最常见的错误类型`() {
        val s = session(listOf(
            err(type = MatchStatus.WRONG_PITCH, expected = "C4", detected = "D4"),
            err(type = MatchStatus.WRONG_PITCH, expected = "C5", detected = "E5"),
            err(type = MatchStatus.MISSING_NOTE, expected = "C4", detected = "—")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        val cStat = report.pitchClassStats.first { it.pitchClass == 0 }
        assertEquals(MatchStatus.WRONG_PITCH, cStat.dominantErrorType)
        assertEquals(2, cStat.errorTypeCounts[MatchStatus.WRONG_PITCH])
        assertEquals(1, cStat.errorTypeCounts[MatchStatus.MISSING_NOTE])
    }

    @Test
    fun `errorTypeCounts 包含所有该音级的错误类型`() {
        val s = session(listOf(
            err(type = MatchStatus.WRONG_PITCH, expected = "A4", detected = "B4"),
            err(type = MatchStatus.RHYTHM_ERROR, expected = "A4", detected = "A4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        val aStat = report.pitchClassStats.first { it.pitchClass == 9 } // A
        assertEquals(1, aStat.errorTypeCounts[MatchStatus.WRONG_PITCH])
        assertEquals(1, aStat.errorTypeCounts[MatchStatus.RHYTHM_ERROR])
    }

    // ════════════════════════════════════════════════════════════════
    //  分析对象选择（expectedNote vs detectedNote）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `EXTRA_NOTE 使用 detectedNote 作为分析对象`() {
        // 多弹了一个 D4，应分析 D（不是 expectedNote "—"）
        val s = session(listOf(
            err(type = MatchStatus.EXTRA_NOTE, expected = "—", detected = "D4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.hasData)
        assertEquals(1, report.totalAnalyzedErrors)
        // 最薄弱是 D
        assertEquals("D", report.weakestPitchClass!!.name)
    }

    @Test
    fun `MISSING_NOTE 使用 expectedNote 作为分析对象`() {
        val s = session(listOf(
            err(type = MatchStatus.MISSING_NOTE, expected = "E4", detected = "(未弹)")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals("E", report.weakestPitchClass!!.name)
    }

    @Test
    fun `RHYTHM_ERROR 使用 expectedNote 作为分析对象`() {
        val s = session(listOf(
            err(type = MatchStatus.RHYTHM_ERROR, expected = "F4", detected = "F4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals("F", report.weakestPitchClass!!.name)
    }

    // ════════════════════════════════════════════════════════════════
    //  黑键 vs 白键
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `黑白键统计正确分类`() {
        // 白键: C(0), D(2), E(4), F(5), G(7), A(9), B(11)
        // 黑键: C#(1), D#(3), F#(6), G#(8), A#(10)
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),   // 白
            err(expected = "C#4", detected = "D4"),  // 黑
            err(expected = "E4", detected = "F4"),   // 白
            err(expected = "F#4", detected = "G4"),  // 黑
            err(expected = "G#4", detected = "A4")   // 黑
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(2, report.keyTypeStats.whiteKeyCount)
        assertEquals(3, report.keyTypeStats.blackKeyCount)
        assertEquals(5, report.keyTypeStats.totalAnalyzedErrors)
    }

    @Test
    fun `whiteKeyRate 和 blackKeyRate 正确计算`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),   // 白
            err(expected = "D4", detected = "E4"),   // 白
            err(expected = "F#4", detected = "G4"),  // 黑
            err(expected = "G#4", detected = "A4")   // 黑
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(0.5f, report.keyTypeStats.whiteKeyRate, 0.001f)
        assertEquals(0.5f, report.keyTypeStats.blackKeyRate, 0.001f)
    }

    @Test
    fun `blackToWhiteRatio 按音级数量归一化`() {
        // 7 个白键错误（全部集中在 C 音级）+ 5 个黑键错误（全部集中在 F# 音级）
        // 7/7白键音级 = 1.0 平均/白键音级
        // 5/5黑键音级 = 1.0 平均/黑键音级
        // 比率 = 1.0
        val whiteErrors = (1..7).map { err(expected = "C4", detected = "D4") }
        val blackErrors = (1..5).map { err(expected = "F#4", detected = "G4") }
        val s = session(whiteErrors + blackErrors)
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(1.0f, report.keyTypeStats.blackToWhiteRatio, 0.01f)
    }

    @Test
    fun `blackToWhiteRatio 黑键错误率更高时比值大于1`() {
        // 假设白键 7 个错误（C 音级），黑键 5 个错误（C# 音级）
        // 平均/白键音级 = 7/7 = 1.0
        // 平均/黑键音级 = 5/5 = 1.0
        // 但如果黑键有 10 个错误（全在 C#），白键有 7 个（全在 C）:
        // 平均/黑键 = 10/5 = 2.0; 平均/白键 = 7/7 = 1.0; 比率 = 2.0
        val whiteErrors = (1..7).map { err(expected = "C4", detected = "D4") }
        val blackErrors = (1..10).map { err(expected = "C#4", detected = "D4") }
        val s = session(whiteErrors + blackErrors)
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(2.0f, report.keyTypeStats.blackToWhiteRatio, 0.01f)
    }

    @Test
    fun `blackToWhiteRatio 白键零错误黑键有错时返回无穷`() {
        val s = session(listOf(
            err(expected = "C#4", detected = "D4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(Float.POSITIVE_INFINITY, report.keyTypeStats.blackToWhiteRatio, 0.0f)
    }

    @Test
    fun `blackToWhiteRatio 黑白都为零时返回零`() {
        // 无法构造——有分析错误时必有白或黑。用空错误测试间接路径
        val report = NoteMasteryAnalyzer.analyze(emptyList())
        assertEquals(0f, report.keyTypeStats.blackToWhiteRatio, 0.0f)
    }

    @Test
    fun `isBlackKeyPitchClass 正确判定所有12个音级`() {
        // 白键 pitch class
        assertFalse(isBlackKeyPitchClass(0))  // C
        assertFalse(isBlackKeyPitchClass(2))  // D
        assertFalse(isBlackKeyPitchClass(4))  // E
        assertFalse(isBlackKeyPitchClass(5))  // F
        assertFalse(isBlackKeyPitchClass(7))  // G
        assertFalse(isBlackKeyPitchClass(9))  // A
        assertFalse(isBlackKeyPitchClass(11)) // B
        // 黑键 pitch class
        assertTrue(isBlackKeyPitchClass(1))   // C#
        assertTrue(isBlackKeyPitchClass(3))   // D#
        assertTrue(isBlackKeyPitchClass(6))   // F#
        assertTrue(isBlackKeyPitchClass(8))   // G#
        assertTrue(isBlackKeyPitchClass(10))  // A#
    }

    // ════════════════════════════════════════════════════════════════
    //  音域分布
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `NoteRegister_forMidi 正确划分音区`() {
        assertEquals(NoteRegister.LOW, NoteRegister.forMidi(59))   // B3
        assertEquals(NoteRegister.MID, NoteRegister.forMidi(60))   // C4
        assertEquals(NoteRegister.MID, NoteRegister.forMidi(72))   // C5
        assertEquals(NoteRegister.HIGH, NoteRegister.forMidi(73))  // C#5
    }

    @Test
    fun `音域错误分布正确分类`() {
        val s = session(listOf(
            err(expected = "A3", detected = "B3"),   // A3=MIDI57, 低音区
            err(expected = "C4", detected = "D4"),   // C4=MIDI60, 中音区
            err(expected = "C5", detected = "D5"),   // C5=MIDI72, 中音区
            err(expected = "E5", detected = "F5"),   // E5=MIDI76, 高音区
            err(expected = "A5", detected = "B5")    // A5=MIDI81, 高音区
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(1, report.registerStats.lowCount)
        assertEquals(2, report.registerStats.midCount)
        assertEquals(2, report.registerStats.highCount)
        assertEquals(5, report.registerStats.totalAnalyzedErrors)
    }

    @Test
    fun `dominantRegister 返回错误最多的音区`() {
        val s = session(listOf(
            err(expected = "A3", detected = "B3"),   // 低
            err(expected = "G3", detected = "A3"),   // 低
            err(expected = "F3", detected = "G3"),   // 低
            err(expected = "C4", detected = "D4")    // 中
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(NoteRegister.LOW, report.registerStats.dominantRegister)
    }

    @Test
    fun `dominantRegister 高音区最多时返回 HIGH`() {
        val s = session(listOf(
            err(expected = "E5", detected = "F5"),   // 高
            err(expected = "A5", detected = "B5"),   // 高
            err(expected = "C4", detected = "D4")    // 中
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(NoteRegister.HIGH, report.registerStats.dominantRegister)
    }

    @Test
    fun `dominantRegister 无错误时返回 MID`() {
        val report = NoteMasteryAnalyzer.analyze(emptyList())
        assertEquals(NoteRegister.MID, report.registerStats.dominantRegister)
    }

    @Test
    fun `rateFor 返回指定音区的错误占比`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),   // 中
            err(expected = "D4", detected = "E4"),   // 中
            err(expected = "E5", detected = "F5"),   // 高
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(0.0f, report.registerStats.rateFor(NoteRegister.LOW), 0.001f)
        assertEquals(2f / 3f, report.registerStats.rateFor(NoteRegister.MID), 0.001f)
        assertEquals(1f / 3f, report.registerStats.rateFor(NoteRegister.HIGH), 0.001f)
    }

    // ════════════════════════════════════════════════════════════════
    //  具体音符排行
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `weakestNotes 按错误数降序排列`() {
        val s = session(listOf(
            err(expected = "F#4", detected = "G4"),  // F#4: 1
            err(expected = "F#4", detected = "A4"),  // F#4: 2
            err(expected = "F#4", detected = "B4"),  // F#4: 3
            err(expected = "C4", detected = "D4"),   // C4: 1
            err(expected = "C4", detected = "E4")    // C4: 2
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertFalse(report.weakestNotes.isEmpty())
        // F#4 最频繁
        assertEquals("F#4", report.weakestNotes[0].noteName)
        assertEquals(3, report.weakestNotes[0].errorCount)
        // C4 第二
        assertEquals("C4", report.weakestNotes[1].noteName)
        assertEquals(2, report.weakestNotes[1].errorCount)
    }

    @Test
    fun `weakestNotes 同错误数按 MIDI 升序排列`() {
        val s = session(listOf(
            err(expected = "A4", detected = "B4"),  // A4: 1
            err(expected = "C4", detected = "D4"),  // C4: 1
            err(expected = "E4", detected = "F4")   // E4: 1
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        // 同为 1 次，按 MIDI 升序：C4(60) < E4(64) < A4(69)
        assertEquals("C4", report.weakestNotes[0].noteName)
        assertEquals("E4", report.weakestNotes[1].noteName)
        assertEquals("A4", report.weakestNotes[2].noteName)
    }

    @Test
    fun `weakestNotes 遵守 maxWeakestNotes 限制`() {
        val errors = (1..10).map {
            err(expected = "C$it", detected = "D$it")
        }
        val s = session(errors)
        val report = NoteMasteryAnalyzer.analyze(
            listOf(s),
            NoteMasteryOptions(maxWeakestNotes = 3)
        )
        assertEquals(3, report.weakestNotes.size)
    }

    @Test
    fun `weakestNotes midi 字段正确`() {
        val s = session(listOf(err(expected = "A4", detected = "B4")))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(69, report.weakestNotes[0].midi) // A4 = MIDI 69
    }

    // ════════════════════════════════════════════════════════════════
    //  音高混淆
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `topConfusions 正确提取 expected-detected 配对`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),   // C4→D4: 2 半音
            err(expected = "C4", detected = "D4"),   // C4→D4: x2
            err(expected = "F#4", detected = "G4")   // F#4→G4: 1 半音
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(2, report.topConfusions.size)
        // C4→D4 出现 2 次，排第一
        val top = report.topConfusions[0]
        assertEquals("C4", top.expectedNote)
        assertEquals("D4", top.detectedNote)
        assertEquals(2, top.count)
        assertEquals(2, top.semitoneDistance)
    }

    @Test
    fun `topConfusions 仅包含 WRONG_PITCH 错误`() {
        // RHYTHM_ERROR 虽然 detected==expected，但不应产生混淆
        val s = session(listOf(
            err(type = MatchStatus.RHYTHM_ERROR, expected = "C4", detected = "C4"),
            err(type = MatchStatus.WRONG_PITCH, expected = "C4", detected = "D4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        // 只有 1 个混淆（来自 WRONG_PITCH）
        assertEquals(1, report.topConfusions.size)
        assertEquals("C4", report.topConfusions[0].expectedNote)
    }

    @Test
    fun `topConfusions 排除 detected 等于 expected 的情况`() {
        // 即使是 WRONG_PITCH，如果 detected==expected 则不构成混淆
        val s = session(listOf(
            err(type = MatchStatus.WRONG_PITCH, expected = "C4", detected = "C4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.topConfusions.isEmpty())
    }

    @Test
    fun `topConfusions 排除无法解析的 detectedNote`() {
        val s = session(listOf(
            err(type = MatchStatus.WRONG_PITCH, expected = "C4", detected = "(未弹)")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.topConfusions.isEmpty())
    }

    @Test
    fun `topConfusions 遵守 maxConfusions 限制`() {
        val errors = (0..9).map { i ->
            err(expected = "C$i", detected = "D$i")
        }
        val s = session(errors)
        val report = NoteMasteryAnalyzer.analyze(
            listOf(s),
            NoteMasteryOptions(maxConfusions = 3)
        )
        assertEquals(3, report.topConfusions.size)
    }

    @Test
    fun `topConfusions 同次数按 semitoneDistance 升序排列`() {
        // 两个混淆各 1 次：C4→D4(2半音) vs C4→C#4(1半音)
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "C4", detected = "C#4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(2, report.topConfusions.size)
        // 1 半音的 C#4 排在前
        assertEquals(1, report.topConfusions[0].semitoneDistance)
        assertEquals(2, report.topConfusions[1].semitoneDistance)
    }

    // ════════════════════════════════════════════════════════════════
    //  多会话聚合
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `多会话错误正确累加`() {
        val s1 = session(listOf(err(expected = "C4", detected = "D4")))
        val s2 = session(listOf(
            err(expected = "C4", detected = "E4"),
            err(expected = "F#4", detected = "G4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s1, s2))
        assertEquals(2, report.totalSessions)
        assertEquals(3, report.totalAnalyzedErrors)
        // C 出错 2 次
        val cStat = report.pitchClassStats.first { it.pitchClass == 0 }
        assertEquals(2, cStat.errorCount)
    }

    @Test
    fun `跨会话相同音符的错误合并`() {
        val s1 = session(listOf(err(expected = "C4", detected = "D4")))
        val s2 = session(listOf(err(expected = "C4", detected = "E4")))
        val s3 = session(listOf(err(expected = "C4", detected = "F4")))
        val report = NoteMasteryAnalyzer.analyze(listOf(s1, s2, s3))
        // C4 出现 3 次
        val c4 = report.weakestNotes.first { it.noteName == "C4" }
        assertEquals(3, c4.errorCount)
    }

    // ════════════════════════════════════════════════════════════════
    //  摘要生成
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `摘要包含练习次数和错误总数`() {
        val s = session(listOf(err(expected = "C4", detected = "D4")))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.summary.contains("1 次"))
        assertTrue(report.summary.contains("1 条"))
    }

    @Test
    fun `摘要黑键显著高出时提及黑键建议`() {
        // 黑键错误远高于白键
        val blackErrors = (1..20).map { err(expected = "C#4", detected = "D4") }
        val whiteErrors = (1..7).map { err(expected = "C4", detected = "D4") }
        val s = session(blackErrors + whiteErrors)
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        // 比率 = (20/5) / (7/7) = 4.0 / 1.0 = 4.0 > 1.5
        assertTrue(report.summary.contains("黑键"))
    }

    @Test
    fun `摘要非黑键显著时提及最薄弱音级`() {
        // 全是白键错误
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "C4", detected = "E4"),
            err(expected = "D4", detected = "E4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.summary.contains("C"))
    }

    @Test
    fun `摘要包含最频繁出错的单音`() {
        val s = session(listOf(
            err(expected = "F#4", detected = "G4"),
            err(expected = "F#4", detected = "A4"),
            err(expected = "F#4", detected = "B4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.summary.contains("F#4"))
    }

    @Test
    fun `摘要包含最易混淆的音对`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "C4", detected = "D4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertTrue(report.summary.contains("C4") && report.summary.contains("D4"))
    }

    // ════════════════════════════════════════════════════════════════
    //  确定性
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `相同输入产生相同结果`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "F#4", detected = "G4")
        ))
        val r1 = NoteMasteryAnalyzer.analyze(listOf(s))
        val r2 = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(r1.totalAnalyzedErrors, r2.totalAnalyzedErrors)
        assertEquals(r1.keyTypeStats.blackKeyCount, r2.keyTypeStats.blackKeyCount)
        assertEquals(r1.pitchClassStats.map { it.errorCount }, r2.pitchClassStats.map { it.errorCount })
        assertEquals(r1.summary, r2.summary)
    }

    @Test
    fun `会话顺序不影响聚合结果`() {
        val s1 = session(listOf(err(expected = "C4", detected = "D4")))
        val s2 = session(listOf(err(expected = "F#4", detected = "G4")))
        val r1 = NoteMasteryAnalyzer.analyze(listOf(s1, s2))
        val r2 = NoteMasteryAnalyzer.analyze(listOf(s2, s1))
        assertEquals(r1.totalAnalyzedErrors, r2.totalAnalyzedErrors)
        assertEquals(r1.keyTypeStats, r2.keyTypeStats)
        assertEquals(r1.registerStats, r2.registerStats)
    }

    // ════════════════════════════════════════════════════════════════
    //  真实场景模拟
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `真实场景模拟 - 黑键是主要弱点`() {
        // 模拟钢琴初学者：黑键（升降号）错误率远高于白键
        val blackKeyErrors = listOf(
            err(expected = "C#4", detected = "D4"),
            err(expected = "C#4", detected = "C4"),
            err(expected = "D#4", detected = "E4"),
            err(expected = "F#4", detected = "G4"),
            err(expected = "G#4", detected = "A4"),
            err(expected = "A#4", detected = "B4")
        )
        val whiteKeyErrors = listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "E4", detected = "F4")
        )
        val s = session(blackKeyErrors + whiteKeyErrors)
        val report = NoteMasteryAnalyzer.analyze(listOf(s))

        // 黑键 6 个错误（分布在 5 个黑键音级），白键 2 个（分布在 2 个白键音级）
        // 平均/黑键音级 = 6/5 = 1.2; 平均/白键音级 = 2/7 ≈ 0.286
        // 比率 ≈ 4.2 >> 1.5
        assertTrue(report.keyTypeStats.blackToWhiteRatio > 1.5f)
        // 最薄弱音级应该是黑键之一（C#=2次最多）
        assertEquals("C#", report.weakestPitchClass!!.name)
        // 摘要应提到黑键
        assertTrue(report.summary.contains("黑键"))
    }

    @Test
    fun `真实场景模拟 - 高音区是主要弱点`() {
        // 模拟换把位困难：高音区错误占多数
        val highErrors = listOf(
            err(expected = "E5", detected = "F5"),
            err(expected = "A5", detected = "B5"),
            err(expected = "C6", detected = "D6"),
            err(expected = "G5", detected = "A5")
        )
        val midErrors = listOf(err(expected = "C4", detected = "D4"))
        val s = session(highErrors + midErrors)
        val report = NoteMasteryAnalyzer.analyze(listOf(s))

        assertEquals(NoteRegister.HIGH, report.registerStats.dominantRegister)
        assertEquals(4, report.registerStats.highCount)
        assertEquals(1, report.registerStats.midCount)
        assertEquals(0, report.registerStats.lowCount)
        assertTrue(report.registerStats.rateFor(NoteRegister.HIGH) > 0.7f)
    }

    @Test
    fun `真实场景模拟 - C4 D4 混淆是最常见问题`() {
        val s = session(listOf(
            err(expected = "C4", detected = "D4"),
            err(expected = "C4", detected = "D4"),
            err(expected = "C4", detected = "D4"),
            err(expected = "D4", detected = "C4"),
            err(expected = "E4", detected = "F4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        val top = report.topConfusions[0]
        assertEquals("C4", top.expectedNote)
        assertEquals("D4", top.detectedNote)
        assertEquals(3, top.count)
    }

    // ════════════════════════════════════════════════════════════════
    //  混合错误类型
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `混合错误类型全部正确分析`() {
        val s = session(listOf(
            err(type = MatchStatus.WRONG_PITCH, expected = "C4", detected = "D4"),
            err(type = MatchStatus.MISSING_NOTE, expected = "E4", detected = "(未弹)"),
            err(type = MatchStatus.EXTRA_NOTE, expected = "—", detected = "G4"),
            err(type = MatchStatus.RHYTHM_ERROR, expected = "A4", detected = "A4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(4, report.totalAnalyzedErrors)
        // 4 个不同音级各 1 次
        assertEquals(4, report.weakestNotes.size)
    }

    @Test
    fun `EXTRA_NOTE 和其他类型的分析对象不同`() {
        // WRONG_PITCH: 期望 C4 → 分析 C
        // EXTRA_NOTE: 多弹 D4 → 分析 D
        val s = session(listOf(
            err(type = MatchStatus.WRONG_PITCH, expected = "C4", detected = "D4"),
            err(type = MatchStatus.EXTRA_NOTE, expected = "—", detected = "D4")
        ))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        // C: 1次, D: 1次
        assertEquals(1, report.pitchClassStats.first { it.pitchClass == 0 }.errorCount) // C
        assertEquals(1, report.pitchClassStats.first { it.pitchClass == 2 }.errorCount) // D
    }

    // ════════════════════════════════════════════════════════════════
    //  PitchClassStat 结构完整性
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `所有12个音级都出现在统计中`() {
        val s = session(listOf(err(expected = "C4", detected = "D4")))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        assertEquals(12, report.pitchClassStats.size)
        // 0-11 全部覆盖
        val classes = report.pitchClassStats.map { it.pitchClass }.toSet()
        assertEquals((0..11).toSet(), classes)
    }

    @Test
    fun `每个音级的字段正确填充`() {
        val s = session(listOf(err(expected = "C4", detected = "D4")))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        for (stat in report.pitchClassStats) {
            assertTrue(stat.pitchClass in 0..11)
            assertTrue(stat.name.isNotEmpty())
            assertTrue(stat.errorCount >= 0)
            assertEquals(1, stat.totalAnalyzedErrors)
            assertTrue(stat.errorRate in 0f..1f)
        }
    }

    @Test
    fun `无错误的音级 errorCount 为零`() {
        val s = session(listOf(err(expected = "C4", detected = "D4")))
        val report = NoteMasteryAnalyzer.analyze(listOf(s))
        val dStat = report.pitchClassStats.first { it.pitchClass == 2 } // D
        assertEquals(0, dStat.errorCount)
    }

    @Test
    fun `weakestPitchClass 无数据时为 null`() {
        val report = NoteMasteryAnalyzer.analyze(emptyList())
        assertNull(report.weakestPitchClass)
    }
}
