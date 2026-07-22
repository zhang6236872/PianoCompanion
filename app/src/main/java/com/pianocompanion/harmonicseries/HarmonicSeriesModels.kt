package com.pianocompanion.harmonicseries

/**
 * 泛音列辨识训练（Harmonic Series Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **泛音列（Harmonic Series / Overtone Series）**：任何乐音都由基频（fundamental）和一系列
 *   整数倍频率的泛音（harmonics / overtones）组成：f、2f、3f、4f、5f …。这些泛音之间的
 *   音程逐渐缩小——八度 → 纯五度 → 纯四度 → 大三度 → 小三度 → 小三度 → … → 半音——
 *   构成了自然界最基本的音高结构。辨识泛音列中各泛音的位置是理解音色、和声与调性的核心能力。
 *
 * 训练流程：
 * 1. 播放基频（含泛音的复合音色，听起来像自然乐器）
 * 2. 短暂静默后播放一个纯音泛音（该基频的某一阶泛音）
 * 3. 用户判断这个纯音是基频的第几泛音（2-8）
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.timbrebrightness] 问「音色有多少泛音」（数量/丰富度）
 * - 本模块问「这个泛音在泛音列中排第几」——定位泛音的位置（频率比感知）
 */

/**
 * 泛音阶数（辨识目标——用户判断播放的纯音是第几泛音）。
 *
 * 使用 A2 = 110 Hz 为基频示例（便于计算）：
 * - 2nd: 220 Hz → A3（八度）
 * - 3rd: 330 Hz → E4（纯五度）
 * - 4th: 440 Hz → A4（纯四度）
 * - 5th: 550 Hz → C#5（大三度）
 * - 6th: 660 Hz → E5（小三度）
 * - 7th: 770 Hz → F#5/G5（小七度，约 −31 cents 偏低）
 * - 8th: 880 Hz → A5（小三度→趋于半音）
 *
 * 注意：泛音频率为基频的精确整数倍（纯律），不等于十二平均律的近似值。
 * 特别是第 7 泛音在平均律中介于 F#5 和 G5 之间（约 −31 cents）。
 *
 * @param number 泛音阶数（2-8）
 * @param displayName 中文名
 * @param englishName 英文名
 * @param intervalName 该泛音与前一个泛音之间的音程名称
 * @param ratio 与基频的频率比（即泛音阶数本身）
 */
enum class HarmonicNumber(
    val number: Int,
    val displayName: String,
    val englishName: String,
    val intervalName: String,
    val ratio: Double
) {
    SECOND(2, "第2泛音", "2nd Harmonic", "八度", 2.0),
    THIRD(3, "第3泛音", "3rd Harmonic", "纯五度", 3.0),
    FOURTH(4, "第4泛音", "4th Harmonic", "纯四度", 4.0),
    FIFTH(5, "第5泛音", "5th Harmonic", "大三度", 5.0),
    SIXTH(6, "第6泛音", "6th Harmonic", "小三度", 6.0),
    SEVENTH(7, "第7泛音", "7th Harmonic", "小七度(偏低)", 7.0),
    EIGHTH(8, "第8泛音", "8th Harmonic", "小三度(趋于半音)", 8.0);

    /** 完整标签（如「第3泛音（3rd Harmonic）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 教学描述（如「第3泛音 · 纯五度 · 基频的3倍频率」）。 */
    val teachingDescription: String
        get() = "$displayName · $intervalName · 基频的${number}倍频率"

    companion object {
        /** 初级难度使用的泛音（第 2、3 泛音——最大、最易区分的音程）。 */
        val BEGINNER_HARMONICS: List<HarmonicNumber> = listOf(SECOND, THIRD)

        /** 中级难度使用的泛音（第 2-5 泛音——增加纯四度、大三度）。 */
        val INTERMEDIATE_HARMONICS: List<HarmonicNumber> = listOf(SECOND, THIRD, FOURTH, FIFTH)

        /** 高级难度使用的泛音（第 2-8 泛音——增加小三度、小七度等微小音程）。 */
        val ADVANCED_HARMONICS: List<HarmonicNumber> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 选项数量
 * @param harmonics 该难度使用的泛音集合
 * @param fundamentalMidi 基频 MIDI 音高
 * @param fundamentalDurationMs 基频播放时长（毫秒）
 * @param harmonicDurationMs 泛音播放时长（毫秒）
 * @param gapMs 基频与泛音之间的间隔（毫秒）
 */
enum class HarmonicDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val harmonics: List<HarmonicNumber>,
    val fundamentalMidi: Int,
    val fundamentalDurationMs: Int,
    val harmonicDurationMs: Int,
    val gapMs: Int
) {
    BEGINNER(
        "初级",
        "第2-3泛音 · 2选项",
        choiceCount = 2,
        harmonics = HarmonicNumber.BEGINNER_HARMONICS,
        fundamentalMidi = 48, // C3
        fundamentalDurationMs = 900,
        harmonicDurationMs = 900,
        gapMs = 400
    ),
    INTERMEDIATE(
        "中级",
        "第2-5泛音 · 3选项",
        choiceCount = 3,
        harmonics = HarmonicNumber.INTERMEDIATE_HARMONICS,
        fundamentalMidi = 48, // C3
        fundamentalDurationMs = 800,
        harmonicDurationMs = 800,
        gapMs = 350
    ),
    ADVANCED(
        "高级",
        "第2-8泛音 · 4选项",
        choiceCount = 4,
        harmonics = HarmonicNumber.ADVANCED_HARMONICS,
        fundamentalMidi = 48, // C3
        fundamentalDurationMs = 700,
        harmonicDurationMs = 700,
        gapMs = 300
    );

    companion object {
        val ALL: List<HarmonicDifficulty> = entries.toList()
    }
}

/**
 * 泛音列辨识训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param targetHarmonic 正确的泛音阶数
 * @param answerChoices 所有选项标签（含正确答案，已打乱）
 * @param correctAnswer 正确答案标签
 */
data class HarmonicSeriesQuestion(
    val difficulty: HarmonicDifficulty,
    val seed: Long,
    val targetHarmonic: HarmonicNumber,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(targetHarmonic in difficulty.harmonics) {
            "目标泛音 ${targetHarmonic.displayName} 不在难度 ${difficulty.displayName} 的泛音集合中"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(answerChoices.distinct().size == answerChoices.size) {
            "选项存在重复"
        }
        require(answerChoices.size == difficulty.choiceCount) {
            "选项数 (${answerChoices.size}) 与难度配置 (${difficulty.choiceCount}) 不一致"
        }
    }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String get() = targetHarmonic.teachingDescription

    /** 基频的 MIDI 音高。 */
    val fundamentalMidi: Int get() = difficulty.fundamentalMidi
}

/**
 * 一次答题结果。
 */
data class HarmonicAnswerRecord(
    val question: HarmonicSeriesQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
