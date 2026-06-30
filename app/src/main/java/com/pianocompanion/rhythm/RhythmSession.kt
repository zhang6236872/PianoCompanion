package com.pianocompanion.rhythm

/**
 * 节奏训练会话状态机（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 管理一次节奏训练会话的完整生命周期：
 * 生成节奏 → 播放参考音频 → 用户敲击 → 匹配判定 → 下一题。
 * 跟踪当前节奏型、已答题数、准确度评分、连续高分记录。
 *
 * 用法：
 * ```
 * val session = RhythmSession(generator, RhythmDifficulty.BEGINNER)
 * session.start()                    // 生成第一题
 * val pattern = session.currentPattern!!
 * // ... 播放参考音频，收集用户敲击 ...
 * val result = session.submitTaps(tapTimestamps)  // 提交敲击
 * session.next()                     // 生成下一题
 * ```
 *
 * @param generator 节奏型生成器
 * @param difficulty 难度
 */
class RhythmSession(
    private val generator: RhythmPatternGenerator,
    private val difficulty: RhythmDifficulty
) {
    /** 当前节奏型（未开始或已结束时为 null）。 */
    var currentPattern: RhythmPattern? = null
        private set

    /** 已答题数（已提交敲击的题目数）。 */
    var answeredCount: Int = 0
        private set

    /** 通过的题数（score ≥ 0.5）。 */
    var passedCount: Int = 0
        private set

    /** 累计准确度评分总和（用于计算平均分）。 */
    var totalScore: Double = 0.0
        private set

    /** 当前连续通过数（未通过归零）。 */
    var currentStreak: Int = 0
        private set

    /** 最长连续通过记录。 */
    var bestStreak: Int = 0
        private set

    /** 答题历史（按时间顺序）。 */
    private val _history = mutableListOf<RhythmAnswerRecord>()
    val history: List<RhythmAnswerRecord> get() = _history.toList()

    /** 是否已开始会话。 */
    val isStarted: Boolean get() = currentPattern != null

    /** 当前题目是否已作答（等待 next()）。 */
    var isAnswered: Boolean = false
        private set

    /** 最后一次匹配结果。 */
    var lastResult: TapMatchResult? = null
        private set

    /**
     * 开始会话，生成第一题。
     */
    fun start() {
        answeredCount = 0
        passedCount = 0
        totalScore = 0.0
        currentStreak = 0
        bestStreak = 0
        _history.clear()
        isAnswered = false
        lastResult = null
        currentPattern = generator.generate(difficulty)
    }

    /**
     * 提交当前题目的敲击时间戳。
     *
     * @param taps 用户敲击时间戳列表（毫秒，相对于敲击阶段开始）
     * @return 匹配结果，如果当前没有题目或已作答则返回 null
     */
    fun submitTaps(taps: List<Long>): TapMatchResult? {
        val pattern = currentPattern ?: return null
        if (isAnswered) return null

        val matcher = RhythmTapMatcher()
        val targets = pattern.toTapTargets()
        val result = matcher.match(targets, taps)

        answeredCount++
        totalScore += result.score

        // 通过判定：score ≥ 0.5
        val isPassed = result.score >= PASS_THRESHOLD
        if (isPassed) {
            passedCount++
            currentStreak++
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
            }
        } else {
            currentStreak = 0
        }

        _history.add(RhythmAnswerRecord(pattern, taps, result, isPassed))
        lastResult = result
        isAnswered = true
        return result
    }

    /**
     * 生成下一题。
     *
     * @return 新节奏型，如果会话未开始则返回 null
     */
    fun next(): RhythmPattern? {
        if (!isStarted) return null
        isAnswered = false
        currentPattern = generator.generate(difficulty)
        return currentPattern
    }

    /**
     * 重置会话（保留生成器配置，清空所有统计）。
     */
    fun reset() {
        currentPattern = null
        answeredCount = 0
        passedCount = 0
        totalScore = 0.0
        currentStreak = 0
        bestStreak = 0
        _history.clear()
        isAnswered = false
        lastResult = null
    }

    /** 平均准确度（0.0-1.0），未答题时为 0.0。 */
    val averageScore: Double
        get() = if (answeredCount == 0) 0.0 else totalScore / answeredCount

    /** 通过率（0.0-1.0）。 */
    val passRate: Double
        get() = if (answeredCount == 0) 0.0 else passedCount.toDouble() / answeredCount

    /** 难度。 */
    fun difficulty(): RhythmDifficulty = difficulty

    companion object {
        /** 通过阈值（准确度 ≥ 此值视为通过）。 */
        const val PASS_THRESHOLD = 0.5
    }
}

/**
 * 一次节奏答题记录。
 *
 * @param pattern 节奏型
 * @param taps 用户敲击时间戳列表
 * @param result 匹配结果
 * @param isPassed 是否通过
 */
data class RhythmAnswerRecord(
    val pattern: RhythmPattern,
    val taps: List<Long>,
    val result: TapMatchResult,
    val isPassed: Boolean
)
