package com.pianocompanion.scaledegreetraining

/**
 * 调内音级辨识训练（Scale Degree Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **调内音级（Scale Degree）**：在已建立的调性中，一个音相对于主音（tonic）的位置。
 *   大调音阶有 7 个音级，用数字 1-7 表示，对应唱名 Do / Re / Mi / Fa / Sol / La / Ti。
 *   例如在 C 大调中：C=第1级(Do)，D=第2级(Re)，E=第3级(Mi)，F=第4级(Fa)，
 *   G=第5级(Sol)，A=第6级(La)，B=第7级(Ti)。
 *
 * - **相对音高（Relative Pitch）vs 绝对音高（Absolute Pitch）**：
 *   - 绝对音高要求识别音的字母名（C/D/E…），不依赖调性上下文（见
 *     [com.pianocompanion.pitchtraining]）。
 *   - 相对音高（本训练）要求识别音在调性内的功能位置（DO/RE/MI…），
 *     必须先建立调性中心（通过主和弦），再判断目标音与主音的关系。
 *     这是视唱练耳（solfège，movable-do 体系）的核心能力，也是听写、
 *     即兴伴奏、扒谱的基础——因为你需要知道「这个音是调里的第几级」，
 *     而不仅仅是「它叫什么字母」。
 *
 * - **主和弦建立调性**：训练开始时播放主三和弦（I 和弦 = 第1/3/5 级叠置），
 *   让听者建立调性中心（DO 的感觉），随后播放一个目标音，用户判断其音级。
 *
 * 训练流程：
 * 1. 播放主和弦（建立调性，确立 DO 的位置）
 * 2. 播放目标音
 * 3. 用户从选项中选出该目标音的音级（第几级 / 唱名）
 */

/**
 * 大调音阶的 7 个音级（辨识目标）。
 *
 * @param degree 音级数字（1-7）
 * @param solfege 唱名（movable-do 体系）
 * @param semitonesFromTonic 相对主音的半音数（大调：0,2,4,5,7,9,11）
 * @param displayName 中文显示名（如「第1级」）
 * @param intervalName 该音级相对主音的音程名（教学参考）
 * @param description 描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class ScaleDegree(
    val degree: Int,
    val solfege: String,
    val semitonesFromTonic: Int,
    val displayName: String,
    val intervalName: String,
    val description: String,
    val hint: String
) {
    DO(
        degree = 1,
        solfege = "Do",
        semitonesFromTonic = 0,
        displayName = "第1级",
        intervalName = "纯一度",
        description = "第1级 Do（主音 Tonic）：调性的「家」，最稳定、最有归属感的音。" +
            "无论旋律如何游走，最终都倾向回到 Do。它是整个调性体系的中心。",
        hint = "听起来是「家」的感觉，最稳定、最圆满"
    ),
    RE(
        degree = 2,
        solfege = "Re",
        semitonesFromTonic = 2,
        displayName = "第2级",
        intervalName = "大二度",
        description = "第2级 Re（上主音 Supertonic）：主音上方的邻居，略带向上进行的倾向，" +
            "常作为经过音或下行解决到 Do 的过渡。",
        hint = "紧挨着 Do 上方一步，有「想继续走」的轻飘感"
    ),
    MI(
        degree = 3,
        solfege = "Mi",
        semitonesFromTonic = 4,
        displayName = "第3级",
        intervalName = "大三度",
        description = "第3级 Mi（中音 Mediant）：主三和弦的中间音，决定大调的明亮色彩。" +
            "它与 Do、Sol 共同构成主三和弦，是调性色彩的关键。",
        hint = "明亮温暖，是主和弦的「色彩音」，和 Do、Sol 是一家人"
    ),
    FA(
        degree = 4,
        solfege = "Fa",
        semitonesFromTonic = 5,
        displayName = "第4级",
        intervalName = "纯四度",
        description = "第4级 Fa（下属音 Subdominant）：强烈倾向上行半音解决到 Mi（第3级），" +
            "带有「悬而未决」的紧张感，是和声中制造张力的常用音。",
        hint = "有「悬空」感，强烈想往下走到 Mi，像站在台阶边缘"
    ),
    SOL(
        degree = 5,
        solfege = "Sol",
        semitonesFromTonic = 7,
        displayName = "第5级",
        intervalName = "纯五度",
        description = "第5级 Sol（属音 Dominant）：仅次于主音的第二重要音，是属功能和弦的根音。" +
            "它强烈倾向解决回主音 Do，构成调性中最基本的张力-解决（V→I）。",
        hint = "稳定但比 Do 略「亮」，是属和弦的根音，倾向回到 Do"
    ),
    LA(
        degree = 6,
        solfege = "La",
        semitonesFromTonic = 9,
        displayName = "第6级",
        intervalName = "大六度",
        description = "第6级 La（下中音 Submediant）：关系小调的主音，带有柔和暗淡的色彩。" +
            "它是大调中引入小调色彩的桥梁（如伪终止 V→vi）。",
        hint = "柔和暗淡，是关系小调的「家」，像大调里的一抹忧郁"
    ),
    TI(
        degree = 7,
        solfege = "Ti",
        semitonesFromTonic = 11,
        displayName = "第7级",
        intervalName = "大七度",
        description = "第7级 Ti（导音 Leading Tone）：最强的「倾向性」音，半音上行强烈解决到 Do。" +
            "它是导音之名（leading tone）的由来——「带领」回到主音。",
        hint = "尖锐紧张，像磁铁一样被「拉」向 Do，强烈想上行半音解决"
    );

    /** 完整标签（如「第1级 Do」）。 */
    val fullLabel: String get() = "$displayName $solfege"

    companion object {
        val ALL: List<ScaleDegree> = entries.toList()

        /** 初级难度候选：主音 vs 属音（最根本的二元对比）。 */
        val BEGINNER_DEGREES: List<ScaleDegree> = listOf(DO, SOL)

        /** 中级难度候选：主三和弦音级 + 下属音（Do/Mi/Fa/Sol）。 */
        val INTERMEDIATE_DEGREES: List<ScaleDegree> = listOf(DO, MI, FA, SOL)

        /** 高级难度候选：全部 7 个音级。 */
        val ADVANCED_DEGREES: List<ScaleDegree> = ALL

        /** 按半音偏移查找音级。 */
        fun fromSemitones(semitones: Int): ScaleDegree? =
            ALL.firstOrNull { it.semitonesFromTonic == semitones }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param degrees 该难度可用的音级候选集
 * @param choiceCount 该难度的选项数量
 */
enum class ScaleDegreeDifficulty(
    val displayName: String,
    val description: String,
    val degrees: List<ScaleDegree>,
    val choiceCount: Int
) {
    BEGINNER(
        "初级",
        "Do vs Sol · 2 选项 · 主音与属音的二元对比",
        ScaleDegree.BEGINNER_DEGREES,
        2
    ),
    INTERMEDIATE(
        "中级",
        "Do/Mi/Fa/Sol · 4 选项 · 主三和弦 + 下属音",
        ScaleDegree.INTERMEDIATE_DEGREES,
        4
    ),
    ADVANCED(
        "高级",
        "全部 7 个音级 · Do Re Mi Fa Sol La Ti · 完整大调音阶",
        ScaleDegree.ADVANCED_DEGREES,
        7
    );

    companion object {
        val ALL: List<ScaleDegreeDifficulty> = entries.toList()
    }
}

/**
 * 调内音级辨识训练题目。
 *
 * @param degree 正确的音级
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param tonicMidi 主音（Do）的 MIDI 音高
 * @param targetMidi 目标音的 MIDI 音高
 * @param answerChoices 所有选项（音级完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class ScaleDegreeQuestion(
    val degree: ScaleDegree,
    val difficulty: ScaleDegreeDifficulty,
    val seed: Long,
    val tonicMidi: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 目标音的 MIDI 音高 = 主音 + 半音偏移。 */
    val targetMidi: Int get() = tonicMidi + degree.semitonesFromTonic

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${degree.displayName}（${degree.solfege}）· 主音上方 ${degree.intervalName}"

    init {
        require(tonicMidi in MIN_TONIC_MIDI..MAX_TONIC_MIDI) {
            "主音 MIDI $tonicMidi 超出范围 [$MIN_TONIC_MIDI, $MAX_TONIC_MIDI]"
        }
        require(degree in difficulty.degrees) {
            "音级 ${degree.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
    }
}

/**
 * 一次答题结果。
 */
data class ScaleDegreeAnswerRecord(
    val question: ScaleDegreeQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 主音音域常量（C3=48 到 C5=72，目标音不超过 MIDI 84）。 */
const val MIN_TONIC_MIDI: Int = 48
const val MAX_TONIC_MIDI: Int = 72
