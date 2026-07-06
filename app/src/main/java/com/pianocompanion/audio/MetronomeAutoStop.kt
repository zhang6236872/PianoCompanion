package com.pianocompanion.audio

/**
 * 节拍器定时自动停止（Metronome Auto-Stop Timer）。
 *
 * 允许用户为节拍器设置一个倒计时时长（1/2/5/10/15/20/30 分钟），
 * 到时自动停止节拍器。用于：
 * - 定时练习（如每天固定练 15 分钟基本功）；
 * - 忘记关节拍器时避免持续耗电/耗流；
 * - 配合分段练习计划。
 *
 * 纯 Kotlin，无 Android 依赖，完全可单元测试。
 *
 * 架构：[AutoStopPreset] 是用户选择的预设时长枚举；[AutoStopState] 是倒计时
 * 运行时状态（密封类）；[AutoStopEngine] 是无状态计算对象，给定状态 + 当前
 * 绝对时间戳即可算出剩余/进度/是否到期，从而把所有时间逻辑变得完全确定性、
 * 可注入任意"当前时间"进行测试。
 */

/**
 * 自动停止预设时长。
 *
 * @property minutes 时长（分钟）。OFF 为 0，表示不启用自动停止。
 * @property displayLabel UI 展示用中文标签（如 "5 分钟"）。
 */
enum class AutoStopPreset(val minutes: Int, val displayLabel: String) {
    OFF(0, "关闭"),
    MIN_1(1, "1 分钟"),
    MIN_2(2, "2 分钟"),
    MIN_5(5, "5 分钟"),
    MIN_10(10, "10 分钟"),
    MIN_15(15, "15 分钟"),
    MIN_20(20, "20 分钟"),
    MIN_30(30, "30 分钟");

    /** 预设对应的毫秒时长。OFF 返回 0。 */
    val durationMillis: Long
        get() = minutes * 60_000L

    /** 是否启用自动停止（OFF 为 false）。 */
    val isActive: Boolean
        get() = this != OFF

    companion object {
        /**
         * 根据分钟数查找匹配的预设（用于从持久化恢复自定义时长）。
         * 没有精确匹配的内置预设时回退 [OFF]。
         */
        fun fromMinutes(minutes: Int): AutoStopPreset =
            entries.firstOrNull { it.minutes == minutes } ?: OFF
    }
}

/**
 * 倒计时运行时状态。
 *
 * - [Idle]：未在计时（节拍器未播放或未设置自动停止）。
 * - [Running]：正在倒计时，记录了起始绝对时间与总时长。
 * - [Finished]：倒计时已结束（节拍器已被自动停止）。
 */
sealed class AutoStopState {
    /** 未在计时。 */
    object Idle : AutoStopState()

    /**
     * 正在倒计时。
     *
     * @param startEpochMs 起始绝对时间戳（System.currentTimeMillis）。
     * @param durationMs 倒计时总时长（毫秒）。
     */
    data class Running(val startEpochMs: Long, val durationMs: Long) : AutoStopState()

    /** 倒计时已结束。 */
    object Finished : AutoStopState()
}

/**
 * 自动停止倒计时引擎（无状态，纯函数）。
 *
 * 所有方法都以"当前绝对时间 [nowEpochMs]"作为输入，不依赖任何全局时钟，
 * 因此在单元测试中可注入任意时间点验证边界条件。
 */
object AutoStopEngine {

    /**
     * 开始一个倒计时。
     *
     * @param durationMillis 倒计时总时长（毫秒），必须 > 0。
     * @param nowEpochMs 当前绝对时间戳。
     * @return [AutoStopState.Running] 状态。
     * @throws IllegalArgumentException 若 [durationMillis] <= 0。
     */
    fun start(durationMillis: Long, nowEpochMs: Long): AutoStopState.Running {
        require(durationMillis > 0) { "durationMillis must be > 0, got $durationMillis" }
        return AutoStopState.Running(startEpochMs = nowEpochMs, durationMs = durationMillis)
    }

    /**
     * 计算剩余毫秒数，下限钳位到 0。Idle/Finished 始终返回 0。
     * 对 [Running] 状态，若设备时钟回拨（now < start），已过时间按 0 处理，
     * 即返回完整时长，避免显示负数。
     */
    fun remainingMillis(state: AutoStopState, nowEpochMs: Long): Long = when (state) {
        AutoStopState.Idle, AutoStopState.Finished -> 0L
        is AutoStopState.Running -> {
            val elapsed = (nowEpochMs - state.startEpochMs).coerceAtLeast(0L)
            (state.durationMs - elapsed).coerceAtLeast(0L)
        }
    }

    /**
     * 倒计时是否已到期（仅 [Running] 且剩余 <= 0 时为 true）。
     */
    fun isExpired(state: AutoStopState, nowEpochMs: Long): Boolean =
        state is AutoStopState.Running && remainingMillis(state, nowEpochMs) <= 0L

    /**
     * 进度比例 0..1（已过时间占总时长的比例）。
     * Idle → 0；Finished → 1；Running → 钳位到 [0, 1]。
     */
    fun progress(state: AutoStopState, nowEpochMs: Long): Float = when (state) {
        AutoStopState.Idle -> 0f
        AutoStopState.Finished -> 1f
        is AutoStopState.Running -> {
            val elapsed = (nowEpochMs - state.startEpochMs).coerceAtLeast(0L)
            (elapsed.toFloat() / state.durationMs).coerceIn(0f, 1f)
        }
    }

    /**
     * 把毫秒数格式化为倒计时时钟字符串（向上取整到秒，避免显示 "0:00" 时还有 <1s 残留）。
     *
     * - < 1 小时 → "M:SS"（如 `5:03`、`0:03`）。
     * - >= 1 小时 → "H:MM:SS"（如 `1:02:03`）。
     * - 负数 → "0:00"。
     *
     * 注：采用向上取整而非四舍五入，是因为倒计时显示中"还剩 0.9 秒"应显示为 "0:01"
     * 而非 "0:00"，避免提前显示 0 让用户以为已结束。
     */
    fun formatClock(millis: Long): String {
        val clamped = millis.coerceAtLeast(0L)
        // 向上取整到整秒
        val totalSeconds = (clamped + 999L) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /**
     * 便捷方法：直接从状态 + 当前时间得到格式化的剩余时间字符串。
     */
    fun formatRemaining(state: AutoStopState, nowEpochMs: Long): String =
        formatClock(remainingMillis(state, nowEpochMs))
}
