package com.pianocompanion.polyphonicmotion

/**
 * 复调运动辨识训练（Polyphonic Motion Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **复调运动（Polyphonic Motion）**：当两个声部（线条）同时进行时，它们之间的
 *   **相对运动关系**。这是对位法 / 复调听觉的核心能力——能听出两条旋律线是「一起走」、
 *   「相向走」还是「一静一动」。
 *
 * 三种基本运动类型（辨识目标）：
 * - **同向运动（Parallel / Similar）**：两个声部朝同一方向移动（同时上行或同时下行）。
 *   两个线条「并肩同行」，听感齐整、协调。
 * - **反向运动（Contrary）**：两个声部朝相反方向移动（一个上行、一个下行）。
 *   两个线条「擦肩」或「背离」，听感有张力、有对话感。
 * - **斜向运动（Oblique）**：一个声部保持同音不动（像踏板），另一个声部移动。
 *   一条线「站定」，另一条线在其上下滑动，听感有铺垫、有延展。
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.melodiccontour] 问「单条旋律的整体轮廓形状」（上/下/拱/谷/波浪）
 * - [com.pianocompanion.melodicdirectiontraining] 问「单个音程的方向」（上/下/同）
 * - [com.pianocompanion.intervaltraining] 问「单个音程的大小」
 * - 本模块问「**两条同时进行的声部**之间的相对运动关系」——维度独立
 *
 * 训练流程：
 * 1. 播放一段两个声部同时进行的短旋律（高音 + 低音，逐音对齐）
 * 2. 用户聆听两条线条是同向、反向还是斜向运动
 * 3. 从选项中选出正确的运动类型
 */

/**
 * 复调运动类型（辨识目标）。
 *
 * @param displayName 中文名
 * @param englishName 英文名
 * @param symbol 符号（如「⇈」「↕」「↑=」）
 * @param description 详细描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class MotionType(
    val displayName: String,
    val englishName: String,
    val symbol: String,
    val description: String,
    val hint: String
) {
    PARALLEL(
        displayName = "同向运动",
        englishName = "Parallel",
        symbol = "⇈",
        description = "同向运动（Parallel）：两个声部朝同一方向移动——同时上行或同时下行。" +
            "两条线条「并肩同行」，间距可能变化但方向一致，听感齐整、协调，" +
            "是和声进行中最常见的运动方式。",
        hint = "两个声音朝同一个方向走——像两人并肩同行"
    ),
    CONTRARY(
        displayName = "反向运动",
        englishName = "Contrary",
        symbol = "↕",
        description = "反向运动（Contrary）：两个声部朝相反方向移动——一个上行、一个下行。" +
            "两条线条「擦肩」或「背离」，间距拉开或收拢，听感富有张力与对话感，" +
            "是独立声部写作中最受推崇的运动方式。",
        hint = "一个声音往上走、另一个往下走——像两人擦肩而过"
    ),
    OBLIQUE(
        displayName = "斜向运动",
        englishName = "Oblique",
        symbol = "↑=",
        description = "斜向运动（Oblique）：一个声部保持同音不动（像踏板），另一个声部移动。" +
            "一条线「站定」，另一条线在其上方或下方滑动，听感有铺垫与延展，" +
            "常用于持续音（pedal point）或伴奏衬托主旋律。",
        hint = "一个声音保持不变、另一个在动——像一人站着、另一人在走动"
    );

    /** 完整标签（如「同向运动（Parallel）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 带符号的标签（如「⇈ 同向运动」）。 */
    val symbolLabel: String get() = "$symbol $displayName"

    companion object {
        /** 全部运动类型。 */
        val ALL: List<MotionType> = entries.toList()

        /** 初级难度候选：同向 vs 反向（最根本的协调 vs 张力二元对比）。 */
        val BEGINNER_MOTIONS: List<MotionType> = listOf(PARALLEL, CONTRARY)

        /** 中级难度候选：同向 / 反向 / 斜向（增加斜向运动）。 */
        val INTERMEDIATE_MOTIONS: List<MotionType> = listOf(PARALLEL, CONTRARY, OBLIQUE)

        /** 高级难度候选：全部 3 种运动（更小音程、更快速度）。 */
        val ADVANCED_MOTIONS: List<MotionType> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param motions 该难度可用的运动候选集
 * @param choiceCount 该难度的选项数量
 * @param noteCount 每个声部的音符数（如 4 个音符 = 3 个运动步骤）
 * @param stepPool 每步移动的候选半音数
 * @param noteDurationMs 每个音符的持续毫秒
 */
enum class MotionDifficulty(
    val displayName: String,
    val description: String,
    val motions: List<MotionType>,
    val choiceCount: Int,
    val noteCount: Int,
    val stepPool: List<Int>,
    val noteDurationMs: Int
) {
    BEGINNER(
        "初级",
        "同向 vs 反向 · 2 选项 · 大音程 · 慢速",
        MotionType.BEGINNER_MOTIONS,
        2,
        4,
        listOf(3, 4, 5),
        620
    ),
    INTERMEDIATE(
        "中级",
        "+ 斜向 · 3 选项 · 中音程 · 中速",
        MotionType.INTERMEDIATE_MOTIONS,
        3,
        4,
        listOf(2, 3, 4),
        500
    ),
    ADVANCED(
        "高级",
        "3 选 1 · 小音程 · 快速",
        MotionType.ADVANCED_MOTIONS,
        3,
        4,
        listOf(1, 2, 3),
        420
    );

    companion object {
        val ALL: List<MotionDifficulty> = entries.toList()
    }
}

/**
 * 复调运动辨识训练题目。
 *
 * @param motionType 正确的运动类型
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param upperVoice 高声部的 MIDI 音高序列
 * @param lowerVoice 低声部的 MIDI 音高序列（与高声部等长、逐音对齐）
 * @param answerChoices 所有选项（运动类型完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class MotionQuestion(
    val motionType: MotionType,
    val difficulty: MotionDifficulty,
    val seed: Long,
    val upperVoice: List<Int>,
    val lowerVoice: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 每个声部的音符数。 */
    val noteCount: Int get() = upperVoice.size

    /** 运动步数（音符数 - 1）。 */
    val motionSteps: Int get() = (upperVoice.size - 1).coerceAtLeast(0)

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${motionType.displayName}（${motionType.englishName} ${motionType.symbol}）"

    init {
        require(upperVoice.isNotEmpty()) { "高声部不能为空" }
        require(upperVoice.size == lowerVoice.size) {
            "高声部 (${upperVoice.size}) 与低声部 (${lowerVoice.size}) 长度必须相同"
        }
        require(motionType in difficulty.motions) {
            "运动类型 ${motionType.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(upperVoice.all { it in MIN_MIDI..MAX_MIDI }) {
            "高声部 $upperVoice 含超出 [$MIN_MIDI, $MAX_MIDI] 的 MIDI 值"
        }
        require(lowerVoice.all { it in MIN_MIDI..MAX_MIDI }) {
            "低声部 $lowerVoice 含超出 [$MIN_MIDI, $MAX_MIDI] 的 MIDI 值"
        }
        // 高声部应始终高于低声部（声部不交叉）
        upperVoice.indices.forEach { i ->
            require(upperVoice[i] > lowerVoice[i]) {
                "第 $i 个音符高声部 ${upperVoice[i]} 未高于低声部 ${lowerVoice[i]}（声部交叉）"
            }
        }
    }
}

/**
 * 一次答题结果。
 */
data class MotionAnswerRecord(
    val question: MotionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** MIDI 有效范围常量。 */
const val MIN_MIDI: Int = 0
const val MAX_MIDI: Int = 127
