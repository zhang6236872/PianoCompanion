package com.pianocompanion.mixedpractice

import com.pianocompanion.chordreading.ChordReadingQuestion
import com.pianocompanion.interval.IntervalQuestion
import com.pianocompanion.keysig.KeySigQuestion
import com.pianocompanion.notation.NoteReadingQuestion
import com.pianocompanion.rhythmreading.RhythmReadingQuestion

/**
 * 综合练习（Mixed Practice）数据模型。
 *
 * 本文件包含综合练习相关的数据模型，均为纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 综合练习将 5 个视唱练耳训练模块（识谱/音程/和弦/调号/节奏视读）的题目
 * 随机交错混合，在一轮练习中不断切换题型，训练用户的综合视唱练耳能力。
 *
 * 核心概念：
 * - **题型（Question Type）**：每道题来自一个子训练模块
 * - **统一接口**：[MixedQuestion] 密封接口统一了 5 种不同子模块的题目，
 *   暴露 [type]/[prompt]/[choices]/[correctAnswer] 四个统一字段，
 *   同时通过 sealed subtype 保留各子模块的原始题目数据供 UI 渲染
 */

/**
 * 综合练习中可能的题型（对应 5 个视唱练耳子训练模块）。
 *
 * @param emoji 用于 UI 标识的 emoji
 * @param displayName 中文题型名称
 * @param prompt 本题型的提问提示语（如"请识别这个音符"）
 */
enum class MixedQuestionType(
    val emoji: String,
    val displayName: String,
    val prompt: String
) {
    NOTE_READING("🎹", "识谱", "请识别这个音符的音名"),
    INTERVAL("📐", "音程", "请识别这两个音符之间的音程"),
    CHORD_READING("🎸", "和弦", "请识别这个和弦的类型"),
    KEY_SIGNATURE("🎼", "调号", "请识别这个调号对应的调性"),
    RHYTHM_READING("🥁", "节奏", "请辨认与图中一致的节奏型");

    companion object {
        val ALL = entries.toList()
    }
}

/**
 * 综合练习难度等级。
 *
 * 映射到各子模块的同名难度（BEGINNER/INTERMEDIATE/ADVANCED）。
 */
enum class MixedDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 综合练习题目（密封接口）。
 *
 * 每个子类型包装一个子训练模块的原始题目，同时提供统一的答题接口。
 * UI 层根据 [type] 分派到对应的渲染逻辑。
 */
sealed interface MixedQuestion {
    /** 题型。 */
    val type: MixedQuestionType

    /** 提问提示语。 */
    val prompt: String

    /** 答案选项列表（含正确答案，已打乱）。 */
    val choices: List<String>

    /** 正确答案文本。 */
    val correctAnswer: String

    /** 识谱训练题目。 */
    data class Note(val question: NoteReadingQuestion) : MixedQuestion {
        override val type get() = MixedQuestionType.NOTE_READING
        override val prompt get() = MixedQuestionType.NOTE_READING.prompt
        override val choices get() = question.answerChoices
        override val correctAnswer get() = question.letterName
    }

    /** 音程识别训练题目。 */
    data class Interval(val question: IntervalQuestion) : MixedQuestion {
        override val type get() = MixedQuestionType.INTERVAL
        override val prompt get() = MixedQuestionType.INTERVAL.prompt
        override val choices get() = question.answerChoices
        override val correctAnswer get() = question.correctAnswer
    }

    /** 和弦识别训练题目。 */
    data class Chord(val question: ChordReadingQuestion) : MixedQuestion {
        override val type get() = MixedQuestionType.CHORD_READING
        override val prompt get() = MixedQuestionType.CHORD_READING.prompt
        override val choices get() = question.answerChoices
        override val correctAnswer get() = question.correctAnswer
    }

    /** 调号识别训练题目。 */
    data class KeySig(val question: KeySigQuestion) : MixedQuestion {
        override val type get() = MixedQuestionType.KEY_SIGNATURE
        override val prompt get() = MixedQuestionType.KEY_SIGNATURE.prompt
        override val choices get() = question.answerChoices
        override val correctAnswer get() = question.correctAnswer
    }

    /** 节奏视读训练题目。 */
    data class Rhythm(val question: RhythmReadingQuestion) : MixedQuestion {
        override val type get() = MixedQuestionType.RHYTHM_READING
        override val prompt get() = MixedQuestionType.RHYTHM_READING.prompt
        override val choices get() = question.answerOptions.map { it.label }
        override val correctAnswer get() = question.answerOptions
            .first { it.fingerprint == question.correctAnswer }.label
    }
}

/**
 * 一次答题结果。
 *
 * @param type 题型
 * @param userAnswer 用户选择的答案文本
 * @param correctAnswer 正确答案文本
 * @param isCorrect 是否答对
 */
data class MixedAnswerRecord(
    val type: MixedQuestionType,
    val userAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean
)
