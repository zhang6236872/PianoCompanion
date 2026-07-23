package com.pianocompanion.intervalsequence

/**
 * 音程序列记忆训练会话状态机（纯 Kotlin，无 Android 依赖）。
 *
 * 生命周期：[start] → [submit] → [next] → [submit] → ... → [reset]
 *
 * 维护：当前题目、答题历史、连击/最佳连击、准确率。
 * 采用防御性副本策略保护内部状态。
 */
class IntervalSequenceSession(
    private val engine: IntervalSequenceEngine,
    val difficulty: IntervalSequenceDifficulty
) {
    private var currentSeed: Long = 0L
    private var seedCounter: Long = 0L

    private var _currentQuestion: IntervalSequenceQuestion? = null
    private val _history = mutableListOf<IntervalSequenceAnswerRecord>()

    private var _streak = 0
    private var _bestStreak = 0

    /** 当前题目（无则 null）。 */
    val currentQuestion: IntervalSequenceQuestion? get() = _currentQuestion

    /** 答题历史（防御性副本）。 */
    val history: List<IntervalSequenceAnswerRecord> get() = _history.toList()

    /** 当前连击数。 */
    val streak: Int get() = _streak

    /** 最佳连击数。 */
    val bestStreak: Int get() = _bestStreak

    /** 已答题数。 */
    val answeredCount: Int get() = _history.size

    /** 正确答题数。 */
    val correctCount: Int get() = _history.count { it.isCorrect }

    /** 准确率（0.0 - 1.0，未答题时为 0.0）。 */
    val accuracy: Double
        get() = if (answeredCount == 0) 0.0 else correctCount.toDouble() / answeredCount

    /** 最后一次答题（无则 null）。 */
    val lastAnswer: IntervalSequenceAnswerRecord? get() = _history.lastOrNull()

    /**
     * 开始/继续会话，生成第一题（或下一题）。
     */
    fun start() {
        seedCounter++
        currentSeed = System.nanoTime() xor seedCounter
        _currentQuestion = engine.generate(currentSeed)
    }

    /**
     * 生成下一题。
     */
    fun next() {
        start()
    }

    /**
     * 提交答案。
     *
     * @param answer 用户选择的展示字符串
     * @return 答题记录
     * @throws IllegalStateException 如果没有当前题目
     * @throws IllegalArgumentException 如果已答过当前题（双击防护）
     */
    fun submit(answer: String): IntervalSequenceAnswerRecord {
        val question = _currentQuestion
            ?: throw IllegalStateException("没有当前题目，请先调用 start()")

        // 双击防护：同一题不可重复作答
        if (_history.isNotEmpty() && _history.last().question == question) {
            throw IllegalArgumentException("当前题目已作答，请调用 next() 获取下一题")
        }

        val isCorrect = answer == question.correctAnswer
        if (isCorrect) {
            _streak++
            if (_streak > _bestStreak) _bestStreak = _streak
        } else {
            _streak = 0
        }

        val record = IntervalSequenceAnswerRecord(
            question = question,
            userAnswer = answer,
            isCorrect = isCorrect
        )
        _history.add(record)
        return record
    }

    /**
     * 重置会话（清空所有状态）。
     */
    fun reset() {
        seedCounter = 0L
        _currentQuestion = null
        _history.clear()
        _streak = 0
        _bestStreak = 0
    }
}

/**
 * 单次答题记录（防御性副本友好）。
 */
data class IntervalSequenceAnswerRecord(
    val question: IntervalSequenceQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
)
