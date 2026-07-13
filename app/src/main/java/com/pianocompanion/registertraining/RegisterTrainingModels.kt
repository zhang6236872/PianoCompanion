package com.pianocompanion.registertraining

/**
 * 音区辨识训练（Register Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **音区辨识（Register Recognition）**：用户听到一段相同旋律（C 大调琶音）在不同
 *   八度区域演奏，需要根据音高范围判断属于哪个音区。
 * - 与音程训练的区别：
 *   - **音程训练（IntervalTraining）**：关注两个音之间的距离（半音数）
 *   - **音区辨识（RegisterTraining）**：关注整段旋律所处的**八度位置**，
 *     训练对不同音区的整体感知能力（从深沉低音到尖锐高音的定位）
 *
 * 本模块支持 6 种音区（从小字组到小字四组）：
 *   1. DEEP_BASS（低低音区/大字组）C2 八度 — 深沉厚重
 *   2. BASS（低音区/小字组）C3 八度 — 温暖饱满
 *   3. TENOR（中音区/小字一组）C4 八度 — 明亮自然
 *   4. ALTO（中高音区/小字二组）C5 八度 — 清晰亮丽
 *   5. SOPRANO（高音区/小字三组）C6 八度 — 尖锐明亮
 *   6. TOP（极高音区/小字四组）C7 八度 — 穿透力极强
 */

/**
 * 音区类型（音区辨识目标）。
 *
 * 每种音区对应一个八度区域，以 C 音为基准命名。
 * 音频渲染使用该八度的 C 大调琶音（C-E-G-C）。
 *
 * @param englishName 英文名（如 "Deep Bass"）
 * @param displayName 中文名（如 "低低音区"）
 * @param octaveName 科学音高表示法八度名（如 "C2 八度"）
 * @param emoji 表情符号（UI 图标）
 * @param description 音区描述（答题后的教学反馈）
 * @param baseC C 音频率（Hz），该音区的琶音起始音
 */
enum class MusicRegister(
    val englishName: String,
    val displayName: String,
    val octaveName: String,
    val emoji: String,
    val description: String,
    val baseC: Double
) {
    DEEP_BASS(
        englishName = "Deep Bass",
        displayName = "低低音区",
        octaveName = "C2 八度",
        emoji = "🐘",
        description = "低低音区（C2 八度）：深沉厚重，如同大提琴最低弦的共鸣。低音提琴、管风琴低音踏板的声音范围。",
        baseC = 65.41
    ),
    BASS(
        englishName = "Bass",
        displayName = "低音区",
        octaveName = "C3 八度",
        emoji = "🐻",
        description = "低音区（C3 八度）：温暖饱满，男低音的歌唱音域。左手伴奏、大提琴低音区的自然范围。",
        baseC = 130.81
    ),
    TENOR(
        englishName = "Tenor",
        displayName = "中音区",
        octaveName = "C4 八度",
        emoji = "🎵",
        description = "中音区（C4 八度）：明亮自然，钢琴中央 C 所在的八度。最舒适、最常用的演奏音区。",
        baseC = 261.63
    ),
    ALTO(
        englishName = "Alto",
        displayName = "中高音区",
        octaveName = "C5 八度",
        emoji = "🦋",
        description = "中高音区（C5 八度）：清晰亮丽，女高音的歌唱音域。小提琴明亮弦区的声音范围。",
        baseC = 523.25
    ),
    SOPRANO(
        englishName = "Soprano",
        displayName = "高音区",
        octaveName = "C6 八度",
        emoji = "🕊️",
        description = "高音区（C6 八度）：尖锐明亮，如同鸟鸣般清透。长笛高音区、短笛的常用音域。",
        baseC = 1046.50
    ),
    TOP(
        englishName = "Top",
        displayName = "极高音区",
        octaveName = "C7 八度",
        emoji = "✨",
        description = "极高音区（C7 八度）：穿透力极强，如同哨音般尖锐。短笛最高音区、钟琴的清脆声。",
        baseC = 2093.00
    );

    init {
        check(baseC > 0.0) { "$englishName: baseC=$baseC 必须为正数" }
    }

    /** 完整标识（如 "🐘 Deep Bass  低低音区"）。 */
    val fullLabel: String get() = "$displayName"

    /** 摘要（如 "低低音区 (C2 八度)"）。 */
    val summary: String get() = "$displayName（$octaveName）"

    /**
     * 该音区的 C 大调琶音频率数组（C-E-G-C）。
     * E = C × 2^(4/12), G = C × 2^(7/12), 高八度 C = C × 2
     */
    val arpeggioFrequencies: DoubleArray
        get() = doubleArrayOf(
            baseC,
            baseC * 1.2599210499,  // E: 2^(4/12) ≈ 1.25992
            baseC * 1.4983070769,  // G: 2^(7/12) ≈ 1.49831
            baseC * 2.0            // C (高一八度)
        )

    companion object {
        val ALL: List<MusicRegister> = entries.toList()

        /** 初级音区：极端差距（低低音/中音/高音），凭直觉即可区分。 */
        val BEGINNER_REGISTERS: List<MusicRegister> = listOf(DEEP_BASS, TENOR, SOPRANO)

        /** 中级音区：中等差距（低低音/低音/中高音/高音），加入相邻音区。 */
        val INTERMEDIATE_REGISTERS: List<MusicRegister> = listOf(DEEP_BASS, BASS, ALTO, SOPRANO)

        /**
         * 按难度返回可用音区集合。
         * - 初级：3 种极端音区（低低音/中音/高音），频率差距最大
         * - 中级：4 种中等差距音区（低低音/低音/中高音/高音），加入相邻音区
         * - 高级：全部 6 种，包含相邻音区（低音 vs 中音、中高音 vs 高音），考验精细辨识
         */
        fun forDifficulty(difficulty: RegisterTrainingDifficulty): List<MusicRegister> = when (difficulty) {
            RegisterTrainingDifficulty.BEGINNER -> BEGINNER_REGISTERS
            RegisterTrainingDifficulty.INTERMEDIATE -> INTERMEDIATE_REGISTERS
            RegisterTrainingDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和音区列表）
 * @param choiceCount 该难度的选项数量
 */
enum class RegisterTrainingDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种极端音区（3 选项）· 低低音区 / 中音区 / 高音区", 3),
    INTERMEDIATE("中级", "4 种中等音区（4 选项）· 加入低音区 / 中高音区", 4),
    ADVANCED("高级", "全部 6 种含相邻音区（6 选项）· 细致区分相邻八度", 6);

    companion object {
        val ALL: List<RegisterTrainingDifficulty> = entries.toList()
    }
}

/**
 * 音区辨识训练题目。
 *
 * @param register 正确的音区类型
 * @param difficulty 难度
 * @param noteCount 播放的音符数量
 * @param answerChoices 所有选项（音区名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class RegisterTrainingQuestion(
    val register: MusicRegister,
    val difficulty: RegisterTrainingDifficulty,
    val noteCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "低低音区（C2 八度）"）。 */
    val fullName: String get() = register.summary
}

/**
 * 一次答题结果。
 */
data class RegisterTrainingAnswerRecord(
    val question: RegisterTrainingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
