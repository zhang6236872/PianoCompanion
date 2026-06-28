package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PracticeCalendarHeatmap] 单元测试。
 *
 * 全部基于 UTC epoch day 数学（`epochMs / MILLIS_PER_DAY`），传入固定 `nowEpochMs`
 * 确保结果可复现，无时区/Calendar 依赖。
 */
class PracticeCalendarHeatmapTest {

    // ════════════════════════════════════════════════════════════════
    //  辅助
    // ════════════════════════════════════════════════════════════════

    private fun session(
        startEpochDay: Long,
        durationMin: Int = 10,
        title: String = "Test"
    ): SessionRecord {
        val startMs = startEpochDay * PracticeCalendarHeatmap.MILLIS_PER_DAY
        return SessionRecord(
            startTime = startMs,
            durationMs = durationMin * 60_000L,
            scoreTitle = title,
            totalNotes = 10,
            correctNotes = 8,
            wrongNotes = 1,
            missedNotes = 1,
            extraNotes = 0,
            accuracy = 0.8f
        )
    }

    /** 构造一个 [nowEpochMs] 使其对应的 epochDay 为 [todayDay]。 */
    private fun nowMs(todayDay: Long): Long = todayDay * PracticeCalendarHeatmap.MILLIS_PER_DAY

    // ════════════════════════════════════════════════════════════════
    //  isoDayOfWeek
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `isoDayOfWeek epoch day 0 is Thursday`() {
        // 1970-01-01 = Thursday; ISO Monday=0 → Thursday=3
        assertEquals(3, PracticeCalendarHeatmap.isoDayOfWeek(0L))
    }

    @Test
    fun `isoDayOfWeek epoch day 4 is Monday`() {
        // 1970-01-05 = Monday → 0
        assertEquals(0, PracticeCalendarHeatmap.isoDayOfWeek(4L))
    }

    @Test
    fun `isoDayOfWeek cycles correctly for full week`() {
        // epochDay 4=Mon,5=Tue,6=Wed,7=Thu,8=Fri,9=Sat,10=Sun
        val expected = listOf(0, 1, 2, 3, 4, 5, 6)
        for (i in expected.indices) {
            assertEquals(
                "epochDay ${4 + i} should be ISO day ${expected[i]}",
                expected[i],
                PracticeCalendarHeatmap.isoDayOfWeek((4 + i).toLong())
            )
        }
    }

    @Test
    fun `isoDayOfWeek handles negative epoch days`() {
        // epochDay -1 = 1969-12-31 = Wednesday → 2
        assertEquals(2, PracticeCalendarHeatmap.isoDayOfWeek(-1L))
        // epochDay -4 = 1969-12-28 = Sunday → 6
        assertEquals(6, PracticeCalendarHeatmap.isoDayOfWeek(-4L))
    }

    // ════════════════════════════════════════════════════════════════
    //  computeLevel
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `computeLevel zero or negative is level 0`() {
        assertEquals(0, PracticeCalendarHeatmap.computeLevel(0L))
        assertEquals(0, PracticeCalendarHeatmap.computeLevel(-1L))
    }

    @Test
    fun `computeLevel one second is level 1`() {
        // Any positive duration → at least level 1
        assertEquals(1, PracticeCalendarHeatmap.computeLevel(1_000L))
    }

    @Test
    fun `computeLevel just under 5 min is level 1`() {
        assertEquals(1, PracticeCalendarHeatmap.computeLevel(5 * 60_000L - 1))
    }

    @Test
    fun `computeLevel exactly 5 min is level 2`() {
        assertEquals(2, PracticeCalendarHeatmap.computeLevel(5 * 60_000L))
    }

    @Test
    fun `computeLevel just under 15 min is level 2`() {
        assertEquals(2, PracticeCalendarHeatmap.computeLevel(15 * 60_000L - 1))
    }

    @Test
    fun `computeLevel exactly 15 min is level 3`() {
        assertEquals(3, PracticeCalendarHeatmap.computeLevel(15 * 60_000L))
    }

    @Test
    fun `computeLevel just under 30 min is level 3`() {
        assertEquals(3, PracticeCalendarHeatmap.computeLevel(30 * 60_000L - 1))
    }

    @Test
    fun `computeLevel exactly 30 min is level 4`() {
        assertEquals(4, PracticeCalendarHeatmap.computeLevel(30 * 60_000L))
    }

    @Test
    fun `computeLevel 2 hours is level 4`() {
        assertEquals(4, PracticeCalendarHeatmap.computeLevel(120 * 60_000L))
    }

    @Test
    fun `computeLevel monotonic non-decreasing`() {
        val durations = listOf(0L, 1_000L, 4 * 60_000L, 10 * 60_000L, 20 * 60_000L, 60 * 60_000L)
        val levels = durations.map { PracticeCalendarHeatmap.computeLevel(it) }
        for (i in 1 until levels.size) {
            assertTrue(
                "level should be non-decreasing: ${levels[i - 1]} -> ${levels[i]}",
                levels[i] >= levels[i - 1]
            )
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  bucketByDay
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `bucketByDay empty sessions returns empty`() {
        assertTrue(PracticeCalendarHeatmap.bucketByDay(emptyList()).isEmpty())
    }

    @Test
    fun `bucketByDay aggregates sessions on same day`() {
        val day = 19000L
        val sessions = listOf(
            session(day, 10),
            session(day, 20),
            session(day, 5)
        )
        val buckets = PracticeCalendarHeatmap.bucketByDay(sessions)
        assertEquals(1, buckets.size)
        val act = buckets[day]!!
        assertEquals(35 * 60_000L, act.durationMs)
        assertEquals(3, act.sessionCount)
    }

    @Test
    fun `bucketByDay separates different days`() {
        val sessions = listOf(
            session(100L, 10),
            session(101L, 15),
            session(100L, 5)
        )
        val buckets = PracticeCalendarHeatmap.bucketByDay(sessions)
        assertEquals(2, buckets.size)
        assertEquals(15 * 60_000L, buckets[100L]!!.durationMs)
        assertEquals(2, buckets[100L]!!.sessionCount)
        assertEquals(15 * 60_000L, buckets[101L]!!.durationMs)
        assertEquals(1, buckets[101L]!!.sessionCount)
    }

    @Test
    fun `bucketByDay ignores zero duration sessions`() {
        val day = 200L
        val sessions = listOf(
            session(day, 10),
            SessionRecord(
                startTime = day * PracticeCalendarHeatmap.MILLIS_PER_DAY,
                durationMs = 0L,
                scoreTitle = "x",
                totalNotes = 0,
                correctNotes = 0,
                wrongNotes = 0,
                missedNotes = 0,
                extraNotes = 0,
                accuracy = 0f
            )
        )
        val buckets = PracticeCalendarHeatmap.bucketByDay(sessions)
        assertEquals(1, buckets[day]!!.sessionCount)
    }

    // ════════════════════════════════════════════════════════════════
    //  longestStreak
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `longestStreak empty is zero`() {
        assertEquals(0, PracticeCalendarHeatmap.longestStreak(emptyList()))
    }

    @Test
    fun `longestStreak single day is one`() {
        assertEquals(1, PracticeCalendarHeatmap.longestStreak(listOf(100L)))
    }

    @Test
    fun `longestStreak consecutive run`() {
        assertEquals(5, PracticeCalendarHeatmap.longestStreak(listOf(10L, 11L, 12L, 13L, 14L)))
    }

    @Test
    fun `longestStreak picks longest of multiple runs`() {
        // runs: [1,2,3] len 3, [5] len 1, [8,9,10,11] len 4
        assertEquals(4, PracticeCalendarHeatmap.longestStreak(listOf(1L, 2L, 3L, 5L, 8L, 9L, 10L, 11L)))
    }

    @Test
    fun `longestStreak unsorted input handled`() {
        assertEquals(3, PracticeCalendarHeatmap.longestStreak(listOf(12L, 10L, 11L)))
    }

    @Test
    fun `longestStreak gap breaks run`() {
        assertEquals(1, PracticeCalendarHeatmap.longestStreak(listOf(10L, 12L)))
    }

    // ════════════════════════════════════════════════════════════════
    //  build — 网格结构
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `build empty sessions produces all-zero grid with correct dimensions`() {
        val today = 19000L
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 10)
        assertEquals(10, heatmap.columns.size)
        for (col in heatmap.columns) {
            assertEquals(7, col.cells.size)
            for (cell in col.cells) {
                assertEquals(0L, cell.durationMs)
                assertEquals(0, cell.sessionCount)
                assertEquals(0, cell.level)
                assertFalse(cell.isActive)
            }
        }
        assertEquals(0, heatmap.activeDays)
        assertEquals(0L, heatmap.totalDurationMs)
        assertEquals(0, heatmap.longestStreak)
        assertNull(heatmap.bestDay)
    }

    @Test
    fun `build column count equals weeks param`() {
        val today = 5000L
        assertEquals(5, PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 5).columns.size)
        assertEquals(18, PracticeCalendarHeatmap.build(emptyList(), nowMs(today)).columns.size)
    }

    @Test
    fun `build each column has 7 cells`() {
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(3000L), weeks = 4)
        for (col in heatmap.columns) {
            assertEquals(7, col.cells.size)
        }
    }

    @Test
    fun `build totalCells equals weeks times 7`() {
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(1000L), weeks = 12)
        assertEquals(84, heatmap.totalCells)
    }

    @Test
    fun `build throws for non-positive weeks`() {
        try {
            PracticeCalendarHeatmap.build(emptyList(), nowMs(1L), weeks = 0)
            assert(false) { "Should have thrown for weeks=0" }
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            PracticeCalendarHeatmap.build(emptyList(), nowMs(1L), weeks = -3)
            assert(false) { "Should have thrown for weeks=-3" }
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `build throws for invalid weekStartIsoDay`() {
        try {
            PracticeCalendarHeatmap.build(emptyList(), nowMs(1L), weekStartIsoDay = 7)
            assert(false) { "Should have thrown for weekStartIsoDay=7" }
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  build — 范围与对齐
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `build last day is today`() {
        val today = 18500L
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 10)
        assertEquals(today, heatmap.lastEpochDay)
    }

    @Test
    fun `build first day is weeks ago aligned to week start`() {
        // today = epochDay 18500; isoDay = (18500+3)%7 = 18503%7
        // 18503 / 7 = 2643*7=18501, remainder 2 → isoDay 2 (Wednesday)
        // weekStart(Monday=0): currentWeekStart = 18500 - ((2-0+7)%7) = 18500 - 2 = 18498
        // firstDay = 18498 - (10-1)*7 = 18498 - 63 = 18435
        val today = 18500L
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 10, weekStartIsoDay = 0)
        assertEquals(18435L, heatmap.firstEpochDay)
    }

    @Test
    fun `build sessions outside range are excluded`() {
        val today = 1000L
        // A session way before the grid first day
        val oldSession = session(0L, 30)
        // A session today
        val todaySession = session(today, 30)
        val heatmap = PracticeCalendarHeatmap.build(
            listOf(oldSession, todaySession), nowMs(today), weeks = 4
        )
        // Only todaySession should count
        assertEquals(1, heatmap.activeDays)
        assertEquals(30 * 60_000L, heatmap.totalDurationMs)
    }

    @Test
    fun `build future sessions are excluded`() {
        val today = 1000L
        val futureSession = session(today + 5L, 30)
        val heatmap = PracticeCalendarHeatmap.build(
            listOf(futureSession), nowMs(today), weeks = 4
        )
        assertEquals(0, heatmap.activeDays)
    }

    @Test
    fun `build last column contains today and may contain future zeros`() {
        val today = 18498L // a Monday
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 3, weekStartIsoDay = 0)
        val lastCol = heatmap.columns.last()
        // First cell of last column should be currentWeekStart = today
        assertEquals(today, lastCol.weekStartEpochDay)
        assertEquals(today, lastCol.cells[0].epochDay)
        // Cells after index 0 are future → zero
        for (i in 1 until 7) {
            assertFalse("future cell $i should be inactive", lastCol.cells[i].isActive)
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  build — 活动量聚合与等级
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `build assigns correct level to today session`() {
        val today = 18498L
        val sessions = listOf(session(today, 40)) // 40 min → level 4
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 3)
        assertEquals(1, heatmap.activeDays)
        val todayCell = heatmap.columns.last().cells[0]
        assertEquals(4, todayCell.level)
        assertEquals(40 * 60_000L, todayCell.durationMs)
    }

    @Test
    fun `build aggregates multiple sessions on same day into one cell`() {
        val today = 18498L
        val sessions = listOf(
            session(today, 10),
            session(today, 20)
        )
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 3)
        val todayCell = heatmap.columns.last().cells[0]
        assertEquals(30 * 60_000L, todayCell.durationMs)
        assertEquals(2, todayCell.sessionCount)
        assertEquals(4, todayCell.level) // 30 min → level 4
    }

    @Test
    fun `build active days counts distinct days`() {
        val today = 18498L
        val sessions = listOf(
            session(today, 10),
            session(today - 1, 10),
            session(today - 3, 10)
        )
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 4)
        assertEquals(3, heatmap.activeDays)
    }

    @Test
    fun `build totalDurationMs sums all in-range sessions`() {
        val today = 18498L
        val sessions = listOf(
            session(today, 10),
            session(today - 1, 15),
            session(today - 5, 5)
        )
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 4)
        assertEquals(30 * 60_000L, heatmap.totalDurationMs)
    }

    // ════════════════════════════════════════════════════════════════
    //  build — streak & bestDay
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `build longestStreak reflects consecutive days in range`() {
        val today = 18498L
        // 5 consecutive days ending today
        val sessions = (0 until 5).map { session(today - it, 10) }
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 6)
        assertEquals(5, heatmap.longestStreak)
    }

    @Test
    fun `build bestDay is the day with max duration`() {
        val today = 18498L
        val sessions = listOf(
            session(today - 2, 10),
            session(today - 1, 50), // 50 min — the best
            session(today, 5)
        )
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 4)
        assertNotNull(heatmap.bestDay)
        assertEquals(today - 1, heatmap.bestDay!!.epochDay)
        assertEquals(50 * 60_000L, heatmap.bestDay!!.durationMs)
    }

    @Test
    fun `build maxDayDurationMs equals best day duration`() {
        val today = 18498L
        val sessions = listOf(session(today, 42))
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 3)
        assertEquals(42 * 60_000L, heatmap.maxDayDurationMs)
    }

    @Test
    fun `build bestDay null when no sessions`() {
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(1000L), weeks = 4)
        assertNull(heatmap.bestDay)
        assertEquals(0L, heatmap.maxDayDurationMs)
    }

    // ════════════════════════════════════════════════════════════════
    //  PracticeHeatmap 派生属性
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `activeRate is zero for no practice`() {
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(1000L), weeks = 4)
        assertEquals(0f, heatmap.activeRate, 0.001f)
    }

    @Test
    fun `activeRate equals activeDays over span`() {
        val today = 18498L
        // 3 active days out of a span
        val sessions = listOf(
            session(today, 10),
            session(today - 1, 10),
            session(today - 3, 10)
        )
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 2)
        val span = today - heatmap.firstEpochDay + 1
        assertEquals(3f / span, heatmap.activeRate, 0.001f)
        assertTrue(heatmap.activeRate in 0f..1f)
    }

    @Test
    fun `formatDuration minutes only`() {
        assertEquals("45 分", PracticeHeatmap.formatDuration(45 * 60_000L))
    }

    @Test
    fun `formatDuration hours and minutes`() {
        assertEquals("1 小时 30 分", PracticeHeatmap.formatDuration(90 * 60_000L))
    }

    @Test
    fun `formatDuration zero`() {
        assertEquals("0 分", PracticeHeatmap.formatDuration(0L))
    }

    @Test
    fun `formatDuration sub-minute rounds down`() {
        assertEquals("0 分", PracticeHeatmap.formatDuration(59_000L))
    }

    @Test
    fun `summary contains weeks activeDays and streak`() {
        val today = 18498L
        val sessions = (0 until 3).map { session(today - it, 20) } // 3 consecutive days, 20 min each
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 5)
        val s = heatmap.summary()
        assertTrue(s.contains("5 周"))
        assertTrue(s.contains("3 天"))
        assertTrue(s.contains("3 天")) // longest streak also 3
        assertTrue(s.contains("1 小时 0 分")) // 60 min total
    }

    // ════════════════════════════════════════════════════════════════
    //  确定性 / 周起始日
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `build is deterministic for same inputs`() {
        val today = 12345L
        val sessions = listOf(session(today, 25), session(today - 7, 10))
        val h1 = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 8)
        val h2 = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 8)
        assertEquals(h1.firstEpochDay, h2.firstEpochDay)
        assertEquals(h1.activeDays, h2.activeDays)
        assertEquals(h1.totalDurationMs, h2.totalDurationMs)
        assertEquals(h1.columns.size, h2.columns.size)
    }

    @Test
    fun `build Sunday-start shifts grid left edge`() {
        val today = 18498L // Monday (isoDay 0)
        // Monday-start: currentWeekStart = 18498
        val mondayStart = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 3, weekStartIsoDay = 0)
        assertEquals(18498L, mondayStart.firstEpochDay + (3 - 1) * 7L)
        // Sunday-start (isoDay 6): currentWeekStart = today - ((0 - 6 + 7) % 7) = today - 1
        val sundayStart = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 3, weekStartIsoDay = 6)
        assertEquals(18498L - 1, sundayStart.firstEpochDay + (3 - 1) * 7L)
    }

    @Test
    fun `build single session today appears in last column`() {
        val today = 18498L
        val sessions = listOf(session(today, 15))
        val heatmap = PracticeCalendarHeatmap.build(sessions, nowMs(today), weeks = 4)
        // Find the active cell
        val activeCells = heatmap.columns.flatMap { it.cells }.filter { it.isActive }
        assertEquals(1, activeCells.size)
        assertEquals(today, activeCells[0].epochDay)
    }

    @Test
    fun `build columns are ordered oldest to newest`() {
        val today = 18498L
        val heatmap = PracticeCalendarHeatmap.build(emptyList(), nowMs(today), weeks = 5)
        for (i in 1 until heatmap.columns.size) {
            assertTrue(
                "column $i should be after column ${i - 1}",
                heatmap.columns[i].weekStartEpochDay > heatmap.columns[i - 1].weekStartEpochDay
            )
        }
    }
}
