package com.pianocompanion.musicalterms

import kotlin.random.Random

/**
 * 音乐术语训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [TermCategory]、[TermDifficulty] 和 [QuizDirection] 随机生成 [TermQuestion]。
 * 使用确定性随机数生成器（注入种子），相同种子产生相同题目，便于测试复现。
 *
 * 设计要点：
 * - 术语从指定难度及以下的池中随机选取
 * - 选项数随难度递增（初级 3 个、中级 4 个、高级 5 个）
 * - 干扰项从同类别（优先）或全局术语中随机选取，确保选项互不相同
 * - 选项已打乱顺序
 * - 出题方向随机切换（或由调用方指定），增强学习效果
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class MusicalTermsEngine(
    private val root: Random = Random.Default
) {

    companion object {
        private const val BEGINNER_CHOICES = 3
        private const val INTERMEDIATE_CHOICES = 4
        private const val ADVANCED_CHOICES = 5
    }

    /**
     * 生成一道题目。
     *
     * @param category 术语类别（null = 混合所有类别）
     * @param difficulty 难度
     * @param direction 出题方向（null = 随机）
     * @return 生成的题目；若候选池不足以生成题目则抛异常
     */
    fun generate(
        category: TermCategory? = null,
        difficulty: TermDifficulty = TermDifficulty.BEGINNER,
        direction: QuizDirection? = null
    ): TermQuestion {
        val pool = MusicalTermsLibrary.filter(category, difficulty)
        require(pool.isNotEmpty()) {
            "没有符合条件的术语 (category=$category, difficulty=$difficulty)"
        }

        val actualDirection = direction ?: pickDirection()
        val target = pool.random(root)
        val choiceCount = choicesFor(difficulty).coerceAtMost(pool.size)

        val correctAnswer = answerText(target, actualDirection)
        val distractors = generateDistractors(target, actualDirection, choiceCount - 1, difficulty)

        val answerChoices = (listOf(correctAnswer) + distractors)
            .distinct()
            .shuffled(root)

        val (prompt, promptLabel) = promptFor(target, actualDirection)

        return TermQuestion(
            prompt = prompt,
            promptLabel = promptLabel,
            correctAnswer = correctAnswer,
            answerChoices = answerChoices,
            term = target,
            direction = actualDirection
        )
    }

    // ── 内部方法 ──────────────────────────────────────────

    private fun pickDirection(): QuizDirection =
        if (root.nextBoolean()) QuizDirection.TERM_TO_MEANING else QuizDirection.MEANING_TO_TERM

    private fun choicesFor(difficulty: TermDifficulty): Int = when (difficulty) {
        TermDifficulty.BEGINNER -> BEGINNER_CHOICES
        TermDifficulty.INTERMEDIATE -> INTERMEDIATE_CHOICES
        TermDifficulty.ADVANCED -> ADVANCED_CHOICES
    }

    /**
     * 根据出题方向返回答案文本。
     * TERM_TO_MEANING: 答案是含义
     * MEANING_TO_TERM: 答案是术语
     */
    private fun answerText(term: MusicalTerm, direction: QuizDirection): String = when (direction) {
        QuizDirection.TERM_TO_MEANING -> term.meaning
        QuizDirection.MEANING_TO_TERM -> term.displayLabel
    }

    /**
     * 根据出题方向返回题面和标签。
     */
    private fun promptFor(term: MusicalTerm, direction: QuizDirection): Pair<String, String> =
        when (direction) {
            QuizDirection.TERM_TO_MEANING -> term.displayLabel to "这个术语是什么意思？"
            QuizDirection.MEANING_TO_TERM -> term.meaning to "哪个术语表示这个含义？"
        }

    /**
     * 生成干扰项。
     * 策略：优先从同类别中选取，不足时从全局补充。
     */
    private fun generateDistractors(
        target: MusicalTerm,
        direction: QuizDirection,
        count: Int,
        difficulty: TermDifficulty
    ): List<String> {
        val correctAnswer = answerText(target, direction)
        val result = mutableSetOf<String>()

        // 优先从同类别取
        val sameCategoryPool = MusicalTermsLibrary.filter(target.category, difficulty)
            .filter { it != target }
            .map { answerText(it, direction) }
            .filter { it != correctAnswer }
            .shuffled(root)
        for (item in sameCategoryPool) {
            if (result.size >= count) break
            result.add(item)
        }

        // 不足则从全局补充
        if (result.size < count) {
            val globalPool = MusicalTermsLibrary.upToDifficulty(difficulty)
                .filter { it != target && it.category != target.category }
                .map { answerText(it, direction) }
                .filter { it != correctAnswer && it !in result }
                .shuffled(root)
            for (item in globalPool) {
                if (result.size >= count) break
                result.add(item)
            }
        }

        return result.toList()
    }
}
