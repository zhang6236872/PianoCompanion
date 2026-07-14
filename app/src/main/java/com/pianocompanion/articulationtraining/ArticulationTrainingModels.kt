package com.pianocompanion.articulationtraining

/**
 * 演奏法辨识训练（Articulation Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **演奏法（Articulation）**：描述音符的连接方式和起音特征——是流畅连贯的、
 *   短促分离的、保持完整的、强调重音的、还是半分离的。同一旋律用不同演奏法演奏，
 *   听感截然不同。
 * - 与力度辨识的区别：
 *   - **力度辨识（DynamicsTraining）**：所有音符音高节奏连接方式相同，唯一区分是**振幅（响度）**
 *   - **演奏法辨识（ArticulationTraining）**：所有音符的**音高和节奏（节拍间距）相同**，
 *     唯一区分依据是**音符的持续时间占比、起音速度和包络形状**
 *
 * 本模块支持的 5 种演奏法：
 *   1. LEGATO（连音）— 音符流畅衔接，微重叠，平滑起音
 *   2. STACCATO（断音）— 短促分离，仅占节拍的 30%，锐利起音
 *   3. TENUTO（保持音）— 充分保持，几乎占满节拍，柔和持续
 *   4. MARCATO（重音）— 强调重音，锐利起音后快速衰减
 *   5. PORTATO（次断音）— 半分离，占节拍 65%，轻柔衔接
 */

/**
 * 演奏法类型。
 *
 * @param symbol 标准记谱符号（如 "—"、"."）
 * @param englishName 英文名（如 "Legato"）
 * @param displayName 中文名（如 "连音"）
 * @param durationRatio 音符持续时间占节拍间距的比例（0.0-1.5，>1.0 表示微重叠）
 * @param attackMs 起音时间（毫秒）——从 0 到满幅的线性上升时长
 * @param decayTimeConstantMs 指数衰减时间常数（毫秒）——越大衰减越慢、越持续
 * @param accent 起音强调系数（0.0-1.0）——0.0=无强调，1.0=极强重音
 * @param description 听感描述（答题后的教学反馈）
 */
enum class ArticulationType(
    val symbol: String,
    val englishName: String,
    val displayName: String,
    val durationRatio: Float,
    val attackMs: Double,
    val decayTimeConstantMs: Double,
    val accent: Float,
    val description: String
) {
    LEGATO(
        symbol = "—",
        englishName = "Legato",
        displayName = "连音",
        durationRatio = 1.05f,
        attackMs = 25.0,
        decayTimeConstantMs = 400.0,
        accent = 0.0f,
        description = "Legato（连音 —）：音符之间流畅连贯，如歌般一气呵成。" +
            "每个音符平滑衔接上一个，没有明显的间隙。"
    ),
    STACCATO(
        symbol = ".",
        englishName = "Staccato",
        displayName = "断音",
        durationRatio = 0.30f,
        attackMs = 2.0,
        decayTimeConstantMs = 80.0,
        accent = 0.1f,
        description = "Staccato（断音 ·）：音符短促而分离，像水滴一样干脆利落。" +
            "每个音符之间有明显的停顿和间隙。"
    ),
    TENUTO(
        symbol = "▰",
        englishName = "Tenuto",
        displayName = "保持音",
        durationRatio = 0.95f,
        attackMs = 12.0,
        decayTimeConstantMs = 350.0,
        accent = 0.0f,
        description = "Tenuto（保持音 ▰）：音符充分保持其完整时值，温和而沉稳。" +
            "既不像连音那样流畅衔接，也不像断音那样急促。"
    ),
    MARCATO(
        symbol = "^",
        englishName = "Marcato",
        displayName = "重音",
        durationRatio = 0.75f,
        attackMs = 1.0,
        decayTimeConstantMs = 120.0,
        accent = 0.7f,
        description = "Marcato（重音 ^）：每个音符都被强烈强调，如同敲击般有力。" +
            "起音极为锐利，带有明显的重音冲击感。"
    ),
    PORTATO(
        symbol = "–",
        englishName = "Portato",
        displayName = "次断音",
        durationRatio = 0.65f,
        attackMs = 8.0,
        decayTimeConstantMs = 200.0,
        accent = 0.05f,
        description = "Portato（次断音 –）：介于连音与断音之间，音符半分离半连贯。" +
            "每个音符轻柔地起音后部分衰减，如同温柔的叹息。"
    );

    init {
        check(durationRatio in 0.05f..1.5f) {
            "$englishName: durationRatio=$durationRatio 超出 [0.05,1.5] 范围"
        }
        check(attackMs >= 0.0) { "$englishName: attackMs=$attackMs 不能为负" }
        check(decayTimeConstantMs > 0.0) {
            "$englishName: decayTimeConstantMs=$decayTimeConstantMs 必须为正"
        }
        check(accent in 0.0f..1.0f) { "$englishName: accent=$accent 超出 [0,1] 范围" }
    }

    /** 完整标识（如 "—  连音"）。 */
    val fullLabel: String get() = "$symbol  $displayName"

    /** 摘要（如 "— Legato 连音"）。 */
    val summary: String get() = "$symbol $englishName $displayName"

    companion object {
        val ALL: List<ArticulationType> = entries.toList()

        /** 初级演奏法：最大对比（连音/断音/重音）。 */
        val BEGINNER_ARTICULATIONS: List<ArticulationType> = listOf(LEGATO, STACCATO, MARCATO)

        /** 中级演奏法：加入保持音（区分连音 vs 保持音）。 */
        val INTERMEDIATE_ARTICULATIONS: List<ArticulationType> =
            listOf(LEGATO, STACCATO, TENUTO, MARCATO)

        /**
         * 按难度返回可用演奏法集合。
         * - 初级：3 种最大对比（连音/断音/重音）
         * - 中级：4 种（加入保持音，区分连音 vs 保持音）
         * - 高级：全部 5 种（含次断音，最考验精细辨识）
         */
        fun forDifficulty(difficulty: ArticulationTrainingDifficulty): List<ArticulationType> =
            when (difficulty) {
                ArticulationTrainingDifficulty.BEGINNER -> BEGINNER_ARTICULATIONS
                ArticulationTrainingDifficulty.INTERMEDIATE -> INTERMEDIATE_ARTICULATIONS
                ArticulationTrainingDifficulty.ADVANCED -> ALL
            }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和演奏法范围）
 * @param choiceCount 该难度的选项数量
 */
enum class ArticulationTrainingDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种最大对比（3 选项）· 连音 / 断音 / 重音", 3),
    INTERMEDIATE("中级", "4 种（4 选项）· 加入保持音，区分连音 vs 保持音", 4),
    ADVANCED("高级", "全部 5 种（5 选项）· 加入次断音，最考验精细辨识", 5);

    companion object {
        val ALL: List<ArticulationTrainingDifficulty> = entries.toList()
    }
}

/**
 * 演奏法辨识训练题目。
 *
 * @param articulation 正确的演奏法类型
 * @param difficulty 难度
 * @param noteCount 播放的音符数量
 * @param answerChoices 所有选项（符号+中文名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class ArticulationTrainingQuestion(
    val articulation: ArticulationType,
    val difficulty: ArticulationTrainingDifficulty,
    val noteCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "— Legato 连音"）。 */
    val fullName: String get() = articulation.summary
}

/**
 * 一次答题结果。
 */
data class ArticulationTrainingAnswerRecord(
    val question: ArticulationTrainingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
