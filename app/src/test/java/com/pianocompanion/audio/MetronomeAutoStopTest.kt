package com.pianocompanion.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AutoStopEngine] 与 [AutoStopPreset] / [AutoStopState] 的单元测试。
 *
 * 引擎完全无状态、以注入的时间戳为输入，因此所有边界条件可确定性验证。
 */
class MetronomeAutoStopTest {

    private companion object {
        const val T0 = 1_700_000_000_000L // 任意起始绝对时间
    }

    // ═══════════════════════ AutoStopPreset ═══════════════════════

    @Test
    fun preset_durationMillis_convertsMinutesToMillis() {
        assertEquals(0L, AutoStopPreset.OFF.durationMillis)
        assertEquals(60_000L, AutoStopPreset.MIN_1.durationMillis)
        assertEquals(120_000L, AutoStopPreset.MIN_2.durationMillis)
        assertEquals(300_000L, AutoStopPreset.MIN_5.durationMillis)
        assertEquals(600_000L, AutoStopPreset.MIN_10.durationMillis)
        assertEquals(900_000L, AutoStopPreset.MIN_15.durationMillis)
        assertEquals(1_200_000L, AutoStopPreset.MIN_20.durationMillis)
        assertEquals(1_800_000L, AutoStopPreset.MIN_30.durationMillis)
    }

    @Test
    fun preset_isActive_onlyFalseForOff() {
        assertFalse(AutoStopPreset.OFF.isActive)
        // 其余全部 true
        AutoStopPreset.entries.filter { it != AutoStopPreset.OFF }.forEach {
            assertTrue("$it should be active", it.isActive)
        }
    }

    @Test
    fun preset_displayLabels_areNonBlank() {
        AutoStopPreset.entries.forEach {
            assertTrue("$it label blank", it.displayLabel.isNotBlank())
        }
    }

    @Test
    fun preset_fromMinutes_roundTrips() {
        AutoStopPreset.entries.forEach { p ->
            assertEquals(p, AutoStopPreset.fromMinutes(p.minutes))
        }
    }

    @Test
    fun preset_fromMinutes_unknownFallsBackToOff() {
        assertEquals(AutoStopPreset.OFF, AutoStopPreset.fromMinutes(7))
        assertEquals(AutoStopPreset.OFF, AutoStopPreset.fromMinutes(45))
        assertEquals(AutoStopPreset.OFF, AutoStopPreset.fromMinutes(-1))
    }

    // ═══════════════════════ start ═══════════════════════

    @Test
    fun start_createsRunningWithCorrectFields() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertEquals(T0, state.startEpochMs)
        assertEquals(300_000L, state.durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun start_rejectsZeroDuration() {
        AutoStopEngine.start(0L, T0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun start_rejectsNegativeDuration() {
        AutoStopEngine.start(-100L, T0)
    }

    // ═══════════════════════ remainingMillis ═══════════════════════

    @Test
    fun remainingMillis_idleAndFinishedReturnZero() {
        assertEquals(0L, AutoStopEngine.remainingMillis(AutoStopState.Idle, T0))
        assertEquals(0L, AutoStopEngine.remainingMillis(AutoStopState.Finished, T0))
    }

    @Test
    fun remainingMillis_fullAtStart() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertEquals(300_000L, AutoStopEngine.remainingMillis(state, T0))
    }

    @Test
    fun remainingMillis_decreasesWithElapsed() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertEquals(250_000L, AutoStopEngine.remainingMillis(state, T0 + 50_000L))
        assertEquals(150_000L, AutoStopEngine.remainingMillis(state, T0 + 150_000L))
        assertEquals(0L, AutoStopEngine.remainingMillis(state, T0 + 300_000L))
    }

    @Test
    fun remainingMillis_clampsAtZeroAfterExpiry() {
        val state = AutoStopEngine.start(60_000L, T0)
        assertEquals(0L, AutoStopEngine.remainingMillis(state, T0 + 60_000L))
        assertEquals(0L, AutoStopEngine.remainingMillis(state, T0 + 120_000L))
    }

    @Test
    fun remainingMillis_handlesClockRollbackAsFullDuration() {
        // 设备时钟回拨：now < start。已过时间按 0 处理，应返回完整时长。
        val state = AutoStopEngine.start(300_000L, T0)
        assertEquals(300_000L, AutoStopEngine.remainingMillis(state, T0 - 10_000L))
    }

    // ═══════════════════════ isExpired ═══════════════════════

    @Test
    fun isExpired_falseBeforeExpiry() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertFalse(AutoStopEngine.isExpired(state, T0))
        assertFalse(AutoStopEngine.isExpired(state, T0 + 299_999L))
    }

    @Test
    fun isExpired_trueAtAndAfterExpiry() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertTrue(AutoStopEngine.isExpired(state, T0 + 300_000L))
        assertTrue(AutoStopEngine.isExpired(state, T0 + 1_000_000L))
    }

    @Test
    fun isExpired_idleAndFinishedNeverRunningExpired() {
        // Idle / Finished 不是 Running，isExpired 返回 false
        assertFalse(AutoStopEngine.isExpired(AutoStopState.Idle, T0))
        assertFalse(AutoStopEngine.isExpired(AutoStopState.Finished, T0))
    }

    // ═══════════════════════ progress ═══════════════════════

    @Test
    fun progress_idleZero_finishedOne() {
        assertEquals(0f, AutoStopEngine.progress(AutoStopState.Idle, T0), 0f)
        assertEquals(1f, AutoStopEngine.progress(AutoStopState.Finished, T0), 0f)
    }

    @Test
    fun progress_runningGoesZeroToOne() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertEquals(0f, AutoStopEngine.progress(state, T0), 1e-6f)
        assertEquals(0.5f, AutoStopEngine.progress(state, T0 + 150_000L), 1e-6f)
        assertEquals(1f, AutoStopEngine.progress(state, T0 + 300_000L), 0f)
        // 超过后钳位到 1
        assertEquals(1f, AutoStopEngine.progress(state, T0 + 600_000L), 0f)
    }

    @Test
    fun progress_handlesClockRollbackAsZero() {
        val state = AutoStopEngine.start(300_000L, T0)
        assertEquals(0f, AutoStopEngine.progress(state, T0 - 50_000L), 0f)
    }

    // ═══════════════════════ formatClock ═══════════════════════

    @Test
    fun formatClock_zeroAndSmall() {
        assertEquals("0:00", AutoStopEngine.formatClock(0L))
        assertEquals("0:00", AutoStopEngine.formatClock(-1000L)) // 负数钳位
    }

    @Test
    fun formatClock_roundsUpToSecond() {
        // 1ms → "0:01"（向上取整）
        assertEquals("0:01", AutoStopEngine.formatClock(1L))
        assertEquals("0:01", AutoStopEngine.formatClock(999L))
        assertEquals("0:01", AutoStopEngine.formatClock(1000L))
        assertEquals("0:02", AutoStopEngine.formatClock(1001L))
        assertEquals("0:03", AutoStopEngine.formatClock(2500L))
    }

    @Test
    fun formatClock_minuteBoundary() {
        assertEquals("1:00", AutoStopEngine.formatClock(60_000L))
        assertEquals("5:00", AutoStopEngine.formatClock(300_000L))
        assertEquals("5:03", AutoStopEngine.formatClock(303_000L))
        assertEquals("5:03", AutoStopEngine.formatClock(302_500L)) // 向上取整
    }

    @Test
    fun formatClock_hourFormat() {
        // >= 1 小时 → H:MM:SS
        assertEquals("1:00:00", AutoStopEngine.formatClock(3_600_000L))
        assertEquals("1:02:03", AutoStopEngine.formatClock(3_723_000L))
        assertEquals("1:02:03", AutoStopEngine.formatClock(3_722_500L)) // 向上取整
    }

    @Test
    fun formatClock_thirtyMinutes() {
        assertEquals("30:00", AutoStopEngine.formatClock(1_800_000L))
    }

    // ═══════════════════════ formatRemaining convenience ═══════════════════════

    @Test
    fun formatRemaining_matchesRemainingFormat() {
        val state = AutoStopEngine.start(300_000L, T0)
        // 起始：5:00
        assertEquals("5:00", AutoStopEngine.formatRemaining(state, T0))
        // 过 1 分钟：4:00
        assertEquals("4:00", AutoStopEngine.formatRemaining(state, T0 + 60_000L))
        // 过 4 分 57 秒：0:03
        assertEquals("0:03", AutoStopEngine.formatRemaining(state, T0 + 297_000L))
        // 到期：0:00
        assertEquals("0:00", AutoStopEngine.formatRemaining(state, T0 + 300_000L))
    }

    @Test
    fun formatRemaining_idleReturnsZeroClock() {
        assertEquals("0:00", AutoStopEngine.formatRemaining(AutoStopState.Idle, T0))
        assertEquals("0:00", AutoStopEngine.formatRemaining(AutoStopState.Finished, T0))
    }

    // ═══════════════════════ 端到端时间线 ═══════════════════════

    @Test
    fun endToEnd_oneMinuteCountdownTimeline() {
        // 模拟 1 分钟倒计时的完整时间线
        val state = AutoStopEngine.start(AutoStopPreset.MIN_1.durationMillis, T0)
        assertFalse(AutoStopEngine.isExpired(state, T0))
        assertEquals("1:00", AutoStopEngine.formatRemaining(state, T0))

        // 30 秒后
        val at30 = T0 + 30_000L
        assertEquals("0:30", AutoStopEngine.formatRemaining(state, at30))
        assertEquals(0.5f, AutoStopEngine.progress(state, at30), 1e-6f)
        assertFalse(AutoStopEngine.isExpired(state, at30))

        // 到期
        val atExpiry = T0 + 60_000L
        assertTrue(AutoStopEngine.isExpired(state, atExpiry))
        assertEquals(0L, AutoStopEngine.remainingMillis(state, atExpiry))
    }
}
