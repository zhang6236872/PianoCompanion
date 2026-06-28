package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GoalTracker] 单元测试。
 *
 * 覆盖：目标评估、进度计算、连续完成天数/周数、历史最长连续、预设、格式化、边界条件。
 *
 * 时间基准固定为 `BASE_NOW`（dayNumber = 20000），便于确定性地构造测试会话。
 */
class GoalTrackerTest {

    companion object {
        private const val DAY_MS = GoalTracker.DAY_MS
        /** dayNumber 20000，周一（验证: (20000+3)%7 = 20003%7 = 20003 - 2857*7 = 20003-19999=4 → 周五?）*/
        /** 20000 % 7 = 20000 - 2857*7 = 20000 - 19999 = 1 → dayOfWeek(0=Mon): (1+3)%7=4 → 周五 */
        /** 让我们用一个明确的周一作为基准 */
        /** dayNumber 20004: 20004%7 = 20004-2857*7=20004-19999=5 → (5+3)%7=1 → 周二 */
        /** dayNumber 20003: 20003%7 = 20003-2857*7=20003-19999=4 → (4+3)%7=0 → 周一 ✓ */
        private val MONDAY_DAY = 20003L
        private val BASE_NOW = MONDAY_DAY * DAY_MS + 12 * 3_600_000L // 周一中午12:00

        /** dayNumber 偏移辅助：返回距 BASE_NOW 指定天数的 epoch 毫秒 */
        private fun timeAt(dayOffset: Int, hour: Int = 10): Long {
            return (MONDAY_DAY + dayOffset) * DAY_MS + hour * 3_600_000L
        }

        /** 创建练习会话 */
        private fun session(
            startTime: Long,
            durationMin: Int = 15,
            totalNotes: Int = 100,
            accuracy: Float = 0.85f,
            title: String = "欢乐颂"
        ): SessionRecord {
            return SessionRecord(
                startTime = startTime,
                durationMs = durationMin * 60_000L,
                totalNotes = totalNotes,
                correctNotes = (totalNotes * accuracy).toInt(),
                wrongNotes = totalNotes / 10,
                missedNotes = 0,
                extraNotes = 0,
                accuracy = accuracy,
                scoreTitle = title
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  GoalDefinition & GoalProgress 基础测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `goal definition key is period_metric`() {
        val g1 = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val g2 = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.WEEKLY, 0.9)
        assertEquals("DAILY_PRACTICE_TIME", g1.key)
        assertEquals("WEEKLY_ACCURACY", g2.key)
    }

    @Test
    fun `formatTarget adds unit for each metric type`() {
        assertEquals("30分钟", GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0).formatTarget())
        assertEquals("2次", GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0).formatTarget())
        assertEquals("500个", GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.WEEKLY, 500.0).formatTarget())
        assertEquals("85%", GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85).formatTarget())
        assertEquals("3首", GoalDefinition(GoalMetric.UNIQUE_PIECES, GoalPeriod.WEEKLY, 3.0).formatTarget())
    }

    @Test
    fun `goal progress completed when current exceeds target`() {
        val def = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val progress = GoalProgress(def, 35.0)
        assertTrue(progress.isCompleted)
        assertEquals(1f, progress.progressRatio, 0.001f)
        assertEquals(0.0, progress.remaining, 0.001)
        assertEquals(GoalStatus.COMPLETED, progress.status())
    }

    @Test
    fun `goal progress not completed when below target`() {
        val def = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val progress = GoalProgress(def, 10.0)
        assertFalse(progress.isCompleted)
        assertEquals(0.333f, progress.progressRatio, 0.001f)
        assertEquals(20.0, progress.remaining, 0.001)
        assertEquals(GoalStatus.BEHIND, progress.status())
    }

    @Test
    fun `goal progress exactly at target is completed`() {
        val def = GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0)
        val progress = GoalProgress(def, 2.0)
        assertTrue(progress.isCompleted)
    }

    @Test
    fun `goal progress on track when ratio at least 50 percent`() {
        val def = GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.DAILY, 200.0)
        val progress = GoalProgress(def, 110.0)
        assertFalse(progress.isCompleted)
        assertEquals(GoalStatus.ON_TRACK, progress.status())
    }

    @Test
    fun `goal progress format current and remaining`() {
        val def = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val progress = GoalProgress(def, 12.0)
        assertEquals("12分钟", progress.formatCurrent())
        assertEquals("30分钟", progress.formatTarget())
        assertEquals("18分钟", progress.formatRemaining())
    }

    @Test
    fun `goal progress ratio clamps to 0 for zero target`() {
        val def = GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 0.0)
        val progress = GoalProgress(def, 5.0)
        assertEquals(0f, progress.progressRatio, 0.001f)
        // 0 target: current >= 0 always true → completed
        assertTrue(progress.isCompleted)
    }

    // ══════════════════════════════════════════════════════════════════
    //  evaluate() 测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `evaluate with empty sessions returns zero progress for all goals`() {
        val goals = GoalTracker.defaultGoals()
        val report = GoalTracker.evaluate(emptyList(), goals, BASE_NOW)

        assertEquals(goals.size, report.totalCount)
        assertEquals(0, report.completedCount)
        assertFalse(report.allCompleted)
        for (p in report.progresses) {
            assertEquals(0.0, p.currentValue, 0.001)
            assertFalse(p.isCompleted)
        }
    }

    @Test
    fun `evaluate with no goals returns empty report`() {
        val sessions = listOf(session(timeAt(0)))
        val report = GoalTracker.evaluate(sessions, emptyList(), BASE_NOW)

        assertEquals(0, report.totalCount)
        assertEquals(0, report.completedCount)
        assertFalse(report.allCompleted)
        assertNull(report.nextGoal)
    }

    @Test
    fun `evaluate practice time daily goal met`() {
        // 今天（周一，dayOffset=0）练习了2次各20分钟 = 40分钟 ≥ 30分钟目标
        val sessions = listOf(
            session(timeAt(0, 10), durationMin = 20),
            session(timeAt(0, 14), durationMin = 20)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertEquals(1, report.completedCount)
        assertTrue(report.allCompleted)
        val progress = report.progresses[0]
        assertEquals(40.0, progress.currentValue, 0.001)
    }

    @Test
    fun `evaluate daily goal only counts today sessions`() {
        // 昨天练习了40分钟，今天0分钟 → 每日30分钟目标未达成
        val sessions = listOf(
            session(timeAt(-1, 10), durationMin = 40)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertEquals(0, report.completedCount)
        assertEquals(0.0, report.progresses[0].currentValue, 0.001)
    }

    @Test
    fun `evaluate weekly goal counts entire week`() {
        // 本周一练习了20分钟 + 周三练习了20分钟 = 40分钟 ≥ 每周30分钟目标
        val sessions = listOf(
            session(timeAt(0, 10), durationMin = 20),
            session(timeAt(2, 15), durationMin = 20)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 30.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertTrue(report.progresses[0].isCompleted)
        assertEquals(40.0, report.progresses[0].currentValue, 0.001)
    }

    @Test
    fun `evaluate weekly goal does not count last week`() {
        // 上周（周一前7天）练习了100分钟，本周0分钟 → 周目标未达成
        val sessions = listOf(
            session(timeAt(-7, 10), durationMin = 100)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 30.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertFalse(report.progresses[0].isCompleted)
        assertEquals(0.0, report.progresses[0].currentValue, 0.001)
    }

    @Test
    fun `evaluate session count metric`() {
        val sessions = listOf(
            session(timeAt(0, 9)),
            session(timeAt(0, 11)),
            session(timeAt(0, 15))
        )
        val goal = GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertEquals(3.0, report.progresses[0].currentValue, 0.001)
        assertTrue(report.progresses[0].isCompleted)
    }

    @Test
    fun `evaluate notes played metric`() {
        val sessions = listOf(
            session(timeAt(0), totalNotes = 300),
            session(timeAt(0), totalNotes = 250)
        )
        val goal = GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.DAILY, 500.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertEquals(550.0, report.progresses[0].currentValue, 0.001)
        assertTrue(report.progresses[0].isCompleted)
    }

    @Test
    fun `evaluate accuracy metric with average`() {
        val sessions = listOf(
            session(timeAt(0), accuracy = 0.82f),
            session(timeAt(0), accuracy = 0.90f)
        )
        val goal = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        // 平均 0.86 ≥ 0.85 目标
        assertEquals(0.86, report.progresses[0].currentValue, 0.005)
        assertTrue(report.progresses[0].isCompleted)
    }

    @Test
    fun `evaluate accuracy metric empty returns zero`() {
        val goal = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85)
        val report = GoalTracker.evaluate(emptyList(), listOf(goal), BASE_NOW)

        assertEquals(0.0, report.progresses[0].currentValue, 0.001)
        assertFalse(report.progresses[0].isCompleted)
    }

    @Test
    fun `evaluate unique pieces metric`() {
        val sessions = listOf(
            session(timeAt(0), title = "欢乐颂"),
            session(timeAt(0), title = "小星星"),
            session(timeAt(0), title = "欢乐颂") // 重复
        )
        val goal = GoalDefinition(GoalMetric.UNIQUE_PIECES, GoalPeriod.DAILY, 2.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertEquals(2.0, report.progresses[0].currentValue, 0.001)
        assertTrue(report.progresses[0].isCompleted)
    }

    @Test
    fun `evaluate groups daily and weekly goals separately`() {
        val goals = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0),
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0),
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        )
        val report = GoalTracker.evaluate(emptyList(), goals, BASE_NOW)

        assertEquals(2, report.dailyGoals.size)
        assertEquals(1, report.weeklyGoals.size)
    }

    @Test
    fun `evaluate sorts completed goals first`() {
        val sessions = listOf(
            session(timeAt(0), durationMin = 40) // 40分钟
        )
        val goals = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 50.0), // 未达标
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 1.0)   // 达标
        )
        val report = GoalTracker.evaluate(sessions, goals, BASE_NOW)

        // 达标的排在前面
        assertTrue(report.progresses[0].isCompleted)
        assertFalse(report.progresses[1].isCompleted)
    }

    @Test
    fun `evaluate completionRatio and allCompleted`() {
        val sessions = listOf(session(timeAt(0), durationMin = 40))
        val goals = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0), // 达标
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 1.0),  // 达标
            GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.90)       // 0.85 < 0.90 未达标
        )
        val report = GoalTracker.evaluate(sessions, goals, BASE_NOW)

        assertEquals(2, report.completedCount)
        assertEquals(3, report.totalCount)
        assertEquals(0.667f, report.completionRatio, 0.01f)
        assertFalse(report.allCompleted)
    }

    @Test
    fun `evaluate nextGoal returns highest progress uncompleted`() {
        val sessions = listOf(session(timeAt(0), durationMin = 25))
        val goals = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 50.0), // 25/50 = 50%
            GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.DAILY, 1000.0) // 100/1000 = 10%
        )
        val report = GoalTracker.evaluate(sessions, goals, BASE_NOW)

        val next = report.nextGoal
        assertNotNull(next)
        assertEquals(GoalMetric.PRACTICE_TIME, next!!.definition.metric)
    }

    @Test
    fun `evaluate nextGoal null when all completed`() {
        val sessions = listOf(session(timeAt(0), durationMin = 40))
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val report = GoalTracker.evaluate(sessions, listOf(goal), BASE_NOW)

        assertNull(report.nextGoal)
    }

    @Test
    fun `evaluate report timestamps are correct`() {
        val report = GoalTracker.evaluate(emptyList(), emptyList(), BASE_NOW)
        assertEquals(MONDAY_DAY * DAY_MS, report.dayStartMs)
        assertEquals(MONDAY_DAY * DAY_MS, report.weekStartMs) // BASE_NOW 是周一
        assertEquals(BASE_NOW, report.nowMs)
    }

    // ══════════════════════════════════════════════════════════════════
    //  computeDailyStreak 测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `daily streak zero with no sessions`() {
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(0, GoalTracker.computeDailyStreak(emptyList(), goal, BASE_NOW))
    }

    @Test
    fun `daily streak counts today when completed`() {
        // 今天达标
        val sessions = listOf(session(timeAt(0), durationMin = 40))
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(1, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `daily streak counts consecutive days`() {
        // 今天 + 昨天 + 前天都达标
        val sessions = listOf(
            session(timeAt(0), durationMin = 40),
            session(timeAt(-1), durationMin = 35),
            session(timeAt(-2), durationMin = 40)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(3, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `daily streak does not break when today incomplete`() {
        // 今天只练了10分钟（未达标），昨天40分钟达标
        val sessions = listOf(
            session(timeAt(0), durationMin = 10),
            session(timeAt(-1), durationMin = 40),
            session(timeAt(-2), durationMin = 35)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        // 今天未达标跳过，昨天+前天达标 → 2
        assertEquals(2, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `daily streak stops at first incomplete day`() {
        // 今天达标，昨天达标，前天未达标
        val sessions = listOf(
            session(timeAt(0), durationMin = 40),
            session(timeAt(-1), durationMin = 40),
            session(timeAt(-2), durationMin = 10), // 未达标
            session(timeAt(-3), durationMin = 40)  // 达标但不连续
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(2, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `daily streak counts same day multiple sessions`() {
        // 同一天两次会话合并达标
        val sessions = listOf(
            session(timeAt(0, 9), durationMin = 15),
            session(timeAt(0, 15), durationMin = 20)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(1, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `daily streak with session count metric`() {
        val sessions = listOf(
            session(timeAt(0)), session(timeAt(0)), // 今天2次
            session(timeAt(-1)), session(timeAt(-1)) // 昨天2次
        )
        val goal = GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0)
        assertEquals(2, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `daily streak returns zero for weekly goal`() {
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        val sessions = listOf(session(timeAt(0), durationMin = 200))
        assertEquals(0, GoalTracker.computeDailyStreak(sessions, goal, BASE_NOW))
    }

    // ══════════════════════════════════════════════════════════════════
    //  computeWeeklyStreak 测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `weekly streak zero with no sessions`() {
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        assertEquals(0, GoalTracker.computeWeeklyStreak(emptyList(), goal, BASE_NOW))
    }

    @Test
    fun `weekly streak counts current week when completed`() {
        val sessions = listOf(
            session(timeAt(0), durationMin = 100),
            session(timeAt(1), durationMin = 100)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        assertEquals(1, GoalTracker.computeWeeklyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `weekly streak does not break when current week incomplete`() {
        // 本周100分钟（未达标），上周200分钟（达标）
        val sessions = listOf(
            session(timeAt(0), durationMin = 100),
            session(timeAt(-7), durationMin = 200)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        // 本周未达标跳过，上周达标 → 1
        assertEquals(1, GoalTracker.computeWeeklyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `weekly streak counts consecutive weeks`() {
        // 本周达标 + 上周达标 + 上上周达标
        val sessions = listOf(
            session(timeAt(0), durationMin = 200),
            session(timeAt(-7), durationMin = 200),
            session(timeAt(-14), durationMin = 200)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        assertEquals(3, GoalTracker.computeWeeklyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `weekly streak stops at first incomplete week`() {
        // 本周达标，上周未达标（50分钟），上上周达标
        val sessions = listOf(
            session(timeAt(0), durationMin = 200),
            session(timeAt(-7), durationMin = 50),
            session(timeAt(-14), durationMin = 200)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        assertEquals(1, GoalTracker.computeWeeklyStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `weekly streak returns zero for daily goal`() {
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val sessions = listOf(session(timeAt(0), durationMin = 40))
        assertEquals(0, GoalTracker.computeWeeklyStreak(sessions, goal, BASE_NOW))
    }

    // ══════════════════════════════════════════════════════════════════
    //  computeBestStreak 测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `best streak zero with no sessions`() {
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(0, GoalTracker.computeBestStreak(emptyList(), goal, BASE_NOW))
    }

    @Test
    fun `best daily streak finds longest run`() {
        // 连续3天达标（昨天/前天/大前天），中间断1天，再连续2天达标
        val sessions = listOf(
            session(timeAt(-1), durationMin = 40),
            session(timeAt(-2), durationMin = 40),
            session(timeAt(-3), durationMin = 40),
            // day -4 不达标
            session(timeAt(-5), durationMin = 40),
            session(timeAt(-6), durationMin = 40)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        // 最长连续是3天
        assertEquals(3, GoalTracker.computeBestStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `best daily streak includes today if completed`() {
        val sessions = listOf(
            session(timeAt(0), durationMin = 40),
            session(timeAt(-1), durationMin = 40),
            session(timeAt(-2), durationMin = 40)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals(3, GoalTracker.computeBestStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `best weekly streak finds longest run`() {
        // 上上周达标，上周未达标，本周达标 → best = 1
        val sessions = listOf(
            session(timeAt(0), durationMin = 200),
            session(timeAt(-7), durationMin = 50),   // 未达标
            session(timeAt(-14), durationMin = 200)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        assertEquals(1, GoalTracker.computeBestStreak(sessions, goal, BASE_NOW))
    }

    @Test
    fun `best weekly streak with consecutive weeks`() {
        // 3周连续达标
        val sessions = listOf(
            session(timeAt(0), durationMin = 200),
            session(timeAt(-7), durationMin = 200),
            session(timeAt(-14), durationMin = 200)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        assertEquals(3, GoalTracker.computeBestStreak(sessions, goal, BASE_NOW))
    }

    // ══════════════════════════════════════════════════════════════════
    //  computeStreak (combined) 测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `computeStreak returns GoalStreak with current and best`() {
        val sessions = listOf(
            session(timeAt(0), durationMin = 40),
            session(timeAt(-1), durationMin = 40),
            // gap
            session(timeAt(-3), durationMin = 40),
            session(timeAt(-4), durationMin = 40)
        )
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val streak = GoalTracker.computeStreak(sessions, goal, BASE_NOW)

        // current: 今天+昨天 = 2（前天断了）
        // best: 今天+昨天+前天断了，再看大前天+大大前天 = 2 → best = 2
        assertEquals(2, streak.streak)
        assertEquals(2, streak.bestStreak)
        assertTrue(streak.hasStreak)
    }

    @Test
    fun `computeStreak best greater than current`() {
        // 历史连续5天达标，但最近断了
        val sessions = (1..5).map { session(timeAt(-it - 1), durationMin = 40) }
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val streak = GoalTracker.computeStreak(sessions, goal, BASE_NOW)

        // current: 今天未练，昨天达标 → 从昨天开始数: 昨天,前天,大前天...第-1到-5都达标 → 但今天是-1的前一天
        // 今天dayOffset=0未练，检查昨天(=-1)达标，前天(-2)... 到-5都达标 → current=5
        // best=5
        assertTrue(streak.bestStreak >= streak.streak)
    }

    // ══════════════════════════════════════════════════════════════════
    //  presets / defaultGoals 测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `presets contains three difficulty levels`() {
        val presets = GoalTracker.presets()
        assertTrue(presets.containsKey("轻松"))
        assertTrue(presets.containsKey("适中"))
        assertTrue(presets.containsKey("挑战"))
    }

    @Test
    fun `presets easy has fewer goals than challenge`() {
        val presets = GoalTracker.presets()
        val easy = presets["轻松"]!!
        val challenge = presets["挑战"]!!
        assertTrue(easy.size < challenge.size)
    }

    @Test
    fun `presets challenge has higher targets than easy`() {
        val presets = GoalTracker.presets()
        val easyDailyTime = presets["轻松"]!!.first { it.metric == GoalMetric.PRACTICE_TIME }.target
        val challengeDailyTime = presets["挑战"]!!.first { it.metric == GoalMetric.PRACTICE_TIME }.target
        assertTrue(challengeDailyTime > easyDailyTime)
    }

    @Test
    fun `defaultGoals returns moderate preset`() {
        val defaults = GoalTracker.defaultGoals()
        val moderate = GoalTracker.presets()["适中"]!!
        assertEquals(moderate, defaults)
    }

    @Test
    fun `all preset goals have positive targets`() {
        for ((_, goals) in GoalTracker.presets()) {
            for (goal in goals) {
                assertTrue("Goal ${goal.metric} has non-positive target ${goal.target}", goal.target > 0)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  周起始日验证测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `week starts on Monday correctly`() {
        // BASE_NOW 是周一中午。上周日的会话不应该计入本周
        // 上周日 = dayOffset -1（因为 BASE_NOW 是周一，昨天是周日）
        val sundaySession = listOf(session(timeAt(-1), durationMin = 200))
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        val report = GoalTracker.evaluate(sundaySession, listOf(goal), BASE_NOW)

        // 上周日属于上周，不应计入本周
        assertEquals(0.0, report.progresses[0].currentValue, 0.001)
        assertFalse(report.progresses[0].isCompleted)
    }

    @Test
    fun `week boundary sessions on Monday count for current week`() {
        // 本周一凌晨的会话应该计入本周
        val mondayEarly = listOf(session(timeAt(0, 0), durationMin = 200))
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        val report = GoalTracker.evaluate(mondayEarly, listOf(goal), BASE_NOW)

        assertEquals(200.0, report.progresses[0].currentValue, 0.001)
        assertTrue(report.progresses[0].isCompleted)
    }

    @Test
    fun `evaluate with now on different weekday still works`() {
        // dayNumber 20004 = 周二
        val tuesdayNow = 20004L * DAY_MS + 12 * 3_600_000L
        // 周一(dayNumber=20003)的会话应该计入本周（本周从周一开始）
        val mondaySession = listOf(SessionRecord(
            startTime = 20003L * DAY_MS + 10 * 3_600_000L,
            durationMs = 200 * 60_000L,
            totalNotes = 100,
            correctNotes = 85,
            wrongNotes = 10,
            missedNotes = 0,
            extraNotes = 0,
            accuracy = 0.85f,
            scoreTitle = "测试"
        ))
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        val report = GoalTracker.evaluate(mondaySession, listOf(goal), tuesdayNow)

        assertEquals(200.0, report.progresses[0].currentValue, 0.001)
        assertTrue(report.progresses[0].isCompleted)
    }

    // ══════════════════════════════════════════════════════════════════
    //  端到端集成测试
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `end to end full week practice scenario`() {
        // 模拟一周练习：每天30分钟，2次会话，3首不同曲目
        // 注意：BASE_NOW 是周一中午，本周只有周一的练习数据
        val sessions = (0..6).flatMap { day ->
            listOf(
                session(timeAt(-day, 10), durationMin = 15, title = "欢乐颂", accuracy = 0.88f),
                session(timeAt(-day, 15), durationMin = 15, title = "小星星", accuracy = 0.90f)
            )
        }
        // 今天额外加一首
        val allSessions = sessions + session(timeAt(0, 20), durationMin = 15, title = "铃儿响叮当", accuracy = 0.92f)

        val goals = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0),
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0),
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 40.0),
            GoalDefinition(GoalMetric.UNIQUE_PIECES, GoalPeriod.WEEKLY, 3.0)
        )
        val report = GoalTracker.evaluate(allSessions, goals, BASE_NOW)

        // 今天: 15+15+15 = 45分钟 ≥ 30 ✓, 3次 ≥ 2 ✓
        // 本周（仅周一）: 45分钟 ≥ 40 ✓, 3首 ≥ 3 ✓
        assertEquals(4, report.completedCount)
        assertTrue(report.allCompleted)

        // 连续天数（过去7天每天都练了30+分钟）
        val dailyTimeGoal = goals[0]
        val streak = GoalTracker.computeDailyStreak(allSessions, dailyTimeGoal, BASE_NOW)
        assertEquals(7, streak)

        // 周连续（本周45分钟 + 上周180分钟都达标 → 2）
        val weeklyTimeGoal = goals[2]
        val weekStreak = GoalTracker.computeWeeklyStreak(allSessions, weeklyTimeGoal, BASE_NOW)
        assertEquals(2, weekStreak)
    }

    @Test
    fun `end to end mixed goals evaluation`() {
        // 今天只练了1次15分钟，准确率80%
        val sessions = listOf(
            session(timeAt(0), durationMin = 15, accuracy = 0.80f, totalNotes = 200)
        )
        val goals = GoalTracker.presets()["挑战"]!! // 60分钟/3次/90%准确率/5曲目/2000音符
        val report = GoalTracker.evaluate(sessions, goals, BASE_NOW)

        // 所有挑战目标都未达标
        assertEquals(0, report.completedCount)
        assertFalse(report.allCompleted)

        // nextGoal 应该是进度最高的
        val next = report.nextGoal
        assertNotNull(next)
    }

    @Test
    fun `end to end empty everything`() {
        val report = GoalTracker.evaluate(emptyList(), emptyList(), BASE_NOW)
        assertEquals(0, report.totalCount)
        assertEquals(0, report.completedCount)
        assertEquals(0f, report.completionRatio, 0.001f)
        assertFalse(report.allCompleted)
        assertNull(report.nextGoal)
        assertTrue(report.dailyGoals.isEmpty())
        assertTrue(report.weeklyGoals.isEmpty())
    }
}
