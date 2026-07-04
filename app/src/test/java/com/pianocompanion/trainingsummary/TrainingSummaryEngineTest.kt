package com.pianocompanion.trainingsummary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TrainingSummaryEngine] 单元测试。
 *
 * 覆盖：空输入、聚合统计、技能等级评估、排名（活跃度/准确率/连击/薄弱项）、
 * 改进建议生成、百分比格式化、排序稳定性、样本量门槛等。
 */
class TrainingSummaryEngineTest {

    // =====================================================================
    // 辅助构造器
    // =====================================================================

    private fun summary(
        type: TrainerType,
        answered: Int = 0,
        correct: Int = 0,
        sessions: Int = 0,
        bestStreak: Int = 0,
        bestAcc: Double = 0.0
    ) = TrainerSummary(
        type = type,
        totalSessions = sessions,
        totalAnswered = answered,
        totalCorrect = correct,
        bestStreak = bestStreak,
        bestAccuracy = bestAcc
    )

    private fun allTypes() = TrainerType.values().toList()

    // =====================================================================
    // 空输入 / 零数据
    // =====================================================================

    @Test
    fun `empty trainer list produces zero report`() {
        val report = TrainingSummaryEngine.summarize(emptyList())
        assertTrue(report.isEmpty)
        assertEquals(0, report.totalSessions)
        assertEquals(0, report.totalAnswered)
        assertEquals(0, report.totalCorrect)
        assertEquals(0.0, report.overallAccuracy, 0.0001)
        assertEquals(0, report.activeTrainerCount)
        assertNull(report.mostPracticed)
        assertNull(report.accuracyLeader)
        assertNull(report.streakLeader)
        assertNull(report.weakestLink)
    }

    @Test
    fun `all-zero trainers produce empty report but non-empty trainer list`() {
        val trainers = allTypes().map { summary(it) }
        val report = TrainingSummaryEngine.summarize(trainers)
        assertTrue(report.isEmpty)
        assertEquals(0, report.totalAnswered)
        assertEquals(0, report.activeTrainerCount)
        assertEquals(7, report.trainers.size)
    }

    @Test
    fun `zero answered has zero accuracy`() {
        val s = summary(TrainerType.NOTE_READING, answered = 0, correct = 0)
        assertEquals(0.0, s.accuracy, 0.0001)
        assertEquals(false, s.hasActivity)
    }

    // =====================================================================
    // 聚合统计
    // =====================================================================

    @Test
    fun `total sessions are summed across trainers`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, sessions = 3, answered = 10, correct = 8),
            summary(TrainerType.INTERVAL, sessions = 5, answered = 20, correct = 15)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(8, report.totalSessions)
        assertEquals(30, report.totalAnswered)
        assertEquals(23, report.totalCorrect)
    }

    @Test
    fun `overall accuracy is correct weighted average`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 10, correct = 9),
            summary(TrainerType.INTERVAL, answered = 10, correct = 7)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        // (9 + 7) / 20 = 0.80
        assertEquals(0.80, report.overallAccuracy, 0.0001)
    }

    @Test
    fun `active trainer count excludes zero-activity trainers`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 10, correct = 5),
            summary(TrainerType.INTERVAL, answered = 0, correct = 0),
            summary(TrainerType.CHORD_READING, answered = 1, correct = 1)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(2, report.activeTrainerCount)
    }

    @Test
    fun `individual trainer accuracy property`() {
        val s = summary(TrainerType.INTERVAL, answered = 8, correct = 6)
        assertEquals(0.75, s.accuracy, 0.0001)
    }

    // =====================================================================
    // 技能等级评估
    // =====================================================================

    @Test
    fun `skill level beginner when no activity`() {
        assertEquals(SkillLevel.BEGINNER, SkillLevel.evaluate(0.0, 0))
        assertEquals(SkillLevel.BEGINNER, SkillLevel.evaluate(0.95, 0))
    }

    @Test
    fun `skill level novice at 50pct accuracy with 2 active`() {
        assertEquals(SkillLevel.NOVICE, SkillLevel.evaluate(0.50, 2))
        assertEquals(SkillLevel.NOVICE, SkillLevel.evaluate(0.55, 2))
    }

    @Test
    fun `skill level intermediate at 65pct with 3 active`() {
        assertEquals(SkillLevel.INTERMEDIATE, SkillLevel.evaluate(0.65, 3))
    }

    @Test
    fun `skill level proficient at 80pct with 4 active`() {
        assertEquals(SkillLevel.PROFICIENT, SkillLevel.evaluate(0.80, 4))
    }

    @Test
    fun `skill level master at 90pct with 5 active`() {
        assertEquals(SkillLevel.MASTER, SkillLevel.evaluate(0.90, 5))
        assertEquals(SkillLevel.MASTER, SkillLevel.evaluate(0.95, 7))
    }

    @Test
    fun `high accuracy but low active count caps skill level`() {
        // 95% accuracy but only 1 active trainer → capped below NOVICE (needs 2)
        assertEquals(SkillLevel.BEGINNER, SkillLevel.evaluate(0.95, 1))
        // 95% accuracy but 2 active → below INTERMEDIATE (needs 3)
        assertEquals(SkillLevel.NOVICE, SkillLevel.evaluate(0.95, 2))
    }

    @Test
    fun `skill level is monotonic in both dimensions`() {
        // Fix accuracy, increase active count → level should not decrease
        for (acc in listOf(0.5, 0.7, 0.85, 0.92)) {
            val levels = (1..7).map { SkillLevel.evaluate(acc, it) }
            for (i in 1 until levels.size) {
                assertTrue(
                    "Level should not decrease with more active trainers " +
                        "(acc=$acc): ${levels[i - 1]} vs ${levels[i]}",
                    levels[i].ordinal >= levels[i - 1].ordinal
                )
            }
        }
    }

    // =====================================================================
    // 排名：活跃度 / 准确率 / 连击 / 薄弱项
    // =====================================================================

    @Test
    fun `most practiced is the trainer with most answered`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 50, correct = 40),
            summary(TrainerType.INTERVAL, answered = 100, correct = 80),
            summary(TrainerType.CHORD_READING, answered = 30, correct = 25)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(TrainerType.INTERVAL, report.mostPracticed?.type)
    }

    @Test
    fun `accuracy leader requires minimum sample size`() {
        // Only 4 answered (< MIN_SAMPLE_FOR_ACCURACY=5), should be filtered out
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 4, correct = 4),
            summary(TrainerType.INTERVAL, answered = 10, correct = 5)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        // NOTE_READING has 100% but <5 samples, so accuracyLeader should be INTERVAL
        assertEquals(TrainerType.INTERVAL, report.accuracyLeader?.type)
    }

    @Test
    fun `accuracy leader is highest accuracy among qualified trainers`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 20, correct = 18),  // 90%
            summary(TrainerType.INTERVAL, answered = 20, correct = 16),      // 80%
            summary(TrainerType.CHORD_READING, answered = 10, correct = 9)  // 90%
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(TrainerType.NOTE_READING, report.accuracyLeader?.type)
    }

    @Test
    fun `streak leader has highest best streak`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 10, correct = 5, bestStreak = 8),
            summary(TrainerType.INTERVAL, answered = 10, correct = 5, bestStreak = 15),
            summary(TrainerType.CHORD_READING, answered = 10, correct = 5, bestStreak = 3)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(TrainerType.INTERVAL, report.streakLeader?.type)
    }

    @Test
    fun `weakest link is lowest accuracy among qualified trainers`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 20, correct = 18),  // 90%
            summary(TrainerType.INTERVAL, answered = 20, correct = 8),       // 40%
            summary(TrainerType.CHORD_READING, answered = 20, correct = 12) // 60%
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(TrainerType.INTERVAL, report.weakestLink?.type)
    }

    @Test
    fun `weakest link is null when no qualified trainers`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 3, correct = 1)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertNull(report.weakestLink)
        assertNull(report.accuracyLeader)
    }

    // =====================================================================
    // rankByActivity
    // =====================================================================

    @Test
    fun `rankByActivity sorts by answered descending`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 10),
            summary(TrainerType.INTERVAL, answered = 50),
            summary(TrainerType.CHORD_READING, answered = 0),
            summary(TrainerType.KEY_SIGNATURE, answered = 30)
        )
        val ranked = TrainingSummaryEngine.rankByActivity(trainers)
        assertEquals(3, ranked.size) // 0-activity excluded
        assertEquals(TrainerType.INTERVAL, ranked[0].type)
        assertEquals(TrainerType.KEY_SIGNATURE, ranked[1].type)
        assertEquals(TrainerType.NOTE_READING, ranked[2].type)
    }

    @Test
    fun `rankByActivity respects limit`() {
        val trainers = allTypes().map { summary(it, answered = it.ordinal * 10 + 5) }
        val ranked = TrainingSummaryEngine.rankByActivity(trainers, limit = 3)
        assertEquals(3, ranked.size)
    }

    // =====================================================================
    // 排序：summarize 输出的 trainers 列表
    // =====================================================================

    @Test
    fun `summarize sorts trainers by answered desc then accuracy desc`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 20, correct = 10),  // 50%
            summary(TrainerType.INTERVAL, answered = 20, correct = 16),      // 80%
            summary(TrainerType.CHORD_READING, answered = 50, correct = 40) // 80%, most answered
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        assertEquals(TrainerType.CHORD_READING, report.trainers[0].type)  // most answered
        assertEquals(TrainerType.INTERVAL, report.trainers[1].type)       // tied answered, higher acc
        assertEquals(TrainerType.NOTE_READING, report.trainers[2].type)   // tied answered, lower acc
    }

    // =====================================================================
    // 改进建议生成
    // =====================================================================

    @Test
    fun `empty report has at least one suggestion`() {
        val report = TrainingSummaryEngine.summarize(emptyList())
        assertTrue(report.suggestions.isNotEmpty())
    }

    @Test
    fun `weak module below 70pct generates suggestion mentioning it`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 20, correct = 18),
            summary(TrainerType.INTERVAL, answered = 20, correct = 10)  // 50%
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        val weakSuggestion = report.suggestions.find { it.contains("音程识别") }
        assertNotNull("Should mention the weak module by name", weakSuggestion)
    }

    @Test
    fun `few active trainers generates breadth suggestion`() {
        // 7 total types but only 1 active
        val trainers = allTypes().mapIndexed { i, t ->
            if (i == 0) summary(t, answered = 10, correct = 8)
            else summary(t)
        }
        val report = TrainingSummaryEngine.summarize(trainers)
        val breadthSuggestion = report.suggestions.find { it.contains("未使用") }
        assertNotNull("Should suggest trying more modules", breadthSuggestion)
    }

    @Test
    fun `high overall accuracy generates encouragement`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 50, correct = 45),
            summary(TrainerType.INTERVAL, answered = 50, correct = 44),
            summary(TrainerType.CHORD_READING, answered = 50, correct = 43),
            summary(TrainerType.KEY_SIGNATURE, answered = 50, correct = 42)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        // overall = 174/200 = 87%, 4 active → >= 0.85
        val encouragement = report.suggestions.find { it.contains("太棒了") }
        assertNotNull("Should encourage high performance", encouragement)
    }

    @Test
    fun `strong module above 90pct is highlighted`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 50, correct = 48), // 96%
            summary(TrainerType.INTERVAL, answered = 50, correct = 30)      // 60%
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        val strong = report.suggestions.find { it.contains("强项") }
        assertNotNull("Should highlight the strong module", strong)
    }

    @Test
    fun `low overall accuracy with 3+ active generates pace suggestion`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 20, correct = 8),
            summary(TrainerType.INTERVAL, answered = 20, correct = 7),
            summary(TrainerType.CHORD_READING, answered = 20, correct = 6)
        )
        val report = TrainingSummaryEngine.summarize(trainers)
        // overall = 21/60 = 35%
        val pace = report.suggestions.find { it.contains("放慢") }
        assertNotNull("Should suggest slowing down", pace)
    }

    @Test
    fun `suggestions never empty`() {
        // Various scenarios
        val scenarios = listOf(
            emptyList(),
            listOf(summary(TrainerType.NOTE_READING, answered = 5, correct = 5)),
            listOf(summary(TrainerType.NOTE_READING, answered = 100, correct = 95)),
            allTypes().map { summary(it, answered = 100, correct = 95) }
        )
        for (trainers in scenarios) {
            val report = TrainingSummaryEngine.summarize(trainers)
            assertTrue(
                "Suggestions should never be empty for scenario: $trainers",
                report.suggestions.isNotEmpty()
            )
        }
    }

    // =====================================================================
    // 百分比格式化
    // =====================================================================

    @Test
    fun `pct formats whole numbers without decimal`() {
        assertEquals("0%", TrainingSummaryEngine.pct(0.0))
        assertEquals("50%", TrainingSummaryEngine.pct(0.5))
        assertEquals("100%", TrainingSummaryEngine.pct(1.0))
        assertEquals("80%", TrainingSummaryEngine.pct(0.8))
    }

    @Test
    fun `pct formats fractional with one decimal`() {
        assertEquals("87.5%", TrainingSummaryEngine.pct(0.875))
        assertEquals("33.3%", TrainingSummaryEngine.pct(1.0 / 3.0))
    }

    @Test
    fun `pct handles edge cases`() {
        assertEquals("0%", TrainingSummaryEngine.pct(0.0))
        assertEquals("100%", TrainingSummaryEngine.pct(1.0))
    }

    // =====================================================================
    // 数据模型完整性
    // =====================================================================

    @Test
    fun `TrainerType has 7 entries with display names`() {
        assertEquals(7, TrainerType.values().size)
        for (t in TrainerType.values()) {
            assertTrue("Display name should not be empty for $t", t.displayName.isNotEmpty())
            assertTrue("Emoji should not be empty for $t", t.emoji.isNotEmpty())
        }
    }

    @Test
    fun `5 visual trainers and 2 audio trainers`() {
        val visual = TrainerType.values().filter { it.isVisual }
        val audio = TrainerType.values().filter { !it.isVisual }
        assertEquals(5, visual.size)
        assertEquals(2, audio.size)
    }

    @Test
    fun `SkillLevel has 5 levels with increasing accuracy thresholds`() {
        val levels = listOf(
            SkillLevel.BEGINNER, SkillLevel.NOVICE, SkillLevel.INTERMEDIATE,
            SkillLevel.PROFICIENT, SkillLevel.MASTER
        )
        for (i in 1 until levels.size) {
            assertTrue(
                "Accuracy threshold should increase: ${levels[i - 1].minAccuracy} vs ${levels[i].minAccuracy}",
                levels[i].minAccuracy >= levels[i - 1].minAccuracy
            )
            assertTrue(
                "Active count threshold should increase: ${levels[i - 1].minActiveCount} vs ${levels[i].minActiveCount}",
                levels[i].minActiveCount >= levels[i - 1].minActiveCount
            )
        }
    }

    @Test
    fun `SkillLevel descriptions and emojis are non-empty`() {
        for (level in SkillLevel.values()) {
            assertTrue("Display name not empty for $level", level.displayName.isNotEmpty())
            assertTrue("Description not empty for $level", level.description.isNotEmpty())
            assertTrue("Emoji not empty for $level", level.emoji.isNotEmpty())
            assertTrue("Color hex not empty for $level", level.colorHex.isNotEmpty())
        }
    }

    @Test
    fun `report isEmpty is true only when no answers anywhere`() {
        val withAnswers = listOf(summary(TrainerType.NOTE_READING, answered = 1, correct = 1))
        val without = listOf(summary(TrainerType.NOTE_READING))
        assertTrue(TrainingSummaryEngine.summarize(without).isEmpty)
        assertTrue(!TrainingSummaryEngine.summarize(withAnswers).isEmpty)
    }

    // =====================================================================
    // 确定性 / 幂等性
    // =====================================================================

    @Test
    fun `summarize is deterministic for same input`() {
        val trainers = listOf(
            summary(TrainerType.NOTE_READING, answered = 20, correct = 15, bestStreak = 10),
            summary(TrainerType.INTERVAL, answered = 30, correct = 20, bestStreak = 8),
            summary(TrainerType.CHORD_READING, answered = 10, correct = 6, bestStreak = 12)
        )
        val r1 = TrainingSummaryEngine.summarize(trainers)
        val r2 = TrainingSummaryEngine.summarize(trainers)
        assertEquals(r1.totalAnswered, r2.totalAnswered)
        assertEquals(r1.overallAccuracy, r2.overallAccuracy, 0.0001)
        assertEquals(r1.skillLevel, r2.skillLevel)
        assertEquals(r1.suggestions, r2.suggestions)
        assertEquals(r1.trainers.map { it.type }, r2.trainers.map { it.type })
    }

    @Test
    fun `MIN_SAMPLE_FOR_ACCURACY is 5`() {
        assertEquals(5, TrainingSummaryEngine.MIN_SAMPLE_FOR_ACCURACY)
    }
}
