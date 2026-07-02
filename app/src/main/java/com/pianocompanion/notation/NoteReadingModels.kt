package com.pianocompanion.notation

/**
 * 识谱训练（Note Reading Trainer）数据模型。
 *
 * 本文件包含所有与识谱训练相关的音乐理论和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **谱号（Clef）**：高音谱号 / 低音谱号，决定五线谱上各线/间的音高
 * - **谱表位置（Staff Step）**：以五线谱底线（最下方线）为 0，每上一条线或一个间 +1，
 *   每下一格 -1。线=偶数步，间=奇数步
 * - **音名（Letter Name）**：C/D/E/F/G/A/B，不含升降号（仅自然音）
 */

/**
 * 谱号。
 * - [TREBLE] 高音谱号（G clef）：底线 = E4
 * - [BASS] 低音谱号（F clef）：底线 = G2
 */
enum class NoteReadingClef(val displayName: String) {
    TREBLE("高音谱号"),
    BASS("低音谱号");

    companion object {
        val ALL = listOf(TREBLE, BASS)
    }
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：仅五线谱上的线（5 个音）
 * - [INTERMEDIATE] 中级：线 + 间（9 个音）
 * - [ADVANCED] 高级：线 + 间 + 上下一两条加线（更多音，含加线音符）
 */
enum class NoteReadingDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 识谱训练题目。
 *
 * @param clef 谱号
 * @param difficulty 难度
 * @param staffStep 谱表位置（底线 = 0，每上一格 +1，下一格 -1）
 * @param midiNote 对应的 MIDI 音符号
 * @param letterName 正确音名（C/D/E/F/G/A/B，不含八度）
 * @param fullNoteName 完整音名（如 "C4"、"E5"），用于答错后的讲解
 * @param answerChoices 所有选项列表（含正确答案，已打乱）
 */
data class NoteReadingQuestion(
    val clef: NoteReadingClef,
    val difficulty: NoteReadingDifficulty,
    val staffStep: Int,
    val midiNote: Int,
    val letterName: String,
    val fullNoteName: String,
    val answerChoices: List<String>
)

/**
 * 一次答题结果。
 *
 * @param question 题目
 * @param userAnswer 用户选择的答案
 * @param isCorrect 是否答对
 */
data class NoteReadingAnswerRecord(
    val question: NoteReadingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.letterName
}
