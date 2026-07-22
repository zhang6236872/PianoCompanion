package com.pianocompanion.modescale

/**
 * 调式音阶色彩对比训练（Mode Scale Color Comparison Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **调式色彩（Mode Color）**：不同的教会调式（church modes）具有不同的「明暗色彩」，
 *   这是音乐中最根本的听觉体验之一。调式的色彩由其音阶中与标准大调/小调的差异音决定——
 *   升高的大三度/大六度/大七度使调式更「明亮」，降低的小二度/小五度/小六度使调式更「暗淡」。
 *
 * 七种教会调式按亮度排列（从最亮到最暗）：
 * 1. **利底亚 Lydian** — 含增四度（#4），最明亮
 * 2. **大调 Ionian** — 标准大调，明亮
 * 3. **混合利底亚 Mixolydian** — 含小七度（b7），略暗于大调
 * 4. **多利亚 Dorian** — 含小三度+大六度，略亮于小调
 * 5. **小调 Aeolian** — 标准自然小调，暗淡
 * 6. **弗里几亚 Phrygian** — 含小二度（b2），更暗淡
 * 7. **洛克利亚 Locrian** — 含减五度（b5），最暗淡（不常用）
 *
 * 训练流程：
 * 1. 听一段上下行的调式音阶（以主音为起止）
 * 2. 判断该音阶属于哪种调式（从选项中选出）
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.moderecognition] 问「这是什么调性/调式」（调性中心辨识）
 * - 本模块问「这个音阶的色彩是什么调式」——专注调式色彩的明暗感知差异
 */

/**
 * 教会调式类型（辨识目标——用户判断播放的音阶属于哪种调式）。
 *
 * 音阶的半音间距以 [semitones] 表示（相对主音，0 = 主音）。
 *
 * @param displayName 中文名
 * @param englishName 英文名
 * @param colorTag 色彩标签（明暗特征的简述）
 * @param semitones 音阶各音相对主音的半音数（含八度，8 个元素）
 * @param characteristicInterval 特征音程（区别于标准大小调的关键音程）
 * @param brightness 亮度指数（-3 最暗 ~ +3 最亮，用于排序和教学）
 */
enum class ModeType(
    val displayName: String,
    val englishName: String,
    val colorTag: String,
    val semitones: List<Int>,
    val characteristicInterval: String,
    val brightness: Int
) {
    IONIAN(
        displayName = "大调（伊奥尼亚）",
        englishName = "Ionian (Major)",
        colorTag = "明亮 · 标准大调",
        semitones = listOf(0, 2, 4, 5, 7, 9, 11, 12),
        characteristicInterval = "大全音音阶 · 无特征变化音",
        brightness = 2
    ),
    DORIAN(
        displayName = "多利亚",
        englishName = "Dorian",
        colorTag = "偏暗 · 含大六度（比小调亮）",
        semitones = listOf(0, 2, 3, 5, 7, 9, 10, 12),
        characteristicInterval = "小三度 + 大六度",
        brightness = 0
    ),
    PHRYGIAN(
        displayName = "弗里几亚",
        englishName = "Phrygian",
        colorTag = "暗淡 · 含小二度",
        semitones = listOf(0, 1, 3, 5, 7, 8, 10, 12),
        characteristicInterval = "小二度（b2）",
        brightness = -2
    ),
    LYDIAN(
        displayName = "利底亚",
        englishName = "Lydian",
        colorTag = "最明亮 · 含增四度",
        semitones = listOf(0, 2, 4, 6, 7, 9, 11, 12),
        characteristicInterval = "增四度（#4）",
        brightness = 3
    ),
    MIXOLYDIAN(
        displayName = "混合利底亚",
        englishName = "Mixolydian",
        colorTag = "偏亮 · 含小七度（比大调暗）",
        semitones = listOf(0, 2, 4, 5, 7, 9, 10, 12),
        characteristicInterval = "小七度（b7）",
        brightness = 1
    ),
    AEOLIAN(
        displayName = "小调（伊奥利亚）",
        englishName = "Aeolian (Minor)",
        colorTag = "暗淡 · 标准自然小调",
        semitones = listOf(0, 2, 3, 5, 7, 8, 10, 12),
        characteristicInterval = "小三度 + 小六度 + 小七度",
        brightness = -1
    ),
    LOCRIAN(
        displayName = "洛克利亚",
        englishName = "Locrian",
        colorTag = "最暗淡 · 含减五度",
        semitones = listOf(0, 1, 3, 5, 6, 8, 10, 12),
        characteristicInterval = "减五度（b5）",
        brightness = -3
    );

    /** 完整标签（如「大调（伊奥尼亚）（Ionian (Major)）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 教学描述（如「多利亚 · 含大六度（比小调亮） · 小三度 + 大六度」）。 */
    val teachingDescription: String
        get() = "$displayName · $colorTag · 特征：$characteristicInterval"

    companion object {
        /** 初级难度使用的调式（大调 vs 小调——最鲜明的明暗对比）。 */
        val BEGINNER_MODES: List<ModeType> = listOf(IONIAN, AEOLIAN)

        /** 中级难度使用的调式（大调/小调/多利亚/混合利底亚——增加色彩变化）。 */
        val INTERMEDIATE_MODES: List<ModeType> = listOf(IONIAN, AEOLIAN, DORIAN, MIXOLYDIAN)

        /** 高级难度使用的调式（全部 7 种教会调式——含利底亚/弗里几亚/洛克利亚）。 */
        val ADVANCED_MODES: List<ModeType> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 选项数量
 * @param modes 该难度使用的调式集合
 * @param tonicMidi 主音 MIDI 音高
 * @param noteDurationMs 每个音符的时长（毫秒）
 * @param gapMs 音符之间的间隔（毫秒）
 * @param playReference 是否播放主音参照
 */
enum class ModeScaleDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val modes: List<ModeType>,
    val tonicMidi: Int,
    val noteDurationMs: Int,
    val gapMs: Int,
    val playReference: Boolean
) {
    BEGINNER(
        "初级",
        "大调 vs 小调 · 2选项",
        choiceCount = 2,
        modes = ModeType.BEGINNER_MODES,
        tonicMidi = 60, // C4
        noteDurationMs = 400,
        gapMs = 30,
        playReference = true
    ),
    INTERMEDIATE(
        "中级",
        "4种调式 · 4选项",
        choiceCount = 4,
        modes = ModeType.INTERMEDIATE_MODES,
        tonicMidi = 60, // C4
        noteDurationMs = 350,
        gapMs = 25,
        playReference = true
    ),
    ADVANCED(
        "高级",
        "7种教会调式 · 7选项",
        choiceCount = 7,
        modes = ModeType.ADVANCED_MODES,
        tonicMidi = 60, // C4
        noteDurationMs = 300,
        gapMs = 20,
        playReference = true
    );

    companion object {
        val ALL: List<ModeScaleDifficulty> = entries.toList()
    }
}

/**
 * 调式音阶色彩对比训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param targetMode 正确的调式
 * @param answerChoices 所有选项标签（含正确答案，已打乱）
 * @param correctAnswer 正确答案标签
 */
data class ModeScaleQuestion(
    val difficulty: ModeScaleDifficulty,
    val seed: Long,
    val targetMode: ModeType,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(targetMode in difficulty.modes) {
            "目标调式 ${targetMode.displayName} 不在难度 ${difficulty.displayName} 的调式集合中"
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
    val fullDescription: String get() = targetMode.teachingDescription

    /** 主音的 MIDI 音高。 */
    val tonicMidi: Int get() = difficulty.tonicMidi
}

/**
 * 一次答题结果。
 */
data class ModeScaleAnswerRecord(
    val question: ModeScaleQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
