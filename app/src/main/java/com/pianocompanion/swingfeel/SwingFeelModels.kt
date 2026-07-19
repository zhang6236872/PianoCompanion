package com.pianocompanion.swingfeel

/**
 * 摇摆感辨识训练（Swing Feel Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **摇摆感（Swing Feel）**：在等分的节拍框架内，把每拍的两个八分音符弹成「长短」不均的形式，
 *   形成「长—短」的律动摇摆。这是爵士、布鲁斯、摇摆乐以及大量流行/民谣风格的灵魂。
 *   与「等分（straight）」的整齐八分音符形成鲜明对比。
 * - 本模块训练**感知一拍内两个音符的时间比例**：是 1:1（等分/straight）、约 3:2（轻摇摆），
 *   还是 2:1（经典三连音摇摆）。这是一种纯粹的「时间感 / groove」听辨能力。
 *
 * 与已有训练模块的区别：
 * - **节奏细分（[com.pianocompanion.subdivisionrecognition]）**：判断一拍被**精确均分**成 2/3/4 等份
 *   ——答案是「等分的份数」，所有音符间距完全相同。
 * - **拍号识别（[com.pianocompanion.meterrecognition]）**：判断每小节几拍——小节层面的分组。
 * - **强拍辨识（[com.pianocompanion.accentrecognition]）**：判断重音落在第几拍——强拍位置感知。
 * - **本模块（摇摆感）**：每拍固定两个音符、不问拍号、不问重音，只问**一拍内两个音符的时间比例**，
 *   答案是「等分 / 轻摇摆 / 摇摆」。所有音符音高相同、音量相同，唯一线索是「长短」的律动。
 *
 * 训练流程：
 * 1. 播放一段由若干拍（每拍两个八分音符）构成的节奏，所有音符同音高同音量。
 * 2. 用户聆听后，判断这段节奏的「摇摆感」。
 * 3. 从「等分 / 轻摇摆 / 摇摆」选项中选出正确答案。
 */

/**
 * 摇摆感类型（一拍内两个八分音符的时间比例）。
 *
 * 设每拍时长为 B，第一个音符在拍头，第二个音符在拍头后 [fraction]×B 处。
 * - 等分：fraction = 0.5 → 两音间距相等（各 0.5B）。
 * - 轻摇摆：fraction ≈ 0.60 → 长短比约 3:2。
 * - 摇摆：fraction = 2/3 ≈ 0.667 → 长短比 2:1（经典三连音摇摆）。
 *
 * @param displayName 中文显示名
 * @param fraction 第二个音符在一拍中的时间占比（0.5–0.667）
 * @param ratioLabel 长短比文字标签（用于 UI/教学）
 * @param description 听感描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class SwingRatio(
    val displayName: String,
    val fraction: Double,
    val ratioLabel: String,
    val description: String,
    val hint: String
) {
    /** 等分：两个八分音符完全均等（1:1），整齐、 marching 感。 */
    STRAIGHT(
        displayName = "等分",
        fraction = 0.50,
        ratioLabel = "1:1",
        description = "每个八分音符的间距完全相等，整齐如行进 —— 这是流行、摇滚、古典中常见的「直」八分音符。",
        hint = "听起来均匀整齐，像钟表滴答，没有任何「颠簸」。"
    ),

    /** 轻摇摆：长短比约 3:2，带轻微律动，常见于 shuffle、乡村、轻爵士。 */
    LIGHT_SWING(
        displayName = "轻摇摆",
        fraction = 0.60,
        ratioLabel = "3:2",
        description = "每拍的两个音开始有一点长短差别（约 3:2），能感到轻微的「颠簸」律动，常见于 shuffle 与轻快爵士。",
        hint = "有一点拖曳的律动感，第一个音稍长、第二个音稍短，但不夸张。"
    ),

    /** 摇摆：长短比 2:1，经典三连音摇摆，爵士/布鲁斯的标志律动。 */
    SWING(
        displayName = "摇摆",
        fraction = 2.0 / 3.0,
        ratioLabel = "2:1",
        description = "每拍的第一个音明显较长、第二个音较短（2:1），形成标志性的「长—短」爵士/布鲁斯摇摆律动。",
        hint = "强烈的「长—短、长—短」摇摆感，像在跳舞，这是经典 jazz swing。"
    );

    companion object {
        val ALL: List<SwingRatio> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * 难度通过**选项数（候选摇摆感种类）+ 速度**两个维度递进：
 * - 初级：等分 vs 摇摆（2 选项，对比最鲜明）· 慢速 80 BPM。
 * - 中级：等分 / 轻摇摆 / 摇摆（3 选项，加入难以判别的轻摇摆）· 中速 100 BPM。
 * - 高级：3 选项（含轻摇摆）· 快速 130 BPM（音符密集，更难解析长短比例）。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param candidateRatios 本难度可能的摇摆感集合（每题从中随机一种）
 * @param tempoBpm 速度（每分钟拍数）
 * @param beatsPerQuestion 题目拍数（每拍两个八分音符）
 */
enum class SwingDifficulty(
    val displayName: String,
    val description: String,
    val candidateRatios: List<SwingRatio>,
    val tempoBpm: Int,
    val beatsPerQuestion: Int
) {
    BEGINNER(
        displayName = "初级",
        description = "等分 vs 摇摆 · 2 选项 · 慢速 80 BPM",
        candidateRatios = listOf(SwingRatio.STRAIGHT, SwingRatio.SWING),
        tempoBpm = 80,
        beatsPerQuestion = 4
    ),

    INTERMEDIATE(
        displayName = "中级",
        description = "等分 / 轻摇摆 / 摇摆 · 3 选项 · 中速 100 BPM",
        candidateRatios = listOf(SwingRatio.STRAIGHT, SwingRatio.LIGHT_SWING, SwingRatio.SWING),
        tempoBpm = 100,
        beatsPerQuestion = 4
    ),

    ADVANCED(
        displayName = "高级",
        description = "3 选项（含轻摇摆）· 快速 130 BPM",
        candidateRatios = listOf(SwingRatio.STRAIGHT, SwingRatio.LIGHT_SWING, SwingRatio.SWING),
        tempoBpm = 130,
        beatsPerQuestion = 4
    );

    companion object {
        val ALL: List<SwingDifficulty> = entries.toList()
    }
}

/**
 * 摇摆感辨识训练题目。
 *
 * @param difficulty 难度
 * @param ratio 正确的摇摆感类型
 * @param swingFraction 第二个音符在一拍中的实际时间占比（与 [ratio] 一致）
 * @param tempoBpm 速度
 * @param beatsPerQuestion 拍数
 * @param answerChoices 所有选项（显示名，含正确答案，按摇摆程度排序：等分→轻摇摆→摇摆）
 * @param correctAnswer 正确答案文本
 */
data class SwingQuestion(
    val difficulty: SwingDifficulty,
    val ratio: SwingRatio,
    val swingFraction: Double,
    val tempoBpm: Int,
    val beatsPerQuestion: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 八分音符总数（每拍两个）。 */
    val noteCount: Int get() = beatsPerQuestion * 2

    /** 一拍时长（毫秒）。 */
    val beatMs: Double get() = 60000.0 / tempoBpm

    /** 长短比（长间距 / 短间距），等分时为 1.0。 */
    val swingRatioValue: Double
        get() {
            val long = swingFraction.coerceAtLeast(1.0 - swingFraction)
            val short = (1.0 - swingFraction).coerceAtMost(swingFraction)
            return if (short <= 0.0) 1.0 else long / short
        }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${ratio.displayName}（长短比 ${ratio.ratioLabel}）· $tempoBpm BPM · $beatsPerQuestion 拍"

    init {
        require(tempoBpm > 0) { "速度必须为正数" }
        require(beatsPerQuestion >= 2) { "拍数至少为 2，实际 $beatsPerQuestion" }
        require(swingFraction in 0.4..0.7) { "swingFraction 应在 0.4–0.7 之间，实际 $swingFraction" }
        require(ratio in difficulty.candidateRatios) {
            "摇摆感 ${ratio.displayName} 不在 ${difficulty.displayName} 的候选集合中"
        }
        require(answerChoices.isNotEmpty()) { "选项不能为空" }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(kotlin.math.abs(swingFraction - ratio.fraction) < 1e-9) {
            "swingFraction $swingFraction 与 ${ratio.displayName} 的标准值 ${ratio.fraction} 不一致"
        }
    }
}

/**
 * 一次答题结果。
 */
data class SwingAnswerRecord(
    val question: SwingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
