package com.pianocompanion.motiftransformation

/** MIDI 有效范围下限。 */
const val MIN_MIDI: Int = 48   // C3
/** MIDI 有效范围上限。 */
const val MAX_MIDI: Int = 84   // C6
/** 动机生成的音高范围下限（保证变换后有余量）。 */
const val MIN_MOTIF_MIDI: Int = 55   // G3
/** 动机生成的音高范围上限。 */
const val MAX_MOTIF_MIDI: Int = 79   // G5

/**
 * 动机发展辨识训练（Motif Transformation Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **动机发展（Motif Development）**：作曲中最核心的技巧——取一个短小的旋律动机，
 *   通过各种变换手法发展出新的音乐材料。辨识这些变换类型是音乐分析与听力的核心能力。
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.sequencerecognition] 问「旋律中是否有重复/模进模式」
 * - [com.pianocompanion.melodiccontour] 问「单条旋律的整体轮廓形状」
 * - 本模块问「动机被做了**哪种变换**」——先播放原始动机，再播放变换版本，
 *   用户判断变换类型（重复 / 模进 / 倒影 / 逆行 / 节奏扩张 / 节奏紧缩）
 *
 * 6 种动机变换类型（辨识目标）：
 * - **重复（Repetition）**：原样重复
 * - **模进（Sequence）**：整体移高或移低
 * - **倒影（Inversion）**：音程方向反转（上行变下行）
 * - **逆行（Retrograde）**：从后往前演奏
 * - **节奏扩张（Augmentation）**：时值加倍（变慢）
 * - **节奏紧缩（Diminution）**：时值减半（变快）
 *
 * 训练流程：
 * 1. 播放原始动机（短旋律）
 * 2. 间隔后播放变换版本
 * 3. 用户聆听两段，判断动机做了哪种变换
 * 4. 从选项中选出正确的变换类型
 */

/**
 * 动机变换类型（辨识目标）。
 *
 * @param symbol 符号
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param description 详细描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class MotifTransformation(
    val symbol: String,
    val displayName: String,
    val englishName: String,
    val description: String,
    val hint: String
) {
    REPETITION(
        symbol = "⟳",
        displayName = "重复",
        englishName = "Repetition",
        description = "重复（Repetition）：动机原封不动地再次出现——音高、节奏完全相同。" +
            "这是最基本的发展手法，在乐曲中起到巩固记忆、建立熟悉感的作用。",
        hint = "两段听起来完全一样——相同的旋律、相同的节奏"
    ),
    SEQUENCE(
        symbol = "↗",
        displayName = "模进",
        englishName = "Sequence",
        description = "模进（Sequence）：动机整体移高或移低——旋律轮廓不变，但起始音高不同。" +
            "模进是音乐中最常见的发展手法之一，给人一种「旋律在移动」的感觉。",
        hint = "旋律的「形状」一样，但整体移高了或移低了"
    ),
    INVERSION(
        symbol = "⇅",
        displayName = "倒影",
        englishName = "Inversion",
        description = "倒影（Inversion）：动机的音程方向反转——上行变下行、下行变上行，如同水面倒影。" +
            "第一音保持不变，之后的进行方向全部相反。",
        hint = "旋律的「轮廓」上下翻转了——原本上行的变成下行"
    ),
    RETROGRADE(
        symbol = "↔",
        displayName = "逆行",
        englishName = "Retrograde",
        description = "逆行（Retrograde）：动机从后往前演奏——最后一个音变成第一个音，整条旋律倒着走。" +
            "这是一种精巧的发展手法，常见于复调音乐（如巴赫赋格）。",
        hint = "旋律从最后一个音开始往前倒着演奏"
    ),
    AUGMENTATION(
        symbol = "⊕",
        displayName = "节奏扩张",
        englishName = "Augmentation",
        description = "节奏扩张（Augmentation）：动机的每个音符时值加倍——旋律变慢、变宽，如同放慢镜头。" +
            "音高不变，但节奏被拉长了。",
        hint = "同样的旋律，但速度变慢了——每个音都拉长了"
    ),
    DIMINUTION(
        symbol = "⊖",
        displayName = "节奏紧缩",
        englishName = "Diminution",
        description = "节奏紧缩（Diminution）：动机的每个音符时值减半——旋律变快、变紧凑，如同快进。" +
            "音高不变，但节奏被压缩了。",
        hint = "同样的旋律，但速度变快了——每个音都缩短了"
    );

    /** 完整标签（如「模进（Sequence）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 带符号的标签（如「↗ 模进」）。 */
    val symbolLabel: String get() = "$symbol $displayName"

    companion object {
        /** 全部变换类型。 */
        val ALL: List<MotifTransformation> =
            listOf(REPETITION, SEQUENCE, INVERSION, RETROGRADE, AUGMENTATION, DIMINUTION)

        /** 初级候选：重复 vs 模进（最根本的「相同 vs 移动」对比）。 */
        val BEGINNER_CANDIDATES: List<MotifTransformation> = listOf(REPETITION, SEQUENCE)

        /** 中级候选：+ 倒影 / 逆行（增加音高变换维度）。 */
        val INTERMEDIATE_CANDIDATES: List<MotifTransformation> =
            listOf(REPETITION, SEQUENCE, INVERSION, RETROGRADE)

        /** 高级候选：全部 6 种变换。 */
        val ADVANCED_CANDIDATES: List<MotifTransformation> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param candidates 该难度可用的变换候选集
 * @param choiceCount 该难度的选项数量
 * @param baseNoteMs 基础音符时值（毫秒）——高级更快
 */
enum class MotifTransformationDifficulty(
    val displayName: String,
    val description: String,
    val candidates: List<MotifTransformation>,
    val choiceCount: Int,
    val baseNoteMs: Double
) {
    BEGINNER(
        "初级",
        "重复 vs 模进 · 2 选项 · 慢速",
        MotifTransformation.BEGINNER_CANDIDATES,
        2,
        380.0
    ),
    INTERMEDIATE(
        "中级",
        "+ 倒影 / 逆行 · 4 选项 · 中速",
        MotifTransformation.INTERMEDIATE_CANDIDATES,
        4,
        320.0
    ),
    ADVANCED(
        "高级",
        "+ 节奏扩张 / 紧缩 · 6 选项 · 快速",
        MotifTransformation.ADVANCED_CANDIDATES,
        6,
        280.0
    );

    companion object {
        val ALL: List<MotifTransformationDifficulty> = entries.toList()
    }
}

/**
 * 动机中的单个音符。
 *
 * @param midi MIDI 音高
 * @param durationMs 持续时间（毫秒）
 */
data class MotifNote(
    val midi: Int,
    val durationMs: Double
) {
    init {
        require(midi in MIN_MIDI..MAX_MIDI) {
            "MIDI 音高 $midi 超出范围 [$MIN_MIDI, $MAX_MIDI]"
        }
        require(durationMs > 0) {
            "音符时值必须为正数: $durationMs"
        }
    }
}

/**
 * 动机发展辨识训练题目。
 *
 * @param transformation 正确的变换类型
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param originalNotes 原始动机音符序列
 * @param transformedNotes 变换后动机音符序列
 * @param answerChoices 所有选项（变换类型完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class MotifTransformationQuestion(
    val transformation: MotifTransformation,
    val difficulty: MotifTransformationDifficulty,
    val seed: Long,
    val originalNotes: List<MotifNote>,
    val transformedNotes: List<MotifNote>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 原始动机的 MIDI 音高列表。 */
    val originalPitches: List<Int> get() = originalNotes.map { it.midi }

    /** 变换后动机的 MIDI 音高列表。 */
    val transformedPitches: List<Int> get() = transformedNotes.map { it.midi }

    /** 原始动机的时值列表。 */
    val originalDurations: List<Double> get() = originalNotes.map { it.durationMs }

    /** 变换后动机的时值列表。 */
    val transformedDurations: List<Double> get() = transformedNotes.map { it.durationMs }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${transformation.symbolLabel} · ${transformation.englishName}"

    init {
        require(originalNotes.isNotEmpty()) { "原始动机不能为空" }
        require(transformedNotes.isNotEmpty()) { "变换后动机不能为空" }
        require(transformation in difficulty.candidates) {
            "变换类型 ${transformation.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
    }
}

/**
 * 一次答题结果。
 */
data class MotifTransformationAnswerRecord(
    val question: MotifTransformationQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
