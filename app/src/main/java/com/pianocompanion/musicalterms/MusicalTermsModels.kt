package com.pianocompanion.musicalterms

/**
 * 音乐表情术语训练（Musical Terms Trainer）数据模型。
 *
 * 本文件包含所有与音乐表情术语相关的分类、难度和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **音乐术语（Musical Term）**：乐谱中常见的意大利语/德语/法语表情标记，
 *   如速度标记（Allegro）、力度标记（forte）、演奏法（legato）等
 * - **术语库（Term Library）**：按类别组织的术语集合
 * - **出题模式（Quiz Direction）**：术语→含义 或 含义→术语
 */

/**
 * 术语类别。
 * - [TEMPO] 速度标记：Largo, Andante, Allegro, Presto 等
 * - [DYNAMICS] 力度标记：pianissimo, forte, crescendo 等
 * - [ARTICULATION] 演奏法：legato, staccato, tenuto 等
 * - [EXPRESSION] 表情术语：dolce, cantabile, maestoso 等
 * - [MODIFIER] 修饰词：molto, poco, più, meno 等
 */
enum class TermCategory(val displayName: String, val description: String) {
    TEMPO("速度术语", "速度标记如 Largo、Andante、Allegro"),
    DYNAMICS("力度术语", "力度标记如 piano、forte、crescendo"),
    ARTICULATION("演奏法", "演奏法如 legato、staccato、tenuto"),
    EXPRESSION("表情术语", "表情术语如 dolce、cantabile、maestoso"),
    MODIFIER("修饰词", "修饰词如 molto、poco、più");

    companion object {
        val ALL = listOf(TEMPO, DYNAMICS, ARTICULATION, EXPRESSION, MODIFIER)
    }
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：常见基础术语（约 20 个），选项少
 * - [INTERMEDIATE] 中级：更多术语（约 40 个），包含中等常见
 * - [ADVANCED] 高级：全部术语（约 70+ 个），包含冷门和易混淆
 */
enum class TermDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 出题方向。
 * - [TERM_TO_MEANING] 显示术语，选择含义（术语→含义）
 * - [MEANING_TO_TERM] 显示含义，选择术语（含义→术语）
 */
enum class QuizDirection(val displayName: String) {
    TERM_TO_MEANING("术语→含义"),
    MEANING_TO_TERM("含义→术语");

    companion object {
        val ALL = listOf(TERM_TO_MEANING, MEANING_TO_TERM)
    }
}

/**
 * 一条音乐术语。
 *
 * @param term 原文术语（意大利语/德语/法语），如 "Allegro"
 * @param meaning 中文含义，如 "快板"
 * @param category 术语类别
 * @param bpmRange 速度范围（仅速度术语有意义，如 "120-168"），其他类别为 null
 * @param difficulty 所属难度等级（一个术语可属于多个难度，这里取最低难度）
 * @param abbreviation 缩写形式（如 "f" 表示 forte），无缩写时为 null
 * @param example 使用场景说明，如 "快板奏鸣曲第一乐章"
 */
data class MusicalTerm(
    val term: String,
    val meaning: String,
    val category: TermCategory,
    val bpmRange: String? = null,
    val difficulty: TermDifficulty,
    val abbreviation: String? = null,
    val example: String? = null
) {
    /** 用于显示的完整标签（术语 + 缩写）。 */
    val displayLabel: String
        get() = if (abbreviation != null) "$term ($abbreviation)" else term
}

/**
 * 一次术语训练题目。
 *
 * @param prompt 显示给用户的题目内容（术语或含义文本）
 * @param promptLabel 题目标签（如 "这个术语是什么意思？" 或 "哪个术语表示..."）
 * @param correctAnswer 正确答案文本
 * @param answerChoices 所有选项列表（含正确答案，已打乱）
 * @param term 对应的完整术语对象（用于答题后显示详情）
 * @param direction 出题方向
 */
data class TermQuestion(
    val prompt: String,
    val promptLabel: String,
    val correctAnswer: String,
    val answerChoices: List<String>,
    val term: MusicalTerm,
    val direction: QuizDirection
)

/**
 * 一次答题结果。
 *
 * @param question 题目
 * @param userAnswer 用户选择的答案
 * @param isCorrect 是否答对
 */
data class TermAnswerRecord(
    val question: TermQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswerText: String? get() = if (isCorrect) null else question.correctAnswer
}
