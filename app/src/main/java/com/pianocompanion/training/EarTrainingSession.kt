package com.pianocompanion.training

/**
 * 听音训练会话状态机（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 管理一次训练会话的完整生命周期：出题 → 播放音频 → 答题 → 判定 → 下一题。
 * 跟踪当前题目、已答题数、正确数、连续答对数、最长连击、答题历史。
 *
 * 用法：
 * ```
 * val session = EarTrainingSession(engine, ExerciseType.INTERVAL, Difficulty.BEGINNER)
 * session.start()        // 生成第一题
 * val question = session.currentQuestion!!
 * session.submit("大三度")  // 提交答案
 * session.next()         // 生成下一题
 * ```
 *
 * @param engine 出题引擎
 * @param exerciseType 练习类型
 * @param difficulty 难度
 */
class EarTrainingSession(
    private val engine: EarTrainingEngine,
    private val exerciseType: ExerciseType,
    private val difficulty: Difficulty
) {
    /** 当前题目（未开始或已结束时为 null）。 */
    var currentQuestion: EarTrainingQuestion? = null
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
    private val _history = mutableListOf<AnswerRecord>()
    val history: List<AnswerRecord> get() = _history.toList()

    /** 是否已开始会话。 */
    val isStarted: Boolean get() = currentQuestion != null

    /** 当前题目是否已作答（等待 next()）。 */
    var isAnswered: Boolean = false
        private set

    /** 最后一次答题记录。 */
    var lastAnswer: AnswerRecord? = null
        private set

    /**
     * 开始会话，生成第一题。
     */
    fun start() {
        answeredCount = 0
        correctCount = 0
        currentStreak = 0
        bestStreak = 0
        _history.clear()
        isAnswered = false
        lastAnswer = null
        currentQuestion = engine.generate(exerciseType, difficulty)
    }

    /**
     * 提交当前题目的答案。
     *
     * @param answer 用户选择的答案文本
     * @return 答题结果，如果当前没有题目或已作答则返回 null
     */
    fun submit(answer: String): AnswerRecord? {
        val question = currentQuestion ?: return null
        if (isAnswered) return null

        val isCorrect = answer == question.correctAnswer
        val record = AnswerRecord(question, answer, isCorrect)

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
    fun next(): EarTrainingQuestion? {
        if (!isStarted) return null
        isAnswered = false
        currentQuestion = engine.generate(exerciseType, difficulty)
        return currentQuestion
    }

    /**
     * 重置会话（保留引擎配置，清空所有统计）。
     */
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

    /** 练习类型。 */
    fun exerciseType(): ExerciseType = exerciseType

    /** 难度。 */
    fun difficulty(): Difficulty = difficulty
}
