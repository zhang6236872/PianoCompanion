package com.pianocompanion.polyrhythmtraining

/**
 * 复合节奏辨识训练（Polyrhythm Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **复合节奏（Polyrhythm）**：两条独立的节奏线在同一时间段内以不同等分数同时演奏。
 *   例如 2:3 表示一条线奏 2 个等距音符、另一条线奏 3 个等距音符，二者恰好同时开始
 *   同时结束。复合节奏在爵士、非洲音乐、现代古典和流行乐中极为常见。
 * - 与节奏型听辨的区别：
 *   - **节奏型听辨（RhythmPattern）**：单声部、连续的节奏型序列
 *   - **复合节奏辨识（Polyrhythm）**：**两条声部同时演奏**不同的等分数，考验对
 *     节奏层次、交叉节奏（cross-rhythm）的感知能力
 *
 * 本模块支持的 5 种复合节奏比：
 *   1. 2:3 — 二对三（最基础、最常见，"三连音对八分音符"）
 *   2. 3:4 — 三对四
 *   3. 4:5 — 四对五
 *   4. 2:5 — 二对五
 *   5. 3:5 — 三对五
 *
 * 音频约定：
 * - 高音声部（880 Hz）奏 [highCount] 个等距音符
 * - 低音声部（440 Hz）奏 [lowCount] 个等距音符
 * - 两条声部同时开始、同时结束，跨越同一个周期
 */

/**
 * 复合节奏类型。
 *
 * @param highCount 高音声部的等距音符数
 * @param lowCount 低音声部的等距音符数
 * @param displayName 中文名（如 "二对三"）
 * @param ratioText 比例文本（如 "2 : 3"）
 * @param description 听感描述（答题后的教学反馈）
 */
enum class PolyrhythmType(
    val highCount: Int,
    val lowCount: Int,
    val displayName: String,
    val ratioText: String,
    val description: String
) {
    TWO_THREE(
        highCount = 2,
        lowCount = 3,
        displayName = "二对三",
        ratioText = "2 : 3",
        description = "二对三（2:3）是最基本的复合节奏。高音声部奏 2 个等距音符，" +
            "低音声部奏 3 个等距音符。常见于爵士和拉丁音乐中的\"三连音对八分音符\"。"
    ),
    THREE_FOUR(
        highCount = 3,
        lowCount = 4,
        displayName = "三对四",
        ratioText = "3 : 4",
        description = "三对四（3:4）在浪漫派和现代音乐中经常出现。高音 3 个、低音 4 个等距音符，" +
            "形成密集的交叉节奏。"
    ),
    FOUR_FIVE(
        highCount = 4,
        lowCount = 5,
        displayName = "四对五",
        ratioText = "4 : 5",
        description = "四对五（4:5）是较高级的复合节奏。4 和 5 互质，两条声部的交叉点极为密集，" +
            "听感复杂而富有张力。"
    ),
    TWO_FIVE(
        highCount = 2,
        lowCount = 5,
        displayName = "二对五",
        ratioText = "2 : 5",
        description = "二对五（2:5）中低音声部密集地奏 5 个音符，而高音声部只奏 2 个，" +
            "形成鲜明的密度对比。"
    ),
    THREE_FIVE(
        highCount = 3,
        lowCount = 5,
        displayName = "三对五",
        ratioText = "3 : 5",
        description = "三对五（3:5）是高级复合节奏。3 和 5 互质，两条声部的等距分割形成" +
            "精致的节奏织体，辨识难度较高。"
    );

    init {
        check(highCount >= 2) { "$displayName: highCount=$highCount 必须 >= 2" }
        check(lowCount >= 2) { "$displayName: lowCount=$lowCount 必须 >= 2" }
    }

    /** 高低音比例的乘积（用于衡量复杂度）。 */
    val complexity: Int get() = highCount * lowCount

    /** 完整标识（如 "2 : 3  二对三"）。 */
    val fullLabel: String get() = "$ratioText  $displayName"

    /** 摘要（如 "2:3 二对三"）。 */
    val summary: String get() = "${highCount}:${lowCount} $displayName"

    companion object {
        val ALL: List<PolyrhythmType> = entries.toList()

        /** 初级复合节奏：最基础的两种（2:3 和 3:4），差异明显。 */
        val BEGINNER_POLYRHYTHMS: List<PolyrhythmType> = listOf(TWO_THREE, THREE_FOUR)

        /** 中级复合节奏：加入 2:5（密度对比鲜明的复合节奏）。 */
        val INTERMEDIATE_POLYRHYTHMS: List<PolyrhythmType> = listOf(TWO_THREE, THREE_FOUR, TWO_FIVE)

        /**
         * 按难度返回可用复合节奏集合。
         * - 初级：2 种最基础（2:3 / 3:4）
         * - 中级：3 种（加入 2:5）
         * - 高级：全部 5 种（含 4:5、3:5，最考验精细辨识）
         */
        fun forDifficulty(difficulty: PolyrhythmDifficulty): List<PolyrhythmType> =
            when (difficulty) {
                PolyrhythmDifficulty.BEGINNER -> BEGINNER_POLYRHYTHMS
                PolyrhythmDifficulty.INTERMEDIATE -> INTERMEDIATE_POLYRHYTHMS
                PolyrhythmDifficulty.ADVANCED -> ALL
            }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和复合节奏范围）
 * @param choiceCount 该难度的选项数量
 */
enum class PolyrhythmDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "2 种基础复合节奏（2 选项）· 2:3 / 3:4", 2),
    INTERMEDIATE("中级", "3 种（3 选项）· 加入 2:5，密度对比鲜明", 3),
    ADVANCED("高级", "全部 5 种（5 选项）· 加入 4:5、3:5，最考验精细辨识", 5);

    companion object {
        val ALL: List<PolyrhythmDifficulty> = entries.toList()
    }
}

/**
 * 复合节奏辨识训练题目。
 *
 * @param polyrhythm 正确的复合节奏类型
 * @param difficulty 难度
 * @param cycleCount 播放的周期数（每个周期内两条声部同时完成各自的等分）
 * @param answerChoices 所有选项（比例+中文名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class PolyrhythmQuestion(
    val polyrhythm: PolyrhythmType,
    val difficulty: PolyrhythmDifficulty,
    val cycleCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "2:3 二对三"）。 */
    val fullName: String get() = polyrhythm.summary
}

/**
 * 一次答题结果。
 */
data class PolyrhythmAnswerRecord(
    val question: PolyrhythmQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
