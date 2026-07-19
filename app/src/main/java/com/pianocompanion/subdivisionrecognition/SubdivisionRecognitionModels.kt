package com.pianocompanion.subdivisionrecognition

/**
 * 节奏细分听辨训练（Beat Subdivision Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **节奏细分听辨（Beat Subdivision Recognition）**：用户听到一段节拍序列，每一拍被**等分**
 *   成若干个音符。需要判断**一拍被分成了几等份**——是二分（2，八分音符 / "直"的感觉）、
 *   三连音（3，摇摆 / 圆舞曲感），还是四分（4，十六分音符 / 密集感）。
 * - 这是节奏听辨中最基础的能力之一：**「2 对 3 对 4」的律动感（duple / triple / quadruple feel）**。
 *   感知一拍内部的细分密度，是读谱、视奏、即兴的核心节奏素养。
 *
 * 与已有节奏训练模块的区别：
 * - **拍号听辨（[com.pianocompanion.meterrecognition]）**：判断每小节有几拍（2/4、3/4、4/4 …）
 *   ——关注节拍的**宏观分组**，答案是拍号。
 * - **强拍辨识（[com.pianocompanion.accentrecognition]）**：判断重音落在第几拍——
 *   关注重音的**周期位置**。
 * - **节奏型听辨（[com.pianocompanion.rhythmpattern]）**：识别整小节的命名节奏型。
 * - **复节奏（[com.pianocompanion.polyrhythmtraining]）**：同时响起的两种冲突节拍。
 * - **本模块（节奏细分）**：判断**一拍被等分成几份**（2 / 3 / 4）——关注拍**内部**的细分密度，
 *   答案是「每拍几个音」。无论拍号如何，单拍细分密度都是独立的节奏维度。
 *
 * 训练流程：
 * 1. 播放一段由若干拍构成的乐句，每拍等分成 N 个音符（每拍起始音略加重音标记拍位）。
 * 2. 用户聆听后，判断每拍被分成了几等份。
 * 3. 从「二分 / 三连音 / 四分」选项中选出正确答案。
 */

/**
 * 拍的细分类型（每拍等分成几个音符）。
 *
 * @param notesPerBeat 每拍等分成的音符数
 * @param displayName 中文显示名
 * @param symbol 符号示意（用于 UI 展示）
 * @param italianName 意大利语术语
 * @param description 听感描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class SubdivisionType(
    val notesPerBeat: Int,
    val displayName: String,
    val symbol: String,
    val italianName: String,
    val description: String,
    val hint: String
) {
    /** 二分细分：每拍等分为 2 个音符（八分音符），「直」的节奏，最常见的细分。 */
    DUPLE(
        notesPerBeat = 2,
        displayName = "二分细分",
        symbol = "♪ ♪",
        italianName = "binario",
        description = "每拍等分为 2 个音符（八分音符），即「直」的节奏。进行曲、摇滚、流行乐中最常见的细分方式。",
        hint = "听感平稳、规整，像走路「1-and、2-and」。一拍里能数出 2 个等距的音。"
    ),

    /** 三连音：每拍等分为 3 个音符（三连音），摇摆、圆舞曲式的「3」的感觉。 */
    TRIPLE(
        notesPerBeat = 3,
        displayName = "三连音",
        symbol = "♪ ♪ ♪",
        italianName = "ternario",
        description = "每拍等分为 3 个音符（三连音）。摇摆乐、圆舞曲、爵士中常见的「3」的律动感。",
        hint = "听感圆滑、有「摇晃感」，像念「tri-o-la」。一拍里能数出 3 个等距的音。"
    ),

    /** 四分细分：每拍等分为 4 个音符（十六分音符），密集、快速的细分。 */
    QUADRUPLE(
        notesPerBeat = 4,
        displayName = "四分细分",
        symbol = "♬ ♬",
        italianName = "quaternario",
        description = "每拍等分为 4 个音符（十六分音符）。快速段落、密集跑动中的典型细分。",
        hint = "听感密集、紧凑，像「1-e-and-a」。一拍里能数出 4 个等距的音。"
    );

    companion object {
        val ALL: List<SubdivisionType> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * 难度通过**候选细分种类、速度、吐音清晰度**三个维度递进：
 * - 初级只区分「2 对 3」（最基础的二元 vs 三元），慢速、断奏清晰。
 * - 中级加入「4」（三选一），中速。
 * - 高级仍是三选一，但**连奏（legato）模糊音头**且更快，须靠整体律动感而非数音头。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param subdivisionOptions 本难度可能的细分类型集合（每题从中随机一种）
 * @param beatMs 一拍的时长（毫秒），越短越快越难
 * @param beatsPerMeasure 每小节拍数
 * @param measureRepeat 小节重复播放次数
 * @param staccato true=断奏（音头清晰，易数）；false=连奏（音符重叠，靠律动感）
 */
enum class SubdivisionDifficulty(
    val displayName: String,
    val description: String,
    val subdivisionOptions: List<SubdivisionType>,
    val beatMs: Double,
    val beatsPerMeasure: Int,
    val measureRepeat: Int,
    val staccato: Boolean
) {
    BEGINNER(
        displayName = "初级",
        description = "2 选项 · 二分 vs 三连音 · 慢速 · 断奏清晰",
        subdivisionOptions = listOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE),
        beatMs = 620.0,
        beatsPerMeasure = 2,
        measureRepeat = 3,
        staccato = true
    ),

    INTERMEDIATE(
        displayName = "中级",
        description = "3 选项 · 加入四分细分 · 中速 · 断奏",
        subdivisionOptions = listOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE, SubdivisionType.QUADRUPLE),
        beatMs = 500.0,
        beatsPerMeasure = 2,
        measureRepeat = 3,
        staccato = true
    ),

    ADVANCED(
        displayName = "高级",
        description = "3 选项 · 连奏模糊音头 · 快速 · 靠律动感",
        subdivisionOptions = listOf(SubdivisionType.DUPLE, SubdivisionType.TRIPLE, SubdivisionType.QUADRUPLE),
        beatMs = 400.0,
        beatsPerMeasure = 2,
        measureRepeat = 3,
        staccato = false
    );

    companion object {
        val ALL: List<SubdivisionDifficulty> = entries.toList()
    }
}

/**
 * 节奏细分听辨训练题目。
 *
 * @param difficulty 难度
 * @param subdivision 正确的细分类型
 * @param beatMs 一拍时长（毫秒）
 * @param beatsPerMeasure 每小节拍数
 * @param measureRepeat 小节重复次数
 * @param staccato 是否断奏
 * @param answerChoices 所有选项（细分显示名，含正确答案，已按细分密度排序）
 * @param correctAnswer 正确答案文本
 */
data class SubdivisionQuestion(
    val difficulty: SubdivisionDifficulty,
    val subdivision: SubdivisionType,
    val beatMs: Double,
    val beatsPerMeasure: Int,
    val measureRepeat: Int,
    val staccato: Boolean,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 总音符数（细分密度 × 拍数 × 重复次数）。 */
    val totalNotes: Int get() = subdivision.notesPerBeat * beatsPerMeasure * measureRepeat

    /** 相邻两个细分音符之间的时间间隔（毫秒）。 */
    val subdivIntervalMs: Double get() = beatMs / subdivision.notesPerBeat

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${subdivision.displayName}（每拍 ${subdivision.notesPerBeat} 音）· " +
            "${"%.0f".format(beatMs)}ms/拍 · ${if (staccato) "断奏" else "连奏"}"

    init {
        require(beatMs > 0) { "一拍时长必须为正数" }
        require(beatsPerMeasure in 1..8) {
            "每小节拍数 $beatsPerMeasure 超出范围 [1, 8]"
        }
        require(measureRepeat in 1..10) {
            "小节重复次数 $measureRepeat 超出范围 [1, 10]"
        }
        require(subdivision in difficulty.subdivisionOptions) {
            "细分类型 ${subdivision.displayName} 不在 ${difficulty.displayName} 的候选集合中"
        }
        require(answerChoices.isNotEmpty()) { "选项不能为空" }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
    }
}

/**
 * 一次答题结果。
 */
data class SubdivisionAnswerRecord(
    val question: SubdivisionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
