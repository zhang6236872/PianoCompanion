package com.pianocompanion.rhythmpattern

/**
 * 节奏型听辨训练会话状态机（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 管理一次训练会话的完整生命周期：出题 → 听辨 → 答题 → 判定 → 下一题。
 * 跟踪当前题目、已答题数、正确数、连续答对数、最长连击、答题历史。
 *
 * @param engine 出题引擎
 * @param difficulty 难度
 * @param tempo 播放速度
 * @param repeatCount 节奏型重复次数
 */
class RhythmPatternSession(
    private val engine: RhythmPatternEngine,
    private val difficulty: RhythmDifficulty,
    private val tempo: RhythmTempo = RhythmTempo.SLOW,
    private val repeatCount: Int = RhythmPatternEngine.DEFAULT_REPEAT_COUNT
) {
    /** 当前题目（未开始或已结束时为 null）。 */
    var currentQuestion: RhythmPatternQuestion? = null
        private set

    /** 已答题数（含答对和答错）。 */
    var answeredCount: Int = 0
        private set

    /** 答对数。 */
    var correctCount: Int = 0
        private set

    /** 当前连续答对数（答错归零）。 */
    var currentStreak: Int = 0
        private set

    /** 最长连续答对记录。 */
    var bestStreak: Int = 0
        private set

    /** 答题历史（按时间顺序）。 */
    private val _history = mutableListOf<RhythmPatternAnswerRecord>()
    val history: List<RhythmPatternAnswerRecord> get() = _history.toList()

    /** 是否已开始会话。 */
    val isStarted: Boolean get() = currentQuestion != null

    /** 当前题目是否已作答（等待 next()）。 */
    var isAnswered: Boolean = false
        private set

    /** 最后一次答题记录。 */
    var lastAnswer: RhythmPatternAnswerRecord? = null
        private set

    /** 开始会话，生成第一题。 */
    fun start() {
        answeredCount = 0
        correctCount = 0
        currentStreak = 0
        bestStreak = 0
        _history.clear()
        isAnswered = false
        lastAnswer = null
        currentQuestion = engine.generate(difficulty, tempo, repeatCount)
    }

    /**
     * 提交当前题目的答案。
     *
     * @param answer 用户选择的答案文本（节奏型显示名）
     * @return 答题结果，如果当前没有题目或已作答则返回 null
     */
    fun submit(answer: String): RhythmPatternAnswerRecord? {
        val question = currentQuestion ?: return null
        if (isAnswered) return null

        val isCorrect = answer == question.correctAnswer
        val record = RhythmPatternAnswerRecord(question, answer, isCorrect)

        answeredCount++
        if (isCorrect) {
            correctCount++
            currentStreak++
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
            }
        } else {
            currentStreak = 0
        }

        _history.add(record)
        lastAnswer = record
        isAnswered = true
        return record
    }

    /**
     * 生成下一题。
     *
     * @return 新题目，如果会话未开始则返回 null
     */
    fun next(): RhythmPatternQuestion? {
        if (!isStarted) return null
        isAnswered = false
        currentQuestion = engine.generate(difficulty, tempo, repeatCount)
        return currentQuestion
    }

    /** 重置会话（保留引擎配置，清空所有统计）。 */
    fun reset() {
        currentQuestion = null
        answeredCount = 0
        correctCount = 0
        currentStreak = 0
        bestStreak = 0
        _history.clear()
        isAnswered = false
        lastAnswer = null
    }

    /** 准确率（0.0-1.0），未答题时为 0.0。 */
    val accuracy: Double get() = if (answeredCount == 0) 0.0 else correctCount.toDouble() / answeredCount

    /** 难度。 */
    fun difficulty(): RhythmDifficulty = difficulty

    /** 播放速度。 */
    fun tempo(): RhythmTempo = tempo

    /** 重复次数。 */
    fun repeatCount(): Int = repeatCount
}
