package com.pianocompanion.melodicdirectiontraining

/**
 * 旋律方向辨识训练（Melodic Direction Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **旋律方向辨识（Melodic Direction Recognition）**：用户听到一段 4 音符旋律，
 *   需要根据音高变化趋势判断旋律的走向（上行/下行/平行/拱形/V形）。
 * - 与音程训练的区别：
 *   - **音程训练（IntervalTraining）**：关注两个音之间的精确距离（半音数）
 *   - **旋律方向辨识（MelodicDirectionTraining）**：关注**整体旋律轮廓**
 *     （contour），训练对旋律走向的感知能力——即"线形/形状"而非具体音程大小
 *
 * 本模块支持 5 种旋律方向：
 *   1. ASCENDING（上行 ↑）— 音符持续升高
 *   2. DESCENDING（下行 ↓）— 音符持续降低
 *   3. STATIC（平行 →）— 音符保持不变
 *   4. ARCH（拱形 ∩）— 先升后降（山峰形）
 *   5. V_SHAPE（V形 ∪）— 先降后升（山谷形）
 */

/**
 * 旋律方向类型（旋律方向辨识目标）。
 *
 * 每种方向定义一组半音偏移量（相对于起始音），描述旋律的音高轮廓。
 *
 * @param symbol 方向符号（如 "↑"）
 * @param displayName 中文名（如 "上行"）
 * @param emoji 表情符号（UI 图标）
 * @param description 方向描述（答题后的教学反馈）
 * @param semitoneOffsets 每个音符相对于起始音的半音偏移量
 */
enum class MelodicDirection(
    val symbol: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val semitoneOffsets: IntArray
) {
    ASCENDING(
        symbol = "↑",
        displayName = "上行",
        emoji = "📈",
        description = "上行旋律：音符持续升高，如同拾级而上。旋律线向上延伸，给人紧张、上升、期待的感觉。",
        semitoneOffsets = intArrayOf(0, 2, 4, 7) // C-D-E-G，大二度大二度小三度
    ),
    DESCENDING(
        symbol = "↓",
        displayName = "下行",
        emoji = "📉",
        description = "下行旋律：音符持续降低，如同顺流而下。旋律线向下延伸，给人放松、舒缓、释放的感觉。",
        semitoneOffsets = intArrayOf(7, 4, 2, 0) // G-E-D-C，反向上行
    ),
    STATIC(
        symbol = "→",
        displayName = "平行",
        emoji = "➡️",
        description = "平行旋律：所有音符音高相同，如同静止的湖面。旋律线保持水平，营造稳定、重复、催眠的效果。",
        semitoneOffsets = intArrayOf(0, 0, 0, 0) // C-C-C-C
    ),
    ARCH(
        symbol = "∩",
        displayName = "拱形",
        emoji = "⛰️",
        description = "拱形旋律：先升后降，如同山峰。旋律先上行到达最高点后折返下行，是最常见的旋律轮廓之一。",
        semitoneOffsets = intArrayOf(0, 4, 7, 4) // C-E-G-E
    ),
    V_SHAPE(
        symbol = "∪",
        displayName = "V形",
        emoji = " valleys",
        description = "V形旋律：先降后升，如同山谷。旋律先下行到达最低点后折返上行，营造下沉后回升的戏剧效果。",
        semitoneOffsets = intArrayOf(7, 4, 0, 4) // G-E-C-E
    );

    /** 完整标识（如 "📈 上行"）。 */
    val fullLabel: String get() = "$emoji $displayName"

    /** 摘要（如 "上行 ↑"）。 */
    val summary: String get() = "$displayName $symbol"

    /** 音符数量（固定为 4）。 */
    val noteCount: Int get() = semitoneOffsets.size

    companion object {
        val ALL: List<MelodicDirection> = entries.toList()

        /** 初级方向：上行、下行、平行（差异最大，凭直觉即可区分）。 */
        val BEGINNER_DIRECTIONS: List<MelodicDirection> = listOf(ASCENDING, DESCENDING, STATIC)

        /** 中级方向：上行、下行、平行、拱形（加入先升后降）。 */
        val INTERMEDIATE_DIRECTIONS: List<MelodicDirection> = listOf(ASCENDING, DESCENDING, STATIC, ARCH)

        /**
         * 按难度返回可用方向集合。
         * - 初级：3 种基础方向（上行/下行/平行），差异最明显
         * - 中级：4 种方向，加入拱形（先升后降）
         * - 高级：全部 5 种，加入 V 形（先降后升），考验拱形 vs V形区分
         */
        fun forDifficulty(difficulty: MelodicDirectionDifficulty): List<MelodicDirection> = when (difficulty) {
            MelodicDirectionDifficulty.BEGINNER -> BEGINNER_DIRECTIONS
            MelodicDirectionDifficulty.INTERMEDIATE -> INTERMEDIATE_DIRECTIONS
            MelodicDirectionDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和方向列表）
 * @param choiceCount 该难度的选项数量
 */
enum class MelodicDirectionDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种方向（3 选项）· 上行 / 下行 / 平行", 3),
    INTERMEDIATE("中级", "4 种方向（4 选项）· 加入拱形（先升后降）", 4),
    ADVANCED("高级", "全部 5 种（5 选项）· 含 V 形（先降后升）区分", 5);

    companion object {
        val ALL: List<MelodicDirectionDifficulty> = entries.toList()
    }
}

/**
 * 旋律方向辨识训练题目。
 *
 * @param direction 正确的旋律方向类型
 * @param difficulty 难度
 * @param noteCount 播放的音符数量
 * @param answerChoices 所有选项（方向名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class MelodicDirectionQuestion(
    val direction: MelodicDirection,
    val difficulty: MelodicDirectionDifficulty,
    val noteCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "上行 ↑"）。 */
    val fullName: String get() = direction.summary
}

/**
 * 一次答题结果。
 */
data class MelodicDirectionAnswerRecord(
    val question: MelodicDirectionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
